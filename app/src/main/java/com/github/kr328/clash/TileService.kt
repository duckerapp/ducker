package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.registerReceiverCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.service.R

class TileService : TileService() {
    private var currentProfile = ""
    private var clashRunning = false

    override fun onClick() {
        val tile = qsTile ?: return

        when (tile.state) {
            Tile.STATE_INACTIVE -> {
                // Перекрашиваем сразу, не дожидаясь службы. Замерено: туннель
                // поднимается за 0,7 с, а сообщение о запуске доходит к 1,4 с — всё
                // это время плитка выглядела ненажатой, и человек жал ещё раз.
                // Если запуск не удастся, сообщение от службы вернёт её обратно.
                showPending(true, com.github.kr328.clash.R.string.tile_connecting)

                // Ненулевой ответ — это запрос разрешения на VPN. Показать его из плитки
                // нельзя, поэтому открываем приложение: иначе нажатие просто ничего
                // не делает, и кажется, что плитка сломана.
                if (startClashService() != null) openApp()
            }
            Tile.STATE_ACTIVE -> {
                showPending(false, com.github.kr328.clash.R.string.tile_disconnecting)

                stopClashService()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        registerReceiverCompat(
            receiver,
            IntentFilter().apply {
                addAction(Intents.ACTION_CLASH_STARTED)
                addAction(Intents.ACTION_CLASH_STOPPED)
                addAction(Intents.ACTION_PROFILE_LOADED)
                addAction(Intents.ACTION_SERVICE_RECREATED)
            },
            Permissions.RECEIVE_SELF_BROADCASTS,
            null
        )

        // Имени нет, пока профиль грузится, а туннель уже поднят (D-226).
        val (running, name) = StatusClient(this).status()

        clashRunning = running
        currentProfile = name ?: ""

        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()

        // Если регистрация не прошла, снятие бросит исключение и уронит службу плитки.
        runCatching { unregisterReceiver(receiver) }
    }

    private fun openApp() {
        val intent = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pending = PendingIntent.getActivity(
            this, 0, intent, pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            // Линтер не видит проверку версии выше и считает вызов ошибкой для Android 14+.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    /**
     * Мгновенный отклик на нажатие: плитка принимает вид «уже сделано» и подписывается
     * «подключение…». Настоящее состояние придёт сообщением от службы и либо подтвердит
     * это, либо вернёт обратно.
     */
    private fun showPending(active: Boolean, subtitle: Int) {
        val tile = qsTile ?: return

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getText(subtitle)

        tile.updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return

        tile.state = if (clashRunning)
            Tile.STATE_ACTIVE
        else
            Tile.STATE_INACTIVE

        tile.label = if (currentProfile.isEmpty())
            getText(R.string.launch_name)
        else
            currentProfile

        // Подпись «подключение…» снимаем: пришло настоящее состояние.
        tile.subtitle = null

        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_service)

        tile.updateTile()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intents.ACTION_CLASH_STARTED -> {
                    clashRunning = true

                    currentProfile = ""
                }
                Intents.ACTION_CLASH_STOPPED, Intents.ACTION_SERVICE_RECREATED -> {
                    clashRunning = false

                    currentProfile = ""
                }
                Intents.ACTION_PROFILE_LOADED -> {
                    currentProfile = StatusClient(this@TileService).currentProfile() ?: ""
                }
            }

            updateTile()
        }
    }
}