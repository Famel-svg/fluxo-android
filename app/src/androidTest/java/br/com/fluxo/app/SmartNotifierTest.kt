package br.com.fluxo.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.AlertKind
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartNotifierTest {
    private class Isolated(base: Context) : ContextWrapper(base) {
        private fun n(name: String) = "smart_notifier_test_$name"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = baseContext.getSharedPreferences(n(name), mode)
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(n(name))
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(n(name), mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(n(name), mode, factory, errorHandler)
    }

    @Test fun pendingAtEightPmPostsAndMarksReviewKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = LedgerRepository(Isolated(context))
        repository.clear()
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = LocalDateTime.of(2026, 11, 10, 20, 0)
        repository.save(Transaction(
            id = "pendente", sourceKey = null, bankPackage = "manual", amountCents = 1_000L, direction = Direction.EXPENSE,
            method = PaymentMethod.DEBIT_CARD, status = ReviewStatus.REVIEW,
            occurredAt = now.atZone(zone).toInstant().toEpochMilli(), description = "Teste sintético",
        ))
        // canPost = true: the emulator may not hold POST_NOTIFICATIONS; a failed notify would leave the key unmarked.
        val posted = SmartNotifier.run(context, repository, now, zone, canPost = true)
        val kinds = posted.map { it.kind }
        assertTrue(kinds.toString(), AlertKind.REVIEW_DIGEST in kinds)
        assertEquals(true, "review:2026-11-10" in repository.sentAlertKeys.value)
        repository.clear()
    }
}
