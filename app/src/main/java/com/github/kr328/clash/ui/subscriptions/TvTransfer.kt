package com.github.kr328.clash.ui.subscriptions

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.github.kr328.clash.R
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Передача подписки с телефона на телевизор в домашней сети (D-162, D-163).
 *
 * Телевизор открывает порт и показывает QR с адресом, номером сеанса и ключом. Телефон шифрует
 * ссылку подписки этим ключом и отправляет одним запросом; телевизор проверяет, что внутри ссылка,
 * и добавляет её как введённую руками. Ключ и сеанс живут, пока QR на экране, и нигде не хранятся.
 * With the provider's mirrors the message is an `install-config` link carrying them ([InstallLink]).
 *
 * Запрос — HTTP, хотя обе стороны свои: на тот же адрес будет слать и страничка для обычной камеры
 * (пакет 2б). Сокеты простые: `HttpURLConnection` без шифрования система пускает только к
 * `127.0.0.1` (F-196), а сервер из библиотеки ради одного запроса растил бы APK.
 */

/** Что зашито в QR телевизора: `http://адрес:порт/d/сеанс#ключ`. Ключ после `#` в сеть не уходит. */
class TvLink(val host: String, val port: Int, val session: String, val key: ByteArray) {
    override fun toString() = "http://$host:$port/d/$session#${Base64.getUrlEncoder().withoutPadding().encodeToString(key)}"

    companion object {
        private val FORMAT = Regex("""http://(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d{1,5})/d/([\w-]{1,32})#([\w-]{43})""")

        /**
         * Код телевизора или `null` — тогда это обычная ссылка подписки. Адрес — только числом и
         * только из домашних диапазонов: чужой QR не уведёт подписку на сервер в интернете.
         */
        fun parse(text: String): TvLink? {
            val parts = FORMAT.matchEntire(text)?.groupValues ?: return null
            val address = parts.subList(1, 5).map { it.toInt() }
            val port = parts[5].toInt()
            val home = address[0] == 10 || (address[0] == 172 && address[1] in 16..31) || (address[0] == 192 && address[1] == 168)

            if (!home || address.any { it > 255 } || port !in 1..65535) return null

            val key = runCatching { Base64.getUrlDecoder().decode(parts[7]) }.getOrNull() ?: return null

            return TvLink(address.joinToString("."), port, parts[6], key)
        }
    }
}

/** Ссылка подписки, какой её берёт телевизор: одна строка, http(s), с адресом. Иначе `null`. */
internal fun subscriptionLink(text: String): String? = text.takeIf { LINK.matches(it) }

/** AES-256-GCM: случайный номер, за ним шифр с меткой подлинности. */
internal fun seal(key: ByteArray, text: String): ByteArray {
    val nonce = ByteArray(NONCE).also(SecureRandom()::nextBytes)

    return nonce + cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(text.toByteArray())
}

/** `null` — сообщение чужое или испорчено: метка подлинности не сошлась. */
internal fun unseal(key: ByteArray, message: ByteArray): String? = runCatching {
    String(cipher(Cipher.DECRYPT_MODE, key, message.copyOf(NONCE)).doFinal(message, NONCE, message.size - NONCE))
}.getOrNull()

private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray) =
    Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce)) }

/**
 * Телефон отправляет ссылку подписки на телевизор. Принял ли он её; связи нет — [IOException].
 */
