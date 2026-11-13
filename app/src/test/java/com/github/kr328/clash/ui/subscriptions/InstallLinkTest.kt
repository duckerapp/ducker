package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.service.util.SubscriptionMirrors
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A link with the provider's mirrors (stage 21, package 3): the install link, «Поделиться», the TV transfer. */
class InstallLinkTest {
    private val link = "https://sub.example.com/api/sub/AbC+1_x?format=mihomo"

    private companion object {
        val KEY = "AGE-SECRET-KEY-1" + "QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L".repeat(2).take(58)
        val PQ_KEY = "AGE-SECRET-KEY-PQ-1" + "QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L".repeat(2).take(58)

        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }

    @Test
    fun `a bare link is itself`() {
        assertEquals(InstallLink(link), InstallLink.parse(link))
        assertEquals(link, InstallLink(link).toString())
    }

    @Test
    fun `a link with mirrors goes there and back`() {
        val given = InstallLink("$link#Имя", listOf("sub2.example.com:8443", "https://other.example/x/AbC?t=a b&c"))
        val text = given.toString()

        assertEquals(given, InstallLink.parse(text))
        assertEquals(given, InstallLink.parse(text.replace("ducker://", "clashmeta://")))
    }

    @Test
    fun `a provider's install link`() {
        assertEquals(
            InstallLink(link, listOf("sub2.example.com", "sub3.example.org")),
            InstallLink.parse("clash://install-config?name=X&url=https%3A%2F%2Fsub.example.com%2Fapi%2Fsub%2FAbC+1_x%3Fformat%3Dmihomo&mirror=sub2.example.com&mirror=sub3.example.org"),
        )
        // Unencoded, as some pages give it: everything up to the next `&` is the link.
        assertEquals(InstallLink("https://sub.example.com/sub?t=1"), InstallLink.parse("ducker://install-config?url=https://sub.example.com/sub?t=1&name=X"))
    }

    @Test
    fun `anything else is not a link`() {
        for (bad in listOf(
            "", "vless://uuid@host:443", "file:///data/config.yaml", "https://a.example/x\nproxies: []",
            "ducker://install-config?mirror=sub2.example.com",
            "ducker://install-config?url=vless%3A%2F%2Fuuid%40host%3A443",
            "ducker://install-config?url=%zz",
            "ducker://other?url=https%3A%2F%2Fa.example%2Fx",
            "ducker://install-config?url=https%3A%2F%2Fa.example%2Fx y",
        )) {
            assertNull(InstallLink.parse(bad), bad)
        }
    }

    @Test
    fun `an age key travels in the link, alone or with mirrors`() {
        for (key in listOf(KEY, PQ_KEY)) {
            val alone = InstallLink.of(link, emptyList(), " $key\n")

            assertEquals(InstallLink(link, key = key), alone)
            assertEquals(alone, InstallLink.parse(alone.toString()))
            assertEquals(InstallLink(link, listOf("sub2.example.com"), key), InstallLink.parse(InstallLink(link, listOf("sub2.example.com"), key).toString()))
        }
        // No key, a blank one or one not shaped as a key: the bare link, as before keys travelled.
        for (none in listOf(null, "", "  ")) assertEquals(link, InstallLink.of(link, emptyList(), none).toString())
    }

    @Test
    fun `anything not shaped as one age key is dropped`() {
        for (bad in listOf(
            KEY.lowercase(), "AGE-SECRET-KEY-", "AGE-PLUGIN-YUBIKEY-1QQQ", "$KEY $KEY", "$KEY\n$KEY", "$KEY\u0000",
            "AGE-SECRET-KEY-1" + "Q".repeat(113), "https://evil.example/",
        )) {
            assertNull(InstallLink.ageKey(bad), bad)
            assertEquals(InstallLink(link), InstallLink.parse("ducker://install-config?url=${encode(link)}&key=${encode(bad)}"), bad)
        }
        // A provider's link may carry one too: it lands in the field, where the person sees it.
        assertEquals(KEY, InstallLink.parse("clash://install-config?url=https%3A%2F%2Fsub.example.com%2Fs&key=$KEY")?.key)
    }

    @Test
    fun `a mirror on the link's path travels as its host, and resolves back to itself`() {
        val mirrors = SubscriptionMirrors.resolve(link, "sub2.example.com:8443, https://other.example/x/AbC", "")
        val short = InstallLink.of(link, mirrors)

        assertEquals(listOf("sub2.example.com:8443", "https://other.example/x/AbC"), short.mirrors)
        assertEquals(mirrors, SubscriptionMirrors.resolve(link, short.mirrors.joinToString(","), ""))
    }
}
