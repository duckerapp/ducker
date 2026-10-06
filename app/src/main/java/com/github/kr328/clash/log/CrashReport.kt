package com.github.kr328.clash.log

import com.github.kr328.clash.service.CrashRecorder
import com.github.kr328.clash.service.FlightRecorder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Bounded diagnostics, scrubbed before they reach either UI, clipboard or export. */
object CrashReport {
    private const val TAIL_BYTES = 128 * 1024
    private const val STORED_CHARS = 192 * 1024
    private const val SYSTEM_CHARS = 64 * 1024
    private const val SOURCE_BYTES = 1024 * 1024

    fun stored(base: File, scrub: (String) -> String): String = buildString {
        section("Core stderr (last $TAIL_BYTES bytes)", base.resolve("clash/stderr.log"), scrub)
        for (process in CrashRecorder.PROCESSES) {
            for (name in listOf(FlightRecorder.CURRENT, FlightRecorder.PREVIOUS)) {
                section("Kotlin crash ($process)", base.resolve(CrashRecorder.DIR).resolve(process).resolve(name), scrub)
            }
        }
    }.let { text ->
        if (text.length <= STORED_CHARS) text
        else text.take(STORED_CHARS) + "\n[remaining diagnostics omitted]\n"
    }

    fun capture(base: File, systemLog: String, scrub: (String) -> String): String = buildString {
        append(stored(base, scrub))
        if (systemLog.isNotBlank()) {
            appendLine("# System crash log:")
            val clean = scrub(systemLog)
            val tail = if (clean.length <= SYSTEM_CHARS) clean else {
                val start = clean.length - SYSTEM_CHARS
                val newline = clean.indexOf('\n', start)
                "[earlier system log omitted]\n" + if (newline >= 0) clean.substring(newline + 1) else ""
            }
            appendLine(tail)
        }
    }.trim()

    private fun StringBuilder.section(title: String, file: File, scrub: (String) -> String) {
        val text = readTail(file, scrub = scrub)
        if (text.isBlank()) return
        appendLine("# $title:")
        appendLine(text)
    }

    internal fun readTail(file: File, limit: Int = TAIL_BYTES, scrub: (String) -> String = { it }): String {
        require(limit > 0)
        if (!file.exists()) return ""
        val source = try {
            RandomAccessFile(file, "r").use { input ->
                val end = input.length()
                // Raw truncation can lose the field name of a multiline secret.
                // Read a bounded complete source, redact, and only then retain its tail.
                if (end > SOURCE_BYTES) throw IOException("Diagnostic exceeds safe input limit")
                val bytes = ByteArray(end.toInt())
                input.readFully(bytes)
                bytes.toString(Charsets.UTF_8)
            }
        } catch (_: IOException) {
            return "[diagnostic file unavailable]"
        }
        val bytes = scrub(source).toByteArray(Charsets.UTF_8)
        return if (bytes.size <= limit) bytes.toString(Charsets.UTF_8) else {
            val tail = bytes.copyOfRange(bytes.size - limit, bytes.size)
            val newline = tail.indexOf('\n'.code.toByte())
            "[earlier diagnostic bytes omitted]\n" +
                if (newline >= 0) tail.copyOfRange(newline + 1, tail.size).toString(Charsets.UTF_8) else ""
        }
    }
}
