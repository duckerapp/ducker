package com.github.kr328.kaidl

import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * The transport of `suspend` commands. A call has two ends, a Binder object in each process: the
 * caller's end receives how the command ended, the service's end takes the cancellation and hands
 * out a reply that is too large for one message. The wire protocol — kaidl/README.md.
 */

// To the caller's end, one-way: how the command ended.
private const val COMPLETE = IBinder.FIRST_CALL_TRANSACTION
private const val CANCELED = IBinder.FIRST_CALL_TRANSACTION + 1
private const val PARKED = IBinder.FIRST_CALL_TRANSACTION + 2

// To the service's end.
private const val CLOSE = IBinder.FIRST_CALL_TRANSACTION
private const val PULL = IBinder.FIRST_CALL_TRANSACTION + 1

/**
 * The most a one-way message of ours carries. One-way messages to a process share half of its
 * 1 MB Binder buffer, and Android reaches the process through the same half.
 */
private const val INLINE_LIMIT = 64 * 1024

/** One piece of a parked reply: a synchronous answer, which may use the whole buffer. */
private const val PIECE = 256 * 1024

/** Waits between tries when the caller's buffer has no room at the moment, in milliseconds. */
private val PAUSES = longArrayOf(10, 20, 40, 80, 160)

/** One reply at a time is pulled into this process: the pieces of several would not fit together. */
private val pulls = Mutex()

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * One `suspend` command from the caller's side. It always ends: with the reply in [reply], with
 * what the command threw, with `DeadObjectException` when the service dies, or cancelled.
 * The generated proxies call it.
 */
suspend fun IBinder.suspendCall(code: Int, data: Parcel, reply: Parcel) {
    val remote = this

    // Nobody when the reply has arrived, in `reply`; else the service's end, where it is parked.
    val parkedAt = suspendCancellableCoroutine<IBinder?> { continuation ->
        val end = CallerEnd(remote, continuation, reply)

        val service = try {
            // Before the command starts: there is no moment when the death of the service goes unseen.
            remote.linkToDeath(end, 0)

            data.writeStrongBinder(end)

            remote.start(code, data)
        } catch (e: Exception) {
            return@suspendCancellableCoroutine end.fail(e)
        }

        continuation.invokeOnCancellation {
            end.abandon()
            service.close()
        }
    }

    parkedAt?.pull(reply)

    reply.setDataPosition(0)
    reply.readException()
}

/**
 * Runs the body of a `suspend` command in the service and tells the caller how it ended.
 * The generated delegates call it.
 */
fun launchSuspend(data: Parcel, reply: Parcel, body: suspend (outcome: Parcel) -> Unit) {
    val end = ServiceEnd(data.readStrongBinder(), body)

    reply.writeNoException()
    reply.writeStrongBinder(end)
}

/** Starts the command and returns the service's end of the call. */
private fun IBinder.start(code: Int, data: Parcel): IBinder {
    val started = Parcel.obtain()

    try {
        call(code, data, started)

        return started.readStrongBinder()
    } finally {
        started.recycle()
    }
}

/** Brings a parked reply into [into], piece by piece, and lets the service drop it. */
private suspend fun IBinder.pull(into: Parcel) {
    try {
        // Synchronous transactions: not for the caller's thread, which may be the main one.
        withContext(Dispatchers.IO) {
            pulls.withLock {
                do {
                    ensureActive()

                    val total = piece(into)
                } while (into.dataSize() < total)
            }
        }
    } finally {
        close()
    }
}

/** Appends the piece that follows what [into] already holds; returns the size of the whole reply. */
private fun IBinder.piece(into: Parcel): Int {
    val request = Parcel.obtain()
    val piece = Parcel.obtain()

    try {
        val offset = into.dataSize()

        request.writeInt(offset)

        call(PULL, request, piece)

        val total = piece.readInt()
        // What `writeByteArray` puts before the bytes; they go from parcel to parcel as they are.
        val length = piece.readInt()

        into.appendFrom(piece, piece.dataPosition(), length)

        check(length > 0 && into.dataSize() == offset + length) { "A piece of a parked reply did not arrive whole" }

        return total
    } finally {
        request.recycle()
        piece.recycle()
    }
}

/**
 * Tells the service's end that nobody waits for this call any more. One-way: the thread that
 * cancels, often the main one, must not wait for the service.
 */
private fun IBinder.close() {
    val nothing = Parcel.obtain()

    try {
        transact(CLOSE, nothing, null, IBinder.FLAG_ONEWAY)
    } catch (e: Exception) {
        // The service cannot be told: it is gone, or it drops the call when this process goes.
    } finally {
        nothing.recycle()
    }
}

/** One one-way message to the caller's end; `false` if it could not be handed over. */
private fun IBinder.post(code: Int, message: Parcel): Boolean {
    return try {
        transact(code, message, null, IBinder.FLAG_ONEWAY)
    } catch (e: Exception) {
        false
    }
}

/** [post] with a few more tries: a full buffer empties within milliseconds. */
private suspend fun IBinder.insist(code: Int, message: Parcel): Boolean {
    for (pause in PAUSES) {
        if (post(code, message)) return true
        if (!isBinderAlive) return false

        delay(pause)
    }

    return post(code, message)
}

