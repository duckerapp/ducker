package com.github.kr328.clash.service

import java.util.concurrent.TimeUnit

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

    val period = interval.coerceAtLeast(TimeUnit.HOURS.toMillis(4))
    return (lastUpdatedAt + period).coerceAtLeast(now)
}
