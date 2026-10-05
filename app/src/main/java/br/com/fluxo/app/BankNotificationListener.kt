package br.com.fluxo.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.com.fluxo.core.NotificationCapturePolicy
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface Event {
        data class Posted(val notification: StatusBarNotification) : Event
        data class Removed(val packageName: String, val identity: String) : Event
    }
    // Callback order matters: a removal must finish before an Android key is reused.
    private val events = Channel<Event>(64)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (event in events) {
                val repository = (application as FluxoApplication).repository
                try {
                    when (event) {
                        is Event.Posted -> {
                            val candidate = extractNotification(event.notification) ?: continue
                            repository.ingest(candidate.sourceKey, candidate.packageName, candidate.timestamp,
                                candidate.text, candidate.fallbackIdentity, event.notification.postTime)
                        }
                        is Event.Removed -> {
                            if (repository.allowsNotification(event.packageName)) {
                                repository.forgetNotification(event.identity)
                            }
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Never log bank notification content or amounts.
                    repository.reportCaptureError()
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CaptureHealth.connected(true)
    }

    override fun onListenerDisconnected() {
        CaptureHealth.connected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val repository = (application as FluxoApplication).repository
        if (!repository.allowsNotification(sbn.packageName)) return
        CaptureHealth.observed(sbn.packageName, System.currentTimeMillis())
        if (!events.trySend(Event.Posted(sbn)).isSuccess) repository.reportCaptureError()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val repository = (application as FluxoApplication).repository
        if (!repository.allowsNotification(sbn.packageName)) return
        if (!events.trySend(Event.Removed(sbn.packageName, notificationIdentity(sbn))).isSuccess) {
            repository.reportCaptureError()
        }
    }

    override fun onDestroy() {
        CaptureHealth.connected(false)
        events.close()
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
    val fallbackIdentity: String? = null,
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
    // Original timestamps identify normal updates. When absent, a persisted lifecycle alias
    // resolves changing postTime. A missed removal/reused key remains ambiguous and needs review.
    val timestamp = sbn.notification.`when`.takeIf { it > 0L } ?: sbn.postTime
    val identity = "${sbn.packageName}|${sbn.key}|$timestamp"
    val sourceKey = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return NotificationCandidate(sourceKey, sbn.packageName, timestamp, text,
        notificationIdentity(sbn).takeIf { sbn.notification.`when` <= 0L })
}

/** Hash only platform identity; never persist or log raw notification content. */
internal fun notificationIdentity(sbn: StatusBarNotification): String =
    MessageDigest.getInstance("SHA-256").digest("${sbn.packageName}|${sbn.key}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
