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
    fun `groups go as the list does, each with its own newest first, and «new» is what came after the last look`() {
        // The model's order: the active one, the favourites, the rest — whoever spoke last.
        val active = profile("Pond").copy(active = true)
        val starred = profile("Reed")
        val other = profile("Lake")
        val state = SubscriptionsState(
            profiles = listOf(active, starred, other),
            favorites = setOf(starred.uuid),
            announcements = listOf(
                Announcement(active.uuid.toString(), "a1", 100),
                Announcement(starred.uuid.toString(), "b1", 200, known = true),
                Announcement(active.uuid.toString(), "a2", 300),
                Announcement(other.uuid.toString(), "c1", 500),
                Announcement(UUID.randomUUID().toString(), "deleted", 400),
            ),
            announcementsSeen = mapOf(active.uuid.toString() to 150L),
        )

        val all = state.announcementGroups(null)
        assertEquals(listOf("Pond", "Reed", "Lake"), all.map { it.profile.name })
        assertEquals(listOf(false, true, false), all.map { it.favorite })
        assertEquals(listOf("a2" to true, "a1" to false), all[0].items.map { it.text to it.fresh })
        assertEquals(listOf(false), all[1].items.map { it.fresh })

        assertEquals(listOf("Reed"), state.announcementGroups(starred.uuid).map { it.profile.name })
    }

    @Test
    fun `a long history shows the newest three and every unseen one, unless it is the only group`() {
        val items = (1..6).map { AnnouncementItem("t$it", 0, known = false, fresh = it == 5) }
        val group = AnnouncementGroup(profile("Pond"), favorite = false, items = items)

        assertEquals(listOf("t1", "t2", "t3", "t5"), group.shown(collapse = true).map { it.text })
        assertEquals(items, group.shown(collapse = false))
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
