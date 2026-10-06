package com.github.kr328.clash.core.bridge

import com.github.kr328.clash.core.model.LogMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogcatChannelTest {
    private val payload = """{"level":"info","message":"test event","time":0}"""

    @Test
    fun `open channel receives decoded events`() {
        val channel = Channel<LogMessage>(1)
        LogcatChannel(channel).received(payload)
        val message = channel.tryReceive().getOrThrow()
        assertEquals(LogMessage.Level.Info, message.level)
        assertEquals("test event", message.message)
        assertEquals(0L, message.time.time)
    }

    @Test
    fun `closed channel signals native unsubscribe`() {
        val channel = Channel<LogMessage>(1)
        channel.close()
        assertFailsWith<CancellationException> { LogcatChannel(channel).received(payload) }
    }

    @Test
    fun `cancelled channel signals native unsubscribe`() {
        val channel = Channel<LogMessage>(1)
        channel.cancel()
        assertFailsWith<CancellationException> { LogcatChannel(channel).received(payload) }
    }

    @Test
    fun `full channel drops a line and keeps accepting later events`() {
        val channel = Channel<LogMessage>(1)
        val callback = LogcatChannel(channel)
        callback.received(payload)
        callback.received(payload)
        assertTrue(channel.tryReceive().isSuccess)
        assertTrue(channel.tryReceive().isFailure)
        callback.received(payload)
        assertTrue(channel.tryReceive().isSuccess)
    }
}
