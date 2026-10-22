package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-345: the provider's other addresses of a subscription and its move. Hosts are fictitious. */
class SubscriptionMirrorsTest {
    private val link = "https://sub.example.com/api/sub/TOKEN?format=clash#Main"

    @Test
    fun `a bare host is the same link on another domain`() {
        assertEquals(
            listOf(
                "https://sub2.example.net/api/sub/TOKEN?format=clash",
                "https://sub3.example.org:8443/api/sub/TOKEN?format=clash",
            ),
            SubscriptionMirrors.resolve(link, " sub2.example.net , sub3.example.org:8443", ""),
        )
    }

    @Test
    fun `a full address is taken as is, the fallback-url after the mirrors`() {
        assertEquals(
            listOf("https://cdn.example.net/s/OTHER", "https://backup.example.org/x"),
            SubscriptionMirrors.resolve(link, "https://cdn.example.net/s/OTHER", "https://backup.example.org/x"),
        )
    }

    @Test
    fun `never less protected than the link`() {
        // An https link: no http mirror, no password in the address.
        assertEquals(
            emptyList(),
            SubscriptionMirrors.resolve(link, "http://plain.example.net/sub, https://user:pass@sub2.example.net/sub", ""),
        )
        // An http link travels in the clear anyway: its mirrors may too, a bare host keeps http.
        assertEquals(
            listOf("http://10.0.2.2:8766/sub", "http://plain.example.net/sub"),
            SubscriptionMirrors.resolve("http://10.0.2.2:8765/sub", "10.0.2.2:8766, http://plain.example.net/sub", ""),
        )
    }

    @Test
    fun `bad entries, repeats and the link itself are skipped`() {
        assertEquals(
            listOf("https://sub2.example.net/api/sub/TOKEN?format=clash"),
            SubscriptionMirrors.resolve(
                link,
                "sub2.example.net, sub2.example.net, sub.example.com, -, not a host, ftp://x.example.net/a, a/b, ,",
                "https://sub2.example.net/api/sub/TOKEN?format=clash",
            ),
        )
    }

    @Test
    fun `at most five, the fallback-url counted`() {
        val many = (1..6).joinToString(",") { "m$it.example.net" }

        assertEquals(5, SubscriptionMirrors.resolve(link, many, "https://backup.example.org/x").size)
        assertEquals(
            "https://backup.example.org/x",
            SubscriptionMirrors.resolve(link, "m1.example.net", "https://backup.example.org/x").last(),
        )
    }

    @Test
    fun `nothing for a link that is not http`() {
        assertEquals(emptyList(), SubscriptionMirrors.resolve("content://files/sub.yaml", "sub2.example.net", ""))
    }

    @Test
    fun `the address that answered comes first while it still belongs`() {
        val mirrors = listOf("https://a.example.net/s", "https://b.example.net/s")

        assertEquals(
            listOf("https://b.example.net/s", link, "https://a.example.net/s"),
            SubscriptionMirrors.order(link, mirrors, "https://b.example.net/s"),
        )
        // Dropped by the provider: not tried again.
        assertEquals(listOf(link) + mirrors, SubscriptionMirrors.order(link, mirrors, "https://gone.example.net/s"))
        assertEquals(listOf(link) + mirrors, SubscriptionMirrors.order(link, mirrors, link))
        assertEquals(listOf(link), SubscriptionMirrors.order(link, emptyList(), ""))
    }

    @Test
    fun `a move by new-url or new-domain`() {
        assertEquals(
            "https://new.example.net/s/NEW",
            SubscriptionMirrors.moveTarget(link, "https://new.example.net/s/NEW", "ignored.example.org"),
        )
        assertEquals(
            "https://new.example.net/api/sub/TOKEN?format=clash",
            SubscriptionMirrors.moveTarget(link, "", "new.example.net"),
        )
    }

    @Test
    fun `no move to the same, a weaker or a bad address`() {
        assertNull(SubscriptionMirrors.moveTarget(link, "", ""))
        assertNull(SubscriptionMirrors.moveTarget(link, "", "sub.example.com"))
        assertNull(SubscriptionMirrors.moveTarget(link, "http://new.example.net/s", ""))
        assertNull(SubscriptionMirrors.moveTarget(link, "https://user:pass@new.example.net/s", ""))
        assertNull(SubscriptionMirrors.moveTarget(link, "", "new.example.net/path"))
        assertNull(SubscriptionMirrors.moveTarget(link, "new.example.net", ""))
    }

    @Test
    fun `only the host is shown`() {
        assertEquals("sub2.example.net:8443", SubscriptionMirrors.host("https://sub2.example.net:8443/api/TOKEN"))
        assertEquals("sub.example.com", SubscriptionMirrors.host("https://user:pass@sub.example.com/TOKEN"))
        assertEquals("", SubscriptionMirrors.host(""))
    }
}
