package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.firstProviderLink
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Уведомления о конце подписки за 7, 3 и 1 день (этап 10, D-149). Только об активной: VPN
 * работает через неё, а карточка «Подписка до» на главной — тоже о ней.
 *
 * Будильник не будит телефон (D-85): срабатывает, когда тот проснётся сам. Проверка идёт и по
 * будильнику, и после каждого изменения подписок (`ProfileReceiver`): так продление или
 * сокращение срока, пришедшее с обновлением, сразу перестраивает пороги.
 */
object ExpiryNotice {
    const val ACTION = "app.ducker.android.service.EXPIRY_CHECK"

    private const val CHANNEL = "subscription_expiry_channel"
    private const val TAG = "subscription_expiry"
    private const val ID = 1

    private val lock = Mutex()

    suspend fun check(context: Context) = lock.withLock {
        val uuid = evaluate(context, mayRefresh = true) ?: return@withLock

        // Порог наступил. Сначала — свежие сведения: подписку могли продлить у провайдера, а ducker
        // узнаёт об этом только при обновлении. Процесс умрёт посреди загрузки — проверим через час.
        arm(context, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))

        runCatching { ProfileProcessor.update(context, uuid, null) }

        // Не скачалось — предупреждаем по последнему известному сроку: один раз на порог.
        evaluate(context, mayRefresh = false)
    }

    /**
     * Решает и делает всё сам; возвращает подписку, только если перед уведомлением её надо
     * обновить.
     */
    private suspend fun evaluate(context: Context, mayRefresh: Boolean): UUID? {
        val store = ServiceStore(context)
        val imported = store.activeProfile?.let { ImportedDao().queryByUUID(it) }
        val now = System.currentTimeMillis()

        if (imported == null || imported.expire <= 0) {
            disarm(context)
            dismiss(context)

            return null
        }

        val decision = decide(
            uuid = imported.uuid.toString(),
            expire = imported.expire,
            now = now,
            zone = ZoneId.systemDefault(),
            seen = Seen.parse(store.expiryNotice),
        )

        if (decision.notify != null && mayRefresh) {
            val updated = context.importedDir.resolve(imported.uuid.toString())
                .resolve("config.yaml").lastModified()

            if (now - updated > FRESH_MS) return imported.uuid
        }

        if (decision.clear) dismiss(context)
        if (decision.notify != null) notify(context, imported, now)

        store.expiryNotice = decision.seen.toString()

        if (decision.next != null) arm(context, decision.next) else disarm(context)

        return null
    }

    private fun notify(context: Context, imported: Imported, now: Long) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.expiry_channel))
                .build()
        )

        val days = ((imported.expire - now).toDouble() / DAY_MS).roundToInt().coerceAtLeast(1)
        val locale = context.resources.configuration.locales[0]
        val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
            .format(Date(imported.expire))
        val text = context.getString(R.string.expiry_text, context.shownName(imported), date)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent().setComponent(Components.MAIN_ACTIVITY),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_service)
            .setColor(context.getColorCompat(R.color.color_clash))
            .setContentTitle(context.resources.getQuantityString(R.plurals.expiry_title, days, days))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)

        // «Продлить» — куда и на карточке (D-119): страница покупки, иначе поддержка, иначе страница
        // провайдера (D-317).
        val provider = ProviderInfo.read(context.importedDir.resolve(imported.uuid.toString()))
        val renew = firstProviderLink(provider.buyUrl, provider.supportUrl, provider.profileWebPageUrl)

        if (renew != null) {
            val intent = PendingIntent.getActivity(
                context, 1,
                Intent(Intent.ACTION_VIEW, Uri.parse(renew)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            )

            builder.addAction(0, context.getString(R.string.expiry_renew), intent)
        }

        // Без разрешения на уведомления (Android 13+) система молча не покажет — не наша беда.
        runCatching { manager.notify(TAG, ID, builder.build()) }
    }

    private fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAG, ID)
    }

    /**
     * Будильник с окном в час: обычный неточный `set` на Android 10–11 система сдвигает на три
     * четверти срока — поставленный за три недели приходил бы после конца подписки.
     */
    private fun arm(context: Context, at: Long) {
        context.getSystemService<AlarmManager>()
            ?.setWindow(AlarmManager.RTC, at, TimeUnit.HOURS.toMillis(1), alarm(context))
    }

    private fun disarm(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(alarm(context))
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(ACTION).setComponent(ProfileReceiver::class.componentName),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
    )
}

