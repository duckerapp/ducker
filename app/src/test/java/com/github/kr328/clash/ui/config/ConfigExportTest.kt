package com.github.kr328.clash.ui.config

import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «Файлом без серверов» (D-192): уходит весь конфиг, кроме серверов, паролей и доступа к панели. */
class ConfigExportTest {
    @Test
    fun `пароли, ключи и комментарии не уходят`() {
        val examples = listOf(
            "secret: QA_SECRET\n",
            "authentication: [user:QA_SECRET]\n",
            "external-controller-tls: QA_SECRET:9443\n",
            "listeners: [{name: in, users: [{username: user, password: QA_SECRET}]}]\n",
            "listeners: [{name: in, type: tuic, token: [QA_SECRET], private_key: QA_SECRET}]\n",
            "proxies :\n  - {name: node, password: QA_SECRET}\n",
            "proxies: [\n{ name: node, password: QA_SECRET }\n]\n",
            "proxy-providers: {sub: {url: 'https://panel.invalid/QA_SECRET'}}\n",
            "dns: {nameserver: ['https://dns.invalid/dns-query?key=QA_SECRET']}\n",
            "rule-providers: {r: {url: 'https://user:QA_SECRET@rules.invalid/list.yaml'}}\n",
            "rule-providers: {r: {url: 'https://rules.invalid/list', header: {Authorization: [Bearer QA_SECRET]}}}\n",
            "x-private: &private {password: QA_SECRET}\nproxy-groups: [{name: group, <<: *private}]\n",
            "\"proxies\": &nodes [{server: node.invalid, uuid: x, servername: QA_SECRET}]\nx-copy: *nodes\n",
            "x-node: &node {type: vless, servername: QA_SECRET}\nproxies: [{name: a, server: node.invalid, <<: *node}]\n",
            "# QA_SECRET\nmode: rule # QA_SECRET\n",
        )
        for (example in examples) {
            assertFalse(withoutServers(example).contains("QA_SECRET"), example)
        }
    }

    @Test
    fun `серверов и их адресов нет, остальное на месте`() {
        val input = """
            mixed-port: 7890
            ipv6: no
            secret: QA_SECRET
            external-controller: 127.0.0.1:9090
            authentication: ["user:QA_AUTH"]
            proxies:
              - {name: "🇩🇪 Германия", type: ss, server: node.invalid, port: 443, password: QA_PASSWORD}
              - name: msk
                type: vless
                server: 192.0.2.1
                uuid: QA_UUID
            proxy-providers:
              sub: {type: http, url: 'https://panel.invalid/QA_TOKEN', path: ./sub.yaml}
            rule-providers:
              ads: {type: http, behavior: domain, format: mrs, url: 'https://rules.invalid/ads.mrs', interval: 86400}
            hosts:
              node.invalid: 192.0.2.1
              router.lan: 192.168.1.1
            sniffer: {enable: true, sniff: {TLS: {ports: [443]}}}
            dns:
              enable: true
              enhanced-mode: fake-ip
              nameserver: ['https://dns.invalid/dns-query', tls://1.1.1.1]
              nameserver-policy: {"+.node.invalid": 223.5.5.5, "+.example.org": 1.1.1.1}
            tun: {enable: true, stack: mixed}
            proxy-groups:
              - name: PROXY
                type: select
                proxies: ["🇩🇪 Германия", msk, node.invalid, DIRECT]
                use: [sub]
                url: https://www.gstatic.com/generate_204
            rules:
              - IP-CIDR,192.0.2.1/32,DIRECT
              - DOMAIN,node.invalid,DIRECT
              - DOMAIN-SUFFIX,panel.invalid,DIRECT
              - DOMAIN-SUFFIX,notnode.invalid,PROXY
              - RULE-SET,ads,REJECT
              - MATCH,PROXY
        """.trimIndent()
        val output = withoutServers(input)

        for (private in listOf("QA_", "node.invalid\n", " node.invalid", "192.0.2.1", "panel.invalid", "9090")) {
            assertFalse(output.contains(private), private)
        }
        // Написание значений — как в исходнике: `no` не стало `false`, кавычки на месте.
        assertTrue("ipv6: no\n" in output, output)
        assertTrue("mixed-port: 7890\n" in output, output)

        val parsed = Yaml().load<Map<String, Any?>>(output)
        val original = Yaml().load<Map<String, Any?>>(input)

        assertEquals(emptyList<Any>(), parsed["proxies"])
        assertEquals(emptyMap<String, Any>(), parsed["proxy-providers"])
        assertEquals(
            original.keys - setOf("secret", "external-controller", "authentication"),
            parsed.keys,
        )
        for (same in listOf("rule-providers", "sniffer", "tun")) assertEquals(original[same], parsed[same], same)
        assertEquals(mapOf("router.lan" to "192.168.1.1"), parsed["hosts"])
        assertEquals(
            mapOf(
                "enable" to true, "enhanced-mode" to "fake-ip",
                "nameserver" to listOf("https://dns.invalid/dns-query", "tls://1.1.1.1"),
                "nameserver-policy" to mapOf("+.example.org" to "1.1.1.1"),
            ),
            parsed["dns"],
        )
        assertEquals(
            listOf(
                mapOf(
                    "name" to "PROXY", "type" to "select", "proxies" to listOf("🇩🇪 Германия", "msk", "DIRECT"),
                    "use" to listOf("sub"), "url" to "https://www.gstatic.com/generate_204",
                ),
            ),
            parsed["proxy-groups"],
        )
        assertEquals(listOf("DOMAIN-SUFFIX,notnode.invalid,PROXY", "RULE-SET,ads,REJECT", "MATCH,PROXY"), parsed["rules"])
    }

    @Test
    fun `встроенный шаблон уходит целиком, с якорями`() {
        val template = File("src/main/assets/ducker/template.yaml").readText()
        // Шаблон кончается списком `proxies` — дописываем в него сервер.
        val output = withoutServers("$template\n  - {name: a, server: node.invalid, password: QA_SECRET}\n")
        val expected = Yaml().load<Map<String, Any?>>(template).filterKeys {
            it != "secret" && it != "authentication" && !it.startsWith("external-controller")
        } + ("proxies" to emptyList<Any>())

        assertEquals(expected, Yaml().load<Map<String, Any?>>(output))
        assertTrue("&rp_domain" in output && "*rp_domain" in output)
        assertFalse("#  " in output)
    }

    @Test
    fun `битый или необычно устроенный YAML — отказ`() {
        for (input in listOf("proxies: [\n", "mode: rule\n---\nsecret: QA_SECRET", "- a\n- b\n", "x: &x {proxies: []}\n<<: *x\n")) {
            assertFailsWith<IllegalArgumentException> { withoutServers(input) }
        }
    }
}
