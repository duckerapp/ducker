package com.github.kr328.clash.ui.subscriptions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Передача подписки на телевизор (D-162, D-163): код из QR, шифр и приём через настоящие сокеты. */
class TvTransferTest {
    private val key = ByteArray(32) { it.toByte() }
    private val link = "https://panel.example/api/sub/AbC-123_x?format=mihomo#Имя"
    private val loopback = InetAddress.getLoopbackAddress()

    @Test
    fun `код телевизора разбирается, остальное — обычная ссылка`() {
        val code = TvLink("192.168.1.20", 40123, "s3ss10n_", key).toString()
        val parsed = TvLink.parse(code)!!

        assertEquals("192.168.1.20", parsed.host)
        assertEquals(40123, parsed.port)
        assertEquals("s3ss10n_", parsed.session)
        assertContentEquals(key, parsed.key)

        assertNull(TvLink.parse("https://panel.example/sub/abc"))
        // Не домашняя сеть, имя вместо числа, не адрес, короткий ключ, хвост после ключа.
        assertNull(TvLink.parse(code.replace("192.168.1.20", "8.8.8.8")))
        assertNull(TvLink.parse(code.replace("192.168.1.20", "tv.example")))
        assertNull(TvLink.parse(code.replace("192.168.1.20", "192.168.1.999")))
        assertNull(TvLink.parse(code.dropLast(1)))
        assertNull(TvLink.parse("$code\nhttps://evil.example"))
    }

    @Test
    fun `чужой ключ и испорченное сообщение не расшифровываются`() {
        val sealed = seal(key, link)

        assertEquals(link, unseal(key, sealed))
        assertNull(unseal(ByteArray(32), sealed))
        assertNull(unseal(key, sealed.copyOf(10)))

        // Любой изменённый байт — номера, шифра или метки.
        for (i in sealed.indices) assertNull(unseal(key, sealed.copyOf().also { it[i] = (it[i] + 1).toByte() }))
    }

    @Test
    fun `телевизор берёт только ссылку подписки`() {
        assertEquals(link, subscriptionLink(link))

        for (bad in listOf("", "https://", "vless://uuid@host:443", "file:///data/config.yaml", "javascript:alert(1)",
            "https://a.example/x\nproxies: []", "https://a.example/ x", "https://a.example/x\u0000")) {
            assertNull(subscriptionLink(bad), bad)
        }
    }

    @Test
    fun `подписка доходит, и сеанс после неё закрыт`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()
        val tv = async { receive(loopback) { shown.complete(it) } }
        val to = shown.await()

        assertTrue(send(to, link))
        assertEquals(InstallLink(link), tv.await())
        // Повтор того же сообщения: порт уже закрыт.
        assertFailsWith<IOException> { send(to, link) }

        Unit
    }

    @Test
    fun `the link arrives with its mirrors and its key`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()
        val tv = async { receive(loopback) { shown.complete(it) } }
        val key = "AGE-SECRET-KEY-1" + "QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L".repeat(2).take(58)
        val given = InstallLink(link, listOf("sub2.example.com:8443", "https://other.example/x?t=a+b&c"), key)

        assertTrue(send(shown.await(), given.toString()))
        assertEquals(given, tv.await())

        Unit
    }

    @Test
    fun `чужое отклоняется, сеанс живёт, пятое чужое его закрывает`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()
        val tv = async { receive(loopback) { shown.complete(it) } }
        val to = shown.await()

        // Чужой ключ, чужой сеанс, свой ключ, но внутри не ссылка подписки.
        assertFalse(send(TvLink(to.host, to.port, to.session, ByteArray(32)), link))
        assertFalse(send(TvLink(to.host, to.port, "other", to.key), link))
        assertFalse(send(to, "ducker://install-config?url=file%3A%2F%2F%2Fdata&mirror=sub2.example.com"))
        // A message with no length, and one over the limit.
        assertEquals("HTTP/1.1 403 Forbidden", raw(to, "POST /d/${to.session} HTTP/1.1\r\n\r\n"))
        assertFalse(tv.isCompleted)
        assertEquals("HTTP/1.1 403 Forbidden", raw(to, "POST /d/${to.session} HTTP/1.1\r\nContent-Length: ${MAX_MESSAGE + 1}\r\n\r\n"))

        assertNull(tv.await())
        assertFailsWith<IOException> { send(to, link) }

        Unit
    }

    @Test
    fun `a camera gets the page, and no request but a message counts as a strike`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()
        val page = "<!doctype html><p>форма</p>".toByteArray()
        val tv = async { receive(loopback, page) { shown.complete(it) } }
        val to = shown.await()

        val reply = Socket(to.host, to.port).use { socket ->
            socket.getOutputStream().write("GET /d/${to.session} HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray())
            socket.getInputStream().readBytes()
        }
        assertTrue(String(reply).startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(String(reply).contains("Content-Type: text/html; charset=utf-8\r\n"))
        assertTrue(reply.takeLast(page.size).toByteArray().contentEquals(page))

        // A browser's spare requests and connections: none of them counts, and none holds the session up.
        val silent = List(STRIKES + 1) { Socket(to.host, to.port) }
        repeat(STRIKES + 1) {
            assertEquals("HTTP/1.1 404 Not Found", raw(to, "GET /favicon.ico HTTP/1.1\r\n\r\n"))
            Socket(to.host, to.port).close()
        }
        assertTrue(send(to, link))
        assertEquals(InstallLink(link), tv.await())
        // The closed session leaves nothing open: the TV has hung up on the silent ones too.
        silent.forEach { assertEquals(-1, it.getInputStream().read()) }
        silent.forEach(Socket::close)

        Unit
    }

    @Test
    fun `the sheet closed — the port and every connection close with it`() = runBlocking {
        // Repeated: a connection whose coroutine was cancelled before it started stayed open in most runs. Outside
        // this scope and with read timeouts, so such a leak fails the test instead of hanging it.
        repeat(20) {
            val shown = CompletableDeferred<TvLink>()
            val tv = CoroutineScope(Dispatchers.Default).launch { receive(loopback) { shown.complete(it) } }
            val to = shown.await()
            val silent = Socket(to.host, to.port).apply { soTimeout = 2000 }

            withTimeout(2000) { tv.cancelAndJoin() }
            assertFailsWith<IOException> { send(to, link) }
            // Closed, or reset when the session ended before accepting it; a timeout means it was left open.
            val end = runCatching { silent.getInputStream().read() }
            assertTrue(end.getOrNull() == -1 || end.exceptionOrNull() is SocketException, "$end")
            silent.close()
        }
    }

    @Test
    fun `время вышло — порт закрыт без подписки`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()

        assertNull(receive(loopback, lifetime = 50) { shown.complete(it) })
        assertFailsWith<IOException> { send(shown.await(), link) }

        Unit
    }

    private fun raw(to: TvLink, request: String): String = Socket(to.host, to.port).use { socket ->
        socket.getOutputStream().write(request.toByteArray())

        socket.getInputStream().bufferedReader().readLine().orEmpty()
    }
}
