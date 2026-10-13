package br.com.fluxo.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.IgnoreReason
import br.com.fluxo.core.ReconnectResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Listener reconnection against the real OS. Opt-in disposable emulator only (see run.ps1). */
@RunWith(AndroidJUnit4::class)
class NotificationConnectionE2eTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = (context.applicationContext as FluxoApplication).repository
    private val listener = "br.com.fluxo.app/.BankNotificationListener"
    private val bank = "com.nu.production"
    private val c6 = "com.c6bank.app"
    private var prepared = false
    private var originallyAllowed = false

    @Before fun prepare() = runBlocking {
        assumeTrue("Use tools/notification-e2e/run.ps1 on disposable emulator",
            InstrumentationRegistry.getArguments().getString("notificationE2e") == "true")
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        originallyAllowed = shell("settings get secure enabled_notification_listeners").contains("br.com.fluxo.app/")
        prepared = true
        setComponent(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        emit(bank, "cancelAll"); emit(c6, "cancelAll")
        repository.clear()
        repository.setAllowOtherApps(false)
        supportedBanks.forEach { repository.setBankEnabled(it.packageName, true) }
        repository.setCaptureEnabled(true)
        shell("cmd notification allow_listener $listener")
        await("access granted") { CaptureConnection.hasAccess(context) }
        await("listener connection") { CaptureHealth.state.value.connected == true }
    }

    @After fun cleanup() = runBlocking {
        if (!prepared) return@runBlocking
        try {
            repository.setCaptureEnabled(false)
            emit(bank, "cancelAll"); emit(c6, "cancelAll")
            repository.clear()
            supportedBanks.forEach { repository.setBankEnabled(it.packageName, false) }
        } finally {
            setComponent(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            shell("cmd notification ${if (originallyAllowed) "allow_listener" else "disallow_listener"} $listener")
            assertComponentUsable()
        }
    }

    @Test fun reconnectRecoversTheListenerAndALaterNotificationIsCaptured() = runBlocking {
        val result = CaptureConnection.reconnect(context)
        assertEquals(ReconnectResult.Connected, result)
        assertTrue(CaptureHealth.state.value.connected == true)
        assertFalse(CaptureHealth.state.value.reconnecting)
        assertComponentUsable()
        post(bank, 1, "Pix recebido R$ 10,00")
        await("capture after reconnect") { repository.transactions.value.size == 1 }
        assertEquals(1000L, repository.transactions.value.single().amountCents)
    }

    @Test fun reconnectWithoutAccessReportsItAndNeverTouchesTheComponent() = runBlocking {
        shell("cmd notification disallow_listener $listener")
        await("access revoked") { !CaptureConnection.hasAccess(context) }
        val before = CaptureConnection.componentState(context)
        assertEquals(ReconnectResult.NoAccess, CaptureConnection.reconnect(context))
        assertEquals(before, CaptureConnection.componentState(context))
        assertComponentUsable()
    }

    @Test fun startCheckRepairsAComponentLeftDisabledAndTheListenerComesBack() = runBlocking {
        setComponent(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        await("component disabled") {
            CaptureConnection.componentState(context) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        CaptureConnection.ensureComponentEnabled(context)
        assertComponentUsable()
        await("listener bound again") { CaptureHealth.state.value.connected == true }
    }

    @Test fun discardedNotificationsCountByCategoryAndStoreNothing() = runBlocking {
        val before = CaptureHealth.state.value.counters[c6] ?: br.com.fluxo.core.SourceCounters()
        post(c6, 1, "Aproveite a oferta de pagamento parcelado")
        await("promotion counted") { (CaptureHealth.state.value.counters[c6]?.ignored ?: 0) == before.ignored + 1 }
        var counters = CaptureHealth.state.value.counters.getValue(c6)
        assertEquals(IgnoreReason.PROMOTION, counters.lastIgnore)
        post(c6, 2, "Pix recebido com sucesso")
        await("no-amount counted") { (CaptureHealth.state.value.counters[c6]?.ignored ?: 0) == before.ignored + 2 }
        counters = CaptureHealth.state.value.counters.getValue(c6)
        assertEquals(IgnoreReason.NO_AMOUNT, counters.lastIgnore)
        assertEquals(before.observed + 2, counters.observed)
        assertEquals(before.stored, counters.stored)
        assertTrue(repository.transactions.value.isEmpty())
    }

    private fun setComponent(state: Int) = context.packageManager.setComponentEnabledSetting(
        android.content.ComponentName(context, BankNotificationListener::class.java), state, PackageManager.DONT_KILL_APP)

    private fun assertComponentUsable() {
        val state = CaptureConnection.componentState(context)
        assertTrue("component state $state", state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    private fun post(pkg: String, id: Int, text: String) {
        val previous = CaptureHealth.state.value.lastObserved[pkg] ?: 0L
        await("distinct callback clock") { System.currentTimeMillis() > previous }
        emit(pkg, "post", id, " --el when ${System.currentTimeMillis()} --es text64 ${encoded(text)} --ez summary false")
        await("OS callback receipt") { (CaptureHealth.state.value.lastObserved[pkg] ?: 0L) > previous }
    }

    private fun emit(pkg: String, operation: String, id: Int = 1, extras: String = "") {
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
}
