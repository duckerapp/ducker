package com.github.kr328.clash.service.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

fun CoroutineScope.cancelAndJoinBlocking() {
    val scope = this

    runBlocking {
        scope.coroutineContext.job.cancel()
        scope.coroutineContext.job.join()
    }
}

/** The same, but waits at most [timeoutMs]: the job goes on finishing by itself. */
fun CoroutineScope.cancelAndJoinBlocking(timeoutMs: Long) {
    val job = coroutineContext.job

    runBlocking {
        job.cancel()
        withTimeoutOrNull(timeoutMs) { job.join() }
    }
}