internal suspend fun send(to: TvLink, link: String): Boolean = withContext(Dispatchers.IO) {
    val body = seal(to.key, link)

    Socket().use { socket ->
        socket.connect(InetSocketAddress(to.host, to.port), TIMEOUT_MS)
        socket.soTimeout = TIMEOUT_MS

        socket.getOutputStream().apply {
            write("POST /d/${to.session} HTTP/1.1\r\nHost: ${to.host}:${to.port}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body)
            flush()
        }

        socket.getInputStream().bufferedReader().readLine().orEmpty().startsWith("HTTP/1.1 200")
    }
}

/**
 * The TV waits for a subscription: listens on [address] at a random port, hands the QR code to [onLink] and
 * returns the accepted subscription link with its mirrors. `null` — the session closed without one: time ran out or
 * [STRIKES] foreign messages came. The session is single-use: the first accepted transfer closes it. A `GET` of the
 * session's address gets [page], the form an ordinary phone camera opens (D-162, step 2б).
 *
 * Each connection is served on its own: a browser opens spare ones and may leave them silent. Only a rejected
 * message counts as a strike; a silent, broken or foreign request does not, and dies by [TIMEOUT_MS].
 */
internal suspend fun receive(
    address: InetAddress,
    page: ByteArray = ByteArray(0),
    lifetime: Long = LIFETIME_MS,
    onLink: (TvLink) -> Unit,
): InstallLink? = withContext(Dispatchers.IO) {
    val random = SecureRandom()
    val key = ByteArray(KEY).also(random::nextBytes)
    val session = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(SESSION).also(random::nextBytes))

    try {
        ServerSocket(0, BACKLOG, address).use { server ->
            coroutineScope {
                val result = CompletableDeferred<InstallLink?>()
                val served = Session(session, key, page, result)
                val open = Semaphore(PARALLEL)

                onLink(TvLink(address.hostAddress.orEmpty(), server.localPort, session, key))

                launch {
                    delay(lifetime)
                    result.complete(null)
                }
                launch {
                    while (true) {
                        val socket = try {
                            server.accept()
                        } catch (e: IOException) {
                            result.complete(null)
                            break
                        }

                        // Over the limit a connection is dropped unread: no strike, a browser retries.
                        if (!open.tryAcquire()) {
                            socket.close()
                            continue
                        }

                        launch {
                            try {
                                socket.use { served.serve(this, it) }
                            } finally {
                                open.release()
                            }
                        }
                    }
                }

                // Closed however the wait ends, cancellation too: a blocked accept() does not hear it, and the port
                // would stay open after the sheet closed.
                try {
                    result.await()
                } finally {
                    server.close()
                    coroutineContext.cancelChildren()
                }
            }
        }
    } catch (e: IOException) {
        null
    }
}

private class Session(val id: String, val key: ByteArray, val page: ByteArray, val result: CompletableDeferred<InstallLink?>) {
    private val strikes = AtomicInteger()
    private val taken = AtomicBoolean()

    /**
     * One connection. The reply is written before the session closes: closing it cancels this connection, and the
     * phone would see «no connection» for a link the TV took.
     */
    fun serve(scope: CoroutineScope, socket: Socket) {
        val watch = scope.closeAfter(socket, TIMEOUT_MS.toLong())

        try {
            when (val asked = read(socket, id, key)) {
                Asked.Page -> reply(socket, "200 OK", "text/html; charset=utf-8", page)
                is Asked.Link -> if (taken.compareAndSet(false, true)) {
                    reply(socket, "200 OK")
                    result.complete(asked.link)
                } else {
                    reply(socket, "403 Forbidden")
                }
                Asked.Refused -> {
                    reply(socket, "403 Forbidden")
                    if (strikes.incrementAndGet() >= STRIKES) result.complete(null)
                }
                Asked.Other -> reply(socket, "404 Not Found")
                null -> Unit
            }
        } finally {
            watch.cancel()
        }
    }
}

/**
 * Закрывает [what] через [after] мс или по отмене. Блокирующие `accept()` и `read()` отмены не
 * слышат, а таймаут сокета отмеряет каждое чтение отдельно — запрос по байту тянулся бы часами.
 */
private fun CoroutineScope.closeAfter(what: Closeable, after: Long) = launch {
    try {
        delay(after)
    } finally {
        what.close()
    }
}

/** What a request asked for. `null` from [read] — it never finished: silent, broken or not HTTP. */
private sealed interface Asked {
    /** The camera page of this session. */
    object Page : Asked

    /** A message of this session with a subscription link inside. */
    class Link(val link: InstallLink) : Asked

    /** A message that is not this session's or has no link inside: a strike. */
    object Refused : Asked

    /** Anything else: a browser's `favicon.ico`, a wrong address. No strike. */
    object Other : Asked
}

/**
 * Reads one request: at most [MAX_HEAD] and [MAX_MESSAGE], and executes nothing it was sent. One refusal for every
 * failure: a stranger need not know what did not match.
 */
private fun read(socket: Socket, session: String, key: ByteArray): Asked? = runCatching {
    val input = socket.getInputStream()
    val head = head(input).lines()
    val line = head.first()

    if (line == "GET /d/$session HTTP/1.1") return@runCatching Asked.Page
    if (!line.startsWith("POST ")) return@runCatching Asked.Other

    val length = head.firstNotNullOfOrNull { LENGTH.matchEntire(it)?.groupValues?.get(1)?.toInt() }
        ?.takeIf { it in NONCE + 1..MAX_MESSAGE } ?: return@runCatching Asked.Refused

    // Сообщение дочитываем и перед отказом: закрытие с непрочитанным обрывает соединение, и
    // телефон с устаревшим кодом увидел бы «нет связи» вместо отказа.
    val message = ByteArray(length).also { DataInputStream(input).readFully(it) }

    if (line != "POST /d/$session HTTP/1.1") return@runCatching Asked.Refused

    unseal(key, message)?.let(InstallLink::parse)?.let(Asked::Link) ?: Asked.Refused
}.getOrNull()

