package com.github.kr328.clash.ui.subscriptions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
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
    fun `the link arrives with its mirrors`() = runBlocking {
        val shown = CompletableDeferred<TvLink>()
        val tv = async { receive(loopback) { shown.complete(it) } }
        val given = InstallLink(link, listOf("sub2.example.com:8443", "https://other.example/x?t=a+b&c"))

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
        // Не тот запрос и сообщение больше разрешённого.
        assertEquals("HTTP/1.1 403 Forbidden", raw(to, "GET /d/${to.session} HTTP/1.1\r\n\r\n"))
        assertFalse(tv.isCompleted)
        assertEquals("HTTP/1.1 403 Forbidden", raw(to, "POST /d/${to.session} HTTP/1.1\r\nContent-Length: ${MAX_MESSAGE + 1}\r\n\r\n"))

        assertNull(tv.await())
        assertFailsWith<IOException> { send(to, link) }

        Unit
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
