package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.service.TunGuard.Companion.restoreTunnel
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                when (intent.action) {
                    // Обновление убило процесс вместе с туннелем, а человек его не
                    // выключал — поднимаем. Система при «Постоянной VPN» пытается сама,
                    // но промахивается: старый туннель у неё ещё числится живым (F-110).
                    Intent.ACTION_MY_PACKAGE_REPLACED -> context.restoreTunnel(afterCrash = false)
                    // Выключение телефона службу штатно не останавливает, и отметка
                    // подъёма переживает перезагрузку. Не сотрём — ближайшее обновление
                    // поднимет туннель, который человек после перезагрузки не включал.
                    Intent.ACTION_BOOT_COMPLETED ->
                        if (!StatusProvider.serviceRunning) ServiceStore(context).tunnelStartedAt = 0
                }

                Global.launch {
                    reset()

                    val service = Intent(Intents.ACTION_PROFILE_SCHEDULE_UPDATES)
                        .setComponent(ProfileWorker::class.componentName)

                    if (!context.startForegroundServiceCompat(service)) {
                        // Планирование будильников не требует службы переднего плана.
                        rescheduleAll(context)
                    }
                }

                // Перезагрузка и обновление стирают будильники, смена времени сдвигает пороги.
                Global.launch { ExpiryNotice.check(context) }
                Global.launch { TrafficNotice.check(context) }
                Global.launch { VerboseLog.checkIfOn(context) }
            }
            VerboseLog.ACTION -> {
                Global.launch { VerboseLog.checkIfOn(context) }
            }
            // Срок мог прийти новый с обновлением подписки, активной могла стать другая (D-149).
            ExpiryNotice.ACTION, Intents.ACTION_PROFILE_CHANGED -> {
                Global.launch { ExpiryNotice.check(context) }
                // Traffic comes only with an update, and an update ends in a change (stage 15).
                if (intent.action == Intents.ACTION_PROFILE_CHANGED) Global.launch { TrafficNotice.check(context) }
            }
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                val redirect = intent.setComponent(ProfileWorker::class.componentName)

                if (!context.startForegroundServiceCompat(redirect)) {
                    // Будильник уже доставлен: не потерять автообновление при отказе Android.
                    val uuid = runCatching { intent.uuid }.getOrNull() ?: return
                    Global.launch {
                        ImportedDao().queryByUUID(uuid)?.let { scheduleRetry(context, it) }
                    }
                }
            }
        }
    }

    companion object {
        private val lock = Mutex()
        private var initialized: Boolean = false
        private val updateFailures = ConcurrentHashMap<UUID, Int>()

        suspend fun rescheduleAll(context: Context): Unit = lock.withLock {
            if (initialized)
                return

            initialized = true

            Log.i("Reschedule all profiles update")

            ImportedDao().queryAllUUIDs()
                .mapNotNull { ImportedDao().queryByUUID(it) }
                .filter { it.type != Profile.Type.File }
                .forEach { scheduleNext(context, it) }
        }

        fun cancelNext(context: Context, imported: Imported) {
            updateFailures.remove(imported.uuid)
            val intent = pendingIntentOf(context, imported)

            context.getSystemService<AlarmManager>()?.cancel(intent)
        }

        fun schedule(context: Context, imported: Imported) {
            val intent = pendingIntentOf(context, imported)

            context.getSystemService<AlarmManager>()?.cancel(intent)

            intent.send(context, 0, null)
        }

        fun scheduleNext(context: Context, imported: Imported) {
            updateFailures.remove(imported.uuid)
            scheduleAlarm(context, imported, failures = 0)
        }

        fun scheduleRetry(context: Context, imported: Imported) {
            if (updateIntervalOf(context, imported) < TimeUnit.MINUTES.toMillis(15)) {
                cancelNext(context, imported)
                return
            }

            val failures = updateFailures.compute(imported.uuid) { _, previous ->
                ((previous ?: 0) + 1).coerceAtMost(3)
            } ?: 1
            scheduleAlarm(context, imported, failures)
        }

        private fun scheduleAlarm(context: Context, imported: Imported, failures: Int) {
            val intent = pendingIntentOf(context, imported)

            context.getSystemService<AlarmManager>()?.cancel(intent)

            val current = System.currentTimeMillis()
            val last = context.importedDir
                .resolve(imported.uuid.toString())
                .resolve("config.yaml")
                .lastModified()

            // После ошибки — отдельная пауза от текущего времени: старый файл не должен дать цикл
            // без задержки.
            val next = nextProfileUpdateAt(updateIntervalOf(context, imported), last, current, failures) ?: return

            // Окно в час, как у уведомлений о сроке: неточный set уплывает на ¾ срока (F-180).
            context.getSystemService<AlarmManager>()
                ?.setWindow(AlarmManager.RTC, next, TimeUnit.HOURS.toMillis(1), intent)
        }

        /** The provider's hours are read at every scheduling, so a changed header is picked up (D-311). */
        private fun updateIntervalOf(context: Context, imported: Imported): Long =
            imported.updateInterval(ProviderInfo.read(context.importedDir.resolve(imported.uuid.toString())).updateIntervalHours)

        private suspend fun reset() = lock.withLock {
            initialized = false
        }

        private fun pendingIntentOf(context: Context, imported: Imported): PendingIntent {
            val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
                .setComponent(ProfileReceiver::class.componentName)
                .setUUID(imported.uuid)

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
    }
}
