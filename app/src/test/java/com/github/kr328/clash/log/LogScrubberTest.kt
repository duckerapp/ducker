package com.github.kr328.clash.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Проверка инварианта 4: из выгрузки журнала не должны уходить адреса узлов, UUID,
 * пароли и секрет API. Обратная сторона тоже важна — журнал, из которого вычистили
 * вообще всё, бесполезен, поэтому здесь же закреплено, что остаётся.
 */
class LogScrubberTest {
    @Test
    fun `many DNS failures in one diagnostic source do not recurse on the JVM stack`() {
        val input = "[DNS] 77.88.8.8:53 failed: read udp 10.0.0.2:4000->77.88.8.8:53: EOF\n"
        val expected = "[DNS] 77.88.8.8:53 failed: read udp 10.0.0.2:4000-><ip>:53: EOF\n"
        assertEquals(expected.repeat(5_000), LogScrubber.scrub(input.repeat(5_000)))
    }
    @Test
    fun `plain YAML secrets include spaces punctuation and indented continuations`() {
        for (value in listOf("synthetic first", "synthetic,second", "synthetic-first\n  synthetic-second")) {
            val clean = LogScrubber.scrub("password: $value\nport: 443\n")
            assertEquals("password: <hidden>\nport: 443\n", clean)
        }
    }
    @Test
    fun `стек падения под строкой с секретным словом остаётся`() {
        val stack = "\tat app.ducker.Node.parse(Node.kt:12)\n\tat app.ducker.Main.run(Main.kt:7)\n"
        for (value in listOf("123-abc", ">", "")) {
            assertEquals(
                "java.lang.IllegalStateException: Invalid uuid: ${if (value.isEmpty()) "" else "<hidden>"}\n$stack",
                LogScrubber.scrub("java.lang.IllegalStateException: Invalid uuid: $value\n$stack"),
            )
        }
        assertEquals(
            "error: auth: <hidden> failed after 3 tries",
            LogScrubber.scrub("error: auth: handshake failed after 3 tries"),
        )
    }
    @Test
    fun `long escaped values and YAML blocks are scrubbed without regex recursion`() {
        val quoted = "synthetic\\\"value".repeat(5_000)
        assertEquals("password: <hidden>\nframe", LogScrubber.scrub("password: \"$quoted\"\nframe"))
        for (header in listOf("|", "|-", ">+", "|2- # comment")) {
            val input = "  - password: $header\n      synthetic-secret\n    port: 443\n"
            val clean = LogScrubber.scrub(input)
            assertFalse("synthetic-secret" in clean)
            assertEquals("  - password: <hidden>\n    port: 443\n", clean)
        }
    }
    @Test
    fun `quoted JSON YAML and secret values with spaces are scrubbed`() {
        assertEquals(
            """{"password": <hidden>, "secret": <hidden>, 'token': <hidden>}""",
            LogScrubber.scrub("""{"password": "synthetic password", "secret": "api-secret", 'token': 'two words'}"""),
        )
        assertEquals(
            """{"password": <hidden>, 'token': <hidden>}""",
            LogScrubber.scrub("""{"password": "escaped\" secret", 'token': 'doubled'' secret'}"""),
        )
    }

    @Test
    fun `subscription query and fragment without path are hidden`() {
        assertEquals("fetch https://panel.test/<hidden>", LogScrubber.scrub("fetch https://panel.test?key=subscription-secret"))
        assertEquals("fetch https://panel.test/<hidden>", LogScrubber.scrub("fetch https://panel.test#subscription-secret"))
    }

    @Test
    fun `failed import node host is hidden even without stored profile`() {
        assertEquals(
            "vless://<hidden>@<server>:443?pbk=<hidden>&sid=<hidden>",
            LogScrubber.scrub("vless://user@unsaved-node.test:443?pbk=public-secret&sid=short-secret"),
        )
        assertEquals(
            "hysteria://<server>:443?auth=<hidden>&obfsParam=<hidden>",
            LogScrubber.scrub("hysteria://unsaved-node.test:443?auth=auth-secret&obfsParam=obfs-secret"),
        )
        assertEquals(
            "hysteria://<hidden>@<server>:443?auth=<hidden>",
            LogScrubber.scrub("hysteria://user@unsaved-node.test:443?auth=auth-secret"),
        )
    }

