package com.github.kr328.clash.ui.subscriptions

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
 * Телевизор ждёт подписку: слушает [address] на случайном порту, отдаёт код для QR в [onLink] и
 * возвращает принятую ссылку подписки с её зеркалами. `null` — сеанс закрыт без неё: вышло время или пришло
 * [STRIKES] чужих сообщений. Сеанс одноразовый: первая принятая передача его закрывает.
 */
internal suspend fun receive(
    address: InetAddress,
    lifetime: Long = LIFETIME_MS,
    onLink: (TvLink) -> Unit,
): InstallLink? = withContext(Dispatchers.IO) {
    val random = SecureRandom()
    val key = ByteArray(KEY).also(random::nextBytes)
    val session = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(SESSION).also(random::nextBytes))

    try {
        ServerSocket(0, BACKLOG, address).use { server ->
            val guard = closeAfter(server, lifetime)

            try {
                onLink(TvLink(address.hostAddress.orEmpty(), server.localPort, session, key))

                // ponytail: запросы по одному, и отказом считается любой, кроме принятого. Телефону с
                // ducker хватает; браузер (2б) открывает соединения про запас — тогда запрос в
                // своей корутине, а молчащее соединение не в счёт.
                repeat(STRIKES) {
                    server.accept().use { socket ->
                        val watch = closeAfter(socket, TIMEOUT_MS.toLong())

                        try {
                            answer(socket, session, key)
                        } finally {
                            watch.cancel()
                        }
                    }?.let { link -> return@withContext link }
                }

                null
            } finally {
                guard.cancel()
            }
        }
    } catch (e: IOException) {
        null
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

/**
 * Один запрос: ссылка подписки — и «принято» в ответ, либо `null` — и отказ. Читает не больше
 * [MAX_HEAD] и [MAX_MESSAGE] и ничего из присланного не исполняет. Отказ один на всё: чужому
 * незачем знать, что именно не сошлось.
 */
private fun answer(socket: Socket, session: String, key: ByteArray): InstallLink? {
    val link = runCatching {
        val input = socket.getInputStream()
        val head = head(input).lines()
        val length = head.firstNotNullOfOrNull { LENGTH.matchEntire(it)?.groupValues?.get(1)?.toInt() }
            ?.takeIf { it in NONCE + 1..MAX_MESSAGE } ?: return@runCatching null

        // Сообщение дочитываем и перед отказом: закрытие с непрочитанным обрывает соединение, и
        // телефон с устаревшим кодом увидел бы «нет связи» вместо отказа.
        val message = ByteArray(length).also { DataInputStream(input).readFully(it) }

        if (head.first() != "POST /d/$session HTTP/1.1") return@runCatching null

        unseal(key, message)?.let(InstallLink::parse)
    }.getOrNull()

    runCatching {
        val status = if (link != null) "200 OK" else "403 Forbidden"

        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    return link
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
private const val MAX_HEAD = 2048
internal const val MAX_MESSAGE = 16 * 1024
private const val BACKLOG = 4
