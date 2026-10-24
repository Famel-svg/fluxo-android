package br.com.fluxo.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.BenefitSettings
import java.time.DayOfWeek
import java.time.YearMonth
import br.com.fluxo.core.Direction
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Category
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import br.com.fluxo.core.TriageDecision
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    // One helper for this test process; database and preferences cannot touch real ledger data.
    private val repository get() = testRepository
    private val bank = "com.nu.production"

    @Before fun prepare() = runBlocking {
        repository.clear()
        repository.setCaptureEnabled(false)
        repository.setAllowOtherApps(false)
        repository.setTriageEnabled(false)
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
    }

    @After fun clean() = runBlocking {
        repository.clear()
        repository.setCaptureEnabled(false)
        repository.setAllowOtherApps(false)
        repository.setTriageEnabled(false)
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
    }

    @Test fun triageKeepsOnlyWhatTheOwnerAuthorizes() = runBlocking {
        repository.setCaptureEnabled(true)
        repository.recordTriage("t0", "com.example.app", null, "Antes", "Pix recebido R$ 1,00", 10, autoStored = false)
        assertTrue("disabled triage stores nothing", repository.triage.value.isEmpty())
        repository.setTriageEnabled(true)
        repository.recordTriage("t1", "com.example.vr", "VR", "VR", "Você usou seu saldo de R$ 12,50", 100, autoStored = false)
        repository.recordTriage("t2", "com.example.chat", null, "Ana", "Oi", 200, autoStored = false)
        repository.recordTriage("t3", bank, "Nubank", "Pix", "Pix recebido R$ 10,00", 300, autoStored = true)
        // An update of a pending notification refreshes its text without creating a second row.
        repository.recordTriage("t2", "com.example.chat", null, "Ana", "Oi de novo", 250, autoStored = false)
        val rows = repository.triage.value.associateBy { it.title }
        assertEquals(3, rows.size)
        assertEquals("Oi de novo", rows.getValue("Ana").text)
        assertEquals(TriageDecision.AUTO_STORED, rows.getValue("Pix").decision)
        assertTrue(repository.transactions.value.isEmpty())

        repository.keepTriage(rows.getValue("VR").id)
        repository.discardTriage(rows.getValue("Ana").id)
        val kept = repository.transactions.value.single()
        assertEquals("com.example.vr", kept.bankPackage)
        assertEquals(ReviewStatus.REVIEW, kept.status)
        assertEquals(100L, kept.occurredAt)
        val decided = repository.triage.value.associateBy { it.title }
        assertEquals(TriageDecision.KEPT, decided.getValue("VR").decision)
        assertEquals(TriageDecision.DISCARDED, decided.getValue("Ana").decision)
        // A decided notification is not reopened by later deliveries, and keeping twice adds nothing.
        repository.recordTriage("t2", "com.example.chat", null, "Ana", "Oi 3", 260, autoStored = false)
        repository.keepTriage(decided.getValue("VR").id)
        assertEquals(TriageDecision.DISCARDED, repository.triage.value.single { it.title == "Ana" }.decision)
        assertEquals(1, repository.transactions.value.size)

        // Triage text never enters the backup payload.
        assertFalse(repository.exportBackupPayload().toString(Charsets.UTF_8).contains("Oi de novo"))
        repository.clearTriage()
        assertTrue(repository.triage.value.isEmpty())
        assertEquals(1, repository.transactions.value.size)
    }

    @Test fun triageRepostsShareOneRowAndKeptRowDedupsWithParser() = runBlocking {
        repository.setCaptureEnabled(true)
        repository.setTriageEnabled(true)
        // Without an original timestamp each repost hashes a new key; the lifecycle identity ties them together.
        repository.recordTriage("first", "com.example.vr", null, "VR", "Saldo usado", 100, autoStored = false, fallbackIdentity = "life")
        repository.recordTriage("second", "com.example.vr", null, "VR", "Saldo usado agora", 110, autoStored = false, fallbackIdentity = "life")
        val row = repository.triage.value.single()
        assertEquals("Saldo usado agora", row.text)
        repository.keepTriage(row.id)
        repository.ingest("third", "com.example.vr", 120, "Compra aprovada de R$ 12,50", fallbackIdentity = "life")
        assertEquals(1, repository.transactions.value.size)
    }

    @Test fun walletReceiptFoldsIntoTheIssuerNotificationInEitherOrder() = runBlocking {
        repository.setCaptureEnabled(true)
        val wallet = br.com.fluxo.core.WalletReceipt.GOOGLE_WALLET
        val receipt = "MERCADO EXEMPLO\nR$ 7,98 com Cartão C6 Mastercard ••1234"
        val issuer = "Compra no crédito aprovada\nSua compra no cartão final 1234 no valor de R$ 7,98 em MERCADO EXEMPLO foi aprovada."
        // Wallet first: stored for review, then retired when the issuer confirms the same purchase.
        repository.ingest("w1", wallet, 1_000_000, receipt)
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
        repository.ingest("c1", "com.c6bank.app", 1_010_000, issuer)
        assertEquals("com.c6bank.app", repository.transactions.value.single().bankPackage)
        repository.ingest("w1", wallet, 1_000_000, receipt)
        assertEquals(1, repository.transactions.value.size)
        // Issuer first: the later wallet receipt adds nothing.
        repository.ingest("c2", "com.c6bank.app", 5_000_000, issuer)
        assertEquals(IngestOutcome.Ignored(br.com.fluxo.core.IgnoreReason.WALLET_DUPLICATE),
            repository.ingestClassified("w2", wallet, 5_004_000, receipt))
        assertEquals(2, repository.transactions.value.size)
        // Outside the window it is a separate purchase that still needs review.
        repository.ingest("w3", wallet, 5_000_000 + br.com.fluxo.core.WalletReceipt.MATCH_WINDOW_MS + 1, receipt)
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single { it.bankPackage == wallet }.status)
    }

    @Test fun ownTransfersConserveBalancesAndRestoreWithAccounts() = runBlocking {
        val a = br.com.fluxo.core.Account("a", "Conta A", "manual", 100000, 0)
        val b = br.com.fluxo.core.Account("b", "Conta B", "manual", 20000, 0, type = br.com.fluxo.core.AccountType.SAVINGS)
        repository.saveAccount(a); repository.saveAccount(b)
        repository.createTransfer("a", "b", 50000, 100)
        val rows = repository.transactions.value
        assertEquals(2, rows.size)
        assertEquals(1, rows.map { it.transferId }.distinct().size)
        assertEquals(java.math.BigInteger.valueOf(50000), br.com.fluxo.core.AccountBalances.observed(a, rows))
        assertEquals(java.math.BigInteger.valueOf(70000), br.com.fluxo.core.AccountBalances.observed(b, rows))
        val payload = repository.exportBackupPayload()
        assertEquals(BackupSummary(2, 0, 0, 2, true), repository.inspectBackupPayload(payload))
        repository.clear(); repository.restoreBackupPayload(payload)
        assertEquals(setOf(a, b), repository.accounts.value.toSet())
        assertEquals(rows, repository.transactions.value)
        var rejected = false
        try { repository.delete(rows.first().id) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(2, repository.transactions.value.size)
        repository.unlinkTransfer(rows.first().transferId!!)
        assertTrue(repository.transactions.value.all { it.transferId == null })
        assertEquals(2, repository.transactions.value.size)
    }

    @Test fun capturedTransferLinksWithoutAddingRowsAndHumanDecisionSurvivesRedelivery() = runBlocking {
        repository.saveAccount(br.com.fluxo.core.Account("a", "A", bank, 10000, 0))
        repository.saveAccount(br.com.fluxo.core.Account("b", "B", "com.c6bank.app", 0, 0))
        enableCapture(); repository.setBankEnabled("com.c6bank.app", true)
        repository.ingest("out", bank, 100, "Pix enviado R$ 50,00")
        repository.ingest("in", "com.c6bank.app", 100, "Pix recebido R$ 50,00")
        val outgoing = repository.transactions.value.first { it.sourceKey == "out" }.copy(accountId = "a")
        val incoming = repository.transactions.value.first { it.sourceKey == "in" }.copy(accountId = "b")
        repository.save(outgoing); repository.save(incoming)
        repository.linkTransfer(outgoing.id, incoming.id)
        repository.ingest("out", bank, 100, "Pix enviado R$ 50,00", observedAt = 200)
        assertEquals(2, repository.transactions.value.size)
        assertTrue(repository.transactions.value.all { it.transferId != null })
        var rejected = false
        try { repository.save(outgoing) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun legacyBackupRestoresWithoutInventingAccountsAndOrphansAreRejected() = runBlocking {
        repository.save(manualTransaction())
        val original = repository.transactions.value
        val old = org.json.JSONObject(repository.exportBackupPayload().toString(Charsets.UTF_8))
        old.remove("accounts"); old.remove("benefits"); old.remove("profile"); old.put("version", 1); old.put("schema", 3)
        old.getJSONArray("transactions").getJSONObject(0).apply { remove("account_id"); remove("transfer_id") }
        repository.restoreBackupPayload(old.toString().toByteArray())
        assertEquals(original, repository.transactions.value)
        assertTrue(repository.accounts.value.isEmpty())
        val orphan = org.json.JSONObject(repository.exportBackupPayload().toString(Charsets.UTF_8))
        orphan.getJSONArray("transactions").getJSONObject(0).put("account_id", "missing")
        var rejected = false
        try { repository.restoreBackupPayload(orphan.toString().toByteArray()) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(original, repository.transactions.value)
    }

    @Test fun backupRoundTripPreservesCorrectionsBudgetsAndTombstones() = runBlocking {
        enableCapture()
        repository.ingest("backup-live", bank, 100, "Pix recebido R$ 10,00", "alias-backup", 100)
        val corrected = repository.transactions.value.single().copy(category = Category.FOOD, description = "Alimentação")
        repository.save(corrected)
        repository.ingest("backup-gone", bank, 101, "Pix enviado R$ 20,00")
        repository.delete(repository.transactions.value.first { it.sourceKey == "backup-gone" }.id)
        val budget = Budget("2026-09", Category.FOOD, 20000)
        repository.saveBudget(budget)
        val payload = repository.exportBackupPayload()
        assertEquals(BackupSummary(1, 1, 1, 0, true), repository.inspectBackupPayload(payload))
        repository.clear()
        repository.restoreBackupPayload(payload)
        assertFalse(repository.captureEnabled.value)
        assertEquals(corrected, repository.transactions.value.single())
        assertEquals(listOf(budget), repository.budgets.value)
        enableCapture()
        repository.ingest("backup-gone", bank, 101, "Pix enviado R$ 20,00")
        repository.ingest("new-post", bank, 102, "Pix recebido R$ 80,00", "alias-backup", 102)
        assertEquals(listOf(corrected), repository.transactions.value)
    }

    @Test fun invalidBackupCannotReplaceExistingHistory() = runBlocking {
        repository.save(manualTransaction())
        val before = repository.transactions.value
        val original = repository.exportBackupPayload()
        val wrongVersion = org.json.JSONObject(original.toString(Charsets.UTF_8)).put("version", 99).toString().toByteArray()
        val wrongMoney = org.json.JSONObject(original.toString(Charsets.UTF_8)).also {
            it.getJSONArray("transactions").getJSONObject(0).put("amount_cents", 1.5)
        }.toString().toByteArray()
        val deep = ("[".repeat(100) + "]".repeat(100)).toByteArray()
        listOf(wrongVersion, wrongMoney, original.copyOf(20), deep).forEach { payload ->
            var failed = false
            try { repository.restoreBackupPayload(payload) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(before, repository.transactions.value)
        }
    }

    @Test fun encryptedBackupRoundTripsOnAndroidProvider() = runBlocking {
        repository.save(manualTransaction())
        val payload = repository.exportBackupPayload()
        val password = "senha de teste segura".toCharArray()
        val encrypted = br.com.fluxo.core.BackupCipher.encrypt(payload, password)
        assertArrayEquals(payload, br.com.fluxo.core.BackupCipher.decrypt(encrypted, password))
        var failed = false
        try { br.com.fluxo.core.BackupCipher.decrypt(encrypted, "senha incorreta".toCharArray()) }
        catch (_: java.security.GeneralSecurityException) { failed = true }
        assertTrue(failed)
        password.fill('\u0000')
    }

    @Test fun captureRequiresGlobalEnableOnly() = runBlocking {
        repository.ingest("paused", bank, 1L, "Pix recebido R$ 10,00")
        repository.setCaptureEnabled(true)
        repository.ingest("unselected", bank, 2L, "Pix recebido R$ 10,00")
        repository.ingest("other-app", "com.example.wallet", 3L, "Pix recebido R$ 10,00")
        assertEquals(setOf("unselected", "other-app"), repository.transactions.value.map { it.sourceKey }.toSet())
    }

    @Test fun multiappConsentPersistsOnlyRecognizedFinancialEvents() = runBlocking {
        val appPackage = "com.example.wallet"
        val ownPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        repository.clear()
        repository.setCaptureEnabled(false)
        repository.setAllowOtherApps(false)
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
        assertFalse(repository.allowOtherApps.value)
        repository.setAllowOtherApps(true)
        repository.ingest("paused", appPackage, 1L, "Pix recebido R$ 10,00")
        repository.setCaptureEnabled(true)
        repository.ingest("promo", appPackage, 2L, "Oferta especial: cashback 10,00")
        repository.ingest("ambiguous", appPackage, 3L, "Pix recebido análise 10,00")
        repository.ingest("missing-amount", appPackage, 31L, "Pix recebido valor oculto")
        repository.ingest("missing-direction", appPackage, 32L, "Pix R$ 10,00")
        repository.ingest("own", ownPackage, 4L, "Pix recebido R$ 10,00")
        repository.setCaptureEnabled(false)
        repository.ingest("paused-again", appPackage, 5L, "Pix recebido R$ 10,00")
        repository.setCaptureEnabled(true)
        repository.ingest("recognized", appPackage, 6L, "Pix recebido R$ 10,00")

        assertEquals(setOf("ambiguous", "recognized"), repository.transactions.value.map { it.sourceKey }.toSet())
        assertTrue(repository.transactions.value.all { it.bankPackage == "com.example.wallet" })
        assertEquals(br.com.fluxo.core.ReviewStatus.REVIEW,
            repository.transactions.value.single { it.sourceKey == "ambiguous" }.status)
        assertEquals("Movimentação detectada", repository.transactions.value.single { it.sourceKey == "recognized" }.description)
        repository.clear()
        repository.setCaptureEnabled(false)
        repository.setAllowOtherApps(false)
    }

    @Test fun duplicateDeliveryHasOneRowButDistinctEventsRemain() = runBlocking {
        enableCapture()
        repeat(3) { repository.ingest("same", bank, 1L, "Pix recebido R$ 10,00") }
        repository.ingest("different", bank, 1L, "Pix recebido R$ 10,00")
        repository.refresh()
        assertEquals(2, repository.transactions.value.size)
        assertEquals(setOf("same", "different"), repository.transactions.value.map { it.sourceKey }.toSet())
    }

    @Test fun manualCorrectionSurvivesOriginalNotificationRedelivery() = runBlocking {
        enableCapture()
        repository.ingest("edited", bank, 1L, "Pix recebido R$ 10,00")
        val original = repository.transactions.value.single()
        val edited = original.copy(amountCents = 1500L, direction = Direction.EXPENSE,
            method = PaymentMethod.DEBIT_CARD, description = "Correção manual", category = Category.FOOD)
        repository.save(edited)
        repository.ingest("edited", bank, 1L, "Pix recebido R$ 10,00")
        repository.refresh()
        assertEquals(edited, repository.transactions.value.single())
    }

    @Test fun deletionTombstonePreventsRecreation() = runBlocking {
        enableCapture()
        repository.ingest("deleted", bank, 1L, "Pix recebido R$ 10,00")
        repository.delete(repository.transactions.value.single().id)
        repository.ingest("deleted", bank, 1L, "Pix recebido R$ 10,00")
        repository.refresh()
        assertTrue(repository.transactions.value.isEmpty())
    }

    @Test fun confirmedRowsRequirePositiveAmountAndKnownDirection() = runBlocking {
        val transaction = manualTransaction()
        listOf(
            transaction.copy(amountCents = null),
            transaction.copy(amountCents = 0),
            transaction.copy(amountCents = -1),
            transaction.copy(direction = Direction.UNKNOWN),
        ).forEach { invalid ->
            var rejected = false
            try { repository.save(invalid) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Invalid confirmed transaction must be rejected", rejected)
        }
        assertTrue(repository.transactions.value.isEmpty())
        repository.save(transaction)
        assertEquals(transaction, repository.transactions.value.single())
    }

    @Test fun ambiguousNotificationRemainsReviewable() = runBlocking {
        enableCapture()
        repository.ingest("ambiguous", bank, 1L, "Pix recebido R$ 10,00 saldo R$ 100,00")
        val transaction = repository.transactions.value.single()
        assertEquals(ReviewStatus.REVIEW, transaction.status)
        assertNull(transaction.amountCents)
        assertTrue(transaction.reason.isNotBlank())
        assertFalse(transaction.description.contains("saldo"))
    }

    @Test fun commonBankWordingIsStoredInsteadOfDropped() = runBlocking {
        enableCapture()
        repository.ingest("debit-purchase", bank, 1L,
            "Compra no débito\nCompra no débito de R$ 18,90 em FARMACIA Z aprovada.")
        repository.ingest("invoice-payment", bank, 2L,
            "Pagamento de fatura\nRecebemos o pagamento da sua fatura no valor de R$ 900,00.")
        val rows = repository.transactions.value
        assertEquals(2, rows.size)
        val purchase = rows.single { it.amountCents == 1890L }
        assertEquals(ReviewStatus.CONFIRMED, purchase.status)
        assertEquals(Direction.EXPENSE, purchase.direction)
        assertEquals(PaymentMethod.DEBIT_CARD, purchase.method)
        assertEquals("FARMACIA Z", purchase.description)
        assertEquals(br.com.fluxo.core.Category.HEALTH, purchase.category)
        val invoice = rows.single { it.amountCents == 90000L }
        assertEquals(ReviewStatus.REVIEW, invoice.status)
        assertTrue(invoice.reason.isNotBlank())
    }

    @Test fun categoryChosenForAMerchantIsReusedOnTheNextCapture() = runBlocking {
        enableCapture()
        repository.ingest("first", bank, 1L, "Compra no débito\nCompra no débito de R$ 20,00 em LOJA XYZ aprovada.")
        val first = repository.transactions.value.single()
        assertEquals("LOJA XYZ", first.description)
        assertEquals(br.com.fluxo.core.Category.UNCATEGORIZED, first.category)
        repository.save(first.copy(category = br.com.fluxo.core.Category.LEISURE))
        repository.ingest("second", bank, 2L, "Compra no débito\nCompra no débito de R$ 35,00 em Loja Xyz aprovada.")
        assertEquals(br.com.fluxo.core.Category.LEISURE,
            repository.transactions.value.single { it.sourceKey == "second" }.category)
        // Clearing the history forgets learned merchants too.
        repository.clear()
        enableCapture()
        repository.ingest("third", bank, 3L, "Compra no débito\nCompra no débito de R$ 35,00 em LOJA XYZ aprovada.")
        assertEquals(br.com.fluxo.core.Category.UNCATEGORIZED, repository.transactions.value.single().category)
    }

    @Test fun clearRemovesHistoryAndFingerprintsAndPausesCapture() = runBlocking {
        enableCapture()
        repository.ingest("clear", bank, 1L, "Pix recebido R$ 10,00")
        repository.delete(repository.transactions.value.single().id)
        repository.reportCaptureError()
        repository.saveBudget(Budget("2026-09", Category.FOOD, 10000))
        repository.clear()
        assertFalse(repository.captureEnabled.value)
        assertNull(repository.captureError.value)
        assertTrue(repository.transactions.value.isEmpty())
        assertTrue(repository.budgets.value.isEmpty())
        repository.ingest("clear", bank, 1L, "Pix recebido R$ 10,00")
        assertTrue(repository.transactions.value.isEmpty())
        repository.setCaptureEnabled(true)
        repository.ingest("clear", bank, 1L, "Pix recebido R$ 10,00")
        assertEquals(1, repository.transactions.value.size)
    }

    @Test fun unknownBankCannotBeEnabled() {
        var rejected = false
        try { repository.setBankEnabled("com.example.untrusted", true) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertTrue(repository.enabledBanks.value.isEmpty())
    }

    @Test fun budgetsPersistUpdateAndRemoveOnlyMatchingMonthAndCategory() = runBlocking {
        val budget = Budget("2026-09", Category.FOOD, 10000)
        repository.saveBudget(budget)
        repository.saveBudget(budget.copy(limitCents = 12000))
        repository.saveBudget(budget.copy(month = "2026-10"))
        repository.saveBudget(budget.copy(category = Category.HEALTH))
        repository.refresh()
        assertEquals(3, repository.budgets.value.size)
        assertTrue(repository.budgets.value.contains(budget.copy(limitCents = 12000)))
        repository.deleteBudget("2026-09", Category.FOOD)
        assertEquals(setOf(budget.copy(month = "2026-10"), budget.copy(category = Category.HEALTH)),
            repository.budgets.value.toSet())
    }

    @Test fun invalidBudgetsAreRejectedWithoutChangingStoredLimits() = runBlocking {
        val budget = Budget("2026-09", Category.FOOD, 10000)
        listOf(budget.copy(month = "2026-13"), budget.copy(month = "2026-9"),
            budget.copy(limitCents = 0), budget.copy(limitCents = -1)).forEach { invalid ->
            var rejected = false
            try { repository.saveBudget(invalid) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        assertTrue(repository.budgets.value.isEmpty())
    }

    @Test fun v1MigrationPreservesHistoryCorrectionsAndDeletionFingerprints() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "migration_${java.util.UUID.randomUUID()}_")
        context.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("""CREATE TABLE transactions (
                id TEXT PRIMARY KEY NOT NULL, source_key TEXT UNIQUE, bank_package TEXT NOT NULL,
                amount_cents INTEGER CHECK(amount_cents IS NULL OR amount_cents > 0),
                direction TEXT NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL,
                occurred_at INTEGER NOT NULL, description TEXT NOT NULL, reason TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0)""")
            db.execSQL("CREATE INDEX idx_transaction_date ON transactions(deleted, occurred_at)")
            db.execSQL("INSERT INTO transactions VALUES ('old', 'live-key', 'com.nu.production', 12345, 'EXPENSE', 'DEBIT_CARD', 'CONFIRMED', 42, 'Correção preservada', '', 0)")
            db.execSQL("INSERT INTO transactions VALUES ('gone', 'deleted-key', 'com.nu.production', NULL, 'INCOME', 'PIX', 'CONFIRMED', 43, '', '', 1)")
            db.version = 1
        }
        val migrated = LedgerRepository(context)
        migrated.refresh()
        assertEquals(Transaction("old", "live-key", bank, 12345, Direction.EXPENSE,
            PaymentMethod.DEBIT_CARD, ReviewStatus.CONFIRMED, 42, "Correção preservada"),
            migrated.transactions.value.single())
        migrated.setBankEnabled(bank, true)
        migrated.setCaptureEnabled(true)
        migrated.ingest("deleted-key", bank, 43, "Pix recebido R$ 10,00")
        migrated.ingest("live-key", bank, 42, "Pix recebido R$ 10,00")
        assertEquals(1, migrated.transactions.value.size)
        val edited = migrated.transactions.value.single().copy(category = Category.TRANSPORT)
        migrated.save(edited)
        migrated.saveBudget(Budget("2026-09", Category.TRANSPORT, 50000))
        val reopened = LedgerRepository(context)
        reopened.refresh()
        assertEquals(edited, reopened.transactions.value.single())
        assertEquals(Budget("2026-09", Category.TRANSPORT, 50000), reopened.budgets.value.single())
        context.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
            assertEquals(7, db.version)
            db.rawQuery("SELECT category, amount_cents FROM transactions WHERE id = 'old'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TRANSPORT", cursor.getString(0))
                assertEquals(12345L, cursor.getLong(1))
            }
            db.rawQuery("SELECT count(*) FROM transactions WHERE deleted = 1", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
        migrated.clear()
    }

    @Test fun everyUpgradePathConvergesOnTheFreshInstallSchema() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        fun schema(context: Context): Pair<Set<String>, Map<String, Set<String>>> =
            context.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
                assertEquals(7, db.version)
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'", null).use { cursor ->
                    generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toSet()
                }
                tables to tables.associateWith { table ->
                    db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                        val name = cursor.getColumnIndexOrThrow("name")
                        // SQLite before 3.35 cannot drop the stray always-null column.
                        generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.toSet() - "invoice_id"
                    }
                }
            }
        val v1 = """CREATE TABLE transactions (
            id TEXT PRIMARY KEY NOT NULL, source_key TEXT UNIQUE, bank_package TEXT NOT NULL,
            amount_cents INTEGER CHECK(amount_cents IS NULL OR amount_cents > 0),
            direction TEXT NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL,
            occurred_at INTEGER NOT NULL, description TEXT NOT NULL, reason TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0)"""
        val row = "INSERT INTO transactions (id, source_key, bank_package, amount_cents, direction, method, status, occurred_at, description, reason, deleted) VALUES ('old', 'key', 'com.nu.production', 12345, 'EXPENSE', 'PIX', 'CONFIRMED', 42, 'Preservado', '', 0)"
        val fresh = IsolatedContext(target, "schema_fresh_${java.util.UUID.randomUUID()}_")
        LedgerRepository(fresh).refresh()
        val expected = schema(fresh)
        assertFalse("invoices" in expected.first)

        val fromV1 = IsolatedContext(target, "schema_v1_${java.util.UUID.randomUUID()}_")
        fromV1.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(v1); db.execSQL(row); db.version = 1
        }
        // Shape left behind by the v4 build that created invoice structures only for v1 upgrades.
        val fromStrayV4 = IsolatedContext(target, "schema_v4_${java.util.UUID.randomUUID()}_")
        fromStrayV4.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(v1)
            db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'UNCATEGORIZED'")
            db.execSQL("CREATE TABLE budgets (month TEXT NOT NULL, category TEXT NOT NULL, limit_cents INTEGER NOT NULL CHECK(limit_cents > 0), PRIMARY KEY(month, category))")
            db.execSQL("CREATE TABLE invoices (id TEXT PRIMARY KEY NOT NULL, account_id TEXT NOT NULL, month TEXT NOT NULL, due_date INTEGER NOT NULL, closing_date INTEGER NOT NULL, is_paid INTEGER NOT NULL DEFAULT 0, amount_cents INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("ALTER TABLE transactions ADD COLUMN invoice_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN user_modified INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE transactions ADD COLUMN observation_hash TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN last_observed_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE notification_aliases (identity_key TEXT PRIMARY KEY NOT NULL, source_key TEXT NOT NULL)")
            db.execSQL("ALTER TABLE transactions ADD COLUMN account_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN transfer_id TEXT")
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, bank_package TEXT NOT NULL, opening_cents INTEGER NOT NULL, opening_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0, type TEXT NOT NULL)")
            db.execSQL(row); db.version = 4
        }
        listOf(fromV1, fromStrayV4).forEach { context ->
            val upgraded = LedgerRepository(context)
            upgraded.refresh()
            assertEquals(12345L, upgraded.transactions.value.single().amountCents)
            assertEquals(expected, schema(context))
            upgraded.save(upgraded.transactions.value.single().copy(category = Category.FOOD))
            assertEquals(Category.FOOD, LedgerRepository(context).also { it.refresh() }.transactions.value.single().category)
        }
        // Resolve the isolated path explicitly; never the name of the real ledger file.
        listOf(fresh, fromV1, fromStrayV4).forEach { SQLiteDatabase.deleteDatabase(it.getDatabasePath("fluxo.db")) }
    }

    @Test fun notificationStateChangeReopensReviewAndIdenticalRedeliveryKeepsIt() = runBlocking {
        enableCapture()
        repository.ingest("updated", bank, 100L, "Pix pendente R$ 50,00", observedAt = 100L)
        repository.ingest("updated", bank, 100L, "Pix enviado R$ 50,00", observedAt = 200L)
        val updated = repository.transactions.value.single()
        assertEquals(5000L, updated.amountCents)
        assertEquals(Direction.EXPENSE, updated.direction)
        assertEquals(ReviewStatus.REVIEW, updated.status)
        assertTrue(updated.reason.startsWith("Notificação atualizada"))
        repository.ingest("updated", bank, 100L, "Pix enviado R$ 50,00", observedAt = 300L)
        assertEquals(updated, repository.transactions.value.single())
    }

    @Test fun cancellationRemovesPreviouslyConfirmedTransactionFromConfirmedTotals() = runBlocking {
        enableCapture()
        repository.ingest("cancel", bank, 100L, "Pix enviado R$ 50,00", observedAt = 100L)
        assertEquals(ReviewStatus.CONFIRMED, repository.transactions.value.single().status)
        repository.ingest("cancel", bank, 100L, "Pix enviado cancelado R$ 50,00", observedAt = 300L)
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
        assertTrue(repository.transactions.value.single().reason.contains("recusado"))
        // Older callback must not restore a previous state or amount.
        repository.ingest("cancel", bank, 100L, "Pix enviado R$ 90,00", observedAt = 200L)
        assertEquals(5000L, repository.transactions.value.single().amountCents)
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
    }

    @Test fun changedPayloadCannotOverwriteManualCorrectionOrResurrectDeletion() = runBlocking {
        enableCapture()
        repository.ingest("protected", bank, 100L, "Pix recebido R$ 10,00")
        val manual = repository.transactions.value.single().copy(amountCents = 1500L, description = "Correção humana")
        repository.save(manual)
        repository.ingest("protected", bank, 100L, "Pix enviado R$ 90,00", observedAt = 200L)
        assertEquals(manual, repository.transactions.value.single())
        repository.delete(manual.id)
        repository.ingest("protected", bank, 100L, "Pix enviado R$ 90,00", observedAt = 300L)
        assertTrue(repository.transactions.value.isEmpty())
    }

    @Test fun fallbackAliasKeepsChangingPostTimesTogetherUntilRemoval() = runBlocking {
        enableCapture()
        repository.ingest("post-100", bank, 100L, "Pix recebido R$ 10,00", "active-key", 100L)
        repository.ingest("post-200", bank, 200L, "Pix recebido R$ 10,00", "active-key", 200L)
        assertEquals(1, repository.transactions.value.size)
        assertEquals(100L, repository.transactions.value.single().occurredAt)
        assertEquals(ReviewStatus.CONFIRMED, repository.transactions.value.single().status)
        repository.ingest("post-300", bank, 300L, "Pix recebido R$ 20,00", "active-key", 300L)
        assertEquals(1, repository.transactions.value.size)
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
        repository.forgetNotification("active-key")
        repository.ingest("post-400", bank, 400L, "Pix recebido R$ 20,00", "active-key", 400L)
        assertEquals(2, repository.transactions.value.size)
    }

    @Test fun fallbackAliasSurvivesRepositoryRecreationAndClearRemovesIt() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "alias_${java.util.UUID.randomUUID()}_")
        val initial = LedgerRepository(context)
        initial.setBankEnabled(bank, true)
        initial.setCaptureEnabled(true)
        initial.ingest("first", bank, 100L, "Pix recebido R$ 10,00", "same-active-key", 100L)
        val reopened = LedgerRepository(context)
        reopened.ingest("second", bank, 200L, "Pix recebido R$ 10,00", "same-active-key", 200L)
        assertEquals(1, reopened.transactions.value.size)
        assertEquals("first", reopened.transactions.value.single().sourceKey)
        reopened.clear()
        reopened.setCaptureEnabled(true)
        reopened.ingest("third", bank, 300L, "Pix recebido R$ 10,00", "same-active-key", 300L)
        assertEquals("third", reopened.transactions.value.single().sourceKey)
        reopened.clear()
    }

    @Test fun v2MigrationProtectsExistingRowsButNewRowsCanReceiveUpdates() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "migration_v2_${java.util.UUID.randomUUID()}_")
        context.openOrCreateDatabase("fluxo.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("""CREATE TABLE transactions (
                id TEXT PRIMARY KEY NOT NULL, source_key TEXT UNIQUE, bank_package TEXT NOT NULL,
                amount_cents INTEGER, direction TEXT NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL,
                occurred_at INTEGER NOT NULL, description TEXT NOT NULL, reason TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0, category TEXT NOT NULL DEFAULT 'UNCATEGORIZED')""")
            db.execSQL("CREATE TABLE budgets (month TEXT NOT NULL, category TEXT NOT NULL, limit_cents INTEGER NOT NULL, PRIMARY KEY(month, category))")
            db.execSQL("INSERT INTO transactions VALUES ('old-v2', 'v2-key', 'com.nu.production', 12345, 'EXPENSE', 'DEBIT_CARD', 'CONFIRMED', 42, 'Antigo', '', 0, 'FOOD')")
            db.execSQL("INSERT INTO budgets VALUES ('2026-09', 'FOOD', 50000)")
            db.version = 2
        }
        val migrated = LedgerRepository(context)
        migrated.setBankEnabled(bank, true)
        migrated.setCaptureEnabled(true)
        migrated.ingest("v2-key", bank, 42L, "Pix recebido R$ 10,00", observedAt = 200L)
        assertEquals(12345L, migrated.transactions.value.single().amountCents)
        assertEquals(Category.FOOD, migrated.transactions.value.single().category)
        assertEquals(50000L, migrated.budgets.value.single().limitCents)
        migrated.ingest("new-v3", bank, 300L, "Pix enviado R$ 10,00")
        migrated.ingest("new-v3", bank, 300L, "Pix enviado cancelado R$ 10,00", observedAt = 400L)
        assertEquals(ReviewStatus.REVIEW, migrated.transactions.value.single { it.sourceKey == "new-v3" }.status)
        migrated.clear()
    }

    @Test fun benefitsPersistAcrossRepositoryRecreationAndLedgerClearWithoutChangingTotals() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "benefits_${java.util.UUID.randomUUID()}_")
        val initial = LedgerRepository(context)
        val month = YearMonth.of(2026, 9)
        val next = month.plusMonths(1)
        try {
            assertEquals(BenefitSettings(), initial.benefits.value)
            initial.save(manualTransaction())
            initial.saveBudget(Budget(month.toString(), Category.FOOD, 5000))
            val rows = initial.transactions.value
            val budgets = initial.budgets.value
            initial.saveBenefits(month, 2550, 1050, BenefitDays(0, 12))
            initial.saveBenefits(next, 2550, 1050, BenefitDays(5, null))
            assertEquals(rows, initial.transactions.value)
            assertEquals(budgets, initial.budgets.value)
            assertTrue(initial.accounts.value.isEmpty())
            assertFalse(context.getSharedPreferences("capture", Context.MODE_PRIVATE)
                .contains("vr_daily_cents"))
            val persisted = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
            assertEquals(2550L, persisted.getLong("vr_daily_cents", -1))
            assertEquals(0, persisted.getInt("vr_days:$month", -1))
            val reopened = LedgerRepository(context)
            assertEquals(initial.benefits.value, reopened.benefits.value)
            reopened.saveBenefits(month, 2550, 1050, BenefitDays(null, 12))
            assertFalse(persisted.contains("vr_days:$month"))
            assertEquals(BenefitDays(5, null), reopened.benefits.value.overrides[next])
            val estimate = BenefitCalculator.estimate(month, reopened.benefits.value)
            assertEquals(22, estimate.vrDays)
            assertEquals(12, estimate.vtDays)
            reopened.saveBenefits(month, 2550, 1050, BenefitDays())
            assertFalse(persisted.contains("vt_days:$month"))
            assertFalse(reopened.benefits.value.overrides.containsKey(month))
            val beforeClear = reopened.benefits.value
            reopened.clear()
            assertTrue(reopened.transactions.value.isEmpty())
            assertTrue(reopened.budgets.value.isEmpty())
            assertEquals(beforeClear, reopened.benefits.value)
            assertEquals(beforeClear, LedgerRepository(context).benefits.value)
        } finally {
            initial.clear()
            context.getSharedPreferences("benefits", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun benefitsRejectInvalidWritesAndPreserveLongMaximumExactly() = runBlocking {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "benefits_limits_${java.util.UUID.randomUUID()}_")
        val isolated = LedgerRepository(context)
        val month = YearMonth.of(2025, 2)
        val prefs = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
        try {
            isolated.saveBenefits(month, Long.MAX_VALUE, Long.MAX_VALUE, BenefitDays(28, 28))
            val saved = isolated.benefits.value
            val stored = prefs.all.toMap()
            for ((vr, vt, days) in listOf(
                Triple(-1L, 0L, BenefitDays()), Triple(0L, -1L, BenefitDays()),
                Triple(0L, 0L, BenefitDays(29, null)), Triple(0L, 0L, BenefitDays(null, -1)))) {
                try {
                    isolated.saveBenefits(month, vr, vt, days)
                    fail("Invalid benefit configuration must be rejected")
                } catch (_: IllegalArgumentException) { /* expected */ }
                assertEquals(saved, isolated.benefits.value)
                assertEquals(stored, prefs.all)
            }
            val reopened = LedgerRepository(context)
            assertEquals(saved, reopened.benefits.value)
            assertEquals(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(56)),
                BenefitCalculator.estimate(month, reopened.benefits.value).totalCents)
        } finally {
            isolated.clear()
            prefs.edit().clear().commit()
        }
    }

    @Test fun benefitsIgnoreMalformedPreferencesAndKeepValidIndependentDays() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "benefits_corrupt_${java.util.UUID.randomUUID()}_")
        val prefs = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().putString("vr_daily_cents", "invalid")
                .putLong("vt_daily_cents", -1).putInt("vr_days:2025-02", 29)
                .putInt("vt_days:2025-02", 0).putInt("vr_days:bad-month", 10)
                .putString("vr_days:2026-09", "3").commit())
            val loaded = LedgerRepository(context).benefits.value
            assertEquals(BenefitSettings(overrides = mapOf(YearMonth.of(2025, 2) to BenefitDays(null, 0))), loaded)
            // No migration from the obsolete prototype preference name is proposed.
            assertTrue(context.getSharedPreferences("benefits_vr_vt", Context.MODE_PRIVATE)
                .edit().putLong("vr_daily_cents", 9999L).commit())
            assertEquals(loaded, LedgerRepository(context).benefits.value)
        } finally {
            prefs.edit().clear().commit()
            context.getSharedPreferences("benefits_vr_vt", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun withBenefitRepository(block: suspend (LedgerRepository, android.content.SharedPreferences) -> Unit) {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "benefits_backup_${java.util.UUID.randomUUID()}_")
        val isolated = LedgerRepository(context)
        val prefs = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
        try { runBlocking { block(isolated, prefs) } } finally {
            runBlocking { isolated.clear() }
            prefs.edit().clear().commit()
            context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun mutated(payload: ByteArray, change: (org.json.JSONObject) -> Unit): ByteArray =
        org.json.JSONObject(payload.toString(Charsets.UTF_8)).also(change).toString().toByteArray()

    @Test fun backupV4RoundTripRestoresBenefitsAndReplacesExistingOverrides() = withBenefitRepository { repo, prefs ->
        val sep = YearMonth.of(2026, 9)
        val oct = sep.plusMonths(1)
        repo.save(manualTransaction())
        repo.saveBenefits(oct, 2550, 1050, BenefitDays(5, null))
        repo.saveBenefits(sep, 2550, 1050, BenefitDays(0, 12))
        val expected = repo.benefits.value
        val payload = repo.exportBackupPayload()
        val json = org.json.JSONObject(payload.toString(Charsets.UTF_8))
        assertEquals(5, json.getInt("version"))
        assertEquals(4, json.getInt("schema"))
        assertEquals(listOf("2026-09", "2026-10"), (0 until 2).map { json.getJSONObject("benefits").getJSONArray("days").getJSONObject(it).getString("month") })
        assertEquals(BackupSummary(1, 0, 0, 0, true), repo.inspectBackupPayload(payload))
        // Device drifts: different rates and an override that is absent from the backup.
        repo.saveBenefits(YearMonth.of(2027, 1), 1, 2, BenefitDays(3, 4))
        repo.saveBenefits(oct, 1, 2, BenefitDays())
        repo.restoreBackupPayload(payload)
        assertEquals(expected, repo.benefits.value)
        assertFalse(prefs.contains("vr_days:2027-01"))
        assertFalse(prefs.contains("vt_days:$oct"))
        assertEquals(5, prefs.getInt("vr_days:$oct", -1))
        assertEquals(1, repo.transactions.value.size)
    }

    @Test fun backupV2LeavesDeviceBenefitsUntouched() = withBenefitRepository { repo, prefs ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(7, 8))
        val v2 = mutated(repo.exportBackupPayload()) { it.remove("benefits"); it.remove("profile"); it.put("version", 2) }
        assertEquals(BackupSummary(1, 0, 0, 0, false), repo.inspectBackupPayload(v2))
        repo.saveBenefits(YearMonth.of(2026, 10), 100, 200, BenefitDays(1, null))
        val before = repo.benefits.value
        val storedBefore = prefs.all.toMap()
        repo.restoreBackupPayload(v2)
        assertEquals(before, repo.benefits.value)
        assertEquals(storedBefore, prefs.all)
        val withBenefitsKey = mutated(v2) { it.put("benefits", org.json.JSONObject()) }
        try { repo.restoreBackupPayload(withBenefitsKey); fail("v2 must not carry benefits") } catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedBenefitsAreRejectedWithoutChangingLedgerOrBenefits() = withBenefitRepository { repo, prefs ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(7, 8))
        val good = repo.exportBackupPayload()
        val ledger = repo.transactions.value
        val benefits = repo.benefits.value
        val stored = prefs.all.toMap()
        fun ben(o: org.json.JSONObject) = o.getJSONObject("benefits")
        fun entry(o: org.json.JSONObject) = ben(o).getJSONArray("days").getJSONObject(0)
        val bad = listOf<(org.json.JSONObject) -> Unit>(
            { ben(it).put("vr_daily_cents", -1) },
            { ben(it).put("vt_daily_cents", 1.5) },
            { entry(it).put("vr", 32) },
            { entry(it).put("vt", -1) },
            { entry(it).put("vr", org.json.JSONObject.NULL).put("vt", org.json.JSONObject.NULL) },
            { entry(it).put("month", "2026-9") },
            { ben(it).getJSONArray("days").put(org.json.JSONObject(entry(it).toString())) },
            { ben(it).put("extra", 1) },
            { entry(it).put("extra", 1) },
            { ben(it).remove("days") },
            { ben(it).remove("vt_daily_cents") },
            { entry(it).remove("vt") },
            { it.remove("benefits") },
            { it.put("benefits", "x") },
        )
        bad.forEachIndexed { index, change ->
            val payload = mutated(good, change)
            try { repo.inspectBackupPayload(payload); fail("case $index accepted") } catch (_: IllegalArgumentException) { }
            try { repo.restoreBackupPayload(payload); fail("case $index restored") } catch (_: IllegalArgumentException) { }
            assertEquals(ledger, repo.transactions.value)
            assertEquals(benefits, repo.benefits.value)
            assertEquals(stored, prefs.all)
        }
    }

    private val monThu = BenefitCalculator.WORKDAYS - DayOfWeek.FRIDAY

    @Test fun backupV4RoundTripPreservesBothWeekdaySetsAndNeverCarriesTheName() = withBenefitRepository { repo, prefs ->
        val sep = YearMonth.of(2026, 9)
        repo.setProfileName("Rafael")
        repo.saveBenefits(sep, 3900, 1080, BenefitDays(), setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), monThu)
        val payload = repo.exportBackupPayload()
        val text = payload.toString(Charsets.UTF_8)
        assertFalse("name must not be exported", text.contains("Rafael"))
        val benefitsJson = org.json.JSONObject(text).getJSONObject("benefits")
        assertEquals("[1,6]", benefitsJson.getJSONArray("vr_weekdays").toString())
        assertEquals("[1,2,3,4]", benefitsJson.getJSONArray("vt_weekdays").toString())
        repo.saveBenefits(sep, 1, 2, BenefitDays(), emptySet(), setOf(DayOfWeek.SUNDAY))
        repo.restoreBackupPayload(payload)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), repo.benefits.value.vrWeekdays)
        assertEquals(monThu, repo.benefits.value.vtWeekdays)
        assertEquals(3900L, repo.benefits.value.vrDailyCents)
        assertEquals("Rafael", repo.profileName.value)
        assertEquals(0b100001, prefs.getInt("vr_weekdays", -1))
    }

    @Test fun backupV3PayloadStillRestoresWithMondayToFriday() = withBenefitRepository { repo, _ ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(7, 8), setOf(DayOfWeek.SUNDAY), emptySet())
        val v3 = mutated(repo.exportBackupPayload()) {
            it.put("version", 3)
            it.remove("profile")
            it.getJSONObject("benefits").remove("vr_weekdays")
            it.getJSONObject("benefits").remove("vt_weekdays")
        }
        assertEquals(BackupSummary(1, 0, 0, 0, true), repo.inspectBackupPayload(v3))
        repo.restoreBackupPayload(v3)
        assertEquals(BenefitCalculator.WORKDAYS, repo.benefits.value.vrWeekdays)
        assertEquals(BenefitCalculator.WORKDAYS, repo.benefits.value.vtWeekdays)
        assertEquals(2550L, repo.benefits.value.vrDailyCents)
        assertEquals(BenefitDays(7, 8), repo.benefits.value.overrides[YearMonth.of(2026, 9)])
        // A v3 file must not carry the v4 keys.
        val v3WithKeys = mutated(v3) { it.getJSONObject("benefits").put("vr_weekdays", org.json.JSONArray()) }
        try { repo.inspectBackupPayload(v3WithKeys); fail("v3 must not carry weekdays") } catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedWeekdayArraysAreRejectedWithoutChangingData() = withBenefitRepository { repo, prefs ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(), monThu, BenefitCalculator.WORKDAYS)
        val good = repo.exportBackupPayload()
        val ledger = repo.transactions.value
        val benefits = repo.benefits.value
        val stored = prefs.all.toMap()
        fun ben(o: org.json.JSONObject) = o.getJSONObject("benefits")
        fun jsonArray(vararg values: Any) = org.json.JSONArray().also { array -> values.forEach { array.put(it) } }
        val bad = listOf<(org.json.JSONObject) -> Unit>(
            { ben(it).put("vr_weekdays", jsonArray(0, 1)) },
            { ben(it).put("vt_weekdays", jsonArray(1, 8)) },
            { ben(it).put("vr_weekdays", jsonArray(1, 1)) },
            { ben(it).put("vt_weekdays", jsonArray(2, 1)) },
            { ben(it).put("vr_weekdays", jsonArray(1.5)) },
            { ben(it).put("vt_weekdays", jsonArray("1")) },
            { ben(it).put("vr_weekdays", jsonArray(-1)) },
            { ben(it).put("vr_weekdays", jsonArray(1, 2, 3, 4, 5, 6, 7, 7)) },
            { ben(it).put("vr_weekdays", "1,2") },
            { ben(it).put("vt_weekdays", org.json.JSONObject.NULL) },
            { ben(it).remove("vr_weekdays") },
            { ben(it).remove("vt_weekdays") },
        )
        bad.forEachIndexed { index, change ->
            val payload = mutated(good, change)
            try { repo.inspectBackupPayload(payload); fail("case $index accepted") } catch (_: IllegalArgumentException) { }
            try { repo.restoreBackupPayload(payload); fail("case $index restored") } catch (_: IllegalArgumentException) { }
            assertEquals(ledger, repo.transactions.value)
            assertEquals(benefits, repo.benefits.value)
            assertEquals(stored, prefs.all)
        }
    }

    @Test fun weekdaySetsPersistAsCompactMasksAndMalformedStoredValuesFallBackToMondayToFriday() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "benefits_weekdays_${java.util.UUID.randomUUID()}_")
        val prefs = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
        try {
            val repo = LedgerRepository(context)
            assertEquals(BenefitCalculator.WORKDAYS, repo.benefits.value.vrWeekdays)
            runBlocking {
                repo.saveBenefits(YearMonth.of(2026, 10), 3900, 1080, BenefitDays(), BenefitCalculator.WORKDAYS, monThu)
            }
            assertEquals(0b0011111, prefs.getInt("vr_weekdays", -1))
            assertEquals(0b0001111, prefs.getInt("vt_weekdays", -1))
            assertEquals(monThu, LedgerRepository(context).benefits.value.vtWeekdays)
            // A save with null weekdays keeps the stored sets.
            runBlocking { repo.saveBenefits(YearMonth.of(2026, 10), 1, 2, BenefitDays()) }
            assertEquals(monThu, LedgerRepository(context).benefits.value.vtWeekdays)
            // Empty set is valid and distinct from "missing".
            runBlocking { repo.saveBenefits(YearMonth.of(2026, 10), 1, 2, BenefitDays(), emptySet(), emptySet()) }
            assertEquals(emptySet<DayOfWeek>(), LedgerRepository(context).benefits.value.vrWeekdays)
            assertEquals(0, prefs.getInt("vr_weekdays", -1))
            for (malformed in listOf(128, -1, Int.MAX_VALUE)) {
                assertTrue(prefs.edit().putInt("vr_weekdays", malformed).commit())
                assertEquals(BenefitCalculator.WORKDAYS, LedgerRepository(context).benefits.value.vrWeekdays)
            }
            assertTrue(prefs.edit().putString("vr_weekdays", "31").putLong("vt_weekdays", 15L).commit())
            val loaded = LedgerRepository(context).benefits.value
            assertEquals(BenefitCalculator.WORKDAYS, loaded.vrWeekdays)
            assertEquals(BenefitCalculator.WORKDAYS, loaded.vtWeekdays)
            assertTrue(prefs.edit().remove("vr_weekdays").remove("vt_weekdays").commit())
            assertEquals(BenefitCalculator.WORKDAYS, LedgerRepository(context).benefits.value.vtWeekdays)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun profileNameIsTrimmedLimitedAndOnboardingFlagPersistsAcrossRecreation() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "profile_${java.util.UUID.randomUUID()}_")
        try {
            val repo = LedgerRepository(context)
            assertEquals("", repo.profileName.value)
            assertFalse(repo.onboardingDone.value)
            runBlocking { repo.setProfileName("  " + "a".repeat(60) + "  ") }
            assertEquals("a".repeat(40), repo.profileName.value)
            runBlocking { repo.setProfileName("   ") }
            assertEquals("", repo.profileName.value)
            runBlocking { repo.setProfileName(" Rafael "); repo.setOnboardingDone(true) }
            val reopened = LedgerRepository(context)
            assertEquals("Rafael", reopened.profileName.value)
            assertTrue(reopened.onboardingDone.value)
            runBlocking { reopened.setOnboardingDone(false) }
            assertFalse(LedgerRepository(context).onboardingDone.value)
            // Completing writes rates, both weekday sets, name and flag together.
            runBlocking { reopened.completeOnboarding("Ana", 3900, 1080, BenefitCalculator.WORKDAYS, monThu) }
            val done = LedgerRepository(context)
            assertTrue(done.onboardingDone.value)
            assertEquals("Ana", done.profileName.value)
            assertEquals(BenefitSettings(3900, 1080, vtWeekdays = monThu), done.benefits.value)
            // The name lives only in the private "profile" preferences, never in the benefits file.
            assertFalse(context.getSharedPreferences("benefits", Context.MODE_PRIVATE).all.values.contains("Ana"))
        } finally {
            context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("benefits", Context.MODE_PRIVATE).edit().clear().commit()
            runBlocking { LedgerRepository(context).clear() }
        }
    }

    @Test fun clearKeepsProfileOnboardingFlagAndBenefits() = withBenefitRepository { repo, _ ->
        repo.save(manualTransaction())
        repo.completeOnboarding("Ana", 3900, 1080, BenefitCalculator.WORKDAYS, monThu)
        val benefits = repo.benefits.value
        repo.clear()
        assertTrue(repo.transactions.value.isEmpty())
        assertEquals("Ana", repo.profileName.value)
        assertTrue(repo.onboardingDone.value)
        assertEquals(benefits, repo.benefits.value)
    }

    @Test fun salaryPersistsAcrossRecreationRejectsNegativeAndSurvivesClearButNeverTheBenefitsFile() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "salary_${java.util.UUID.randomUUID()}_")
        try {
            val repo = LedgerRepository(context)
            assertEquals(0L, repo.salaryMonthlyCents.value)
            runBlocking { repo.setSalaryMonthlyCents(350000) }
            assertEquals(350000L, repo.salaryMonthlyCents.value)
            assertEquals(350000L, LedgerRepository(context).salaryMonthlyCents.value)
            try { runBlocking { repo.setSalaryMonthlyCents(-1) }; fail("negative salary accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(350000L, LedgerRepository(context).salaryMonthlyCents.value)
            runBlocking { repo.setSalaryMonthlyCents(Long.MAX_VALUE) }
            assertEquals(Long.MAX_VALUE, LedgerRepository(context).salaryMonthlyCents.value)
            // Name and salary commit together from the Perfil dialog; zero clears the salary.
            runBlocking { repo.saveProfile(" Ana ", 420000) }
            assertEquals("Ana", repo.profileName.value)
            assertEquals(420000L, LedgerRepository(context).salaryMonthlyCents.value)
            runBlocking { repo.saveProfile("Ana", 0) }
            assertEquals(0L, LedgerRepository(context).salaryMonthlyCents.value)
            // Completing onboarding persists it with the rest of the profile; omitting it keeps the stored value.
            runBlocking { repo.completeOnboarding("Bia", 3900, 1080, BenefitCalculator.WORKDAYS, monThu, 510000) }
            assertEquals(510000L, LedgerRepository(context).salaryMonthlyCents.value)
            runBlocking { repo.completeOnboarding("Bia", 3900, 1080, BenefitCalculator.WORKDAYS, monThu) }
            assertEquals(510000L, LedgerRepository(context).salaryMonthlyCents.value)
            assertFalse(context.getSharedPreferences("benefits", Context.MODE_PRIVATE).all.keys.any { it.contains("salary") })
            // A malformed stored value falls back to "not set".
            assertTrue(context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putLong("salary_monthly_cents", -5).commit())
            assertEquals(0L, LedgerRepository(context).salaryMonthlyCents.value)
            assertTrue(context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putString("salary_monthly_cents", "10").commit())
            assertEquals(0L, LedgerRepository(context).salaryMonthlyCents.value)
        } finally {
            context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("benefits", Context.MODE_PRIVATE).edit().clear().commit()
            runBlocking { LedgerRepository(context).clear() }
        }
    }

    @Test fun clearKeepsTheSalaryLikeTheRestOfTheProfile() = withBenefitRepository { repo, _ ->
        repo.save(manualTransaction())
        repo.completeOnboarding("Ana", 3900, 1080, BenefitCalculator.WORKDAYS, monThu, 350000)
        repo.clear()
        assertTrue(repo.transactions.value.isEmpty())
        assertEquals(350000L, repo.salaryMonthlyCents.value)
        assertEquals("Ana", repo.profileName.value)
    }

    @Test fun backupV5RoundTripCarriesTheSalaryButNeverTheName() = withBenefitRepository { repo, _ ->
        repo.save(manualTransaction())
        repo.setProfileName("Rafael")
        repo.setSalaryMonthlyCents(350000)
        val payload = repo.exportBackupPayload()
        val text = payload.toString(Charsets.UTF_8)
        assertFalse("name must not be exported", text.contains("Rafael"))
        val json = org.json.JSONObject(text)
        assertEquals(5, json.getInt("version"))
        assertEquals(4, json.getInt("schema"))
        assertEquals("{\"salary_monthly_cents\":350000}", json.getJSONObject("profile").toString())
        assertEquals(BackupSummary(1, 0, 0, 0, true), repo.inspectBackupPayload(payload))
        repo.setSalaryMonthlyCents(1)
        repo.restoreBackupPayload(payload)
        assertEquals(350000L, repo.salaryMonthlyCents.value)
        assertEquals("Rafael", repo.profileName.value)
        // A backup made without a salary restores "not set" over a stored one.
        repo.setSalaryMonthlyCents(0)
        val without = repo.exportBackupPayload()
        repo.setSalaryMonthlyCents(777)
        repo.restoreBackupPayload(without)
        assertEquals(0L, repo.salaryMonthlyCents.value)
    }

    @Test fun backupV4StillRestoresAndLeavesTheStoredSalaryUntouched() = withBenefitRepository { repo, _ ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(7, 8), monThu, BenefitCalculator.WORKDAYS)
        repo.setSalaryMonthlyCents(350000)
        val v4 = mutated(repo.exportBackupPayload()) { it.put("version", 4); it.remove("profile") }
        assertEquals(BackupSummary(1, 0, 0, 0, true), repo.inspectBackupPayload(v4))
        repo.setSalaryMonthlyCents(420000)
        repo.saveBenefits(YearMonth.of(2026, 9), 1, 2, BenefitDays())
        repo.restoreBackupPayload(v4)
        assertEquals(420000L, repo.salaryMonthlyCents.value)
        assertEquals(2550L, repo.benefits.value.vrDailyCents)
        assertEquals(monThu, repo.benefits.value.vrWeekdays)
        // A v4 file must not carry the v5 profile object, and a v5 file must carry it.
        try { repo.inspectBackupPayload(mutated(v4) { it.put("profile", org.json.JSONObject().put("salary_monthly_cents", 1)) }); fail("v4 with profile accepted") }
        catch (_: IllegalArgumentException) { }
        try { repo.inspectBackupPayload(mutated(v4) { it.put("version", 5) }); fail("v5 without profile accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedProfileObjectsAreRejectedWithoutChangingDataOrSalary() = withBenefitRepository { repo, prefs ->
        repo.save(manualTransaction())
        repo.saveBenefits(YearMonth.of(2026, 9), 2550, 1050, BenefitDays(7, 8))
        repo.setSalaryMonthlyCents(350000)
        val good = repo.exportBackupPayload()
        val ledger = repo.transactions.value
        val benefits = repo.benefits.value
        val stored = prefs.all.toMap()
        fun profile(o: org.json.JSONObject) = o.getJSONObject("profile")
        val bad = listOf<(org.json.JSONObject) -> Unit>(
            { profile(it).put("salary_monthly_cents", -1) },
            { profile(it).put("salary_monthly_cents", 1.5) },
            { profile(it).put("salary_monthly_cents", "350000") },
            { profile(it).put("salary_monthly_cents", org.json.JSONObject.NULL) },
            { profile(it).put("salary_monthly_cents", true) },
            { profile(it).remove("salary_monthly_cents") },
            { profile(it).put("name", "Rafael") },
            { profile(it).put("extra", 1) },
            { it.remove("profile") },
            { it.put("profile", "x") },
            { it.put("profile", org.json.JSONArray().put(1)) },
            { it.put("profile", org.json.JSONObject.NULL) },
        )
        bad.forEachIndexed { index, change ->
            val payload = mutated(good, change)
            try { repo.inspectBackupPayload(payload); fail("case $index accepted") } catch (_: IllegalArgumentException) { }
            try { repo.restoreBackupPayload(payload); fail("case $index restored") } catch (_: IllegalArgumentException) { }
            assertEquals(ledger, repo.transactions.value)
            assertEquals(benefits, repo.benefits.value)
            assertEquals(stored, prefs.all)
            assertEquals(350000L, repo.salaryMonthlyCents.value)
        }
    }

    @Test fun restoreFailingOnPreferenceWriteRollsBackDatabaseAndRevertsPreferences() {
        var profileStore: FailingPreferences? = null
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext,
            "restore_atomic_${java.util.UUID.randomUUID()}_") { name, prefs ->
            if (name == "profile") FailingPreferences(prefs).also { profileStore = it } else prefs
        }
        val repo = LedgerRepository(context)
        val benefitPrefs = context.getSharedPreferences("benefits", Context.MODE_PRIVATE)
        try { runBlocking {
            val sep = YearMonth.of(2026, 9)
            repo.save(manualTransaction())
            repo.saveBenefits(sep, 2550, 1050, BenefitDays(7, 8))
            repo.setSalaryMonthlyCents(350000)
            val payload = repo.exportBackupPayload()
            // Device drifts after the backup: one more transaction, other benefits and salary.
            repo.save(manualTransaction().copy(id = "after", amountCents = 2500L))
            repo.saveBenefits(sep, 1, 2, BenefitDays())
            repo.setSalaryMonthlyCents(420000)
            val ledger = repo.transactions.value
            val benefits = repo.benefits.value
            val storedBenefits = benefitPrefs.all.toMap()
            // Benefits are written first and succeed; the salary write then fails before the SQLite commit.
            profileStore!!.failures = 1
            try { repo.restoreBackupPayload(payload); fail("restore must fail") }
            catch (e: RestoreFailedException) { assertTrue(e.previousKept) }
            assertEquals(ledger, repo.transactions.value)
            assertEquals(benefits, repo.benefits.value)
            assertEquals(storedBenefits, benefitPrefs.all)
            assertEquals(420000L, repo.salaryMonthlyCents.value)
            assertEquals(420000L, profileStore!!.getLong("salary_monthly_cents", -1))
            // Once the store works again the same payload restores completely.
            repo.restoreBackupPayload(payload)
            assertEquals(listOf("manual"), repo.transactions.value.map { it.id })
            assertEquals(2550L, repo.benefits.value.vrDailyCents)
            assertEquals(350000L, repo.salaryMonthlyCents.value)
        } } finally {
            runBlocking { repo.clear() }
            benefitPrefs.edit().clear().commit()
            profileStore?.edit()?.clear()?.commit()
        }
    }

    /** Preference store whose next [failures] commits throw, as a failed disk write would. */
    private class FailingPreferences(private val base: SharedPreferences) : SharedPreferences by base {
        var failures = 0
        override fun edit(): SharedPreferences.Editor = Editor(base.edit())
        private inner class Editor(private val e: SharedPreferences.Editor) : SharedPreferences.Editor by e {
            override fun putString(key: String?, value: String?) = apply { e.putString(key, value) }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { e.putStringSet(key, values) }
            override fun putInt(key: String?, value: Int) = apply { e.putInt(key, value) }
            override fun putLong(key: String?, value: Long) = apply { e.putLong(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { e.putFloat(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { e.putBoolean(key, value) }
            override fun remove(key: String?) = apply { e.remove(key) }
            override fun clear() = apply { e.clear() }
            override fun commit(): Boolean {
                if (failures > 0) { failures--; throw IllegalStateException("Falha simulada de gravação.") }
                return e.commit()
            }
        }
    }

    private fun enableCapture() {
        repository.setBankEnabled(bank, true)
        repository.setCaptureEnabled(true)
    }

    @Test fun discardedNotificationReportsItsCategoryAndStoresNothing() = runBlocking {
        repository.setBankEnabled(bank, true)
        repository.setCaptureEnabled(true)
        val before = repository.transactions.value.size
        val promo = repository.ingestClassified("k1", bank, 1L, "Aproveite a oferta de pagamento parcelado")
        assertEquals(IngestOutcome.Ignored(br.com.fluxo.core.IgnoreReason.PROMOTION), promo)
        val noWord = repository.ingestClassified("k2", bank, 1L, "Seu extrato está pronto")
        assertEquals(IngestOutcome.Ignored(br.com.fluxo.core.IgnoreReason.NO_FINANCIAL_WORD), noWord)
        val noAmount = repository.ingestClassified("k3", bank, 1L, "Pix recebido com sucesso")
        assertEquals(IngestOutcome.Ignored(br.com.fluxo.core.IgnoreReason.NO_AMOUNT), noAmount)
        assertEquals(before, repository.transactions.value.size)
        assertEquals(IngestOutcome.Stored, repository.ingestClassified("k4", bank, 1L, "Pix recebido R$ 12,34"))
        assertEquals(before + 1, repository.transactions.value.size)
    }

    @Test fun sessionCountersKeepOnlyCountsAndAFixedCategory() {
        CaptureHealth.resetForTest()
        CaptureHealth.observed("com.c6bank.app", 5L)
        CaptureHealth.observed("com.c6bank.app", 6L)
        CaptureHealth.stored("com.c6bank.app")
        CaptureHealth.ignored("com.c6bank.app", br.com.fluxo.core.IgnoreReason.GROUP_SUMMARY)
        val counters = CaptureHealth.state.value.counters.getValue("com.c6bank.app")
        assertEquals(br.com.fluxo.core.SourceCounters(2, 1, 1, br.com.fluxo.core.IgnoreReason.GROUP_SUMMARY), counters)
        assertEquals("2 observadas · 1 lançada · 1 ignorada (resumo de grupo)", counters.summary())
        CaptureHealth.resetForTest()
    }

    private fun manualTransaction() = Transaction(
        id = "manual", sourceKey = null, bankPackage = "manual", amountCents = 1000L,
        direction = Direction.EXPENSE, method = PaymentMethod.DEBIT_CARD,
        status = ReviewStatus.CONFIRMED, occurredAt = 1L, description = "Teste sintético",
    )

    companion object {
        private val testRepository: LedgerRepository by lazy {
            LedgerRepository(IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext))
        }
    }

    private class IsolatedContext(
        base: Context, private val prefix: String = "instrumentation_ledger_",
        private val wrap: (String, SharedPreferences) -> SharedPreferences = { _, prefs -> prefs },
    ) : ContextWrapper(base) {
        private fun isolated(name: String) = "$prefix$name"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            wrap(name, baseContext.getSharedPreferences(isolated(name), mode))
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(isolated(name))
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(isolated(name), mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(isolated(name), mode, factory, errorHandler)
    }
}
