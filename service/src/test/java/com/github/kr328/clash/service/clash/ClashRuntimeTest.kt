package com.github.kr328.clash.service.clash

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A module's own exception stays its own (audit 06.11.2026, item 2). */
class ClashRuntimeTest {
    @Test
    fun `a module that throws does not cancel the runtime or the other modules`() = runBlocking {
        val failure = CompletableDeferred<Exception?>()
        val other = CompletableDeferred<Unit>()

        // As `install` does: each module a child of the runtime's coroutine.
        coroutineScope {
            launch { failure.complete(moduleFailure { error("full disk") }) }
            launch {
                yield()
                other.complete(Unit)
            }
        }

        assertEquals("full disk", failure.await()?.message)
        assertTrue(other.isCompleted)
        assertNull(moduleFailure { })
    }

    @Test
    fun `stopping the runtime still cancels a module`() = runBlocking {
        val module = launch { moduleFailure { awaitCancellation() } }

        yield()
        module.cancel()
        module.join()

        assertTrue(module.isCancelled)
    }
}
