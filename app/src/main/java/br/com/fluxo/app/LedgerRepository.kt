package br.com.fluxo.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Category
import br.com.fluxo.core.Direction
import br.com.fluxo.core.NotificationParser
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.util.UUID
import java.security.MessageDigest
import java.time.YearMonth
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
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets
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

    suspend fun exportBackupPayload(): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock { LedgerBackup.export(database.readableDatabase) }
    }

    fun inspectBackupPayload(payload: ByteArray): BackupSummary = LedgerBackup.validate(payload).summary

    suspend fun restoreBackupPayload(payload: ByteArray) = withContext(Dispatchers.IO) {
        val validated = LedgerBackup.validate(payload)
        mutex.withLock {
            // Device permissions and bank selection are not imported. Consent stays local.
            setCaptureEnabled(false)
            LedgerBackup.restore(database.writableDatabase, validated)
            _captureError.value = null
            readAll()
        }
    }

    /** A fallback identity belongs to one active notification lifecycle, never to an amount. */
    suspend fun ingest(
        sourceKey: String, bankPackage: String, occurredAt: Long, text: String,
        fallbackIdentity: String? = null, observedAt: Long = occurredAt,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!_captureEnabled.value || bankPackage !in _enabledBanks.value) return@withLock
            val parsed = NotificationParser.parse(text) ?: return@withLock
            val signature = MessageDigest.getInstance("SHA-256").digest(
                listOf(parsed.amountCents, parsed.direction, parsed.method, parsed.status, parsed.reason)
                    .joinToString("|").toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }
            val db = database.writableDatabase
            db.beginTransaction()
            try {
                val canonicalKey = fallbackIdentity?.let { identity ->
                    db.query("notification_aliases", arrayOf("source_key"), "identity_key = ?",
                        arrayOf(identity), null, null, null).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                } ?: sourceKey
                var exists = false
                db.query("transactions", arrayOf("id", "deleted", "user_modified", "observation_hash", "last_observed_at"),
                    "source_key = ?", arrayOf(canonicalKey), null, null, null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        exists = true
                        // Older callbacks, tombstones and human corrections always win over automation.
                        if (cursor.getInt(1) == 0 && cursor.getInt(2) == 0 && observedAt >= cursor.getLong(4)) {
                            val changed = cursor.getString(3) != signature
                            val updates = ContentValues().apply {
                                put("last_observed_at", observedAt)
                                if (changed) {
                                    put("amount_cents", parsed.amountCents)
                                    put("direction", parsed.direction.name)
                                    put("method", parsed.method.name)
                                    put("status", ReviewStatus.REVIEW.name)
                                    put("reason", "Notificação atualizada; confira a movimentação." +
                                        parsed.reason.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
                                    put("observation_hash", signature)
                                }
                            }
                            db.update("transactions", updates, "id = ?", arrayOf(cursor.getString(0)))
                        }
                    }
                }
                if (!exists) {
                    val transaction = Transaction(
                        id = UUID.randomUUID().toString(), sourceKey = canonicalKey,
                        bankPackage = bankPackage, amountCents = parsed.amountCents,
                        direction = parsed.direction, method = parsed.method, status = parsed.status,
                        occurredAt = occurredAt, description = "Movimentação detectada", reason = parsed.reason,
                    )
                    db.insertOrThrow("transactions", null, values(transaction).apply {
                        put("user_modified", 0)
                        put("observation_hash", signature)
                        put("last_observed_at", observedAt)
                    })
                }
                if (fallbackIdentity != null) {
                    db.insertWithOnConflict("notification_aliases", null, ContentValues().apply {
                        put("identity_key", fallbackIdentity)
                        put("source_key", canonicalKey)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            _captureError.value = null
            readAll()
        }
    }

    /** Removing a notification ends its fallback identity; a reused Android key is a new event. */
    suspend fun forgetNotification(identityKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.writableDatabase.delete("notification_aliases", "identity_key = ?", arrayOf(identityKey))
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
            val changed = db.update("transactions", values(transaction).apply { put("user_modified", 1) }, "id = ? AND deleted = 0", arrayOf(transaction.id))
            if (changed == 0) db.insertOrThrow("transactions", null, values(transaction).apply { put("user_modified", 1) })
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
                put("category", Category.UNCATEGORIZED.name)
            }, "id = ?", arrayOf(id))
            readAll()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            setCaptureEnabled(false)
            val db = database.writableDatabase
            db.beginTransaction()
            try {
                db.delete("transactions", null, null)
                db.delete("budgets", null, null)
                db.delete("notification_aliases", null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            _captureError.value = null
            readAll()
        }
    }

    suspend fun saveBudget(budget: Budget) = withContext(Dispatchers.IO) {
        validateMonth(budget.month)
        require(budget.limitCents > 0) { "Informe um limite positivo." }
        mutex.withLock {
            database.writableDatabase.insertWithOnConflict("budgets", null, ContentValues().apply {
                put("month", budget.month)
                put("category", budget.category.name)
                put("limit_cents", budget.limitCents)
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Não foi possível salvar o orçamento." } }
            readAll()
        }
    }

    suspend fun deleteBudget(month: String, category: Category) = withContext(Dispatchers.IO) {
        validateMonth(month)
        mutex.withLock {
            database.writableDatabase.delete("budgets", "month = ? AND category = ?", arrayOf(month, category.name))
            readAll()
        }
    }

    private fun validateMonth(month: String) {
        val parsed = try { YearMonth.parse(month) } catch (_: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Informe um mês válido no formato AAAA-MM.")
        }
        require(parsed.toString() == month) { "Informe um mês válido no formato AAAA-MM." }
    }

    private fun values(t: Transaction) = ContentValues().apply {
        put("id", t.id); put("source_key", t.sourceKey); put("bank_package", t.bankPackage)
        put("amount_cents", t.amountCents); put("direction", t.direction.name)
        put("method", t.method.name); put("status", t.status.name); put("occurred_at", t.occurredAt)
        put("description", t.description); put("reason", t.reason); put("deleted", 0)
        put("category", t.category.name)
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
                    category = Category.valueOf(string("category")),
                )
            }
        }
        val limits = mutableListOf<Budget>()
        database.readableDatabase.query("budgets", null, null, null, null, null, "month DESC, category ASC").use { cursor ->
            while (cursor.moveToNext()) {
                limits += Budget(
                    month = cursor.getString(cursor.getColumnIndexOrThrow("month")),
                    category = Category.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("category"))),
                    limitCents = cursor.getLong(cursor.getColumnIndexOrThrow("limit_cents")),
                )
            }
        }
        _transactions.value = result
        _budgets.value = limits
    }
}

private class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "fluxo.db", null, 3) {
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
                deleted INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT 'UNCATEGORIZED',
                user_modified INTEGER NOT NULL DEFAULT 0,
                observation_hash TEXT,
                last_observed_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_transaction_date ON transactions(deleted, occurred_at)")
        createBudgets(db)
        createNotificationAliases(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'UNCATEGORIZED'")
            createBudgets(db)
        }
        if (oldVersion < 3) {
            // Older schemas did not record which fields were edited. Preserve every old row.
            db.execSQL("ALTER TABLE transactions ADD COLUMN user_modified INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE transactions ADD COLUMN observation_hash TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN last_observed_at INTEGER NOT NULL DEFAULT 0")
            createNotificationAliases(db)
        }
    }

    private fun createNotificationAliases(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notification_aliases (identity_key TEXT PRIMARY KEY NOT NULL, source_key TEXT NOT NULL)")
    }

    private fun createBudgets(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE budgets (
                month TEXT NOT NULL,
                category TEXT NOT NULL,
                limit_cents INTEGER NOT NULL CHECK(limit_cents > 0),
                PRIMARY KEY(month, category)
            )
        """.trimIndent())
    }
}
