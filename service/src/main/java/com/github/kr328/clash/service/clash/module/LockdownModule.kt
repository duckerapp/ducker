package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import com.github.kr328.clash.common.constants.Intents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

/**
 * Поводы проверить, не включили ли «Блокировать соединения без VPN» при поднятом туннеле.
 *
 * Маршруты туннеля от этого режима зависят (F-110), а система при его включении туннель
 * не пересоздаёт: человек сначала включает «Постоянную VPN» — туннель перезапускается, —
 * и только потом блокировку, уже без перезапуска. Сообщений об этом система не шлёт,
 * поэтому сверяемся, когда человек мог что-то поменять: при открытии приложения и при
 * включении экрана. Таймера нет — он будил бы телефон зря. Сама сверка — в службе:
 * только она знает, с каким режимом собран туннель.
 */
class LockdownModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val screen = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val app = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_CHECK_LOCKDOWN)
        }

        while (true) {
            select {
                screen.onReceive { }
                app.onReceive { }
            }

            enqueueEvent(Unit)
        }
    }
}
