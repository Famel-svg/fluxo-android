package br.com.fluxo.app

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.com.fluxo.core.IgnoreReason
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
        data class Posted(val notification: StatusBarNotification, val replay: Boolean = false) : Event
        data class Removed(val packageName: String, val identity: String) : Event
    }
    // Callback order matters: a removal must finish before an Android key is reused.
    private val events = Channel<Event>(256)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (event in events) {
                val repository = (application as FluxoApplication).repository
                try {
                    when (event) {
                        is Event.Posted -> {
                            val pkg = event.notification.packageName
                            val candidate = extractNotification(event.notification)
                            if (candidate == null) {
                                if (!event.replay) CaptureHealth.ignored(pkg, extractionIgnoreReason(event.notification))
                                continue
                            }
                            val outcome = repository.ingestClassified(candidate.sourceKey, candidate.packageName,
                                candidate.timestamp, candidate.text, candidate.fallbackIdentity, event.notification.postTime)
                            // Replayed active notifications were not counted as observed; keep counters consistent.
                            if (!event.replay) when (outcome) {
                                is IngestOutcome.Stored -> CaptureHealth.stored(pkg)
                                is IngestOutcome.Ignored -> CaptureHealth.ignored(pkg, outcome.reason)
                                is IngestOutcome.NotAllowed -> {}
                            }
                            // Ongoing notifications (players, downloads) refresh constantly and are never bank events.
                            if (repository.triageEnabled.value &&
                                event.notification.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) {
                                // Triage is a provisional aid: its failure must not report a lost capture.
                                try {
                                    repository.recordTriage(candidate.sourceKey, pkg, appLabel(event.notification),
                                        candidate.title, candidate.text, candidate.timestamp,
                                        outcome is IngestOutcome.Stored, candidate.fallbackIdentity)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                }
                            }
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

    /** Visible app name for triage; null when Android hides the package from Fluxo. */
    private fun appLabel(sbn: StatusBarNotification): String? = try {
        // Package visibility hides most apps from getApplicationInfo; the posting app's info rides in the extras.
        @Suppress("DEPRECATION")
        val info = sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
            ?: packageManager.getApplicationInfo(sbn.packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CaptureHealth.connected(true)
        // Notifications posted while the listener was unbound never reach onNotificationPosted.
        // Replaying the ones still visible is safe: ingestion is idempotent per source key.
        val repository = (application as FluxoApplication).repository
        val active = try { activeNotifications.orEmpty() } catch (_: RuntimeException) { return }
        active.filter { repository.allowsNotification(it.packageName) }.forEach { sbn ->
            if (!events.trySend(Event.Posted(sbn, replay = true)).isSuccess) repository.reportCaptureError()
        }
    }

    override fun onListenerDisconnected() {
        CaptureHealth.connected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val repository = (application as FluxoApplication).repository
        if (!repository.allowsNotification(sbn.packageName)) { CaptureHealth.blocked(); return }
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

/** Transient input only: raw text is never logged, and persisted only by the opt-in triage mode. */
internal class NotificationCandidate(
    val sourceKey: String,
    val packageName: String,
    val timestamp: Long,
    val text: String,
    val fallbackIdentity: String? = null,
    val title: String = "",
)

/** Category for a notification that [extractNotification] rejects. Never includes content. */
internal fun extractionIgnoreReason(sbn: StatusBarNotification): IgnoreReason =
    if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) IgnoreReason.GROUP_SUMMARY
    else IgnoreReason.EMPTY_TEXT

internal fun extractNotification(sbn: StatusBarNotification): NotificationCandidate? {
    if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    val extras = sbn.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
    val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeUnless { it.isBlank() }
        ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
    // Inbox style keeps its lines apart from EXTRA_TEXT; the summary is an extra line some apps use for amounts.
    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString().trim() }
    val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty().trim()
    // Some banks repeat the title as body; avoid introducing duplicate monetary values.
    val text = (listOf(title, body) + lines + summary).filter { it.isNotBlank() }.distinct().joinToString("\n")
    if (text.isBlank()) return null
    // Original timestamps identify normal updates. When absent, a persisted lifecycle alias
    // resolves changing postTime. A missed removal/reused key remains ambiguous and needs review.
    val timestamp = sbn.notification.`when`.takeIf { it > 0L } ?: sbn.postTime
    val identity = "${sbn.packageName}|${sbn.key}|$timestamp"
    val sourceKey = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return NotificationCandidate(sourceKey, sbn.packageName, timestamp, text,
        notificationIdentity(sbn).takeIf { sbn.notification.`when` <= 0L }, title)
}

/** Hash only platform identity; never persist or log raw notification content. */
internal fun notificationIdentity(sbn: StatusBarNotification): String =
    MessageDigest.getInstance("SHA-256").digest("${sbn.packageName}|${sbn.key}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