    @Test
    fun `DNS resolver exception also scrubs URI credentials`() {
        assertEquals(
            "[DNS] https://<hidden>@resolver.test/<hidden> failed: EOF",
            LogScrubber.scrub("[DNS] https://user:password@resolver.test?key=secret failed: EOF"),
        )
    }
    @Test
    fun `ключ подписки не уходит, домен панели остаётся`() {
        assertEquals(
            "fetch https://panel.example.com/<hidden> ok",
            LogScrubber.scrub("fetch https://panel.example.com/sub/aBc123?token=xyz ok"),
        )
    }

    @Test
    fun `адрес узла прячется, локальные адреса остаются`() {
        assertEquals(
            "[TCP] 192.168.1.5:4000 --> <ip>:443",
            LogScrubber.scrub("[TCP] 192.168.1.5:4000 --> 203.0.113.9:443"),
        )

        assertEquals(
            "dns 127.0.0.1 fakeip 198.18.0.7",
            LogScrubber.scrub("dns 127.0.0.1 fakeip 198.18.0.7"),
        )
    }

    @Test
    fun `ipv6 прячется, время в сообщении не ломается`() {
        assertEquals("resolve <ip>", LogScrubber.scrub("resolve 2606:4700:4700::1111"))
        assertEquals("start at 18:54:27", LogScrubber.scrub("start at 18:54:27"))
    }

    @Test
    fun `uuid и пароли прячутся`() {
        assertEquals(
            "proxy <hidden> password: <hidden>",
            LogScrubber.scrub("proxy 3f2504e0-4f89-11d3-9a0c-0305e82c3301 password: hunter2"),
        )
    }

    @Test
    fun `известный секрет вырезается целиком`() {
        assertEquals(
            "x-hwid: <hidden>",
            LogScrubber.scrub("x-hwid: a1b2c3d4e5f60718", listOf("a1b2c3d4e5f60718")),
        )
    }

    @Test
    fun `серверы узлов берутся из конфига в любом виде`() {
        val config = """
            ntp:
              server: pool.ntp.org
            dns:
              nameserver: [1.1.1.1]
              proxy-server-nameserver: [77.88.8.8]
            proxies:
              - name: "🇩🇪 Германия"
                type: vless
                server: de2.example.net
                servername: www.microsoft.com
              - {name: msk, type: vless, server: "msk.example.net", port: 443}
              - {"name": "j", "server": "203.0.113.9"}
        """.trimIndent()

        assertEquals(
            setOf("pool.ntp.org", "de2.example.net", "msk.example.net", "203.0.113.9"),
            LogScrubber.serversIn(config),
        )

        val links = "vless://3f2504e0@fi.example.net:443?security=reality#Fi\ntrojan://pw@[2001:db8::1]:443#v6"

        assertEquals(setOf("fi.example.net", "2001:db8::1"), LogScrubber.serversIn(links))
        assertEquals(
            setOf("fi.example.net", "2001:db8::1"),
            LogScrubber.serversIn(java.util.Base64.getEncoder().encodeToString(links.toByteArray())),
        )
    }

    @Test
    fun `сервер узла прячется целым словом, соседние домены остаются`() {
        val servers = LogScrubber.serversRegex(setOf("de2.example.net", "203.0.113.9"))

        assertEquals(
            "dial tcp <server>:443: i/o timeout; dns resolve failed for <server>. " +
                "x.de2.example.net ok, de2.example.network ok, <server> via <ip>",
            LogScrubber.scrub(
                "dial tcp DE2.example.net:443: i/o timeout; dns resolve failed for de2.example.net. " +
                    "x.de2.example.net ok, de2.example.network ok, 203.0.113.9 via 203.0.113.99",
                servers = servers,
            ),
        )
        assertEquals(
            "get https://<server>/<hidden>",
            LogScrubber.scrub("get https://de2.example.net/sub/key", servers = servers),
        )
    }

    @Test
    fun `адрес отказавшего DNS-сервера остаётся, узел и ключ в пути — нет`() {
        val servers = LogScrubber.serversRegex(setOf("203.0.113.9"))

        assertEquals(
            "12:00:01 D [DNS] 77.88.8.8:53 failed: read udp 10.0.0.2:4000-><ip>:53: i/o timeout",
            LogScrubber.scrub(
                "12:00:01 D [DNS] 77.88.8.8:53 failed: read udp 10.0.0.2:4000->77.88.8.8:53: i/o timeout",
                servers = servers,
            ),
        )
        assertEquals(
            "[DNS] https://dns.nextdns.io/<hidden> failed: EOF",
            LogScrubber.scrub("[DNS] https://dns.nextdns.io/abc123 failed: EOF"),
        )
        assertEquals(
            "[DNS] <server>:53 failed: timeout",
            LogScrubber.scrub("[DNS] 203.0.113.9:53 failed: timeout", servers = servers),
        )
    }

