package br.com.fluxo.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import br.com.fluxo.core.AlertInput
import br.com.fluxo.core.AlertKind
import br.com.fluxo.core.AlertSettings
import br.com.fluxo.core.RecurringDetector
import br.com.fluxo.core.SmartAlert
import br.com.fluxo.core.SmartAlerts
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Builds the alert input from the ledger, asks the pure engine what to say and posts it. Texts come from the engine
 * (amounts of the owner's own ledger only); nothing from other apps' notifications is copied.
 */
object SmartNotifier {
    const val EXTRA_TAB = "fluxo.tab"
    private const val CHANNEL = "alertas_fluxo"
    private const val GROUP = "fluxo.alertas"
    private const val SUMMARY_ID = 7_900

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    /**
     * Evaluates and posts what is due. Alerts are marked as sent only when they were really posted, so a missing
     * permission does not swallow them. Returns the alerts posted. [now] and [canPost] are parameters for tests.
     */
    @Synchronized
    fun run(
        context: Context, repository: LedgerRepository, now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(), canPost: Boolean = canPost(context),
    ): List<SmartAlert> {
        if (!canPost) return emptyList()
        val sent = repository.sentAlertKeys.value
        val today = now.toLocalDate()
        val (quietStart, quietEnd) = repository.alertQuietHours.value
        val rows = repository.transactions.value
        val kinds = repository.alertsEnabled.value.mapNotNull { n -> AlertKind.entries.firstOrNull { it.name == n } }.toSet()
        val alerts = SmartAlerts.evaluate(AlertInput(
            now = now, zone = zone, rows = rows, budgets = repository.budgets.value,
            recurring = RecurringDetector.detect(rows, today, zone),
            captureEnabled = repository.captureEnabled.value, captureHealthy = CaptureHealth.state.value.connected == true,
            lastCaptureAt = repository.lastCaptureAt.value,
            settings = AlertSettings(enabled = kinds, quietStartHour = quietStart, quietEndHour = quietEnd),
            sentKeys = sent.keys,
            sentToday = sent.values.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today },
        ))
        if (alerts.isEmpty()) return alerts
        val at = now.atZone(zone).toInstant().toEpochMilli()
        val posted = alerts.filter { post(context, it) }
        posted.forEach { repository.markAlertSent(it.key, at) }
        if (posted.isNotEmpty()) postSummary(context)
        return posted
    }

    private fun channel(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alertas do Fluxo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Revisão pendente, orçamento, contas e gastos fora do padrão."
            })
        }
        return manager
    }

    private fun tabFor(kind: AlertKind): Int? = when (kind) {
        AlertKind.REVIEW_DIGEST, AlertKind.BILL_DUE, AlertKind.UNUSUAL -> 1
        AlertKind.BUDGET_80, AlertKind.BUDGET_OVER -> 2
        else -> null
    }

    private fun post(context: Context, alert: SmartAlert): Boolean = try {
        val manager = channel(context)!!
        val id = 8_000 + (alert.key.hashCode() and 0xFFFF)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        tabFor(alert.kind)?.let { intent.putExtra(EXTRA_TAB, it) }
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(id, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_fluxo_mark).setContentTitle(alert.title).setContentText(alert.body)
            .setContentIntent(open).setAutoCancel(true).setGroup(GROUP).build())
        true
    } catch (_: Exception) {
        false
    }

    private fun postSummary(context: Context) {
        try {
            val manager = channel(context) ?: return
            val open = PendingIntent.getActivity(context, SUMMARY_ID, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(SUMMARY_ID, Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_fluxo_mark).setContentTitle("Fluxo").setContentText("Avisos do Fluxo")
                .setContentIntent(open).setAutoCancel(true).setGroup(GROUP).setGroupSummary(true).build())
        } catch (_: Exception) {
            // Best effort: the individual alerts are already visible.
        }
    }
}
