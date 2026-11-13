package com.github.kr328.clash.ui.home

import com.github.kr328.clash.service.model.Profile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** What Home keeps when the core answers with no group (F-451, F-453, F-482). */
class KeptGroupsTest {
    private fun profile() = Profile(
        uuid = UUID.randomUUID(), name = "synthetic", type = Profile.Type.Url, source = "https://example.com/sub",
        active = true, interval = 0, upload = 0, download = 0, total = 0, expire = 0, updatedAt = 0,
        imported = true, pending = false,
    )

    private val old = profile()
    private val shown = HomeState(profile = old, groups = listOf(GroupState("Main", "Frankfurt", 70)))

    @Test
    fun `the tunnel just switched and the core has no groups yet — the node stays`() {
        assertEquals(shown.groups, keptGroups(shown, old, awaitingGroups = true))
    }

    @Test
    fun `the core already answered with groups — an empty answer is the config's own`() {
        assertEquals(emptyList(), keptGroups(shown, old, awaitingGroups = false))
    }

    @Test
    fun `another subscription — the previous one's group goes`() {
        assertEquals(emptyList(), keptGroups(shown, profile(), awaitingGroups = true))
    }

    @Test
    fun `no subscription — nothing to keep`() {
        assertEquals(emptyList(), keptGroups(shown, null, awaitingGroups = true))
    }
}
