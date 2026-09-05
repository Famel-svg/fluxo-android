package br.com.fluxo.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.Direction
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Category
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
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
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
    }

    @After fun clean() = runBlocking {
        repository.clear()
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
    }

    @Test fun captureRequiresBothGlobalEnableAndSelectedBank() = runBlocking {
        repository.ingest("paused", bank, 1L, "Pix recebido R$ 10,00")
        repository.setCaptureEnabled(true)
        repository.ingest("unselected", bank, 2L, "Pix recebido R$ 10,00")
        assertTrue(repository.transactions.value.isEmpty())
        repository.setBankEnabled(bank, true)
        repository.ingest("selected", bank, 3L, "Pix recebido R$ 10,00")
        repository.ingest("other-bank", "com.itau", 4L, "Pix recebido R$ 10,00")
        assertEquals(listOf("selected"), repository.transactions.value.map { it.sourceKey })
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
            assertEquals(2, db.version)
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

    private fun enableCapture() {
        repository.setBankEnabled(bank, true)
        repository.setCaptureEnabled(true)
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

    private class IsolatedContext(base: Context, private val prefix: String = "instrumentation_ledger_") : ContextWrapper(base) {
        private fun isolated(name: String) = "$prefix$name"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(isolated(name), mode)
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(isolated(name))
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(isolated(name), mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(isolated(name), mode, factory, errorHandler)
    }
}
