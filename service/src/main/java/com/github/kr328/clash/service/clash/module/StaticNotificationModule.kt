package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

class StaticNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = notificationBuilder(service)

    override suspend fun run() = coroutineScope {
        val loaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        // Узел меняют на вкладке «Прокси», и в шторке должен оказаться новый: там
        // написано, чем именно сейчас идёт трафик.
        val selector = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_SELECTOR_CHANGED)
        }

        while (true) {
            select<Unit> {
                loaded.onReceive { }
                selector.onReceive { }
            }

            val notification = builder
                .setContentTitle(StatusProvider.currentProfile ?: service.getText(R.string.running))
                .setContentText(currentNode().ifEmpty { service.getText(R.string.running).toString() })
                .build()

            service.startForegroundCompat(R.id.nf_clash_status, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "clash_status_channel"

        /**
         * Узел, которым идёт трафик, — выбор первой видимой группы. Скрытые группы ядро
         * отсеивает само (F-29), поэтому первая видимая и есть основная.
         *
         * Спрашиваем только по событию (профиль загружен, узел переключили), а не по
         * таймеру: ответ содержит всю группу целиком, и дёргать её каждую секунду ради
         * одной строки незачем.
         */
        fun currentNode(): String = runCatching {
            val group = Clash.queryGroupNames(true).firstOrNull() ?: return ""

            // « · мобильная сеть» while the mobile choice is in effect: a quiet line instead of a notice (D-423).
            Clash.queryGroup(group, ProxySort.Default).now.ifEmpty { return "" } + MobileModule.note
        }.getOrDefault("")

        fun notificationBuilder(service: Service): NotificationCompat.Builder =
            NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_service)
                .setOngoing(true)
                .setColor(service.getColorCompat(R.color.color_clash))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(
                    PendingIntent.getActivity(
                        service,
                        R.id.nf_clash_status,
                        Intent().setComponent(Components.MAIN_ACTIVITY)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                )
                // Выключают чаще всего из шторки, не открывая приложение (D-69).
                .addAction(
                    0,
                    service.getText(R.string.disconnect),
                    PendingIntent.getBroadcast(
                        service,
                        R.id.nf_clash_status,
                        Intent(Intents.ACTION_CLASH_REQUEST_STOP).setPackage(service.packageName),
                        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                )

        fun createNotificationChannel(service: Service) {
            NotificationManagerCompat.from(service).createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(service.getText(R.string.clash_service_status_channel)).build()
            )
        }

        fun notifyLoadingNotification(service: Service) {
            val notification =
                NotificationCompat.Builder(service, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_logo_service)
                    .setOngoing(true)
                    .setColor(service.getColorCompat(R.color.color_clash))
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setContentTitle(service.getText(R.string.loading))
                    .build()

            service.startForegroundCompat(R.id.nf_clash_status, notification)
        }
    }
}
