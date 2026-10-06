package com.github.kr328.clash.util

import com.github.kr328.clash.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Тексты — как их отдаёт Go: `context`, `net`, `io`. */
class NodeFailureTest {
    @Test
    fun `familiar reasons get words`() {
        val cases = mapOf(
            "context deadline exceeded" to R.string.node_failure_timeout,
            "dial tcp 192.0.2.1:443: i/o timeout" to R.string.node_failure_timeout,
            "dial tcp 192.0.2.1:443: connect: connection refused" to R.string.node_failure_refused,
            "dial tcp 192.0.2.1:443: connect: network is unreachable" to R.string.node_failure_no_network,
            "dial tcp: lookup node.example: no such host" to R.string.node_failure_dns,
            "read tcp 10.0.0.2:1->192.0.2.1:443: read: connection reset by peer" to R.string.node_failure_dropped,
            "unexpected EOF" to R.string.node_failure_dropped,
            NO_SERVICE to R.string.node_failure_no_service,
        )

        for ((raw, expected) in cases) assertEquals(expected, nodeFailure(raw), raw)
    }

    @Test
    fun `unfamiliar reason stays as the core put it`() {
        assertNull(nodeFailure("tls: first record does not look like a TLS handshake"))
        assertNull(nodeFailure("proxy not found: DE"))
    }
}
