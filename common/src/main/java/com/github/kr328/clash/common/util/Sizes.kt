package com.github.kr328.clash.common.util

import android.content.Context
import com.github.kr328.clash.common.R
import java.text.NumberFormat

/**
 * Размер в двоичных единицах, как их считает провайдер; число — по языку приложения.
 *
 * Лежит в `common`, а не в интерфейсе: те же килобайты пишутся в шторке, а там нет
 * ни Compose, ни ресурсов приложения. Ядро своё форматирование тоже умеет, но выдаёт
 * `KiB/s` латиницей и без учёта языка (F-95).
 */
fun Context.formatBytes(value: Long): String {
    val units = listOf(R.string.unit_b, R.string.unit_kb, R.string.unit_mb, R.string.unit_gb, R.string.unit_tb)

    var size = value.coerceAtLeast(0).toDouble()
    var unit = 0

    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }

    // Байты дробными не бывают, а от сотни и выше десятая доля ничего не добавляет.
    val decimals = if (unit == 0 || size >= 100) 0 else 1

    val number = NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = false
    }.format(size)

    return getString(R.string.size_format, number, getString(units[unit]))
}

fun Context.formatSpeed(value: Long): String =
    getString(R.string.speed_per_second, formatBytes(value))
