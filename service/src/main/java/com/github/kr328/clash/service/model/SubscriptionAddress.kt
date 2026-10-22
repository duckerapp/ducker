package com.github.kr328.clash.service.model

import kotlinx.serialization.Serializable

/**
 * One line of a subscription's list of addresses (Q-82): its link or a mirror, with the outcome of its
 * last try the usual way and of the last resort's tries of it. The host only — the path carries the
 * person's token.
 */
@Serializable
data class SubscriptionAddress(
    val host: String,
    /** The subscription's own link, not a mirror. */
    val own: Boolean = false,
    /** ok, connection, timeout, status, webpage, failed, untried; "" — not tried since this was kept. */
    val outcome: String = "",
    /** The seconds waited for timeout, the HTTP status for status. */
    val code: Int = 0,
    /** The last resort's tries of this address (Q-81), in their order. */
    val resorts: List<Resort> = emptyList(),
    /** The last successful update came through it, one way or another. */
    val served: Boolean = false,
) {
    /** [via]: "direct" or "proxy". */
    @Serializable
    data class Resort(val via: String, val outcome: String, val code: Int = 0)
}
