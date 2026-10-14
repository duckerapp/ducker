package com.github.kr328.clash.service

import com.github.kr328.clash.service.data.Imported
import java.util.concurrent.TimeUnit

/**
 * How often the subscription updates itself: the provider's hours when “Провайдер” is chosen and the
 * last download brought them, the person's own [Imported.interval] otherwise (D-311).
 */
internal fun Imported.updateInterval(providerHours: Int): Long =
    if (followProvider && providerHours > 0) TimeUnit.HOURS.toMillis(providerHours.toLong()) else interval

/** Срок обычного обновления или повтора после ошибки; null — автообновление выключено. */
internal fun nextProfileUpdateAt(
    interval: Long,
    lastUpdatedAt: Long,
    now: Long,
    failures: Int = 0,
): Long? {
    if (interval < TimeUnit.MINUTES.toMillis(15)) return null

    if (failures > 0) {
        val hours = 1L shl (failures - 1).coerceAtMost(2)
        return now + TimeUnit.HOURS.toMillis(hours)
    }

    // An hour is the shortest a provider's header can say; the person's own stops start at two (D-311).
    val period = interval.coerceAtLeast(TimeUnit.HOURS.toMillis(1))
    return (lastUpdatedAt + period).coerceAtLeast(now)
}
