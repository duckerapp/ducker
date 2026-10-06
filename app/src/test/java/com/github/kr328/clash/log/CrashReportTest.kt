package com.github.kr328.clash.log

import com.github.kr328.clash.service.CrashRecorder
import com.github.kr328.clash.service.FlightRecorder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportTest {
    @get:Rule val folder = TemporaryFolder()
    private val hwid = "synthetic-hwid-12345"
    private val servers = LogScrubber.serversRegex(setOf("node.example.test"))
    private fun scrub(text: String) = LogScrubber.scrub(text, listOf(hwid), servers)
    private fun file(path: String, text: String): File = folder.root.resolve(path).apply {
        requireNotNull(parentFile).mkdirs()
        writeText(text)
    }

    @Test
    fun `all process crashes stderr and system text use the same scrubber`() {
        for (process in CrashRecorder.PROCESSES) {
            for (name in listOf(FlightRecorder.CURRENT, FlightRecorder.PREVIOUS)) {
                file("${CrashRecorder.DIR}/$process/$name", "FATAL $process node.example.test $hwid\n")
            }
        }
        file("clash/stderr.log", "panic: {\"password\": \"synthetic password\"}\nframe.go:12\n")
        val report = CrashReport.capture(folder.root, "fetch https://panel.test?key=synthetic-token", ::scrub)
        assertTrue("frame.go:12" in report)
        assertTrue("System crash log" in report)
        for (process in CrashRecorder.PROCESSES) assertTrue("Kotlin crash ($process)" in report)
        for (privateValue in listOf("node.example.test", hwid, "synthetic password", "synthetic-token")) {
            assertFalse(privateValue in report, privateValue)
        }
    }

    @Test
    fun `missing diagnostics produce an empty report`() {
        assertEquals("", CrashReport.capture(folder.root, "", ::scrub))
    }

    @Test
    fun `tail omits a partial line and does not split UTF8`() {
        val source = file("tail", "Секрет".repeat(20) + "\nframe.go:12\n")
        val tail = CrashReport.readTail(source, 20)
        assertTrue("frame.go:12" in tail)
        assertFalse("Секрет" in tail)
        assertFalse('\uFFFD' in tail)
        assertTrue("omitted" in tail)
    }

    @Test
    fun `single oversized line is omitted completely`() {
        val source = file("tail", "password: " + "synthetic-secret".repeat(100))
        val tail = CrashReport.readTail(source, 20)
        assertEquals("[earlier diagnostic bytes omitted]\n", tail)
    }

    @Test
    fun `small Unicode diagnostic is preserved before export scrub`() {
        val text = "Ошибка\nframe.go:12\n"
        assertEquals(text, CrashReport.readTail(file("tail", text), 100))
    }

    @Test
    fun `unreadable diagnostic never prints exception contents`() {
        folder.root.resolve("clash/stderr.log").mkdirs()
        val report = CrashReport.stored(folder.root, ::scrub)
        assertTrue("diagnostic file unavailable" in report)
        assertFalse(folder.root.absolutePath in report)
    }

    @Test
    fun `scrubbing failure propagates for the screen to show a static safe error`() {
        file("clash/stderr.log", "synthetic secret")
        assertFailsWith<IllegalStateException> {
            CrashReport.capture(folder.root, "raw secret", { error("scrubber failed") })
        }
    }

    @Test
    fun `large report remains bounded and retains core stderr`() {
        file("clash/stderr.log", "panic: synthetic-core-failure\n")
        for (process in CrashRecorder.PROCESSES) {
            for (name in listOf(FlightRecorder.CURRENT, FlightRecorder.PREVIOUS)) {
                file("${CrashRecorder.DIR}/$process/$name", ("x".repeat(200) + "\n").repeat(400))
            }
        }
        val report = CrashReport.capture(folder.root, "y".repeat(100_000) + "\nlast frame\n", ::scrub)
        assertTrue(report.length < 270_000)
        assertTrue("synthetic-core-failure" in report)
        assertTrue("last frame" in report)
    }

    @Test
    fun `multiline quoted secret is scrubbed before splitting diagnostic lines`() {
        file("clash/stderr.log", "panic: password: \"synthetic-first\nsynthetic-second\"\nframe.go:12\n")
        val report = CrashReport.capture(folder.root, "", ::scrub)
        assertFalse("synthetic-first" in report)
        assertFalse("synthetic-second" in report)
        assertTrue("frame.go:12" in report)
    }

    @Test
    fun `truncated quoted secret never reaches the exported report`() {
        file("clash/stderr.log", "panic: {\"password\": \"synthetic secret without closing quote…\n")
        val report = CrashReport.capture(folder.root, "", ::scrub)
        assertFalse("synthetic secret" in report)
    }

    @Test
    fun `YAML block secret does not leak its continuation lines`() {
        file("clash/stderr.log", "panic: config\npassword: |-\n  synthetic-first\n  synthetic-second\nport: 443\nframe.go:12\n")
        val report = CrashReport.capture(folder.root, "", ::scrub)
        assertFalse("synthetic-first" in report)
        assertFalse("synthetic-second" in report)
        assertTrue("port: 443" in report)
        assertTrue("frame.go:12" in report)
    }

    @Test
    fun `redaction keeps field context beyond raw tail bounds`() {
        val value = "synthetic-secret\n".repeat(10_000)
        file("clash/stderr.log", "password: \"$value\"\nframe.go:12\n")
        val report = CrashReport.capture(folder.root, "secret: \"$value\"\nlast frame\n", ::scrub)
        assertFalse("synthetic-secret" in report)
        assertTrue("frame.go:12" in report)
        assertTrue("last frame" in report)
    }

    @Test
    fun `oversized raw source is omitted safely instead of exposing a tail without context`() {
        file("clash/stderr.log", "password: |\n" + "  synthetic-secret\n".repeat(70_000))
        val report = CrashReport.stored(folder.root, ::scrub)
        assertFalse("synthetic-secret" in report)
        assertTrue("diagnostic file unavailable" in report)
    }
}