    @Test
    fun `ключи REALITY в ссылке узла прячутся, остальные параметры и имя остаются`() {
        val servers = LogScrubber.serversRegex(setOf("fi.example.net"))

        assertEquals(
            "vless://<hidden>@<server>:443?encryption=none&security=reality&pbk=<hidden>&sid=<hidden>" +
                "&sni=www.microsoft.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#Fi",
            LogScrubber.scrub(
                "vless://3f2504e0-4f89-11d3-9a0c-0305e82c3301@fi.example.net:443?encryption=none&security=reality" +
                    "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179e30d4fc2" +
                    "&sni=www.microsoft.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#Fi",
                servers = servers,
            ),
        )
    }

    @Test
    fun `пароль перед @ в ссылке прячется в любом протоколе`() {
        assertEquals(
            "trojan://<hidden>@<server>:443?sni=example.org#Amsterdam",
            LogScrubber.scrub("trojan://secret-pass@203.0.113.9:443?sni=example.org#Amsterdam"),
        )
        assertEquals(
            "hysteria2://<hidden>@<server>:8443?obfs=salamander&obfs-password=<hidden>&sni=example.org#hy2",
            LogScrubber.scrub(
                "hysteria2://letmein@example.org:8443?obfs=salamander&obfs-password=gawrgura&sni=example.org#hy2",
            ),
        )
        assertEquals(
            "tuic://<hidden>@<server>:443?congestion_control=bbr#tuic",
            LogScrubber.scrub("tuic://3f2504e0-4f89-11d3-9a0c-0305e82c3301:hunter2@example.org:443?congestion_control=bbr#tuic"),
        )
        assertEquals(
            "ss://<hidden>@<server>:8388#ss",
            LogScrubber.scrub("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@example.org:8388#ss"),
        )
        // Ключ shadowsocks-2022 без кодирования: `/` внутри не должен обрывать пароль.
        assertEquals(
            "ss://<hidden>@<server>:8388#ss2022",
            LogScrubber.scrub("ss://2022-blake3-aes-128-gcm:qM3fxPJ/9AYFbmrIV87N5g==@example.org:8388#ss2022"),
        )
        assertEquals(
            "get https://<hidden>@panel.example.com/<hidden>",
            LogScrubber.scrub("get https://user:hunter2@panel.example.com/sub/key"),
        )
        // Адреса DNS-серверов тоже ссылки, но без пароля — их правило не трогает.
        assertEquals(
            "dns tls://dns.google:853 tcp://192.168.1.1:53",
            LogScrubber.scrub("dns tls://dns.google:853 tcp://192.168.1.1:53"),
        )
    }

    @Test
    fun `ссылки, где всё тело в base64, прячутся целиком`() {
        val encoder = java.util.Base64.getEncoder()
        val vmess = """{"v":"2","ps":"de","add":"de2.example.net","port":"443","id":"3f2504e0-4f89-11d3-9a0c-0305e82c3301"}"""
        val ss = "aes-256-gcm:hunter2@203.0.113.9:8388"

        assertEquals(
            "vmess://<hidden> ss://<hidden>#old",
            LogScrubber.scrub(
                "vmess://${encoder.encodeToString(vmess.toByteArray())} ss://${encoder.encodeToString(ss.toByteArray())}#old",
            ),
        )
    }

    @Test
    fun `ключи REALITY и пароль обфускации прячутся и в тексте конфига`() {
        assertEquals(
            "reality-opts: {public-key: <hidden>, short-id: <hidden>}",
            LogScrubber.scrub(
                "reality-opts: {public-key: SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc, short-id: 6ba85179e30d4fc2}",
            ),
        )
        assertEquals(
            "obfs: salamander\nobfs-password: <hidden>",
            LogScrubber.scrub("obfs: salamander\nobfs-password: gawrgura"),
        )
    }

    @Test
    fun `версии и обычный текст не трогаются`() {
        assertEquals(
            "mihomo v1.19.11 rule DOMAIN-SUFFIX,youtube.com,Proxy",
            LogScrubber.scrub("mihomo v1.19.11 rule DOMAIN-SUFFIX,youtube.com,Proxy"),
        )
    }
}
