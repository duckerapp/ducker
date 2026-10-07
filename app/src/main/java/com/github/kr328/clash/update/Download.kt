package com.github.kr328.clash.update

import com.github.kr328.clash.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Скачивает файл выпуска в [file] и сверяет SHA-256.
 *
 * Адреса пробует по порядку: не ответил первый — следующий. Скачанная часть не выбрасывается:
 * файл весит десятки мегабайт и при включённом VPN идёт через узел, из трафика подписки, —
 * повтор и следующий адрес продолжают с того же места. Бросает [IOException], когда не вышло
 * ни с одним адресом.
 */
internal fun download(apk: Apk, file: File, progress: (Float) -> Unit) {
    var failure = IOException("no addresses")

    for (url in apk.urls) {
        try {
            if (file.length() < apk.size) fetch(url, file, apk.size, progress)

            // Связь оборвалась на середине: часть остаётся следующему адресу или повтору.
            if (file.length() < apk.size) throw IOException("incomplete: $url")

            if (sha256(file) == apk.sha256) return

            file.delete()

            failure = IOException("checksum mismatch: $url")
        } catch (e: IOException) {
            failure = e
        }
    }

    throw failure
}

private fun fetch(url: String, file: File, size: Long, progress: (Float) -> Unit) {
    val have = file.length()
    val connection = URL(url).openConnection() as HttpURLConnection

    connection.connectTimeout = CONNECT_MS
    connection.readTimeout = READ_MS
    connection.setRequestProperty("User-Agent", AGENT)
    // Без сжатия на лету: иначе место продолжения считалось бы по другим байтам.
    connection.setRequestProperty("Accept-Encoding", "identity")
    if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")

    try {
        // Сервер вправе не уметь продолжать и отдать файл с начала.
        val resumed = when (connection.responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> true
            HttpURLConnection.HTTP_OK -> false
            else -> throw IOException("HTTP ${connection.responseCode}: $url")
        }
        var done = if (resumed) have else 0

        connection.inputStream.use { input ->
            FileOutputStream(file, resumed).use { output ->
                val buffer = ByteArray(64 * 1024)

                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break

                    done += read
                    // Больше обещанного — не наш файл: не даём забить память телефона.
                    if (done > size) throw IOException("longer than announced: $url")

                    output.write(buffer, 0, read)
                    progress(done.toFloat() / size)
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")

    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break

            digest.update(buffer, 0, read)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Кем приложение называется серверу обновлений. Свой, а не стандартный: тот несёт модель
 * телефона и номер сборки Android, а серверу достаточно версии приложения.
 */
internal const val AGENT = "ducker/" + BuildConfig.VERSION_NAME

private const val CONNECT_MS = 15_000
private const val READ_MS = 30_000
