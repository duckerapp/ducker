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
 * The same service runs the automatic update (D-287): one job while the phone lies idle.
 */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Global.launch {
            // Not done — the system runs an idle job again in its next idle window.
            val again = if (params.jobId == INSTALL_ID) {
                !Updater.unattended()
            } else {
                Updater.scheduled()
                false
            }

            jobFinished(params, again)
        }

        return true
    }

    // Прервали — ничего не теряем: следующая проверка придёт по расписанию. The automatic
    // update is stopped when the phone is picked up; it waits for the next idle window.
    override fun onStopJob(params: JobParameters): Boolean = params.jobId == INSTALL_ID

    companion object {
        private const val ID = 7001
        private const val INSTALL_ID = 7002
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
         * Download and install while nobody uses the phone — usually at night. The tunnel takes its
         * meteredness from the network beneath it; [Updater.unattended] also wants Wi-Fi or a cable.
         */
        fun scheduleInstall(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

            if (scheduler.getPendingJob(INSTALL_ID) != null) return

            scheduler.schedule(
                JobInfo.Builder(INSTALL_ID, ComponentName(context, UpdateJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresDeviceIdle(true)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build()
            )
        }

        fun cancelInstall(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(INSTALL_ID)
        }
    }
}
