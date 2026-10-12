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
 */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Global.launch {
            Updater.scheduled()

            jobFinished(params, false)
        }

        return true
    }

    // Прервали — ничего не теряем: следующая проверка придёт по расписанию.
    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val ID = 7001
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
    }
}
