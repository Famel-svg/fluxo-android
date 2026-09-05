package br.com.fluxo.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val repository = (application as FluxoApplication).repository
        if (!repository.captureEnabled.value || sbn.packageName !in repository.enabledBanks.value) return
        scope.launch {
            try {
                val candidate = extractNotification(sbn) ?: return@launch
                repository.ingest(candidate.sourceKey, candidate.packageName, candidate.timestamp, candidate.text)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Never log bank notification content or amounts.
                repository.reportCaptureError()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Transient input only: raw text is never persisted or logged. */
internal class NotificationCandidate(
    val sourceKey: String,
    val packageName: String,
    val timestamp: Long,
    val text: String,
)

internal fun extractNotification(sbn: StatusBarNotification): NotificationCandidate? {
    if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    val extras = sbn.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
    val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeUnless { it.isBlank() }
        ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
    // Some banks repeat the title as body; avoid introducing duplicate monetary values.
    val text = listOf(title, body).filter { it.isNotBlank() }.distinct().joinToString("\n")
    if (text.isBlank()) return null
    // Preserved original timestamps make redelivery and notification updates idempotent.
    val timestamp = sbn.notification.`when`.takeIf { it > 0L } ?: sbn.postTime
    val identity = "${sbn.packageName}|${sbn.key}|$timestamp"
    val sourceKey = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return NotificationCandidate(sourceKey, sbn.packageName, timestamp, text)
}
