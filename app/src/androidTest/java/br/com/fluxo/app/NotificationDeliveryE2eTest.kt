package br.com.fluxo.app

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual OS delivery, never direct callback/ingest injection. Opt-in disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryE2eTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository get() = (instrumentation.targetContext.applicationContext as FluxoApplication).repository
    private val listener = "br.com.fluxo.app/.BankNotificationListener"
    private val bank = "com.nu.production"
    private val originalTime = System.currentTimeMillis()
    private val emitters = listOf(bank, "com.c6bank.app", "com.itau", "br.com.fluxo.fixture.unsupported")
    private var prepared = false
    private var originallyAllowed = false

    @Before fun prepare() = runBlocking {
        assumeTrue("Use tools/notification-e2e/run.ps1 on disposable emulator",
            InstrumentationRegistry.getArguments().getString("notificationE2e") == "true")
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        originallyAllowed = shell("settings get secure enabled_notification_listeners")
            .contains("br.com.fluxo.app/")
        emitters.forEach { emit(it, "cancelAll") }
        prepared = true
        repository.clear()
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, true) }
        repository.setCaptureEnabled(true)
        shell("cmd notification allow_listener $listener")
        await("Android listener connection") { CaptureHealth.state.value.connected == true }
    }

    @After fun cleanup() = runBlocking {
        if (prepared) {
            repository.setCaptureEnabled(false)
            try { emitters.forEach { emit(it, "cancelAll") } }
            finally {
                try {
                    repository.clear()
                    supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
                } finally {
                    shell("cmd notification ${if (originallyAllowed) "allow_listener" else "disallow_listener"} $listener")
                }
            }
        }
    }

    @Test fun allSupportedPackagesDeliverThroughAndroidAndSurviveRepositoryReopen() = runBlocking {
        supportedBanks.forEachIndexed { index, supported ->
            post(supported.packageName, index + 1, "Pix recebido R$ 10,00")
            await("delivery from supported package") { repository.transactions.value.size == index + 1 }
        }
        assertEquals(supportedBanks.map { it.packageName }.toSet(), repository.transactions.value.map { it.bankPackage }.toSet())
        repository.transactions.value.forEach {
            assertEquals(1000L, it.amountCents)
            assertEquals(Direction.INCOME, it.direction)
            assertEquals(PaymentMethod.PIX, it.method)
            assertEquals(ReviewStatus.CONFIRMED, it.status)
            assertEquals("Movimentação detectada", it.description)
        }
        post(bank, 10, "Compra aprovada no credito R$ 15,00")
        post(bank, 11, "Compra aprovada no debito R$ 25,00")
        await("card modalities") { repository.transactions.value.size == 5 }
        assertEquals(PaymentMethod.CREDIT_CARD, repository.transactions.value.single { it.amountCents == 1500L }.method)
        assertEquals(PaymentMethod.DEBIT_CARD, repository.transactions.value.single { it.amountCents == 2500L }.method)
        val reopened = LedgerRepository(instrumentation.targetContext)
        reopened.refresh()
        assertEquals(repository.transactions.value.toSet(), reopened.transactions.value.toSet())
    }

    @Test fun malformedAndFutureEventsAreReviewAndBigTextOverridesShortBody() {
        listOf("Pix recebido R$ 10,00e3", "Pix recebido R$ 10,00/20,00",
            "Pix recebido R$ 10,00abc", "Pix recebido sera confirmado R$ 10,00").forEachIndexed { i, text ->
            post(bank, i + 1, text)
            await("review event") { repository.transactions.value.size == i + 1 }
        }
        assertTrue(repository.transactions.value.all { it.status == ReviewStatus.REVIEW })
        post(bank, 20, "Pix recebido R$ 1,00", big = "Pix recebido R$ 23,45")
        await("BigText") { repository.transactions.value.any { it.amountCents == 2345L } }
        assertFalse(repository.transactions.value.any { it.amountCents == 100L })
    }

    @Test fun redeliveryUpdatesAndHumanCorrectionsNeverDuplicate() = runBlocking {
        post(bank, 1, "Pix recebido R$ 10,00")
        await("initial event") { repository.transactions.value.size == 1 }
        post(bank, 1, "Pix recebido R$ 10,00")
        stableCount(1)
        post(bank, 1, "Pix recebido R$ 20,00")
        await("updated amount") { repository.transactions.value.single().amountCents == 2000L }
        assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
        val corrected = repository.transactions.value.single().copy(amountCents = 3000, status = ReviewStatus.CONFIRMED)
        repository.save(corrected)
        post(bank, 1, "Pix recebido R$ 40,00")
        stableCount(1)
        assertEquals(corrected, repository.transactions.value.single())
        repository.delete(corrected.id)
        post(bank, 1, "Pix recebido R$ 50,00")
        stableCount(0)
    }

    @Test fun missingTimestampUpdatesDeduplicateAndRemovalAllowsKeyReuse() {
        post(bank, 1, "Pix recebido R$ 10,00", timestamp = 0)
        await("missing when") { repository.transactions.value.size == 1 }
        post(bank, 1, "Pix recebido R$ 20,00", timestamp = 0)
        await("alias update") { repository.transactions.value.single().amountCents == 2000L }
        emit(bank, "cancel", 1)
        SystemClock.sleep(700)
        post(bank, 1, "Pix recebido R$ 30,00", timestamp = 0)
        await("reused key after removal") { repository.transactions.value.size == 2 }
    }

    @Test fun pauseBankSelectionUnsupportedPackageAndGroupSummaryAreIgnored() {
        repository.setCaptureEnabled(false)
        post(bank, 1, "Pix recebido R$ 10,00")
        stableCount(0)
        emit(bank, "cancelAll")
        repository.setCaptureEnabled(true)
        repository.setBankEnabled(bank, false)
        post(bank, 2, "Pix recebido R$ 20,00")
        stableCount(0)
        repository.setBankEnabled(bank, true)
        post("br.com.fluxo.fixture.unsupported", 3, "Pix recebido R$ 30,00")
        post(bank, 4, "Pix recebido R$ 40,00", summary = true)
        post(bank, 5, "")
        stableCount(0)
        post(bank, 6, "Pix recebido R$ 60,00")
        await("positive control after ignored events") { repository.transactions.value.size == 1 }
        assertEquals(6000L, repository.transactions.value.single().amountCents)
    }

    @Test fun revocationStopsDeliveryAndRegrantReceivesFutureEvent() {
        shell("cmd notification disallow_listener $listener")
        await("disconnect") { CaptureHealth.state.value.connected == false }
        post(bank, 1, "Pix recebido R$ 10,00")
        stableCount(0)
        emit(bank, "cancelAll")
        shell("cmd notification allow_listener $listener")
        await("reconnect") { CaptureHealth.state.value.connected == true }
        post(bank, 2, "Pix recebido R$ 20,00")
        await("delivery after reconnect") { repository.transactions.value.size == 1 }
    }

    private fun post(pkg: String, id: Int, text: String, timestamp: Long = originalTime,
        big: String? = null, summary: Boolean = false) {
        val expectCallback = CaptureHealth.state.value.connected == true &&
            repository.captureEnabled.value && pkg in repository.enabledBanks.value
        val previous = CaptureHealth.state.value.lastObserved[pkg] ?: 0L
        if (expectCallback) {
            await("distinct callback clock") { System.currentTimeMillis() > previous }
        }
        emit(pkg, "post", id, " --el when $timestamp" +
            (if (text.isNotEmpty()) " --es text64 ${encoded(text)}" else "") +
            (big?.let { " --es big64 ${encoded(it)}" } ?: "") + " --ez summary $summary")
        if (expectCallback) await("OS callback receipt") {
            (CaptureHealth.state.value.lastObserved[pkg] ?: 0L) > previous
        }
    }

    private fun emit(pkg: String, operation: String, id: Int = 1, extras: String = "") {
        require(pkg in emitters)
        val output = shell("am broadcast --receiver-foreground --include-stopped-packages -n $pkg/br.com.fluxo.fixture.EmitterReceiver --es operation $operation --ei id $id$extras")
        assertTrue("Fixture broadcast must complete successfully: $output", output.contains("result=1"))
    }

    private fun encoded(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(label: String, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (!predicate() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertTrue("Timed out: $label", predicate())
    }
    private fun stableCount(expected: Int) {
        val end = SystemClock.elapsedRealtime() + 1500
        while (SystemClock.elapsedRealtime() < end) {
            assertEquals(expected, repository.transactions.value.size)
            SystemClock.sleep(50)
        }
    }
}
