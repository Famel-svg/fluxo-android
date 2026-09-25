package br.com.fluxo.app

import android.content.ComponentName
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two instrumentation processes separated by host force-stop; never an in-memory reopen. */
@RunWith(AndroidJUnit4::class)
class NotificationRestartE2eTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val repository get() = (context.applicationContext as FluxoApplication).repository
    private val marker get() = context.getSharedPreferences("notification_e2e_restart", 0)
    private val bank = "com.nu.production"

    @Test fun persistenceAndFutureCaptureAcrossProcessRestart() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("restartPhase")
        assumeTrue(args.getString("notificationE2e") == "true" && phase in setOf("seed", "verify", "cleanup"))
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        var seeded = false
        try {
            when (phase) {
                "seed" -> {
                    repository.clear()
                    supportedBanks.forEach { repository.setBankEnabled(it.packageName, it.packageName == bank) }
                    repository.setCaptureEnabled(true)
                    connect()
                    post(701, "Pix recebido R$ 17,31")
                    await { repository.transactions.value.size == 1 }
                    val row = repository.transactions.value.single()
                    assertEquals(1731L, row.amountCents)
                    assertEquals(ReviewStatus.CONFIRMED, row.status)
                    assertEquals(PaymentMethod.PIX, row.method)
                    assertEquals(Direction.INCOME, row.direction)
                    assertTrue(marker.edit().putInt("pid", Process.myPid()).putString("id", row.id)
                        .putString("source", row.sourceKey).commit())
                    seeded = true
                }
                "verify" -> {
                    assertTrue("Seed evidence required", marker.contains("pid"))
                    assertNotEquals("Must be a different OS process", marker.getInt("pid", -1), Process.myPid())
                    assertTrue("Capture consent must survive", repository.captureEnabled.value)
                    assertEquals(setOf(bank), repository.enabledBanks.value)
                    repository.refresh()
                    val before = repository.transactions.value.single()
                    assertEquals(marker.getString("id", null), before.id)
                    assertEquals(marker.getString("source", null), before.sourceKey)
                    assertEquals(1731L, before.amountCents)
                    connect()
                    // Same active Android key, no original timestamp: persisted alias must win.
                    post(701, "Pix recebido R$ 28,42")
                    await { repository.transactions.value.singleOrNull()?.amountCents == 2842L }
                    assertEquals(before.id, repository.transactions.value.single().id)
                    assertEquals(ReviewStatus.REVIEW, repository.transactions.value.single().status)
                    post(702, "Pix recebido R$ 39,53")
                    await { repository.transactions.value.size == 2 }
                    assertEquals(ReviewStatus.CONFIRMED, repository.transactions.value.single { it.amountCents == 3953L }.status)
                }
            }
        } finally {
            if (!seeded) {
                repository.setCaptureEnabled(false)
                try { broadcast("--es operation cancelAll") }
                finally {
                    repository.clear()
                    supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
                    assertTrue(marker.edit().clear().commit())
                }
            }
        }
    }

    private fun connect() {
        shell("cmd notification allow_listener br.com.fluxo.app/.BankNotificationListener")
        instrument.runOnMainSync {
            NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java))
        }
        await { CaptureHealth.state.value.connected == true }
    }
    private fun post(id: Int, text: String) {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val previous = CaptureHealth.state.value.lastObserved[bank] ?: 0L
        await { System.currentTimeMillis() > previous }
        broadcast("--es operation post --ei id $id --el when 0 --es text64 $encoded")
        await { (CaptureHealth.state.value.lastObserved[bank] ?: 0L) > previous }
    }
    private fun broadcast(args: String) {
        assertTrue(shell("am broadcast --receiver-foreground --include-stopped-packages -n $bank/br.com.fluxo.fixture.EmitterReceiver $args").contains("result=1"))
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrument.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (!predicate() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertTrue("Timed out waiting for restart delivery state", predicate())
    }
}
