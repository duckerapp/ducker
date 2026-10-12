package com.github.kr328.kaidl.test

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.SparseBooleanArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val atTheGate = AtomicInteger()
    private val gate = CompletableDeferred<Unit>()

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
    override fun echoSet(v: Set<Long>) = v
    override fun echoSets(v: List<Set<Long>>) = v
    override fun echoPair(v: Pair<String, Long?>) = v
    override fun echoMap(v: Map<String, Long>) = v
    override fun echoNested(v: Map<String, List<Set<Long>>>) = v
    override fun merge(a: Map<String, Long>, b: Map<String, Long>, c: Pair<String, Long>, d: Pair<String, Long>) = a + b + c + d

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

    override fun echoNames(code: Int, data: String, reply: String, flags: Int, remote: String, impl: String, it: String): String {
        return "$code $data $reply $flags $remote $impl $it"
    }

    override suspend fun suspendEchoNames(
        code: Int, data: String, reply: String, flags: Int, remote: String, impl: String, it: String,
    ) = echoNames(code, data, reply, flags, remote, impl, it)

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

    override suspend fun suspendBytesAtTheGate(size: Int): ByteArray {
        atTheGate.incrementAndGet()
        gate.await()

        return pattern(size)
    }

    override fun atTheGate() = atTheGate.get()

    override fun openTheGate() {
        gate.complete(Unit)
    }

    override suspend fun suspendBundle(bytes: Int, binder: Boolean) = Bundle().apply {
        putByteArray("bytes", pattern(bytes))

        if (binder) putBinder("binder", Binder())
    }

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

    override fun callHang(peer: IExchange) {
        CoroutineScope(Dispatchers.IO).launch { peer.suspendHang() }
    }

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
