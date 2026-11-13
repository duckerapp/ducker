package com.github.kr328.clash.service.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeKeysTest {
    private val valid = { key: String -> key.startsWith("AGE-SECRET-KEY-") }

    @Test
    fun `every subscription's key, one per line, without blanks, repeats and bad keys`() {
        val keys = listOf("AGE-SECRET-KEY-1A", null, "", "  ", " AGE-SECRET-KEY-1B\n", "junk", "AGE-SECRET-KEY-1A")

        assertEquals("AGE-SECRET-KEY-1A\nAGE-SECRET-KEY-1B", AgeKeys.joined(keys, valid))
    }

    @Test
    fun `no key clears the set`() {
        assertNull(AgeKeys.joined(listOf(null, "", "junk"), valid))
    }
}
