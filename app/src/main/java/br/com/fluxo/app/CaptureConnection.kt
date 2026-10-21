package br.com.fluxo.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import br.com.fluxo.core.ComponentRecycle
import br.com.fluxo.core.NotificationAccess
import br.com.fluxo.core.ReconnectPolicy
import br.com.fluxo.core.ReconnectResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class SettingsTarget { NOTIFICATION_ACCESS, APP_DETAILS, BATTERY, AUTOSTART }

/**
 * Android calls around the notification listener. Decisions live in core (ListenerStatus,
 * ReconnectPolicy, ComponentRecycle) where they are unit tested.
 */
object CaptureConnection {
    private const val PREFS = "capture_connection"
    private const val LAST_AUTO = "last_auto_attempt_at"
    private const val CONNECT_WAIT_MS = 4_000L

    private val running = AtomicBoolean(false)
    @Volatile private var autoAttemptedThisProcess = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun component(context: Context) = ComponentName(context, BankNotificationListener::class.java)

    /** Real signal: our exact component is listed in the system's enabled listeners. */
    fun hasAccess(context: Context): Boolean = NotificationAccess.isGranted(
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"),
        context.packageName, BankNotificationListener::class.java.name,
    )

    /** Run on every app start: the component must never stay disabled after an interrupted toggle. */
    fun ensureComponentEnabled(context: Context) {
        try {
            val pm = context.packageManager
            val cn = component(context)
            when (pm.getComponentEnabledSetting(cn)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ->
                    pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        } catch (_: Exception) {
            // Best effort; a failure here must not stop the app.
        }
    }

    fun componentState(context: Context): Int = context.packageManager.getComponentEnabledSetting(component(context))

    /**
     * Toggles the listener component disabled -> enabled so the system re-evaluates and binds it, then
     * asks for a rebind. The component is touched only when notification access is granted.
     */
    suspend fun reconnect(context: Context, waitMs: Long = CONNECT_WAIT_MS): ReconnectResult {
        val app = context.applicationContext
        if (!hasAccess(app)) return ReconnectResult.NoAccess.also { CaptureHealth.reconnectFinished(it) }
        if (!running.compareAndSet(false, true)) return ReconnectResult.AlreadyRunning
        CaptureHealth.reconnectStarted()
        var result: ReconnectResult = ReconnectResult.NotConnected
        try {
            val pm = app.packageManager
            val cn = component(app)
            val failures = withContext(NonCancellable + Dispatchers.IO) {
                ComponentRecycle.run(
                    disable = { pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) },
                    enable = { pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP) },
                    rebind = { NotificationListenerService.requestRebind(cn) },
                )
            }
            val connected = withTimeoutOrNull(waitMs) { CaptureHealth.state.first { it.connected == true } } != null
            result = when {
                connected -> ReconnectResult.Connected
                failures.isNotEmpty() -> ReconnectResult.Failed(failures.first())
                else -> ReconnectResult.NotConnected
            }
            return result
        } finally {
            CaptureHealth.reconnectFinished(result)
            running.set(false)
        }
    }

    /** Fire-and-forget from the UI; survives the composable leaving the screen. */
    fun launchReconnect(context: Context) {
        val app = context.applicationContext
        scope.launch { reconnect(app) }
    }

    /**
     * Called while the app is in the foreground. Waits for the listener to connect on its own; if it
     * does not, runs one reconnect per process and at most once per cooldown. No loops, no polling.
     */
    suspend fun autoReconnectIfNeeded(context: Context, captureEnabled: Boolean) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun allowed(): Boolean {
            val state = CaptureHealth.state.value
            return ReconnectPolicy.shouldAutoReconnect(
                access = hasAccess(app), captureEnabled = captureEnabled, connected = state.connected,
                reconnecting = state.reconnecting, attemptedThisProcess = autoAttemptedThisProcess,
                lastAutoAttemptAt = prefs.getLong(LAST_AUTO, 0L), now = System.currentTimeMillis(),
            )
        }
        if (!allowed()) return
        delay(ReconnectPolicy.SETTLE_MS)
        if (!allowed()) return
        autoAttemptedThisProcess = true
        prefs.edit().putLong(LAST_AUTO, System.currentTimeMillis()).apply()
        reconnect(app)
    }

    /** Opens a system screen with safe fallbacks; returns false when nothing could be opened. */
    fun open(context: Context, target: SettingsTarget): Boolean {
        val candidates = buildList {
            when (target) {
                SettingsTarget.NOTIFICATION_ACCESS -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString()))
                    }
                    add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                SettingsTarget.BATTERY -> add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                SettingsTarget.APP_DETAILS -> {}
                // Xiaomi security center screens; names vary by MIUI/HyperOS version, so app details is the fallback.
                SettingsTarget.AUTOSTART -> {
                    add(Intent().setComponent(ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                    add(Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter")
                        .putExtra("extra_pkgname", context.packageName))
                }
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
