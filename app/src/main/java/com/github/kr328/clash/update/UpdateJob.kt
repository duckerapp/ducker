package com.github.kr328.clash.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.github.kr328.clash.common.Global
import kotlinx.coroutines.launch

/**
 * Фоновая проверка новой версии: раз в полдня, когда есть сеть. Расписание держит система —
 * без служб Google и без своих будильников.
 *
 * The same service runs the download in advance (D-287, F-603): one job on Wi-Fi.
 */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Global.launch {
            // Not done — the system runs the job again later.
            val again = if (params.jobId == PREFETCH_ID) {
                !Updater.prefetch()
            } else {
                Updater.scheduled()
                false
            }

            jobFinished(params, again)
        }

        return true
    }

    // Прервали — ничего не теряем: следующая проверка придёт по расписанию. The download in
    // advance stops when Wi-Fi goes; it goes on from where it stopped next time.
    override fun onStopJob(params: JobParameters): Boolean = params.jobId == PREFETCH_ID

    companion object {
        private const val ID = 7001
        private const val PREFETCH_ID = 7002
        private const val PERIOD = 12 * 60 * 60 * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

            if (scheduler.getPendingJob(ID) != null) return

            scheduler.schedule(
                JobInfo.Builder(ID, ComponentName(context, UpdateJob::class.java))
                    .setPeriodic(PERIOD)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            )
        }

        /**
         * Download the new version ahead of the person's tap. The tunnel takes its meteredness from
         * the network beneath it; [Updater.prefetch] also wants Wi-Fi or a cable. No idle wait: the
         * file only downloads, so the “ready” notification comes the same day, not at night.
         */
        fun schedulePrefetch(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

            if (scheduler.getPendingJob(PREFETCH_ID) != null) return

            scheduler.schedule(
                JobInfo.Builder(PREFETCH_ID, ComponentName(context, UpdateJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build()
            )
        }

        fun cancelPrefetch(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(PREFETCH_ID)
        }
    }
}
