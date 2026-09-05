package br.com.fluxo.app

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvExportViewModelTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val files = mutableListOf<File>()
    private fun destination() = File.createTempFile("csv-export-test-", ".csv", application.cacheDir).also { files += it }
    private fun transaction() = Transaction("test", null, "manual", 1000, Direction.EXPENSE, PaymentMethod.PIX, ReviewStatus.CONFIRMED, 0, "Sintético")
    private suspend fun awaitCompletion(model: CsvExportViewModel) = withTimeout(5_000) { model.busy.first { !it } }

    @After fun cleanOwnTemporaryFiles() { files.forEach { it.delete() } }

    @Test fun writesFrozenSnapshotAndSignalsSuccessOnlyAfterClose() = runBlocking {
        val model = CsvExportViewModel(application)
        val rows = mutableListOf(transaction())
        assertTrue(model.prepare(rows))
        rows.clear()
        assertFalse(model.prepare(emptyList()))
        val file = destination()
        model.destination(Uri.fromFile(file))
        awaitCompletion(model)
        assertTrue(file.readText().contains("\"10,00\""))
        assertEquals("CSV salvo: 1 lançamento(s).", model.message.value)
    }

    @Test fun canceledPickerReleasesBusyStateAndDoesNotClaimSuccess() {
        val model = CsvExportViewModel(application)
        model.prepare(listOf(transaction()))
        model.destination(null)
        assertFalse(model.busy.value)
        assertEquals("Exportação cancelada.", model.message.value)
        assertTrue(model.prepare(emptyList()))
    }

    @Test fun unwritableDestinationReportsFailure() = runBlocking {
        val model = CsvExportViewModel(application)
        model.prepare(listOf(transaction()))
        // Parent is an existing regular file, so opening its child must fail.
        model.destination(Uri.fromFile(File(destination(), "cannot-exist.csv")))
        awaitCompletion(model)
        assertTrue(model.message.value.orEmpty().startsWith("Não foi possível concluir"))
        assertFalse(model.message.value.orEmpty().contains("CSV salvo"))
    }

    @Test fun missingSnapshotAfterProcessRestartNeverExportsDifferentData() {
        val model = CsvExportViewModel(application)
        val file = destination()
        model.destination(Uri.fromFile(file))
        assertFalse(model.busy.value)
        assertTrue(model.message.value.orEmpty().contains("sessão foi reiniciada"))
        assertEquals(0L, file.length())
    }
}
