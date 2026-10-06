package com.github.kr328.clash.service

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Ротация по записанным байтам UTF-8; даже одна длинная строка укладывается в лимит. */
internal class FlightLogWriter(private val dir: File, private val limit: Long) : Closeable {
    private var output: FileOutputStream? = null
    private var size = dir.resolve(FlightRecorder.CURRENT).length()

    init { require(limit in 4..Int.MAX_VALUE.toLong()) }

    @Synchronized
    fun append(line: String) {
        val bytes = boundedLine(line)
        val current = dir.resolve(FlightRecorder.CURRENT)
        if (size + bytes.size > limit) {
            close()
            if (!current.renameTo(dir.resolve(FlightRecorder.PREVIOUS))) {
                throw IOException("Cannot rotate flight log")
            }
            size = 0
        }

        val out = output ?: FileOutputStream(current, true).also { output = it }
        out.write(bytes)
        out.flush()
        size += bytes.size
    }

    private fun boundedLine(line: String): ByteArray {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= limit) return bytes

        val suffix = "…\n".toByteArray(Charsets.UTF_8)
        var end = limit.toInt() - suffix.size
        while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
        return bytes.copyOf(end) + suffix
    }

    @Synchronized
    override fun close() {
        output?.close()
        output = null
    }
}