private fun reply(socket: Socket, status: String, type: String? = null, body: ByteArray = ByteArray(0)) = runCatching {
    val head = StringBuilder("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n")

    // The page sends only to its own address and loads nothing; its key never leaves through a referrer.
    if (type != null) head.append("Content-Type: $type\r\nReferrer-Policy: no-referrer\r\n$PAGE_POLICY\r\n")

    socket.getOutputStream().apply {
        write(head.append("\r\n").toString().toByteArray())
        write(body)
        flush()
    }
}

/** Заголовки запроса до пустой строки; длиннее [MAX_HEAD] — отказ. */
private fun head(input: InputStream): String {
    val bytes = StringBuilder()

    while (!bytes.endsWith("\r\n\r\n")) {
        val byte = input.read()

        if (byte < 0 || bytes.length >= MAX_HEAD) throw IOException()

        bytes.append(byte.toChar())
    }

    return bytes.toString()
}

/**
 * The page an ordinary phone camera opens from the TV's code (D-162, step 2б), in the app's language: [context] is
 * the activity, which carries the language chosen in the app's settings.
 */
internal fun transferPage(context: Context): ByteArray {
    val res = context.resources
    val locale = res.configuration.locales[0]
    val texts = mapOf(
        "lang" to locale.toLanguageTag(),
        "dir" to if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr",
        "title" to res.getString(R.string.tv_page_title),
        "hint" to res.getString(R.string.tv_page_hint),
        "field" to res.getString(R.string.tv_page_field),
        "send" to res.getString(R.string.tv_page_send),
        "sending" to res.getString(R.string.tv_page_sending),
        "sent" to res.getString(R.string.tv_page_sent),
        "not_link" to res.getString(R.string.tv_page_not_link),
        "refused" to res.getString(R.string.tv_page_refused),
        "no_answer" to res.getString(R.string.tv_page_no_answer),
        "no_key" to res.getString(R.string.tv_page_no_key),
    )
    val template = res.openRawResource(R.raw.tv_page).use { String(it.readBytes()) }

    return PLACEHOLDER.replace(template) { texts.getValue(it.groupValues[1]).escapeHtml() }.toByteArray()
}

private fun String.escapeHtml() =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/**
 * Адрес телевизора в домашней сети: Wi-Fi или кабель, мимо VPN — запрос сетей по умолчанию VPN не
 * берёт. `null` — такой сети нет. Сеть сменилась — придёт новый адрес.
 */
internal fun homeAddress(context: Context): Flow<InetAddress?> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val found = LinkedHashMap<Network, InetAddress>()

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            val address = properties.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }

            if (address != null) found[network] = address else found.remove(network)

            trySend(found.values.firstOrNull())
        }

        override fun onLost(network: Network) {
            found.remove(network)

            trySend(found.values.firstOrNull())
        }
    }

    // Сети может не быть вовсе, и тогда система промолчит: начинаем с «нет».
    trySend(null)

    manager.registerNetworkCallback(
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build(),
        callback,
    )

    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

private val LINK = Regex("""https?://[^\s\p{Cc}/?#]+[^\s\p{Cc}]*""", RegexOption.IGNORE_CASE)
private val PLACEHOLDER = Regex("""\{\{(\w+)\}\}""")
private val LENGTH = Regex("""content-length:\s*(\d{1,6})\s*""", RegexOption.IGNORE_CASE)

private const val KEY = 32
private const val SESSION = 6
private const val NONCE = 12
private const val TAG_BITS = 128

/** Сколько телевизор слушает сеть, даже если QR всё ещё на экране. */
private const val LIFETIME_MS = 5 * 60_000L

/** Столько чужих или испорченных сообщений закрывают сеанс. */
internal const val STRIKES = 5

/** И на связь с телевизором, и на один запрос к нему целиком. */
private const val TIMEOUT_MS = 3000
private const val MAX_HEAD = 4096
internal const val MAX_MESSAGE = 16 * 1024

/** Connections served at once: a browser opens up to six to one address. */
private const val PARALLEL = 8

private const val PAGE_POLICY = "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; " +
    "style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'"
private const val BACKLOG = 4
