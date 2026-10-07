package com.github.kr328.clash.update

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Обновление приложения: какой выпуск предложить и как скачивается его файл. */
class UpdateTest {
    private val arm = Apk("aa", 10, listOf("https://one.example/arm64.apk"))
    private val old = Apk("bb", 10, listOf("https://one.example/v7.apk"))

    private fun entry(code: Long, minSdk: Int = 29, apk: Map<String, Apk> = mapOf("arm64-v8a" to arm, "armeabi-v7a" to old)) =
        Entry("0.0.$code", code, minSdk, mapOf("en" to "notes"), apk)

    private fun Feed.pick(beta: Boolean = false, installed: Long = 36, sdk: Int = 35) =
        pick(beta, installed, sdk, listOf("arm64-v8a", "armeabi-v7a"))

    @Test
    fun `бета приходит только тем, кто её включил`() {
        val feed = Feed(stable = entry(37), beta = entry(38), mirrors = emptyList())

        assertEquals(37, feed.pick()?.code)
        assertEquals(38, feed.pick(beta = true)?.code)
        // Выпуск перевели всем: записи совпали, бета-канал получает его же.
        assertEquals(38, Feed(entry(38), entry(38), emptyList()).pick(beta = true)?.code)
        // Бета отстала от выпуска для всех — её не предлагаем и включившим.
        assertEquals(39, Feed(entry(39), entry(38), emptyList()).pick(beta = true)?.code)
    }

    @Test
    fun `не новее установленного — предлагать нечего`() {
        val feed = Feed(stable = entry(36), beta = entry(37), mirrors = emptyList())

        assertNull(feed.pick())
        assertNull(feed.pick(beta = true, installed = 37))
        assertNull(Feed(null, null, emptyList()).pick(beta = true))
    }

    @Test
    fun `выпуск, который на телефон не встанет, не предлагаем`() {
        // Новый требует Android новее — остаётся прежний, если он всё ещё новее установленного.
        assertEquals(37, Feed(entry(37), entry(38, minSdk = 31), emptyList()).pick(beta = true, sdk = 29)?.code)
        assertNull(Feed(entry(38, minSdk = 31), null, emptyList()).pick(sdk = 29))
        // Файла под процессор телефона в выпуске нет.
        assertNull(Feed(entry(38, apk = mapOf("x86_64" to arm)), null, emptyList()).pick())
        // Файл берём под первый подходящий ABI телефона.
        assertEquals(old, Feed(entry(38, apk = mapOf("armeabi-v7a" to old)), null, emptyList()).pick()?.apk)
        assertEquals(arm, Feed(entry(38), null, emptyList()).pick()?.apk)
    }

    // Скачивание — через настоящий сервер на петлевом адресе.

    private val body = ByteArray(300_000) { (it * 31 + it / 7).toByte() }
    private val ranges = mutableListOf<String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        // Отдаёт файл и умеет продолжать с места.
        createContext("/good") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0

            ranges += range
            exchange.sendResponseHeaders(if (range == null) 200 else 206, (body.size - from).toLong())
            exchange.responseBody.use { it.write(body, from, body.size - from) }
        }
        // Отдаёт файл той же длины, но другой.
        createContext("/other") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(ByteArray(body.size)) }
        }
        createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        start()
    }
    private val file = File.createTempFile("update", ".apk").apply { delete() }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun apk(vararg paths: String) = Apk(
        sha256 = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) },
        size = body.size.toLong(),
        urls = paths.map(::url),
    )

    @AfterTest
    fun stop() {
        server.stop(0)
        file.delete()
    }

    @Test
    fun `первый адрес не ответил — файл приходит со второго`() {
        var last = 0f

        download(apk("/missing", "/good"), file) { last = it }

        assertContentEquals(body, file.readBytes())
        assertEquals(1f, last)
    }

    @Test
    fun `скачанная часть не качается заново`() {
        file.writeBytes(body.copyOf(100_000))

        download(apk("/good"), file) { }

        assertContentEquals(body, file.readBytes())
        assertEquals(listOf<String?>("bytes=100000-"), ranges)
    }

    @Test
    fun `чужой файл не остаётся на диске, а следующий адрес отдаёт настоящий`() {
        download(apk("/other", "/good"), file) { }

        assertContentEquals(body, file.readBytes())
        // После чужого файла — с начала, а не «продолжением» с его конца.
        assertEquals(listOf<String?>(null), ranges)

        file.delete()

        assertFailsWith<IOException> { download(apk("/other"), file) { } }
        assertFalse(file.exists())
    }
}
