package com.github.kr328.clash.core.bridge

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.core.util.parseInetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress

/** Diagnostics must not throw back into a Go thread either. */
internal fun reportCallbackFailure(name: String, failure: Throwable) {
    runCatching { Log.e("Core callback $name failed", failure) }
}

internal class SafeTunInterface(
    private val protect: (Int) -> Boolean,
    private val queryUid: (Int, InetSocketAddress, InetSocketAddress) -> Int,
    private val reportFailure: (String, Throwable) -> Unit = ::reportCallbackFailure,
) : TunInterface {
    override fun markSocket(fd: Int): Boolean = try {
        protect(fd)
    } catch (failure: Throwable) {
        runCatching { reportFailure("markSocket", failure) }
        false
    }

    override fun querySocketUid(protocol: Int, source: String, target: String): Int = try {
        queryUid(protocol, parseInetSocketAddress(source), parseInetSocketAddress(target))
    } catch (failure: Throwable) {
        runCatching { reportFailure("querySocketUid", failure) }
        -1
    }
}

internal class SafeFetchCallback(
    private val result: CompletableDeferred<Unit>,
    private val reportStatus: (FetchStatus) -> Unit,
    private val reportFailure: (String, Throwable) -> Unit = ::reportCallbackFailure,
) : FetchCallback {
    private var failure: Throwable? = null

    override fun report(statusJson: String) {
        if (failure != null) return
        try {
            reportStatus(statusJsonFormat.decodeFromString(FetchStatus.serializer(), statusJson))
        } catch (caught: Throwable) {
            failure = caught
            runCatching { reportFailure("fetch.report", caught) }
        }
    }

    override fun complete(error: String?) {
        // Wait for the native operation to finish even after a bad progress callback:
        // the caller may reuse the processing directory as soon as await() returns.
        val caught = failure
        when {
            caught != null -> result.completeExceptionally(caught)
            error != null -> result.completeExceptionally(ClashException(error))
            else -> result.complete(Unit)
        }
    }

    private companion object {
        val statusJsonFormat = Json { ignoreUnknownKeys = true }
    }
}
