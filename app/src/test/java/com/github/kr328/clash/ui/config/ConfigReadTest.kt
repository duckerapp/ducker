package com.github.kr328.clash.ui.config

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigReadTest {
    @Test
    fun `exactly the limit is accepted but one more byte is rejected`() {
        val bytes = ByteArray(MAX_CONFIG_FILE_BYTES) { 'a'.code.toByte() }
        assertEquals(MAX_CONFIG_FILE_BYTES, readConfigText(ByteArrayInputStream(bytes)).length)
        assertFailsWith<ConfigFileTooLargeException> {
            readConfigText(ByteArrayInputStream(bytes + byteArrayOf(1)))
        }
    }

    @Test
    fun `short reads and unknown length cannot silently return a prefix`() {
        val input = object : InputStream() {
            var remaining = MAX_CONFIG_FILE_BYTES + 17
            override fun read(): Int = if (remaining-- > 0) 'a'.code else -1
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(997, len, remaining)
                buffer.fill('a'.code.toByte(), off, off + count)
                remaining -= count
                return count
            }
        }
        assertFailsWith<ConfigFileTooLargeException> { readConfigText(input) }
    }

    @Test
    fun `small UTF8 and empty files are preserved`() {
        val text = "# Проверка\nmode: rule\n"
        assertEquals(text, readConfigText(ByteArrayInputStream(text.toByteArray())))
        assertEquals("", readConfigText(ByteArrayInputStream(byteArrayOf())))
    }

    @Test
    fun `excess is detected with at most one extra byte read`() {
        var consumed = 0
        val input = object : InputStream() {
            override fun read(): Int { consumed++; return 1 }
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                consumed += len
                return len
            }
        }
        assertFailsWith<ConfigFileTooLargeException> { readConfigText(input) }
        assertTrue(consumed <= MAX_CONFIG_FILE_BYTES + 1)
    }
}