/** За сколько дней до конца предупреждаем — от дальнего к ближнему. */
private val STEPS = listOf(7, 3, 1)

private const val NONE = Int.MAX_VALUE
private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Обновление моложе этого — сведения свежие, перед уведомлением не скачиваем заново. */
private val FRESH_MS = TimeUnit.MINUTES.toMillis(15)

/** Днём, чтобы не звенеть ночью. */
private val DAY_START: LocalTime = LocalTime.of(10, 0)
private val DAY_END: LocalTime = LocalTime.of(21, 0)

/** Что помним: подписка, её срок и самый близкий к концу порог, о котором уже сказали. */
internal data class Seen(val uuid: String, val expire: Long, val step: Int) {
    override fun toString() = "$uuid|$expire|$step"

    companion object {
        fun parse(text: String): Seen? {
            val (uuid, expire, step) = text.split('|').takeIf { it.size == 3 } ?: return null

            return Seen(uuid, expire.toLongOrNull() ?: return null, step.toIntOrNull() ?: return null)
        }
    }
}

internal data class Decision(
    /** Показать уведомление за столько дней; `null` — не показывать. */
    val notify: Int?,
    val seen: Seen,
    /** Подписку продлили или активной стала другая — показанное уведомление больше не о том. */
    val clear: Boolean,
    /** Когда проверить снова; `null` — порогов впереди нет. */
    val next: Long?,
)

internal fun decide(uuid: String, expire: Long, now: Long, zone: ZoneId, seen: Seen?): Decision {
    val step = dueStep(expire, now, zone)
    val same = seen?.takeIf { it.uuid == uuid }
    val extended = same != null && expire > same.expire
    val clear = extended || (seen != null && same == null)

    val told = when {
        // Срок сократили — прошедший порог человеку ещё неизвестен: говорим сразу.
        same != null && expire < same.expire -> NONE
        same != null && !extended -> same.step
        // Новая подписка, другая или продлённая: прошедшие пороги не догоняем, иначе пробная
        // на три дня звенела бы в момент добавления, а продлённая — сразу после продления.
        else -> step ?: NONE
    }
    val notify = step?.takeIf { it < told }

    // Ночь — ждём утра, если подписка до него доживёт.
    val morning = daytimeFrom(now, zone)
    if (notify != null && morning > now && morning < expire) {
        return Decision(null, Seen(uuid, expire, told), clear, morning)
    }

    return Decision(notify, Seen(uuid, expire, notify ?: told), clear, nextNotice(expire, now, zone))
}

/** Когда предупредить за [days] дней: в тот же день, но не раньше 10:00 и не позже 21:00. */
internal fun noticeAt(expire: Long, days: Int, zone: ZoneId): Long {
    val at = expire - days * DAY_MS
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    return at.coerceIn(
        day.atTime(DAY_START).atZone(zone).toInstant().toEpochMilli(),
        day.atTime(DAY_END).atZone(zone).toInstant().toEpochMilli(),
    )
}

/** Самый близкий к концу порог, время которого наступило; `null` — ни один или срок вышел. */
internal fun dueStep(expire: Long, now: Long, zone: ZoneId): Int? =
    if (now >= expire) null else STEPS.lastOrNull { noticeAt(expire, it, zone) <= now }

private fun nextNotice(expire: Long, now: Long, zone: ZoneId): Long? =
    STEPS.map { noticeAt(expire, it, zone) }.firstOrNull { it > now }

/** Ближайший миг дневного окна: сейчас, если уже день. */
private fun daytimeFrom(now: Long, zone: ZoneId): Long {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val time = local.toLocalTime()

    return when {
        time < DAY_START -> local.toLocalDate().atTime(DAY_START).atZone(zone).toInstant().toEpochMilli()
        time > DAY_END -> local.toLocalDate().plusDays(1).atTime(DAY_START).atZone(zone).toInstant().toEpochMilli()
        else -> now
    }
}
