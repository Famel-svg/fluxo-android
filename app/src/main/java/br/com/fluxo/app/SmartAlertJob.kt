package br.com.fluxo.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Hourly, offline evaluation of the smart alerts. A persisted job survives reboot; the app start re-schedules it. */
object SmartAlertScheduler {
    private const val JOB_ID = 4_218
    private const val PERIOD_MS = 60 * 60_000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, SmartAlertJob::class.java))
            .setPeriodic(PERIOD_MS).setPersisted(true).build())
    }
}

class SmartAlertJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        scope.launch {
            try {
                SmartNotifier.run(this@SmartAlertJob, (application as FluxoApplication).repository)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The next period tries again.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        scope.cancel()
        return true
    }
}
