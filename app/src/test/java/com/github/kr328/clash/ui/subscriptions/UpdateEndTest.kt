package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.service.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** The card learns an update's end from what the service recorded, when it did not hear of it (D-436). */
class UpdateEndTest {
    @Test
    fun `an end after the card's moment is read, success as empty`() {
        assertEquals("", endedSince(profile(endedAt = 2_000L, failure = ""), since = 1_000L))
        assertEquals("DOWNLOAD_FAILED: profile: timeout", endedSince(profile(2_000L, "DOWNLOAD_FAILED: profile: timeout"), 1_000L))
    }

    @Test
    fun `an end before it, none at all, or no moment is not`() {
        assertNull(endedSince(profile(endedAt = 1_000L, failure = ""), since = 1_000L))
        assertNull(endedSince(profile(endedAt = 0L, failure = ""), since = 1_000L))
        assertNull(endedSince(profile(endedAt = 2_000L, failure = ""), since = 0L))
        assertNull(endedSince(null, since = 1_000L))
    }

    private fun profile(endedAt: Long, failure: String) = Profile(
        uuid = UUID.randomUUID(), name = "synthetic", type = Profile.Type.Url, source = "https://example.com/sub",
        active = false, interval = 0, upload = 0, download = 0, total = 0, expire = 0, updatedAt = 0,
        imported = true, pending = false, updateEndedAt = endedAt, updateFailure = failure,
    )
}
