package br.com.fluxo.app

import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.BenefitSettings
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Account
import br.com.fluxo.core.AccountType
import br.com.fluxo.core.Category
import br.com.fluxo.core.Direction
import br.com.fluxo.core.NotificationCapturePolicy
import br.com.fluxo.core.NotificationParser
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.util.UUID
import java.security.MessageDigest
import java.time.DayOfWeek
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
    private val appPackageName = context.applicationContext.packageName
    private val preferences = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
    private val benefitPreferences = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
    private val _benefits = MutableStateFlow(readBenefits())
    val benefits: StateFlow<BenefitSettings> = _benefits
    // Local profile. The name never leaves the device: no logs, no CSV, no backup payload.
    // Profile and benefits live in their own preference files, so clear() never touches them.
    private val profilePreferences = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _profileName = MutableStateFlow(cleanProfileName(profilePreferences.getString("name", null).orEmpty()))
    val profileName: StateFlow<String> = _profileName
    private val _onboardingDone = MutableStateFlow(profilePreferences.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone
    private val database = LedgerDatabase(context.applicationContext)
    private val mutex = Mutex()
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions
    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts
    private val _captureEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val captureEnabled: StateFlow<Boolean> = _captureEnabled
    private val _allowOtherApps = MutableStateFlow(preferences.getBoolean("allow_other_apps", false))
    val allowOtherApps: StateFlow<Boolean> = _allowOtherApps
    private val _enabledBanks = MutableStateFlow(preferences.getStringSet("banks", emptySet())!!.toSet())
    val enabledBanks: StateFlow<Set<String>> = _enabledBanks
    private val _captureError = MutableStateFlow<String?>(null)
    val captureError: StateFlow<String?> = _captureError

    private fun readBenefits(): BenefitSettings {
        val stored = benefitPreferences.all
        fun rate(key: String) = (stored[key] as? Long)?.takeIf { it >= 0 } ?: 0L
        val overrides = mutableMapOf<YearMonth, BenefitDays>()
        stored.forEach { (key, value) ->
            if (key.startsWith("vr_days:") || key.startsWith("vt_days:")) {
                val rawMonth = key.substringAfter(':')
                val month = runCatching { YearMonth.parse(rawMonth) }.getOrNull()
                val days = value as? Int
                if (month != null && month.toString() == rawMonth && days != null && days in 0..month.lengthOfMonth()) {
                    val current = overrides[month] ?: BenefitDays()
                    overrides[month] = if (key.startsWith("vr_days:")) current.copy(vr = days) else current.copy(vt = days)
                }
            }
        }
        return BenefitSettings(rate("vr_daily_cents"), rate("vt_daily_cents"), overrides.toMap(),
            weekdaysFrom(stored["vr_weekdays"]), weekdaysFrom(stored["vt_weekdays"]))
    }

    private fun cleanProfileName(raw: String): String = raw.trim().take(MAX_PROFILE_NAME).trim()

    /** Missing or malformed values fall back to Monday-Friday; mask 0 is a valid empty schedule. */
    private fun weekdaysFrom(stored: Any?): Set<DayOfWeek> {
        val mask = stored as? Int
        return if (mask == null || mask !in 0..WEEKDAY_MASK_ALL) BenefitCalculator.WORKDAYS else weekdaysOfMask(mask)
    }

    /** Rates, weekday sets and the month override are committed together; null weekdays keep the stored set. */
    suspend fun saveBenefits(
        month: YearMonth, vrDailyCents: Long, vtDailyCents: Long, days: BenefitDays,
        vrWeekdays: Set<DayOfWeek>? = null, vtWeekdays: Set<DayOfWeek>? = null,
    ) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                BenefitCalculator.validateDays(month, days)
                val current = readBenefits()
                val overrides = current.overrides.toMutableMap()
                if (days == BenefitDays()) overrides.remove(month) else overrides[month] = days
                val vrSet = vrWeekdays ?: current.vrWeekdays
                val vtSet = vtWeekdays ?: current.vtWeekdays
                val updated = BenefitSettings(vrDailyCents, vtDailyCents, overrides.toMap(), vrSet, vtSet)
                val editor = benefitPreferences.edit()
                    .putLong("vr_daily_cents", vrDailyCents)
                    .putLong("vt_daily_cents", vtDailyCents)
                    .putInt("vr_weekdays", weekdayMask(vrSet))
                    .putInt("vt_weekdays", weekdayMask(vtSet))
                val vrKey = "vr_days:$month"
                val vtKey = "vt_days:$month"
                val vrDays = days.vr
                val vtDays = days.vt
                if (vrDays == null) editor.remove(vrKey) else editor.putInt(vrKey, vrDays)
                if (vtDays == null) editor.remove(vtKey) else editor.putInt(vtKey, vtDays)
                // Commit on IO: do not announce success before disk persistence succeeds.
                check(editor.commit()) { "Não foi possível salvar os benefícios no aparelho." }
                _benefits.value = updated
            }
        }

    /** Caller holds the mutex. Replaces stored benefits entirely; runs only after the SQLite restore succeeded. */
    private fun replaceBenefits(settings: BenefitSettings) {
        val editor = benefitPreferences.edit()
        benefitPreferences.all.keys.filter { it.startsWith("vr_days:") || it.startsWith("vt_days:") }
            .forEach { editor.remove(it) }
        editor.putLong("vr_daily_cents", settings.vrDailyCents).putLong("vt_daily_cents", settings.vtDailyCents)
            .putInt("vr_weekdays", weekdayMask(settings.vrWeekdays))
            .putInt("vt_weekdays", weekdayMask(settings.vtWeekdays))
        settings.overrides.forEach { (month, days) ->
            days.vr?.let { editor.putInt("vr_days:$month", it) }
            days.vt?.let { editor.putInt("vt_days:$month", it) }
        }
        check(editor.commit()) { "Não foi possível salvar os benefícios no aparelho." }
        _benefits.value = settings
    }

    suspend fun setProfileName(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clean = cleanProfileName(name)
            check(profilePreferences.edit().putString("name", clean).commit()) { "Não foi possível salvar o nome no aparelho." }
            _profileName.value = clean
        }
    }

    suspend fun setOnboardingDone(done: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(profilePreferences.edit().putBoolean("onboarding_done", done).commit()) {
                "Não foi possível salvar a configuração inicial no aparelho."
            }
            _onboardingDone.value = done
        }
    }

    /**
     * First-run completion. Rates and weekday sets (month overrides untouched) are committed first, then name
     * and flag together; any failure throws and leaves the flag unset so the screen can be retried.
     */
    suspend fun completeOnboarding(
        name: String, vrDailyCents: Long, vtDailyCents: Long,
        vrWeekdays: Set<DayOfWeek>, vtWeekdays: Set<DayOfWeek>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(vrDailyCents >= 0 && vtDailyCents >= 0) { "As tarifas não podem ser negativas." }
            val updated = BenefitSettings(vrDailyCents, vtDailyCents, _benefits.value.overrides, vrWeekdays, vtWeekdays)
            check(benefitPreferences.edit()
                .putLong("vr_daily_cents", vrDailyCents).putLong("vt_daily_cents", vtDailyCents)
                .putInt("vr_weekdays", weekdayMask(vrWeekdays)).putInt("vt_weekdays", weekdayMask(vtWeekdays))
                .commit()) { "Não foi possível salvar os benefícios no aparelho." }
            _benefits.value = updated
            val clean = cleanProfileName(name)
            check(profilePreferences.edit().putString("name", clean).putBoolean("onboarding_done", true).commit()) {
                "Não foi possível salvar o perfil no aparelho."
            }
            _profileName.value = clean
            _onboardingDone.value = true
        }
    }

    @Synchronized
    fun setCaptureEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply()
        _captureEnabled.value = value
    }

    @Synchronized
    fun setAllowOtherApps(value: Boolean) {
        preferences.edit().putBoolean("allow_other_apps", value).apply()
        _allowOtherApps.value = value
    }

    fun allowsNotification(packageName: String): Boolean = NotificationCapturePolicy.allows(
        packageName = packageName,
        ownPackageName = appPackageName,
        captureEnabled = _captureEnabled.value,
        allowOtherApps = _allowOtherApps.value,
        enabledLegacyPackages = _enabledBanks.value,
    )

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
        mutex.withLock { LedgerBackup.export(database.readableDatabase, readBenefits()) }
    }

    fun inspectBackupPayload(payload: ByteArray): BackupSummary = LedgerBackup.validate(payload).summary

    suspend fun restoreBackupPayload(payload: ByteArray) = withContext(Dispatchers.IO) {
        val validated = LedgerBackup.validate(payload)
        mutex.withLock {
            // Device permissions and bank selection are not imported. Consent stays local.
            setCaptureEnabled(false)
            LedgerBackup.restore(database.writableDatabase, validated)
            validated.benefits?.let { replaceBenefits(it) }
            _captureError.value = null
            readAll()
        }
    }

    /** fallback identity belongs one active notification lifecycle. */
    suspend fun ingest(
        sourceKey: String, bankPackage: String, occurredAt: Long, text: String,
        fallbackIdentity: String? = null, observedAt: Long = occurredAt,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!allowsNotification(bankPackage)) return@withLock
            val parsed = NotificationParser.parse(text) ?: return@withLock
            // Only financial events with directional and monetary evidence may reach storage.
            if (!parsed.hasAmountCandidate ||
                (parsed.direction == Direction.UNKNOWN && !parsed.hasEventStateSignal)) return@withLock
            // A candidate without a reliable amount remains reviewable, never inferred as money.
            val initialStatus = if (parsed.amountCents?.let { it > 0 } == true &&
                parsed.direction != Direction.UNKNOWN) parsed.status else ReviewStatus.REVIEW
            // Fingerprint parser output, not raw text, posting time or the stored review status.
            // Length prefixes keep field boundaries unambiguous; null cannot match a numeric amount.
            val signature = MessageDigest.getInstance("SHA-256").digest(
                listOf(bankPackage, parsed.amountCents?.toString() ?: "null", parsed.direction.name,
                    parsed.method.name, parsed.status.name, parsed.reason)
                    .joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8),
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
                                // Even identical redelivery advances the watermark against stale callbacks.
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
                        direction = parsed.direction, method = parsed.method, status = initialStatus,
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
        require(transaction.transferId == null) { "Desvincule a transferência antes de editar." }
        require(transaction.occurredAt in -62135596800000L..253402300799999L) { "Informe data entre os anos 1 e 9999." }
        require(transaction.amountCents?.let { it > 0 } != false) { "Informe um valor positivo." }
        require(transaction.status != ReviewStatus.CONFIRMED ||
            (transaction.amountCents != null && transaction.direction != Direction.UNKNOWN)) {
            "Confirme o valor e a direção do lançamento."
        }
        require(transaction.description.length <= 200) { "Descrição muito longa." }
        mutex.withLock {
            val db = database.writableDatabase
            checkNotTransfer(db, transaction.id)
            transaction.accountId?.let { id ->
                require(_accounts.value.any { it.id == id }) { "Conta não encontrada." }
            }
            val changed = db.update("transactions", values(transaction).apply { put("user_modified", 1) }, "id = ? AND deleted = 0", arrayOf(transaction.id))
            if (changed == 0) db.insertOrThrow("transactions", null, values(transaction).apply { put("user_modified", 1) })
            readAll()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkNotTransfer(database.writableDatabase, id)
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
                db.delete("accounts", null, null)
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
        put("account_id", t.accountId); put("transfer_id", t.transferId)
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
                    accountId = string("account_id"), transferId = string("transfer_id"),
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
        val accountRows = mutableListOf<Account>()
        database.readableDatabase.query("accounts", null, null, null, null, null, "name, id").use { cursor ->
            while (cursor.moveToNext()) accountRows += Account(
                cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("bank_package")), cursor.getLong(cursor.getColumnIndexOrThrow("opening_cents")),
                cursor.getLong(cursor.getColumnIndexOrThrow("opening_at")), cursor.getInt(cursor.getColumnIndexOrThrow("archived")) == 1, AccountType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))))
        }
        _accounts.value = accountRows
    }

    suspend fun saveAccount(account: Account) = withContext(Dispatchers.IO) { mutex.withLock {
        require(account.id.isNotBlank() && account.id.length <= 200 && account.name.isNotBlank() && account.name.length <= 80)
        require(account.openingAt in -62135596800000L..253402300799999L)
        require(account.bankPackage == "manual" || supportedBanks.any { it.packageName == account.bankPackage })
        val values = ContentValues().apply {
            put("id", account.id); put("name", account.name.trim()); put("bank_package", account.bankPackage)
            put("type", account.type.name); put("opening_cents", account.openingCents); put("opening_at", account.openingAt); put("archived", if (account.archived) 1 else 0)
        }
        val db = database.writableDatabase
        if (db.update("accounts", values, "id = ?", arrayOf(account.id)) == 0) db.insertOrThrow("accounts", null, values)
        readAll()
    } }

    suspend fun createTransfer(from: String, to: String, cents: Long, occurredAt: Long) = withContext(Dispatchers.IO) { mutex.withLock {
        require(from != to && cents > 0)
        require(occurredAt in -62135596800000L..253402300799999L)
        val source = _accounts.value.first { it.id == from && !it.archived }
        val target = _accounts.value.first { it.id == to && !it.archived }
        val transfer = UUID.randomUUID().toString()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            listOf(source to Direction.EXPENSE, target to Direction.INCOME).forEach { (account, direction) ->
                val row = Transaction(UUID.randomUUID().toString(), null, account.bankPackage, cents, direction,
                    PaymentMethod.UNKNOWN, ReviewStatus.CONFIRMED, occurredAt, "Transferência entre contas próprias",
                    accountId = account.id, transferId = transfer)
                db.insertOrThrow("transactions", null, values(row).apply { put("user_modified", 1) })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        readAll()
    } }

    suspend fun linkTransfer(outgoingId: String, incomingId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val outgoing = _transactions.value.first { it.id == outgoingId }
        val incoming = _transactions.value.first { it.id == incomingId }
        require(outgoing.transferId == null && incoming.transferId == null)
        require(outgoing.accountId != null && incoming.accountId != null && outgoing.accountId != incoming.accountId)
        require(outgoing.direction == Direction.EXPENSE && incoming.direction == Direction.INCOME)
        require(outgoing.status == ReviewStatus.CONFIRMED && incoming.status == ReviewStatus.CONFIRMED)
        require(outgoing.method.affectsCash && incoming.method.affectsCash)
        require(outgoing.amountCents != null && outgoing.amountCents == incoming.amountCents)
        val values = ContentValues().apply { put("transfer_id", UUID.randomUUID().toString()); put("user_modified", 1) }
        database.writableDatabase.update("transactions", values, "id IN (?, ?) AND deleted = 0", arrayOf(outgoingId, incomingId))
        readAll()
    } }

    suspend fun unlinkTransfer(transferId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        database.writableDatabase.update("transactions", ContentValues().apply { putNull("transfer_id"); put("user_modified", 1) },
            "transfer_id = ? AND deleted = 0", arrayOf(transferId))
        readAll()
    } }

    private fun checkNotTransfer(db: SQLiteDatabase, id: String) {
        db.query("transactions", arrayOf("transfer_id"), "id = ?", arrayOf(id), null, null, null).use { cursor ->
            require(!cursor.moveToFirst() || cursor.isNull(0)) { "Desvincule a transferência antes de editar ou excluir." }
        }
    }

    private companion object {
        const val MAX_PROFILE_NAME = 40
    }
}

private class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "fluxo.db", null, 5) {
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
                last_observed_at INTEGER NOT NULL DEFAULT 0,
                account_id TEXT,
                transfer_id TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_transaction_date ON transactions(deleted, occurred_at)")
        createBudgets(db)
        createNotificationAliases(db)
        createAccounts(db)
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
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN account_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN transfer_id TEXT")
            createAccounts(db)
        }
        if (oldVersion < 5) {
            // A v4 build created unused invoice structures only when upgrading from v1.
            // Nothing ever wrote to them; remove them so every install converges on one schema.
            db.execSQL("DROP TABLE IF EXISTS invoices")
            if (hasColumn(db, "transactions", "invoice_id")) {
                try {
                    db.execSQL("ALTER TABLE transactions DROP COLUMN invoice_id")
                } catch (_: SQLException) {
                    // SQLite before 3.35 cannot drop columns. The always-null column stays;
                    // later migrations must check hasColumn before adding it again.
                }
            }
        }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.any { it == column }
        }

    private fun createAccounts(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, bank_package TEXT NOT NULL, opening_cents INTEGER NOT NULL, opening_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0, type TEXT NOT NULL)")
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
