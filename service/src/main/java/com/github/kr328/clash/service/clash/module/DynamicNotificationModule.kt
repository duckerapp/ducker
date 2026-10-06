package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.formatSpeed
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.downloadBytes
import com.github.kr328.clash.core.util.uploadBytes
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

/**
 * То же уведомление, что и обычное, плюс текущая скорость в подзаголовке. Включается
 * тумблером в настройках: за скорость платят секундным опросом ядра и перерисовкой
 * шторки, и это должен быть выбор человека (D-69).
 *
 * Итогов трафика здесь нет: сколько всего скачано за сеанс — цифра, на которую нельзя
 * ничего решить, а место в шторке она занимала.
 */
class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = StaticNotificationModule.notificationBuilder(service)

    private val notificationManager = NotificationManagerCompat.from(service)

    private fun update() {
        val now = Clash.queryTrafficNow()

        val notification = builder
            .setSubText(
                service.getString(
                    R.string.clash_notification_content,
                    service.formatSpeed(now.uploadBytes()),
                    service.formatSpeed(now.downloadBytes()),
                )
            )
            .build()

        notificationManager.notify(R.id.nf_clash_status, notification)
    }

    override suspend fun run() = coroutineScope {
        var shouldUpdate = service.getSystemService<PowerManager>()?.isInteractive ?: true

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        val profileLoaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val selector = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_SELECTOR_CHANGED)
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (true) {
            select<Unit> {
                screenToggle.onReceive {
                    when (it.action) {
                        Intent.ACTION_SCREEN_ON ->
                            shouldUpdate = true
                        Intent.ACTION_SCREEN_OFF ->
                            shouldUpdate = false
                    }
                }
                profileLoaded.onReceive {
                    builder.setContentTitle(
                        StatusProvider.currentProfile ?: service.getText(R.string.running)
                    )
                    builder.setContentText(node())
                }
                selector.onReceive {
                    builder.setContentText(node())
                }
                if (shouldUpdate) {
                    ticker.onReceive {
                        update()
                    }
                }
            }
        }
    }

    private fun node(): String = StaticNotificationModule.currentNode()
        .ifEmpty { service.getText(R.string.running).toString() }
}
