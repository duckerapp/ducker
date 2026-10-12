package com.github.kr328.kaidl

import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.TransactionTooLargeException

/**
 * One command from the caller's side: the transaction, then whatever the other side threw.
 * The generated proxies call it.
 */
fun IBinder.call(code: Int, data: Parcel, reply: Parcel) {
    val known = try {
        transact(code, data, reply, 0)
    } catch (e: DeadObjectException) {
        // Android reports any failed transaction with a request under 200 KB as the death of the
        // other process. Callers reconnect on this exception, so it has to be true.
        if (!isBinderAlive) throw e

        throw TransactionTooLargeException(
            "Transaction $code (${data.dataSize()} bytes) failed although the other process is alive: " +
                "its Binder buffer or ours has no room, or that process is frozen"
        ).apply { initCause(e) }
    }

    if (!known) throw UnsupportedOperationException("Transaction $code is not known to the other side")

    reply.readException()
}

/**
 * What a command threw, as its reply. The types Android carries between processes keep their
 * type; any other arrives as `IllegalArgumentException` with the same message.
 * The generated delegates call it.
 */
fun Parcel.writeFailure(failure: Exception) {
    // A result may be half written.
    setDataSize(0)
    setDataPosition(0)

    try {
        writeException(failure)
    } catch (notCarried: RuntimeException) {
        // `writeException` rethrows what it does not carry, after it has started writing.
        setDataSize(0)
        setDataPosition(0)

        // The stack trace travels in debuggable builds; let it point at the real place.
        writeException(IllegalArgumentException(failure.message).apply { stackTrace = failure.stackTrace })
    }
}
