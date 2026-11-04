package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.Announcement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class AnnouncementsTest {
    @Test
    fun `links open without the closing mark, and only those a provider link may be`() {
        val text = "Подробности: https://t.me/example_channel. Бот — tg://resolve?domain=example_bot, а это javascript://x и http://"
        val links = announcementLinks(text)

        assertEquals(listOf("https://t.me/example_channel", "tg://resolve?domain=example_bot"), links.map { it.second })
        assertEquals("https://t.me/example_channel", text.substring(links[0].first))
    }

    @Test
    fun `groups go newest first, each with its own, and «new» is what came after the last look`() {
        val a = profile("Pond")
        val b = profile("Reed")
        val state = SubscriptionsState(
            profiles = listOf(a, b),
            announcements = listOf(
                Announcement(a.uuid.toString(), "a1", 100),
                Announcement(b.uuid.toString(), "b1", 200, known = true),
                Announcement(a.uuid.toString(), "a2", 300),
                Announcement(UUID.randomUUID().toString(), "deleted", 400),
            ),
            announcementsSeen = mapOf(a.uuid.toString() to 150L),
        )

        val all = state.announcementGroups(null)
        assertEquals(listOf("Pond", "Reed"), all.map { it.profile.name })
        assertEquals(listOf("a2" to true, "a1" to false), all[0].items.map { it.text to it.fresh })
        assertEquals(listOf(false), all[1].items.map { it.fresh })

        assertEquals(listOf("Reed"), state.announcementGroups(b.uuid).map { it.profile.name })
    }

    @Test
    fun `seen marks survive the store`() {
        val seen = mapOf("a" to 1L, "b" to 2L)

        assertEquals(seen, seenOf(seenText(seen)))
        assertEquals(emptyMap<String, Long>(), seenOf(""))
        assertEquals(mapOf("b" to 2L), seenOf("a:x,b:2,junk"))
    }

    private fun profile(name: String) = Profile(
        uuid = UUID.randomUUID(), name = name, type = Profile.Type.Url, source = "https://example.com/$name",
        active = false, interval = 0, upload = 0, download = 0, total = 0, expire = 0, updatedAt = 0,
        imported = true, pending = false,
    )
}
