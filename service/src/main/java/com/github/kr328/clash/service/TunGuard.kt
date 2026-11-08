package com.github.kr328.clash.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.sendClashStopped
import java.io.File

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

        /** Long enough for a guard that Android still holds back to run first: it starts with the process (F-397). */
        private const val SETTLE = 5_000L

        /**
         * Поднять туннель, если он умер на ходу: служба успевает обнулить отметку
         * подъёма только при штатной остановке (D-90).
         *
         * Пауза между подъёмами — только после падений: обновление в цикл не уйдёт,
         * а упавший сразу после подъёма туннель — уйдёт.
         */
        fun Context.restoreTunnel(afterCrash: Boolean): Boolean {
            val store = ServiceStore(this)
            val startedAt = store.tunnelStartedAt

            if (startedAt == 0L || StatusProvider.serviceRunning) return false

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

                if (afterCrash) leftDown(startedAt)

                return false
            }

            Log.i("Tunnel died unexpectedly, restoring")

            if (!startForegroundServiceCompat(TunService::class.intent)) {
                if (afterCrash) leftDown(startedAt)

                return false
            }

            if (afterCrash) store.tunnelRestoredAt = now

            return true
        }

        /**
         * After a second crash in a row Android holds this guard back for half an hour, and with ducker in the
         * background drops it a minute later (F-397): nothing of ours runs, the VPN is off with no word, and its
         * marks stay. Whatever starts this process next — the app, a widget, the tile, an update alarm — tells
         * the person, without reconnecting: that start may come hours later (Q-105).
         *
         * A tunnel cut by the app's update is brought back by the same start (D-433): `ProfileReceiver` does it on
         * Android's word of the update, and a slow phone delivers that word half an hour late (F-420).
         */
        fun Context.noticeTunnelLeftDown() {
            Handler(Looper.getMainLooper()).postDelayed({
                val now = System.currentTimeMillis()
                val updatedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                val startedAt = ServiceStore(this).tunnelStartedAt
                val bootedAt = now - SystemClock.elapsedRealtime()
                val running = StatusProvider.serviceRunning

                if (leftDownUnnoticed(startedAt, bootedAt, updatedAt, running)) {
                    Log.w("Tunnel found down with no word after its process died")

                    leftDown(startedAt)
                } else if (cutByUpdate(startedAt, bootedAt, updatedAt, running)) {
                    Log.i("Tunnel cut by the app's update, restoring before Android's word of it")

                    restoreTunnel(afterCrash = false)
                }
            }, SETTLE)
        }

        /**
         * The tunnel went down unnoticed: it is not running, though it was up after the last boot and the last
         * update. A tunnel killed by either is the business of `ProfileReceiver` and `RestartReceiver`.
         */
        internal fun leftDownUnnoticed(startedAt: Long, bootedAt: Long, updatedAt: Long, running: Boolean): Boolean =
            !running && startedAt > maxOf(bootedAt, updatedAt)

        /** The tunnel was up after the last boot and the app's update cut it: what `ProfileReceiver` restores. */
        internal fun cutByUpdate(startedAt: Long, bootedAt: Long, updatedAt: Long, running: Boolean): Boolean =
            !running && startedAt > bootedAt && startedAt <= updatedAt

        /**
         * A crash of ours left a record after the tunnel came up at [startedAt]: Go's panic or fatal error in the
         * core's stderr, or the crash handler's file of the tunnel's process (`CrashRecorder`). With none, the
         * system closed the process (F-418): MIUI without its autostart, a cleaned recents list, low memory.
         * A panic the core recovered from also lands in stderr; such a death is put down to us.
         */
        internal fun crashedSince(files: File, startedAt: Long): Boolean {
            val crashes = files.resolve(CrashRecorder.DIR).resolve("background")

            return listOf(
                files.resolve("clash/stderr.log"),
                crashes.resolve(FlightRecorder.CURRENT),
                crashes.resolve(FlightRecorder.PREVIOUS),
            ).any { it.lastModified() > startedAt }
        }

        /**
         * The tunnel stays down after its process died. Android keeps the dead [TunService] started and in
         * the foreground with its ongoing notification, and never restarts it (F-113): the shade would say
         * the VPN is up while nothing is protected. Stopping drops both; the notice says why it is off.
         * The dead service could not remove its marks either: `RestartReceiver` would bring the tunnel up at
         * the next boot, and `ProfileReceiver` at the next update.
         */
        private fun Context.leftDown(startedAt: Long) {
            stopService(TunService::class.intent)

            ServiceStore(this).tunnelStartedAt = 0
            StatusProvider.shouldStartClashOnBoot = false

            val reason = if (crashedSince(filesDir, startedAt)) Failure.CRASHED else Failure.KILLED

            StopNotice.show(this, reason)

            // Home on screen reads the reason at a stop, as do the tile and the widget.
            sendClashStopped(reason)
        }
    }
}
