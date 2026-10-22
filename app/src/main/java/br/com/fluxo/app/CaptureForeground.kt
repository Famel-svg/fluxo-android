package br.com.fluxo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fixed "capture active" notification. While capture is on, the listener runs as a foreground service: the owner sees
 * that Fluxo is reading notifications, and battery managers (MIUI above all) are far less likely to kill the process.
 * The notification never shows notification content or amounts, only when the last capture happened.
 */
object CaptureForeground {
    private const val CHANNEL = "captura_ativa"
    private const val ID = 7_301
    private val time = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    // The connected listener, so the app can retry the promotion once it is in the foreground.
    @Volatile private var service: Service? = null
    @Volatile private var lastCaptureAt: Long? = null

    private fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Captura ativa", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mostra que o Fluxo está lendo as notificações financeiras."
            setShowBadge(false)
        })
    }

    private fun build(context: Context): Notification {
        channel(context)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val last = lastCaptureAt?.let { "Última captura: " + time.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_fluxo_mark)
            .setContentTitle("Fluxo está lendo suas notificações")
            .setContentText(last ?: "Compras e Pix entram sozinhos. Nada sai do aparelho.")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    /** Puts the listener in the foreground; false when Android refuses it from the background (retried on app open). */
    fun promote(listener: Service): Boolean {
        service = listener
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                listener.startForeground(ID, build(listener), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                listener.startForeground(ID, build(listener))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Capture paused or listener gone: remove the notification and leave the foreground. */
    fun demote(listener: Service) {
        try { listener.stopForeground(Service.STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    fun disconnected(listener: Service) {
        demote(listener)
        if (service === listener) service = null
    }

    /** Called with the app in the foreground, where starting a foreground service is always allowed. */
    fun retry(captureEnabled: Boolean) {
        val listener = service ?: return
        if (captureEnabled) promote(listener) else demote(listener)
    }

    /** Refreshes the "last capture" line of the visible notification. */
    fun captured(context: Context, at: Long) {
        lastCaptureAt = at
        if (service == null) return
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(ID, build(context))
        } catch (_: Exception) {
            // Without notification permission the service still runs; only the text is not shown.
        }
    }
}
