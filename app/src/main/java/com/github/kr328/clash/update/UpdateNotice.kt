package com.github.kr328.clash.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.HomeActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags

/**
 * Уведомления об обновлении приложения: «вышла версия» — один раз на версию, «готова к установке» —
 * когда файл скачан заранее, и «обновлён» — после установки, которая закрыла приложение у человека
 * на глазах. Место в шторке одно: следующее сменяет прежнее.
 */
object UpdateNotice {
    private const val CHANNEL = "app_update_channel"
    private const val TAG = "app_update"
    private const val ID = 1

    fun available(context: Context, version: String) = show(
        context,
        title = context.getString(R.string.app_update_notice_title, version),
        text = context.getString(R.string.app_update_notice_text),
        action = Updater.ACTION_SHOW,
    )

    fun ready(context: Context, version: String) = show(
        context,
        title = context.getString(R.string.app_update_ready_title, version),
        text = context.getString(R.string.app_update_ready_text),
        // The sheet with “what's new”; the file is there, so its button opens Android's dialog at once.
        action = Updater.ACTION_SHOW,
    )

    fun updated(context: Context, version: String) = show(
        context,
        title = context.getString(R.string.app_update_done_title, version),
        text = context.getString(R.string.app_update_done_text),
        action = Intent.ACTION_MAIN,
    )

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(TAG, ID)

    // The notify below is in runCatching, and without the permission the system drops it: lint sees no check.
    @SuppressLint("MissingPermission")
    private fun show(context: Context, title: String, text: String, action: String) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.app_update_notice_channel))
                .build()
        )

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, HomeActivity::class.java).setAction(action),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.github.kr328.clash.service.R.drawable.ic_logo_service)
            .setColor(context.getColorCompat(com.github.kr328.clash.service.R.color.color_clash))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        // Без разрешения на уведомления (Android 13+) система молча не покажет: о версии всё
        // равно скажет карточка главной.
        runCatching { manager.notify(TAG, ID, notification) }
    }
}
