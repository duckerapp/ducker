package com.github.kr328.clash.log

import android.content.Context
import com.github.kr328.clash.common.compat.preferredLocale
import java.text.SimpleDateFormat
import java.util.Date

private const val DATE_ONLY = "yyyy-MM-dd"
private const val TIME_ONLY = "HH:mm:ss.SSS"

/**
 * Время в журнале. Своё, а не из модуля `design`: тот мы удаляем, а формат нужен ровно
 * здесь — в заголовке выгрузки и в каждой строке.
 */
fun Date.formatLog(context: Context, includeDate: Boolean = true): String {
    val locale = context.resources.configuration.preferredLocale
    val pattern = if (includeDate) "$DATE_ONLY $TIME_ONLY" else TIME_ONLY

    return SimpleDateFormat(pattern, locale).format(this)
}
