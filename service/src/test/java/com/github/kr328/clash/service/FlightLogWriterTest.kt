package com.github.kr328.clash.service

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlightLogWriterTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun rotatesByUtf8BytesBeforeExceedingLimit() {
        FlightLogWriter(folder.root, 12).use {
            it.append("Привет") // 6 символов, 12 байт
            assertEquals(12, folder.root.resolve(FlightRecorder.CURRENT).length())
            it.append("мир")
        }
        assertEquals("Привет", folder.root.resolve(FlightRecorder.PREVIOUS).readText())
        assertEquals("мир", folder.root.resolve(FlightRecorder.CURRENT).readText())
    }

    @Test fun countsExistingBytesOnRestartAndReplacesPrevious() {
        folder.newFile(FlightRecorder.CURRENT).writeText("Привет")
        folder.newFile(FlightRecorder.PREVIOUS).writeText("old")
        FlightLogWriter(folder.root, 12).use { it.append("next") }
        assertEquals("Привет", folder.root.resolve(FlightRecorder.PREVIOUS).readText())
        assertEquals("next", folder.root.resolve(FlightRecorder.CURRENT).readText())
    }

    @Test fun oversizedLineIsBoundedWithoutSplittingUtf8Characters() {
        FlightLogWriter(folder.root, 13).use { it.append("🦆Русский текст".repeat(100)) }
        val bytes = folder.root.resolve(FlightRecorder.CURRENT).readBytes()
        assertTrue(bytes.size <= 13)
        val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        assertTrue(decoded.endsWith("…\n"))
    }

    @Test fun rotationFailureDoesNotOverwriteCurrentLog() {
        folder.newFile(FlightRecorder.CURRENT).writeText("full")
        folder.newFolder(FlightRecorder.PREVIOUS).resolve("keep").writeText("keep")
        FlightLogWriter(folder.root, 4).use {
            assertFailsWith<IOException> { it.append("more") }
        }
        assertEquals("full", folder.root.resolve(FlightRecorder.CURRENT).readText())
    }
}
