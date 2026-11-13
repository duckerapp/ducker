package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Прочерк как «не задано» (D-68): пустое значение заголовка панель сохранить не даёт,
 * и убрать свой заголовок провайдер может только так.
 */
class ProviderInfoTest {
    @Test
    fun `прочерк значит не задано`() {
        val info = ProviderInfo.of(
            mapOf(
                "profile-title" to "-",
                "ducker-brand-name" to " - ",
                "ducker-brand-logo" to "-",
                "ducker-buy-url" to "-",
                "support-url" to "-",
                "ducker-override-user" to "off",
            )
        )

        assertEquals("", info.profileTitle)
        assertEquals("", info.brandName)
        assertEquals("", info.brandLogo)
        assertEquals("", info.buyUrl)
        assertEquals("", info.supportUrl)
        assertFalse(info.overrideUser)
    }

    @Test
    fun `обычные значения не трогаем`() {
        val info = ProviderInfo.of(
            mapOf(
                "profile-title" to "Утка в инвизе",
                "support-url" to "https://t.me/support",
                // Прочерк внутри строки — часть текста, а не признак пустоты.
                "ducker-brand-name" to "Duck-VPN",
            )
        )

        assertEquals("Утка в инвизе", info.profileTitle)
        assertEquals("https://t.me/support", info.supportUrl)
        assertEquals("Duck-VPN", info.brandName)
    }

    @Test
    fun `a name in a broken encoding counts as not sent (Q-96)`() {
        val info = ProviderInfo.of(
            mapOf(
                "profile-title" to "\uFFFD\uFFFD\uFFFD",
                "ducker-brand-name" to "Duck \uFFFD VPN",
                "announce" to "\uFFFD stays",
            )
        )

        assertEquals("", info.profileTitle)
        assertEquals("", info.brandName)
        assertEquals("\uFFFD stays", info.announce)
    }

    @Test
    fun `переключатель Global прячется только по off`() {
        assertTrue(ProviderInfo.of(mapOf("Ducker-Global" to " Off ")).globalHidden)
        assertFalse(ProviderInfo.of(mapOf("ducker-global" to "on")).globalHidden)
        assertFalse(ProviderInfo.of(emptyMap()).globalHidden)
    }

    @Test
    fun `локальные порты закрываются только по off`() {
        assertTrue(ProviderInfo.of(mapOf("Ducker-Local-Proxy" to " Off ")).localProxyOff)
        assertFalse(ProviderInfo.of(mapOf("ducker-local-proxy" to "on")).localProxyOff)
        assertFalse(ProviderInfo.of(emptyMap()).localProxyOff)
    }

    @Test
    fun `sharing closes only on ducker-share off, apart from the local ports (D-471)`() {
        assertTrue(ProviderInfo.of(mapOf("Ducker-Share" to " Off ")).shareOff)
        assertFalse(ProviderInfo.of(mapOf("Ducker-Share" to " Off ")).localProxyOff)
        assertFalse(ProviderInfo.of(mapOf("ducker-share" to "on")).shareOff)
        assertFalse(ProviderInfo.of(mapOf("ducker-local-proxy" to "off")).shareOff)
        assertFalse(ProviderInfo.of(emptyMap()).shareOff)
    }

    private val link = "https://sub.example.com/sub/TOKEN"
    private val mirror = "https://sub2.example.net/sub/TOKEN"

    @Test
    fun `mirrors and a move are read for the link they came with (D-345)`() {
        val info = ProviderInfo.of(
            mapOf("Ducker-Mirrors" to "sub2.example.net", "fallback-url" to "https://backup.example.org/b", "new-domain" to "new.example.net"),
            link,
        )

        assertEquals(link, info.source)
        assertEquals(listOf(mirror, "https://backup.example.org/b"), info.mirrors)
        assertEquals("https://new.example.net/sub/TOKEN", info.moveTo)
        assertEquals(info.mirrors, info.mirrorsFor(link))
        assertEquals(emptyList(), info.mirrorsFor("https://other.example.com/sub/OTHER"))
    }

    private val previous = ProviderInfo(source = link, mirrors = listOf(mirror), movedFrom = "https://old.example.com/sub/TOKEN", movedAt = 1_000)

    @Test
    fun `a mirror's answer without the header keeps the list, the own link's clears it`() {
        val fromMirror = ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), previous, emptyMap(), mirror, "", 2_000)
        assertEquals(listOf(mirror), fromMirror.mirrors)
        assertEquals(mirror, fromMirror.servedBy)

        val fromLink = ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), previous, emptyMap(), link, "proxy", 2_000)
        assertEquals(emptyList(), fromLink.mirrors)
        assertEquals("proxy", fromLink.servedVia)

        // A mirror that sends the header replaces the list, even with a dash — that is how a provider removes them.
        val dash = mapOf("ducker-mirrors" to "-")
        assertEquals(emptyList(), ProviderInfo.continued(ProviderInfo.of(dash, link), previous, dash, mirror, "", 2_000).mirrors)
    }

    @Test
    fun `the install link's name stands in for a title not sent, and only for its link (D-506)`() {
        val added = ProviderInfo(source = link, linkName = "Pond")
        val title = mapOf("profile-title" to "Пруд")

        val bare = ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), added, emptyMap(), link, "")
        assertEquals("Pond" to "Pond", bare.profileTitle to bare.linkName)

        val titled = ProviderInfo.continued(ProviderInfo.of(title, link), bare, title, link, "")
        assertEquals("Пруд" to "Pond", titled.profileTitle to titled.linkName)

        // The provider stops sending it, or sends «-»: the link's name again.
        assertEquals("Pond", ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), titled, emptyMap(), link, "").profileTitle)
        val dash = mapOf("profile-title" to "-")
        assertEquals("Pond", ProviderInfo.continued(ProviderInfo.of(dash, link), titled, dash, link, "").profileTitle)

        val other = ProviderInfo.continued(ProviderInfo.of(emptyMap(), "https://other.example/s"), titled, emptyMap(), "", "")
        assertEquals("" to "", other.profileTitle to other.linkName)
    }

    @Test
    fun `nothing is kept from another link, and the move mark lasts a week`() {
        val other = "https://other.example.com/sub/OTHER"
        val elsewhere = ProviderInfo.continued(ProviderInfo.of(emptyMap(), other), previous, emptyMap(), mirror, "", 2_000)
        assertEquals(emptyList(), elsewhere.mirrors)
        assertEquals("", elsewhere.movedFrom)

        val week = ProviderInfo.MOVED_SHOWN
        assertEquals(previous.movedFrom, ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), previous, emptyMap(), link, "", 1_000 + week - 1).movedFrom)
        assertEquals("", ProviderInfo.continued(ProviderInfo.of(emptyMap(), link), previous, emptyMap(), link, "", 1_000 + week).movedFrom)
    }
}
