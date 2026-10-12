package com.github.kr328.kaidl.test

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.SparseBooleanArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** The far side of [BinderExchangeTest]: it lives in the `:exchange` process of the test package. */
class ExchangeService : Service() {
    private val binder = Exchange().wrap()

    override fun onBind(intent: Intent?): IBinder = binder
}

class Exchange : IExchange {
    private val hangsStarted = AtomicInteger()
    private val hangsCancelled = AtomicInteger()

    override fun nothing() = Unit
    override fun echoInt(v: Int) = v
    override fun echoLong(v: Long) = v
    override fun echoFloat(v: Float) = v
    override fun echoDouble(v: Double) = v
    override fun echoByte(v: Byte) = v
    override fun echoBoolean(v: Boolean) = v
    override fun echoString(v: String) = v

    override fun echoByteArray(v: ByteArray) = v
    override fun echoCharArray(v: CharArray) = v
    override fun echoBooleanArray(v: BooleanArray) = v
    override fun echoIntArray(v: IntArray) = v
    override fun echoLongArray(v: LongArray) = v
    override fun echoFloatArray(v: FloatArray) = v
    override fun echoDoubleArray(v: DoubleArray) = v

    override fun echoBinder(v: IBinder) = v
    override fun echoBundle(v: Bundle) = v
    override fun echoSparseBooleanArray(v: SparseBooleanArray) = v

    override fun echoList(v: List<String>) = v
    override fun echoSets(v: List<Set<Long>>) = v
    override fun echoMap(v: Map<String, Long>) = v
    override fun echoNested(v: Map<String, List<Set<Long>>>) = v

    override fun echoModel(v: Model) = v
    override fun echoModels(v: List<Model>) = v
    override fun echoEnum(v: Level) = v
    override fun echoNestedEnum(v: Model.Kind) = v
    override fun echoUuid(v: UUID) = v

    override fun echoNullableInt(v: Int?) = v
    override fun echoNullableString(v: String?) = v
    override fun echoNullableModel(v: Model?) = v
    override fun echoNullableEnum(v: Level?) = v
    override fun echoNullableUuid(v: UUID?) = v
    override fun echoNullableMap(v: Map<String, Long?>?) = v

    override fun describe(count: Int, name: String, model: Model?, levels: List<Level>): String {
        return "$count $name ${model?.name} $levels"
    }

    override fun tell(observer: IObserver, text: String) = observer.onEvent(text)
    override fun tellAll(observers: List<IObserver>, text: String) = observers.forEach { it.onEvent(text) }
    override fun peer(name: String): IPeer = Peer(name)

    override suspend fun suspendNothing() = Unit
    override suspend fun suspendEchoInt(v: Int) = v
    override suspend fun suspendEchoString(v: String) = v
    override suspend fun suspendEchoModels(v: List<Model>) = v
    override suspend fun suspendEchoNullableModel(v: Model?) = v
    override suspend fun suspendEchoUuid(v: UUID) = v
    override suspend fun suspendPeer(name: String): IPeer = Peer(name)

    override suspend fun suspendTell(text: String, observer: IObserver?): Boolean {
        observer?.onEvent(text)

        return observer != null
    }

    override fun fail(failure: Failure): Int = raise(failure)
    override suspend fun suspendFail(failure: Failure): Int = raise(failure)

    override fun bytes(size: Int) = pattern(size)
    override suspend fun suspendBytes(size: Int) = pattern(size)

    override suspend fun suspendEchoAfter(v: Int, millis: Long): Int {
        delay(millis)

        return v
    }

    override suspend fun suspendHang() {
        hangsStarted.incrementAndGet()

        try {
            awaitCancellation()
        } finally {
            hangsCancelled.incrementAndGet()
        }
    }

    override fun hangs() = intArrayOf(hangsStarted.get(), hangsCancelled.get())

    override fun sleep(millis: Long) = Thread.sleep(millis)
    override fun pid() = Process.myPid()

    private fun raise(failure: Failure): Nothing {
        throw when (failure) {
            Failure.IllegalState -> IllegalStateException(MESSAGE)
            Failure.IllegalArgumentSubclass -> OwnArgumentException(MESSAGE)
            Failure.Security -> SecurityException(MESSAGE)
            Failure.NullPointer -> NullPointerException(MESSAGE)
            Failure.UnsupportedOperation -> UnsupportedOperationException(MESSAGE)
            Failure.Io -> IOException(MESSAGE)
            Failure.OwnRuntime -> OwnRuntimeException(MESSAGE)
            Failure.Cancellation -> CancellationException(MESSAGE)
        }
    }

    private class Peer(private val name: String) : IPeer {
        override fun name() = name
    }

    // The core's own error has this shape: `ClashException` extends `IllegalArgumentException`.
    private class OwnArgumentException(message: String) : IllegalArgumentException(message)
    private class OwnRuntimeException(message: String) : RuntimeException(message)

    companion object {
        const val MESSAGE = "from the service: утка 🦆"

        fun pattern(size: Int) = ByteArray(size) { (it % 251).toByte() }
    }
}
