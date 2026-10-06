package com.github.kr328.clash.ui.config

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

internal class ConfigFileTooLargeException(message: String = "File is too large (maximum 8 MiB)") : IOException(message)

/** Ограничиваем фактически прочитанное тело, независимо от метаданных провайдера. */
internal fun readConfigText(input: InputStream): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < MAX_CONFIG_FILE_BYTES) {
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_CONFIG_FILE_BYTES - out.size()))
        if (count < 0) return out.toString(Charsets.UTF_8.name())
        if (count == 0) {
            val byte = input.read()
            if (byte < 0) return out.toString(Charsets.UTF_8.name())
            out.write(byte)
        } else out.write(buffer, 0, count)
    }
    // Ровно лимит допустим, ещё один байт означает ошибку, а не усечение.
    if (input.read() >= 0) throw ConfigFileTooLargeException()
    return out.toString(Charsets.UTF_8.name())
}

internal const val MAX_CONFIG_FILE_BYTES = 8 * 1024 * 1024
