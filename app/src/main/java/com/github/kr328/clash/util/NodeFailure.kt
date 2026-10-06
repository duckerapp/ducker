package com.github.kr328.clash.util

import androidx.annotation.StringRes
import com.github.kr328.clash.R

/** Замер не дошёл до ядра: служба не ответила (`ProbeViewModel`). */
internal const val NO_SERVICE = "нет ответа от службы"

/**
 * Почему узел не ответил — словами (D-227). Текст приходит от ядра (`tunnel/probe.go`, ошибка
 * `URLTest`) и у каждого узла свой, с адресами. Знакомые причины называем; незнакомая — `null`,
 * и экран показывает текст ядра как есть.
 */
@StringRes
internal fun nodeFailure(raw: String): Int? = when {
    raw == NO_SERVICE -> R.string.node_failure_no_service
    "deadline exceeded" in raw || "timeout" in raw -> R.string.node_failure_timeout
    "connection refused" in raw -> R.string.node_failure_refused
    "network is unreachable" in raw || "no route to host" in raw -> R.string.node_failure_no_network
    "no such host" in raw -> R.string.node_failure_dns
    "connection reset" in raw || "broken pipe" in raw || raw.endsWith("EOF") -> R.string.node_failure_dropped
    else -> null
}
