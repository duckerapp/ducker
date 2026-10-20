package com.github.kr328.clash.core

import com.github.kr328.clash.core.model.AgeKeyPair
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreJsonTest {
    // An answer cut off in the middle of the public key.
    private val broken = """{"secretKey":"AGE-SECRET-KEY-1TEST","publicKey":"age1"""

    @Test
    fun `a parse error does not quote the input`() {
        val error = assertFailsWith<SerializationException> {
            CoreJson.decodeFromString(AgeKeyPair.serializer(), broken)
        }
        assertFalse("AGE-SECRET-KEY" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the default configuration would quote it`() {
        val error = assertFailsWith<SerializationException> {
            Json.decodeFromString(AgeKeyPair.serializer(), broken)
        }
        assertTrue("AGE-SECRET-KEY" in error.message.orEmpty(), error.message)
    }
}
