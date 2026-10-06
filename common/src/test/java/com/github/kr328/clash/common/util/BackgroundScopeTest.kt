package com.github.kr328.clash.common.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundScopeTest {
    @Test fun failureIsReportedWithoutCancellingSiblingOrScope() = runBlocking {
        val reported = CompletableDeferred<Throwable>()
        val scope = backgroundScope("test") { _, error -> reported.complete(error) }
        try {
            withTimeout(5_000) {
                val release = CompletableDeferred<Unit>()
                val sibling = scope.launch { release.await() }
                val failure = IllegalStateException("expected")
                scope.launch { throw failure }.join()
                assertEquals(failure, reported.await())
                assertTrue(sibling.isActive)
                assertTrue(scope.isActive)
                release.complete(Unit)
                sibling.join()
                val later = CompletableDeferred<Unit>()
                scope.launch { later.complete(Unit) }
                later.await()
            }
        } finally { scope.cancel() }
    }

    @Test fun cancellationIsNotReportedAndCancelsChildren() = runBlocking {
        var reported = false
        val scope = backgroundScope("test") { _, _ -> reported = true }
        val child = scope.launch { CompletableDeferred<Unit>().await() }
        scope.cancel()
        withTimeout(5_000) { child.join() }
        assertTrue(child.isCancelled)
        assertEquals(false, reported)
    }

    @Test fun diagnosticFailureDoesNotEscapeHandler() = runBlocking {
        val scope: CoroutineScope = backgroundScope("test") { _, _ -> error("disk failure") }
        try {
            withTimeout(5_000) { scope.launch { error("task failure") }.join() }
            assertTrue(scope.isActive)
        } finally { scope.cancel() }
    }
}