/**
 * The caller's end of a call: it receives how the command ended and watches the service die.
 * The service's process holds it until it collects its garbage, long after the call, so what
 * belongs to the call leaves it when the call ends.
 */
private class CallerEnd(
    private val remote: IBinder,
    private var waiting: CancellableContinuation<IBinder?>?,
    private var into: Parcel?,
) : Binder(), IBinder.DeathRecipient {
    /**
     * The one gate every ending goes through: the reply, a cancellation on either side, a failed
     * start, the death of the service. The first to arrive takes the waiting caller and may
     * [write] into its reply parcel; later ones find nobody and touch nothing. Under the lock,
     * because a caller let go by a cancellation recycles that parcel at once.
     */
    private inline fun end(write: (into: Parcel) -> Unit = {}): CancellableContinuation<IBinder?>? {
        synchronized(this) {
            val continuation = waiting ?: return null

            try {
                remote.unlinkToDeath(this, 0)
            } catch (e: NoSuchElementException) {
                // Never linked, or the service died and the link went with it.
            }

            write(into!!)

            waiting = null
            into = null

            return continuation
        }
    }

    /** The caller was cancelled and is gone already. */
    fun abandon() {
        end()
    }

    fun fail(cause: Throwable) {
        end()?.resumeWithException(cause)
    }

    override fun binderDied() = fail(DeadObjectException())

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            COMPLETE -> end { it.appendFrom(data, 0, data.dataSize()) }?.resume(null)
            PARKED -> end()?.resume(data.readStrongBinder())
            CANCELED -> end()?.cancel()
            else -> return super.onTransact(code, data, reply, flags)
        }

        return true
    }
}

/**
 * The service's end of a call: it takes the cancellation and hands out a parked reply. The
 * caller's process holds it until it collects its garbage, long after the call — so the caller's
 * binder is not kept here: it belongs to the coroutine and goes when that ends.
 */
private class ServiceEnd(caller: IBinder, body: suspend (outcome: Parcel) -> Unit) : Binder() {
    /** A reply that waits for the caller to pull it. */
    @Volatile
    private var parked: ByteArray? = null

    private val job = scope.launch { serve(caller, body) }

    /** Runs the body and delivers its outcome; ends when the caller needs nothing more of this call. */
    private suspend fun serve(caller: IBinder, body: suspend (outcome: Parcel) -> Unit) {
        val job = currentCoroutineContext().job
        val watch = IBinder.DeathRecipient { job.cancel() }

        try {
            // Here and not while the caller still waits for the command to start.
            caller.linkToDeath(watch, 0)
        } catch (e: RemoteException) {
            return
        }

        val outcome = Parcel.obtain()

        try {
            try {
                body(outcome)
            } catch (e: CancellationException) {
                outcome.setDataSize(0)

                // The caller is told unless the cancellation is its own or its death.
                if (job.isActive) caller.post(CANCELED, outcome)

                return
            } catch (e: Exception) {
                outcome.writeFailure(e)
            }

            deliver(caller, outcome)
        } finally {
            parked = null

            outcome.recycle()

            try {
                caller.unlinkToDeath(watch, 0)
            } catch (e: NoSuchElementException) {
                // The caller died and the link went with it.
            }
        }
    }

    private suspend fun deliver(caller: IBinder, outcome: Parcel) {
        val small = outcome.dataSize() <= INLINE_LIMIT

        if (small && caller.post(COMPLETE, outcome)) return

        // Too large for a one-way message, or the caller's buffer has no room for it right now:
        // the reply stays here as bytes, and the caller is told to come for it.
        val bytes = try {
            outcome.marshall()
        } catch (e: RuntimeException) {
            null // `Parcel` does not marshall binders and file descriptors.
        }

        val notice = Parcel.obtain()

        try {
            val told = when {
                bytes != null -> {
                    parked = bytes

                    notice.writeStrongBinder(this)

                    caller.insist(PARKED, notice)
                }
                small -> caller.insist(COMPLETE, outcome)
                else -> {
                    notice.writeFailure(
                        IllegalArgumentException("A reply of ${outcome.dataSize()} bytes cannot hold a binder or a file descriptor")
                    )

                    caller.insist(COMPLETE, notice)
                }
            }

            if (!told) {
                if (caller.isBinderAlive) Log.w("Binder: the outcome of a suspend command did not reach its caller")

                return
            }
        } finally {
            notice.recycle()
        }

        // Parked until the caller has pulled the reply, gives the call up or dies: each cancels this job.
        if (bytes != null) awaitCancellation()
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            CLOSE -> job.cancel()
            PULL -> {
                reply ?: return false

                val bytes = parked ?: throw IllegalStateException("No reply waits here")
                val offset = data.readInt()

                reply.writeNoException()
                reply.writeInt(bytes.size)
                reply.writeByteArray(bytes, offset, minOf(PIECE, bytes.size - offset))
            }
            else -> return super.onTransact(code, data, reply, flags)
        }

        return true
    }
}
