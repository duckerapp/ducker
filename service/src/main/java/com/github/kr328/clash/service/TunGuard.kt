package com.github.kr328.clash.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure

/**
 * Поднимает туннель обратно, если процесс службы умер, а человек туннель не выключал (F-110).
 *
 * Сама [TunService] после гибели процесса не перезапускается, хоть и «липкая»: вместе
 * с процессом закрывается интерфейс туннеля, система VPN отвязывается от уже мёртвой
 * службы, и диспетчер служб в этой гонке забывает, что её надо поднять (F-113). Эта
 * служба ни к чему не привязана, поэтому её система перезапускает честно, а она уже
 * поднимает туннель. Запускает её [TunService] при подъёме, останавливает при штатной
 * остановке.
 */
class TunGuard : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Без намерения — это перезапуск после гибели процесса, а не старт из TunService.
        if (intent == null && !restoreTunnel(afterCrash = true)) stopSelf()

        return START_STICKY
    }

    companion object {
        /** Упал сразу после подъёма — второй раз не поднимаем, иначе цикл «поднял — упал». */
        private const val RESTORE_COOLDOWN = 2 * 60 * 1000L

        /**
         * Поднять туннель, если он умер на ходу: служба успевает обнулить отметку
         * подъёма только при штатной остановке (D-90).
         *
         * Пауза между подъёмами — только после падений: обновление в цикл не уйдёт,
         * а упавший сразу после подъёма туннель — уйдёт.
         */
        fun Context.restoreTunnel(afterCrash: Boolean): Boolean {
            val store = ServiceStore(this)

            if (store.tunnelStartedAt == 0L || StatusProvider.serviceRunning) return false

            val now = System.currentTimeMillis()

            val allowed = when {
                // Согласия на VPN нет (скорее всего, его забрал другой VPN) — окно
                // согласия умеет показать только интерфейс.
                VpnService.prepare(this) != null -> false
                afterCrash && now - store.tunnelRestoredAt < RESTORE_COOLDOWN -> {
                    Log.w("Tunnel died again right after restore, leaving it down")

                    false
                }
                else -> true
            }

            // Решили не поднимать — отметку стираем, иначе ближайшее обновление
            // поднимет туннель, который все уже считают выключенным.
            if (!allowed) {
                store.tunnelStartedAt = 0

                if (afterCrash) leftDown()

                return false
            }

            Log.i("Tunnel died unexpectedly, restoring")

            if (!startForegroundServiceCompat(TunService::class.intent)) {
                if (afterCrash) leftDown()

                return false
            }

            if (afterCrash) store.tunnelRestoredAt = now

            return true
        }

        /**
         * The tunnel stays down after its process died. Android keeps the dead [TunService] started and in
         * the foreground with its ongoing notification, and never restarts it (F-113): the shade would say
         * the VPN is up while nothing is protected. Stopping drops both; the notice says why it is off.
         * The dead service could not remove its marker either, and `RestartReceiver` would bring the tunnel
         * up at the next update or boot.
         */
        private fun Context.leftDown() {
            stopService(TunService::class.intent)

            StatusProvider.shouldStartClashOnBoot = false

            StopNotice.show(this, Failure.CRASHED)
        }
    }
}
