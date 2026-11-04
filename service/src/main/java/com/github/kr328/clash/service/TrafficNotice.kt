package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.util.firstProviderLink
import com.github.kr328.clash.common.util.formatBytes
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId

/**
 * A warning at 80 % and 95 % of the active subscription's traffic (stage 15), once per threshold, beside the expiry
 * notice and with its «Продлить». Traffic is known only from `subscription-userinfo`, so the check runs after every
 * subscription change (`ProfileReceiver`); no alarm is needed. A threshold reached at night is told without a sound.
 */
object TrafficNotice {
    private const val CHANNEL = "subscription_traffic_channel"
    private const val TAG = "subscription_traffic"
    private const val ID = 1

    private val lock = Mutex()

    suspend fun check(context: Context) = lock.withLock {
        val store = ServiceStore(context)
        val imported = store.activeProfile?.let { ImportedDao().queryByUUID(it) }

        // No subscription, or one without an allowance: a warning shown is no longer about the one in use.
        if (imported == null || imported.total <= 0) {
            dismiss(context)

            return@withLock
        }

        val used = imported.upload + imported.download
        val decision = decideTraffic(imported.uuid.toString(), used, imported.total, TrafficSeen.parse(store.trafficNotice))

        if (decision.clear) dismiss(context)
        if (decision.notify != null) notify(context, imported, used, decision.notify)

        store.trafficNotice = decision.seen.toString()
    }

    private fun notify(context: Context, imported: Imported, used: Long, step: Int) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.traffic_channel))
                .build()
        )

        val locale = context.resources.configuration.locales[0]
        val percent = NumberFormat.getPercentInstance(locale).format(step / 100.0)
        val text = context.getString(
            R.string.traffic_text,
            context.shownName(imported),
            context.formatBytes((imported.total - used).coerceAtLeast(0)),
            context.formatBytes(imported.total),
        )

        val open = PendingIntent.getActivity(
            context, 0,
            Intent().setComponent(Components.MAIN_ACTIVITY),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        val now = Instant.now().atZone(ZoneId.systemDefault()).toLocalTime()

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(context.getColorCompat(R.color.color_clash))
            .setContentTitle(context.getString(R.string.traffic_title, percent))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // A background update brings the threshold at any hour: at night it waits in the shade quietly.
            .setSilent(now < DAY_START || now > DAY_END)

        // «Продлить» — where it leads on the card and in the expiry notice (D-119, D-317).
        val provider = ProviderInfo.read(context.importedDir.resolve(imported.uuid.toString()))
        val renew = firstProviderLink(provider.buyUrl, provider.supportUrl, provider.profileWebPageUrl)

        if (renew != null) {
            val intent = PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_VIEW, Uri.parse(renew)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            )

            builder.addAction(0, context.getString(R.string.expiry_renew), intent)
        }

        // Without the notification permission (Android 13+) the system drops it silently.
        runCatching { manager.notify(TAG, ID, builder.build()) }
    }

    private fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAG, ID)
    }
}

/** Percent of the allowance, from the lower to the higher. */
private val TRAFFIC_STEPS = listOf(80, 95)

/** What is remembered: the subscription and the highest threshold already told about, 0 — none. */
internal data class TrafficSeen(val uuid: String, val step: Int) {
    override fun toString() = "$uuid|$step"

    companion object {
        fun parse(text: String): TrafficSeen? {
            val (uuid, step) = text.split('|').takeIf { it.size == 2 } ?: return null

            return TrafficSeen(uuid, step.toIntOrNull() ?: return null)
        }
    }
}

internal data class TrafficDecision(
    /** Warn at this percent; `null` — not now. */
    val notify: Int?,
    val seen: TrafficSeen,
    /** The warning shown is out of date: the counter was reset, traffic bought, or another subscription is active. */
    val clear: Boolean,
)

internal fun decideTraffic(uuid: String, used: Long, total: Long, seen: TrafficSeen?): TrafficDecision {
    // In doubles: a provider's “unlimited” can be a total near Long.MAX_VALUE.
    val share = used.toDouble() / total
    val due = TRAFFIC_STEPS.lastOrNull { share * 100 >= it } ?: 0
    val same = seen?.takeIf { it.uuid == uuid }

    return when {
        // A new subscription or another one made active: thresholds already passed stay silent, as with the expiry
        // notice (D-149) — its card shows the traffic anyway.
        same == null -> TrafficDecision(null, TrafficSeen(uuid, due), clear = seen != null)
        due > same.step -> TrafficDecision(due, TrafficSeen(uuid, due), clear = false)
        // Fewer used than told: the next threshold is told again in the new period.
        due < same.step -> TrafficDecision(null, TrafficSeen(uuid, due), clear = true)
        else -> TrafficDecision(null, same, clear = false)
    }
}
