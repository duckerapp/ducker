package com.github.kr328.clash.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.github.kr328.clash.common.util.formatBytes
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

/** Одно форматирование на приложение и службу — лежит в `common`. */
@Composable
fun formatBytes(value: Long): String = LocalContext.current.formatBytes(value)

/**
 * Дата без хвоста «г.»: русский длинный формат дописывает его литералом, и в карточке
 * подписки он занимает место, ничего не сообщая. Шаблон правим, а не строку: в других
 * языках этого литерала просто нет, и формат остаётся системным.
 */
@Composable
fun formatDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateFormat.getDateInstance(DateFormat.LONG, locale)

    val trimmed = (format as? SimpleDateFormat)
        ?.takeIf { "'г'." in it.toPattern() }
        ?.let { SimpleDateFormat(it.toPattern().replace("'г'.", "").trim(), locale) }

    return (trimmed ?: format).format(Date(millis))
}

@Composable
fun formatPercent(fraction: Float): String = NumberFormat.getPercentInstance(
    LocalConfiguration.current.locales[0],
).format(fraction.toDouble())

/** A count in the app's language: Persian and Arabic have their own digits. A bare `"$n"` is always Latin. */
@Composable
fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value)
