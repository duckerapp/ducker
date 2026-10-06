package com.github.kr328.clash.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CrashHandlerTest {
    @Test
    fun `records before delegating original thread and throwable`() {
        val events = mutableListOf<String>()
        val thread = Thread.currentThread()
        val failure = IllegalStateException("synthetic crash")
        val handler = CrashHandler(Thread.UncaughtExceptionHandler { t, e ->
            assertSame(thread, t)
            assertSame(failure, e)
            events += "system"
        }) { t, e ->
            assertSame(thread, t)
            assertSame(failure, e)
            events += "record"
        }
        handler.uncaughtException(thread, failure)
        assertEquals(listOf("record", "system"), events)
    }

    @Test
    fun `recording failure still delegates to system`() {
        var delegated = 0
        val handler = CrashHandler(Thread.UncaughtExceptionHandler { _, _ -> delegated++ }) { _, _ ->
            throw java.io.IOException("disk full")
        }
        handler.uncaughtException(Thread.currentThread(), IllegalStateException())
        assertEquals(1, delegated)
    }

    @Test
    fun `nested recording is skipped and both errors delegate`() {
        var recorded = 0
        var delegated = 0
        lateinit var handler: CrashHandler
        handler = CrashHandler(Thread.UncaughtExceptionHandler { _, _ -> delegated++ }) { thread, _ ->
            recorded++
            handler.uncaughtException(thread, IllegalStateException("nested"))
        }
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("outer"))
        assertEquals(1, recorded)
        assertEquals(2, delegated)
    }

    @Test
    fun `recording guard resets after failed record`() {
        var recorded = 0
        var delegated = 0
        val handler = CrashHandler(Thread.UncaughtExceptionHandler { _, _ -> delegated++ }) { _, _ ->
            recorded++
            error("write failed")
        }
        repeat(2) { handler.uncaughtException(Thread.currentThread(), IllegalStateException()) }
        assertEquals(2, recorded)
        assertEquals(2, delegated)
    }
}
