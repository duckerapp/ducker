package com.github.kr328.clash.core.util

import com.github.kr328.clash.core.model.Traffic

fun Traffic.trafficUpload(): String {
    return trafficString(scaleTraffic(this ushr 32))
}

fun Traffic.trafficDownload(): String {
    return trafficString(scaleTraffic(this and 0xFFFFFFFF))
}

fun Traffic.trafficTotal(): String {
    val upload = scaleTraffic(this ushr 32)
    val download = scaleTraffic(this and 0xFFFFFFFF)

    return trafficString(upload + download)
}

/**
 * ducker: то же значение, но числом в байтах — своему интерфейсу нужны цифры,
 * а не готовая строка с английскими единицами.
 *
 * Упаковка описана в одном месте (cpp/bridge_helper.c): верхние два бита — единица
 * измерения, остальное — значение. Для всех единиц, кроме байтов, значение умножено
 * на 100, поэтому здесь деление.
 */
fun Traffic.uploadBytes(): Long = trafficBytes(this ushr 32)

fun Traffic.downloadBytes(): Long = trafficBytes(this and 0xFFFFFFFF)

private fun trafficBytes(value: Long): Long {
    val type = (value ushr 30) and 0x3
    val data = value and 0x3FFFFFFF

    return when (type) {
        0L -> data
        1L -> data * 1024 / 100
        2L -> data * 1024 * 1024 / 100
        3L -> data * 1024 * 1024 * 1024 / 100
        else -> 0
    }
}

private fun trafficString(scaled: Long): String {
    return when {
        scaled > 1024 * 1024 * 1024 * 100L -> {
            val data = scaled / 1024 / 1024 / 1024

            String.format("%.2f GiB", data.toFloat() / 100)
        }
        scaled > 1024 * 1024 * 100L -> {
            val data = scaled / 1024 / 1024

            String.format("%.2f MiB", data.toFloat() / 100)
        }
        scaled > 1024 * 100L -> {
            val data = scaled / 1024

            String.format("%.2f KiB", data.toFloat() / 100)
        }
        else -> {
            "$scaled Bytes"
        }
    }
}

private fun scaleTraffic(value: Long): Long {
    val type = (value ushr 30) and 0x3
    val data = value and 0x3FFFFFFF

    return when (type) {
        0L -> data
        1L -> data * 1024
        2L -> data * 1024 * 1024
        3L -> data * 1024 * 1024 * 1024
        else -> throw IllegalArgumentException("invalid value type")
    }
}