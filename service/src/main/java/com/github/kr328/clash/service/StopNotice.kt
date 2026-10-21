package com.github.kr328.clash.service

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure

/**
 * VPN остановился не по команде (D-227). Причина ложится на диск — главная покажет её карточкой,
 * когда бы приложение ни открыли, — и в шторку, если приложения нет на экране: включали плиткой,
 * виджетом, командой, или систему попросили держать VPN постоянным.
 */
object StopNotice {
    private const val CHANNEL = "vpn_failure_channel"

    fun show(context: Context, reason: String) {
        val failure = Failure.of(reason)

        // «Подписка не выбрана» главная говорит сама (D-213) — карточка повторяла бы её.
        if (failure.kind != Failure.Kind.NoProfile) ServiceStore(context).stopReason = reason

        if (appOnScreen(context)) return

        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.vpn_failure_channel))
                .build()
        )

        val text = failure.text(context)

        val open = PendingIntent.getActivity(
            context, R.id.nf_vpn_failure,
            Intent().setComponent(Components.MAIN_ACTIVITY),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        val crashed = failure.kind == Failure.Kind.Crashed

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(context.getColorCompat(R.color.color_clash))
            .setContentTitle(context.getString(failure.stopTitle))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                // Started from the app's own process: after a repeated crash Android restarts nothing
                // in the tunnel's process until the person starts it, and this tap is that start (F-327).
                if (crashed) addAction(0, context.getString(R.string.vpn_crashed_connect), connect(context))
            }
            .build()

        // Без разрешения на уведомления (Android 13+) система молча не покажет — не наша беда.
        runCatching { manager.notify(R.id.nf_vpn_failure, notification) }
    }

    private fun connect(context: Context): PendingIntent = PendingIntent.getActivity(
        context, R.id.nf_vpn_failure,
        Intent(Intents.ACTION_START_CLASH)
            .setComponent(Components.INTERNAL_CONTROL_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
    )

    /** Причина больше не правда: туннель поднимают заново или карточку закрыли. */
    fun clear(context: Context) {
        ServiceStore(context).stopReason = ""

        hide(context)
    }

    /** Главная на экране и говорит то же самое карточкой. */
    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(R.id.nf_vpn_failure)
    }

    /**
     * Окно приложения сейчас перед человеком. Список процессов система отдаёт только свой, а
     * важность «на переднем плане» получает процесс с открытым окном: плитка и виджет её не дают.
     */
    private fun appOnScreen(context: Context): Boolean =
        context.getSystemService<ActivityManager>()?.runningAppProcesses.orEmpty()
            .any { it.processName == context.packageName && it.importance == IMPORTANCE_FOREGROUND }
}
