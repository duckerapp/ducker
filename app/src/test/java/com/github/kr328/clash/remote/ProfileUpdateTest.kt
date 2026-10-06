package com.github.kr328.clash.remote

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileUpdateTest {
    @Test
    fun `update without a valid profile identifier is ignored`() {
        for (value in listOf(null, "", "broken", "00000000-0000-0000-0000-00000000000z")) {
            assertNull(profileUpdateUuid(value))
        }
    }

    @Test
    fun `valid profile identifier is preserved`() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        assertEquals(uuid, profileUpdateUuid(uuid.toString()))
    }
}
