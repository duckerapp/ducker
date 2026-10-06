package com.github.kr328.clash.core.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeCallbacksTest {
    private val status = """{"action":"Verifying","args":[],"progress":0,"max":1,"future":true}"""

    @Test
    fun `new status fields are tolerated and successful operation completes`() = runBlocking {
        val result = CompletableDeferred<Unit>()
        var reports = 0
        val callback = SafeFetchCallback(result, { reports++ })
        callback.report(status)
        callback.complete(null)
        result.await()
        assertEquals(1, reports)
    }

    @Test
    fun `bad status fails only after native operation completes`() = runBlocking {
        for (payload in listOf("invalid", status.replace("Verifying", "FutureAction"))) {
            val result = CompletableDeferred<Unit>()
            var failures = 0
            val callback = SafeFetchCallback(result, { error("must not run") }, { _, _ -> failures++ })
            callback.report(payload)
            callback.report(status)
            assertFalse(result.isCompleted)
            callback.complete(null)
            assertFailsWith<Exception> { result.await() }
            assertEquals(1, failures)
        }
    }

    @Test
    fun `status consumer and diagnostic failure never escape to native thread`() = runBlocking {
        val result = CompletableDeferred<Unit>()
        val callback = SafeFetchCallback(result, { error("consumer") }, { _, _ -> error("diagnostic") })
        callback.report(status)
        assertFalse(result.isCompleted)
        callback.complete(null)
        assertEquals("consumer", assertFailsWith<IllegalStateException> { result.await() }.message)
        callback.complete(null)
    }

    @Test
    fun `native fetch error is returned to awaiting caller`() = runBlocking {
        val result = CompletableDeferred<Unit>()
        SafeFetchCallback(result, {}).complete("fetch error")
        assertEquals("fetch error", assertFailsWith<ClashException> { result.await() }.message)
    }

    @Test
    fun `socket failures are explicit and diagnostic failure is contained`() {
        val callback = SafeTunInterface({ error("protect") }, { _, _, _ -> error("uid") }, { _, _ -> error("diagnostic") })
        assertFalse(callback.markSocket(7))
        assertEquals(-1, callback.querySocketUid(6, "127.0.0.1:100", "127.0.0.1:200"))
        assertEquals(-1, callback.querySocketUid(6, "broken address", "127.0.0.1:200"))
    }

    @Test
    fun `socket callbacks preserve protect result and IPv4 IPv6 endpoints`() {
        val callback = SafeTunInterface({ it == 7 }, { protocol, source, target ->
            assertEquals(6, protocol)
            assertEquals(100, source.port)
            assertEquals(200, target.port)
            1234
        })
        assertTrue(callback.markSocket(7))
        assertFalse(callback.markSocket(8))
        assertEquals(1234, callback.querySocketUid(6, "127.0.0.1:100", "[::1]:200"))
    }
}
