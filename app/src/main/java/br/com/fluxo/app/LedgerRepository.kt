package br.com.fluxo.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.fluxo.core.Direction
import br.com.fluxo.core.NotificationParser
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Bank(val packageName: String, val name: String)
val supportedBanks = listOf(
    Bank("com.c6bank.app", "C6 Bank"),
    Bank("com.nu.production", "Nubank"),
    Bank("com.itau", "Itaú"),
)

class LedgerRepository(context: Context) {
    private val preferences = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
    private val database = LedgerDatabase(context.applicationContext)
    private val mutex = Mutex()
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions
    private val _captureEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val captureEnabled: StateFlow<Boolean> = _captureEnabled
    private val _enabledBanks = MutableStateFlow(preferences.getStringSet("banks", emptySet())!!.toSet())
    val enabledBanks: StateFlow<Set<String>> = _enabledBanks
    private val _captureError = MutableStateFlow<String?>(null)
    val captureError: StateFlow<String?> = _captureError

    @Synchronized
    fun setCaptureEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply()
        _captureEnabled.value = value
    }

    @Synchronized
    fun setBankEnabled(packageName: String, enabled: Boolean) {
        require(supportedBanks.any { it.packageName == packageName })
        val selected = if (enabled) _enabledBanks.value + packageName else _enabledBanks.value - packageName
        preferences.edit().putStringSet("banks", selected).apply()
        _enabledBanks.value = selected
    }

    fun reportCaptureError() {
        _captureError.value = "Não foi possível salvar uma notificação. Verifique o armazenamento do aparelho."
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { readAll() } }

    suspend fun ingest(sourceKey: String, bankPackage: String, occurredAt: Long, text: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!_captureEnabled.value || bankPackage !in _enabledBanks.value) return@withLock
                val parsed = NotificationParser.parse(text) ?: return@withLock
                val transaction = Transaction(
                    id = UUID.randomUUID().toString(), sourceKey = sourceKey,
                    bankPackage = bankPackage, amountCents = parsed.amountCents,
                    direction = parsed.direction, method = parsed.method, status = parsed.status,
                    occurredAt = occurredAt, description = "Movimentação detectada", reason = parsed.reason,
                )
                database.writableDatabase.insertWithOnConflict(
                    "transactions", null, values(transaction), SQLiteDatabase.CONFLICT_IGNORE,
                )
                _captureError.value = null
                readAll()
            }
        }

    suspend fun save(transaction: Transaction) = withContext(Dispatchers.IO) {
        require(transaction.amountCents?.let { it > 0 } != false) { "Informe um valor positivo." }
        require(transaction.status != ReviewStatus.CONFIRMED ||
            (transaction.amountCents != null && transaction.direction != Direction.UNKNOWN)) {
            "Confirme o valor e a direção do lançamento."
        }
        require(transaction.description.length <= 200) { "Descrição muito longa." }
        mutex.withLock {
            val db = database.writableDatabase
            val changed = db.update("transactions", values(transaction), "id = ? AND deleted = 0", arrayOf(transaction.id))
            if (changed == 0) db.insertOrThrow("transactions", null, values(transaction))
            readAll()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.writableDatabase.update("transactions", ContentValues().apply {
                put("deleted", 1)
                putNull("amount_cents")
                put("description", "")
                put("reason", "")
            }, "id = ?", arrayOf(id))
            readAll()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            setCaptureEnabled(false)
            database.writableDatabase.delete("transactions", null, null)
            _captureError.value = null
            readAll()
        }
    }

    private fun values(t: Transaction) = ContentValues().apply {
        put("id", t.id); put("source_key", t.sourceKey); put("bank_package", t.bankPackage)
        put("amount_cents", t.amountCents); put("direction", t.direction.name)
        put("method", t.method.name); put("status", t.status.name); put("occurred_at", t.occurredAt)
        put("description", t.description); put("reason", t.reason); put("deleted", 0)
    }

    private fun readAll() {
        val result = mutableListOf<Transaction>()
        database.readableDatabase.query("transactions", null, "deleted = 0", null, null, null,
            "occurred_at DESC, id ASC").use { cursor ->
            fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
            while (cursor.moveToNext()) {
                val amountIndex = cursor.getColumnIndexOrThrow("amount_cents")
                result += Transaction(
                    id = string("id"), sourceKey = string("source_key"), bankPackage = string("bank_package"),
                    amountCents = if (cursor.isNull(amountIndex)) null else cursor.getLong(amountIndex),
                    direction = Direction.valueOf(string("direction")), method = PaymentMethod.valueOf(string("method")),
                    status = ReviewStatus.valueOf(string("status")),
                    occurredAt = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
                    description = string("description"), reason = string("reason"),
                )
            }
        }
        _transactions.value = result
    }
}

private class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "fluxo.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY NOT NULL,
                source_key TEXT UNIQUE,
                bank_package TEXT NOT NULL,
                amount_cents INTEGER CHECK(amount_cents IS NULL OR amount_cents > 0),
                direction TEXT NOT NULL,
                method TEXT NOT NULL,
                status TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                description TEXT NOT NULL,
                reason TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_transaction_date ON transactions(deleted, occurred_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Migração de banco de dados não implementada: $oldVersion → $newVersion")
    }
}
