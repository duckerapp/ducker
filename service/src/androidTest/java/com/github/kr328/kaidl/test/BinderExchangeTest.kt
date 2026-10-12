package com.github.kr328.kaidl.test

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.SparseBooleanArray
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The generated Binder glue and its runtime between two real processes (D-268). The tests run in
 * the main process of the test package and stand for the UI; [ExchangeService] lives in its
 * `:exchange` process and stands for the service.
 *
 * Needs a device, so it is run by hand, on one named emulator:
 *
 *     ./gradlew :service:assembleMetaDebugAndroidTest -Pabi=x86_64
 *     adb -s emulator-5556 install -r service/build/outputs/apk/androidTest/meta/debug/service-meta-debug-androidTest.apk
 *     adb -s emulator-5556 shell am instrument -w -e class com.github.kr328.kaidl.test.BinderExchangeTest \
 *         com.github.kr328.clash.service.test/androidx.test.runner.AndroidJUnitRunner
 *
 * [roundTripTimes] prints its figures into the output of `am instrument`.
 */
class BinderExchangeTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val link = Link()
    private lateinit var exchange: IExchange

    @Before
    fun bind() {
        val intent = Intent(context, ExchangeService::class.java)

        assertTrue(context.bindService(intent, link, Context.BIND_AUTO_CREATE))

        exchange = link.await().unwrap(IExchange::class)
    }

    @After
    fun unbind() {
        context.unbindService(link)
    }

    @Test
    fun scalarsAndArrays() {
        exchange.nothing()

        for (v in listOf(0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE)) assertEquals(v, exchange.echoInt(v))
        for (v in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) assertEquals(v, exchange.echoLong(v))

        assertEquals(1.5f, exchange.echoFloat(1.5f))
        assertTrue(exchange.echoFloat(Float.NaN).isNaN())
        assertEquals(-2.25, exchange.echoDouble(-2.25), 0.0)
        assertEquals(Double.MAX_VALUE, exchange.echoDouble(Double.MAX_VALUE), 0.0)
        assertEquals(Byte.MIN_VALUE, exchange.echoByte(Byte.MIN_VALUE))
        assertEquals(true, exchange.echoBoolean(true))
        assertEquals(false, exchange.echoBoolean(false))

        for (v in listOf("", "text", "утка 🦆 <&>\n\u0000", "x".repeat(100_000))) {
            assertEquals(v, exchange.echoString(v))
        }

        assertArrayEquals(byteArrayOf(), exchange.echoByteArray(byteArrayOf()))
        assertArrayEquals(byteArrayOf(0, -1, 127), exchange.echoByteArray(byteArrayOf(0, -1, 127)))
        assertArrayEquals(charArrayOf('a', 'я', '\u0000'), exchange.echoCharArray(charArrayOf('a', 'я', '\u0000')))
        assertArrayEquals(booleanArrayOf(true, false), exchange.echoBooleanArray(booleanArrayOf(true, false)))
        assertArrayEquals(intArrayOf(1, -2, 3), exchange.echoIntArray(intArrayOf(1, -2, 3)))
        assertArrayEquals(longArrayOf(Long.MIN_VALUE, 0), exchange.echoLongArray(longArrayOf(Long.MIN_VALUE, 0)))
        assertArrayEquals(floatArrayOf(0.5f, -1f), exchange.echoFloatArray(floatArrayOf(0.5f, -1f)), 0f)
        assertArrayEquals(doubleArrayOf(0.5, -1.0), exchange.echoDoubleArray(doubleArrayOf(0.5, -1.0)), 0.0)
    }

    @Test
    fun platformTypes() {
        val token = Binder()

        assertSame(token, exchange.echoBinder(token))

        val bundle = exchange.echoBundle(Bundle().apply {
            putString("string", "text")
            putInt("int", 7)
        })

        assertEquals(setOf("string", "int"), bundle.keySet())
        assertEquals("text", bundle.getString("string"))
        assertEquals(7, bundle.getInt("int"))

        val flags = SparseBooleanArray().apply {
            put(1, true)
            put(40, false)
        }

        assertEquals(flags.toString(), exchange.echoSparseBooleanArray(flags).toString())
    }

    @Test
    fun containers() {
        assertEquals(emptyList<String>(), exchange.echoList(emptyList()))
        assertEquals(listOf("a", "", "б"), exchange.echoList(listOf("a", "", "б")))
        assertEquals(listOf(setOf(3L, 1L, 2L), emptySet()), exchange.echoSets(listOf(setOf(3L, 1L, 2L), emptySet())))
        assertEquals(mapOf("a" to 1L, "b" to -1L), exchange.echoMap(mapOf("a" to 1L, "b" to -1L)))
        assertEquals(emptyMap<String, Long>(), exchange.echoMap(emptyMap()))

        val nested = mapOf("a" to listOf(setOf(1L, 2L), emptySet()), "b" to emptyList())

        assertEquals(nested, exchange.echoNested(nested))

        // The order of a list and of a map's entries survives.
        val order = (1..500).map { "name $it" }.shuffled()

        assertEquals(order, exchange.echoList(order))
        assertEquals(order, exchange.echoMap(order.associateWith { it.length.toLong() }).keys.toList())
    }

    @Test
    fun modelsEnumsAndUuid() {
        assertEquals(model(1), exchange.echoModel(model(1)))
        assertEquals(models(300), exchange.echoModels(models(300)))

        for (v in Level.values()) assertEquals(v, exchange.echoEnum(v))
        for (v in Model.Kind.values()) assertEquals(v, exchange.echoNestedEnum(v))

        val uuid = UUID.randomUUID()

        assertEquals(uuid, exchange.echoUuid(uuid))
        assertEquals(UUID(0, 0), exchange.echoUuid(UUID(0, 0)))

        assertEquals("2 name model 1 [Info, Debug]", exchange.describe(2, "name", model(1), listOf(Level.Info, Level.Debug)))
        assertEquals("0  null []", exchange.describe(0, "", null, emptyList()))
    }

    @Test
    fun nullables() {
        assertNull(exchange.echoNullableInt(null))
        assertEquals(0, exchange.echoNullableInt(0))
        assertNull(exchange.echoNullableString(null))
        assertEquals("", exchange.echoNullableString(""))
        assertNull(exchange.echoNullableModel(null))
        assertEquals(model(2), exchange.echoNullableModel(model(2)))
        assertNull(exchange.echoNullableEnum(null))
        assertEquals(Level.Debug, exchange.echoNullableEnum(Level.Debug))
        assertNull(exchange.echoNullableUuid(null))
        assertEquals(UUID(1, 2), exchange.echoNullableUuid(UUID(1, 2)))
        assertNull(exchange.echoNullableMap(null))
        assertEquals(mapOf("a" to null, "b" to 0L), exchange.echoNullableMap(mapOf("a" to null, "b" to 0L)))
    }

    @Test
    fun interfacesGoBothWays() = runBlocking<Unit> {
        val events = LinkedBlockingQueue<String>()
        val observer = IObserver { events.put(it) }

        exchange.tell(observer, "one")

        assertEquals("one", events.poll(TIMEOUT, TimeUnit.MILLISECONDS))

        exchange.tellAll(listOf(observer, IObserver { events.put("second: $it") }), "two")

        assertEquals("two", events.poll(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals("second: two", events.poll(TIMEOUT, TimeUnit.MILLISECONDS))

        exchange.tellAll(emptyList(), "nobody")

        assertEquals("peer", exchange.peer("peer").name())
        assertEquals("suspend peer", exchange.suspendPeer("suspend peer").name())

        // Default arguments are taken from the interface, on the caller's side.
        assertFalse(exchange.suspendTell())
        assertTrue(exchange.suspendTell("three", observer))
        assertEquals("three", events.poll(TIMEOUT, TimeUnit.MILLISECONDS))
        assertNull(events.poll())
    }

    @Test
    fun suspendCommands() = runBlocking<Unit> {
        exchange.suspendNothing()

        assertEquals(Int.MIN_VALUE, exchange.suspendEchoInt(Int.MIN_VALUE))
        assertEquals("утка 🦆", exchange.suspendEchoString("утка 🦆"))
        assertEquals(models(300), exchange.suspendEchoModels(models(300)))
        assertEquals(emptyList<Model>(), exchange.suspendEchoModels(emptyList()))
        assertNull(exchange.suspendEchoNullableModel(null))
        assertEquals(model(3), exchange.suspendEchoNullableModel(model(3)))
        assertEquals(UUID(3, 4), exchange.suspendEchoUuid(UUID(3, 4)))

        // From another dispatcher as well: the UI calls from the main thread, workers from IO.
        assertEquals(5, withTimeout(TIMEOUT) { async(Dispatchers.IO) { exchange.suspendEchoInt(5) }.await() })
    }

    @Test
    fun withinOneProcessThereIsNoProxy() {
        val binder = Exchange().wrap()

        assertSame(binder, binder.unwrap(IExchange::class))
        assertSame(binder, binder.unwrap(IExchange::class).wrap())

        assertEquals(IExchangeProxy::class.java, exchange.javaClass)
    }

    @Test
    fun anOrdinaryCommandThrowsWhatAndroidCarries() {
        assertEquals(IllegalStateException::class.java, failureOf { exchange.fail(Failure.IllegalState) })
        assertEquals(IllegalArgumentException::class.java, failureOf { exchange.fail(Failure.IllegalArgumentSubclass) })
        assertEquals(SecurityException::class.java, failureOf { exchange.fail(Failure.Security) })
        assertEquals(NullPointerException::class.java, failureOf { exchange.fail(Failure.NullPointer) })
        assertEquals(UnsupportedOperationException::class.java, failureOf { exchange.fail(Failure.UnsupportedOperation) })

        // The service is still there for the next caller.
        assertEquals(7, exchange.echoInt(7))
    }

    // Fails today: the caller gets an empty answer instead (D-268, F-264).
    @Test
    fun anOrdinaryCommandThrowsAnythingElseAsIllegalArgument() {
        assertEquals(IllegalArgumentException::class.java, failureOf { exchange.fail(Failure.Io) })
        assertEquals(IllegalArgumentException::class.java, failureOf { exchange.fail(Failure.OwnRuntime) })

        assertEquals(7, exchange.echoInt(7))
    }

    @Test
    fun aSuspendCommandThrowsWithTheMessage() = runBlocking<Unit> {
        val carried = listOf(
            Failure.IllegalState, Failure.IllegalArgumentSubclass, Failure.Security,
            Failure.NullPointer, Failure.UnsupportedOperation,
        )

        // Callers recognise failures by their text; the type is checked only where it is settled.
        for (failure in carried) failureOf { exchange.suspendFail(failure) }

        assertEquals(IllegalArgumentException::class.java, failureOf { exchange.suspendFail(Failure.Io) })
        assertEquals(IllegalArgumentException::class.java, failureOf { exchange.suspendFail(Failure.OwnRuntime) })

        assertEquals(7, exchange.suspendEchoInt(7))
    }

    @Test
    fun cancellingTheCallerCancelsTheService() = runBlocking<Unit> {
        val (started, cancelled) = exchange.hangs()
        val call = launch(Dispatchers.IO) { exchange.suspendHang() }

        await("the command started") { exchange.hangs()[0] == started + 1 }

        withTimeout(TIMEOUT) { call.cancelAndJoin() }

        await("the service saw the cancellation") { exchange.hangs()[1] == cancelled + 1 }
    }

    @Test
    fun aCancellationInTheServiceCancelsTheCaller() = runBlocking<Unit> {
        val call = async(Dispatchers.IO) { exchange.suspendFail(Failure.Cancellation) }

        withTimeout(TIMEOUT) { call.join() }

        assertTrue(call.isCancelled)
    }

    @Test
    fun theDeathOfTheServiceEndsASuspendCall() = runBlocking<Unit> {
        val started = exchange.hangs()[0]
        val call = async(Dispatchers.IO) { runCatching { exchange.suspendHang() }.exceptionOrNull() }

        await("the command started") { exchange.hangs()[0] == started + 1 }

        Process.killProcess(exchange.pid())

        val failure = withTimeoutOrNull(TIMEOUT) { call.await() }

        assertEquals(DeadObjectException::class.java, failure?.javaClass)
    }

    @Test
    fun theDeathOfTheServiceEndsAnOrdinaryCall() {
        val pid = exchange.pid()
        val failure = AtomicReference<Throwable?>()
        val caller = thread { failure.set(runCatching { exchange.sleep(60_000) }.exceptionOrNull()) }

        // Long enough for the call to be inside the service; a kill before that fails the same way.
        Thread.sleep(300)
        Process.killProcess(pid)
        caller.join(TIMEOUT)

        assertFalse("The call is still waiting", caller.isAlive)
        assertEquals(DeadObjectException::class.java, failure.get()?.javaClass)
    }

    @Test
    fun aThousandConcurrentCalls() = runBlocking<Unit> {
        // The delay keeps all of them waiting in the service at the same time.
        val results = withTimeout(60_000) {
            (0 until 1000).map { async(Dispatchers.IO) { exchange.suspendEchoAfter(it, 500) } }.awaitAll()
        }

        assertEquals((0 until 1000).toList(), results)
    }

    @Test
    fun anOrdinaryReplyOf600Kilobytes() {
        assertArrayEquals(Exchange.pattern(600 * 1024), exchange.bytes(600 * 1024))
    }

    // The three below fail today: a reply over half of the Binder buffer never arrives (D-268, F-263).
    @Test
    fun aSuspendReplyOf600Kilobytes() = suspendReplyOf(600 * 1024)

    @Test
    fun aSuspendReplyOf2Megabytes() = suspendReplyOf(2 * 1024 * 1024)

    @Test
    fun aSuspendReplyOf8Megabytes() = suspendReplyOf(8 * 1024 * 1024)

    @Test
    fun roundTripTimes() = runBlocking<Unit> {
        val models = models(100)

        report("ordinary, Int", measure { exchange.echoInt(it) })
        report("suspend, Int", measure { exchange.suspendEchoInt(it) })
        report("ordinary, 100 models", measure { exchange.echoModels(models) })
        report("suspend, 100 models", measure { exchange.suspendEchoModels(models) })
    }

    private fun suspendReplyOf(size: Int) = runBlocking<Unit> {
        val reply = withTimeoutOrNull(REPLY_TIMEOUT) { exchange.suspendBytes(size) }

        assertNotNull("Neither a reply of $size bytes nor an error in $REPLY_TIMEOUT ms", reply)
        assertArrayEquals(Exchange.pattern(size), reply)
    }

    /** The class of what [block] throws; the message must be the service's. */
    private inline fun failureOf(block: () -> Any?): Class<*>? {
        val failure = runCatching(block).exceptionOrNull() ?: return null

        assertEquals(Exchange.MESSAGE, failure.message)

        return failure.javaClass
    }

    private inline fun await(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT

        while (!condition()) {
            assertTrue("Not in $TIMEOUT ms: $what", SystemClock.elapsedRealtime() < deadline)

            Thread.sleep(10)
        }
    }

    /** Sorted durations of [ROUNDS] calls, in nanoseconds. */
    private inline fun measure(call: (Int) -> Unit): LongArray {
        repeat(WARM_UP) { call(it) }

        return LongArray(ROUNDS) {
            val started = System.nanoTime()

            call(it)

            System.nanoTime() - started
        }.apply { sort() }
    }

    private fun report(what: String, sorted: LongArray) {
        val line = "round trip, $what: median %d µs, 90%% under %d µs (%d calls, API %d)".format(
            sorted[sorted.size / 2] / 1000, sorted[sorted.size * 9 / 10] / 1000, sorted.size, Build.VERSION.SDK_INT,
        )

        Log.i("BinderExchangeTest", line)

        // `am instrument` prints this key as it arrives.
        instrumentation.sendStatus(0, Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "\n$line") })
    }

    private fun model(id: Int) = Model(id, "model $id", Model.Kind.values()[id % 3], List(id % 4) { "tag $it" })
    private fun models(count: Int) = List(count, ::model)

    private class Link : ServiceConnection {
        private val binders = LinkedBlockingQueue<IBinder>()

        override fun onServiceConnected(name: ComponentName, service: IBinder) = binders.put(service)
        override fun onServiceDisconnected(name: ComponentName) = Unit

        fun await(): IBinder {
            return binders.poll(TIMEOUT, TimeUnit.MILLISECONDS) ?: throw AssertionError("The exchange service did not start")
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val REPLY_TIMEOUT = 10_000L
        const val WARM_UP = 500
        const val ROUNDS = 2000
    }
}
