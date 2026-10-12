package com.github.kr328.clash.ui.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshGateTest {
    @Test
    fun `a burst before the first answer is one run, a burst after it adds one more`() = runBlocking {
        var finished = 0
        val service = CompletableDeferred<Unit>()
        val work = CompletableDeferred<Unit>()

        val gate = RefreshGate(this) { answered ->
            service.await()
            answered()
            work.await()
            finished++
        }

        // The screen, the first composition and the service's message at startup.
        repeat(3) {
            gate.request()
            yield()
        }

        service.complete(Unit)
        yield()

        // Now the run holds an answer that these requests may have made stale.
        repeat(3) { gate.request() }

        work.complete(Unit)
        coroutineContext.job.children.forEach { it.join() }

        assertEquals(2, finished)
    }
}
