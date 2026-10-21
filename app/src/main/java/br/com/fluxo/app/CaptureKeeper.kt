package br.com.fluxo.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * Keeps only the capture alive. Android binds the listener by itself; when an aggressive battery manager kills the
 * process, nothing brings it back until the app is opened. These hooks ask Android to bind it again after boot,
 * after an update and every 15 minutes. They never read or store notification content.
 */
object CaptureKeeper {
    private const val JOB_ID = 4_217
    private const val PERIOD_MS = 15 * 60_000L

    /** Asks Android to bind the listener when access is granted, capture is on and this process is not connected. */
    fun rebindIfNeeded(context: Context) {
        val app = context.applicationContext
        if (!CaptureConnection.hasAccess(app)) return
        // Read the switch directly: building the repository here would load all of its state on the main thread.
        if (!app.getSharedPreferences("capture", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        if (CaptureHealth.state.value.connected == true) return
        try {
            NotificationListenerService.requestRebind(ComponentName(app, BankNotificationListener::class.java))
        } catch (_: Exception) {
            // Best effort; the next check or opening the app tries again.
        }
    }

    /** Periodic check; keeping the existing job avoids resetting its timer on every app start. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, CaptureWatchdogJob::class.java))
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build())
    }
}

/** Runs the periodic check; the work is a single binder call, so it finishes at once. */
class CaptureWatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        CaptureKeeper.rebindIfNeeded(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

/** Boot and app update end the listener's binding on some devices; ask for it again and restore the schedule. */
class CaptureRebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        CaptureKeeper.schedule(context)
        CaptureKeeper.rebindIfNeeded(context)
    }
}
