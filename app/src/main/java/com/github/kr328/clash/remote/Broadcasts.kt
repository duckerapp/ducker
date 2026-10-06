package com.github.kr328.clash.remote

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.common.log.Log
import java.util.*

class Broadcasts(private val context: Application) {
    interface Observer {
        fun onServiceRecreated()
        fun onStarted()
        fun onStopped(cause: String?)
        fun onProfileChanged()
        fun onProfileUpdateCompleted(uuid: UUID?)
        fun onProfileUpdateFailed(uuid: UUID?, reason: String?)
        fun onProfileLoaded()
    }

    var clashRunning: Boolean = false

    private var registered = false
    private val receivers = mutableListOf<Observer>()
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null || intent.`package` != context.packageName)
                return

            when (intent.action) {
                Intents.ACTION_SERVICE_RECREATED -> {
                    clashRunning = false

                    receivers.forEach {
                        it.onServiceRecreated()
                    }
                }
                Intents.ACTION_CLASH_STARTED -> {
                    clashRunning = true

                    receivers.forEach {
                        it.onStarted()
                    }
                }
                Intents.ACTION_CLASH_STOPPED -> {
                    clashRunning = false

                    receivers.forEach {
                        it.onStopped(intent.getStringExtra(Intents.EXTRA_STOP_REASON))
                    }
                }
                Intents.ACTION_PROFILE_CHANGED ->
                    receivers.forEach {
                        it.onProfileChanged()
                    }
                Intents.ACTION_PROFILE_UPDATE_COMPLETED -> {
                    val uuid = profileUpdateUuid(intent.getStringExtra(Intents.EXTRA_UUID)) ?: return
                    receivers.forEach {
                        it.onProfileUpdateCompleted(uuid)
                    }
                }
                Intents.ACTION_PROFILE_UPDATE_FAILED -> {
                    val uuid = profileUpdateUuid(intent.getStringExtra(Intents.EXTRA_UUID)) ?: return
                    receivers.forEach {
                        it.onProfileUpdateFailed(
                            uuid,
                            intent.getStringExtra(Intents.EXTRA_FAIL_REASON))
                    }
                }
                Intents.ACTION_PROFILE_LOADED -> {
                    receivers.forEach {
                        it.onProfileLoaded()
                    }
                }
            }
        }
    }

    fun addObserver(observer: Observer) {
        receivers.add(observer)
    }

    fun removeObserver(observer: Observer) {
        receivers.remove(observer)
    }

    /**
     * Подписаться на сообщения службы и спросить у неё, поднят ли туннель.
     *
     * Зовётся при каждом возврате в приложение (F-142). Приёмник подписывается один раз на всю
     * жизнь процесса и в фоне не снимается: `clashRunning` читают и корутины, которые доживают
     * до ухода в фон (сохранение правил, перезапуск туннеля после выбора приложений).
     */
    fun register() {
        try {
            if (!registered) {
                context.registerReceiverCompat(broadcastReceiver, IntentFilter().apply {
                    addAction(Intents.ACTION_SERVICE_RECREATED)
                    addAction(Intents.ACTION_CLASH_STARTED)
                    addAction(Intents.ACTION_CLASH_STOPPED)
                    addAction(Intents.ACTION_PROFILE_CHANGED)
                    addAction(Intents.ACTION_PROFILE_UPDATE_COMPLETED)
                    addAction(Intents.ACTION_PROFILE_UPDATE_FAILED)
                    addAction(Intents.ACTION_PROFILE_LOADED)
                }, Permissions.RECEIVE_SELF_BROADCASTS)

                registered = true
            }

            // Не по имени профиля: пока профиль грузится, имени нет, а туннель уже поднят —
            // главная показывала «Не подключено» и после загрузки не исправлялась (D-226).
            clashRunning = StatusClient(context).status().first
        } catch (e: Exception) {
            Log.w("Register global receiver: $e", e)
        }
    }
}

internal fun profileUpdateUuid(value: String?): UUID? =
    value?.let { runCatching { UUID.fromString(it) }.getOrNull() }
