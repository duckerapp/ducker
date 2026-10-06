package com.github.kr328.clash.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals

/** «Выровнять» конфиг (D-124). */
class YamlFormatTest {
    @Test
    fun `отступы по два пробела, порядок и значения на месте`() {
        val input = """
            |dns:
            |    enable: true
            |    nameserver:
            |        - 1.1.1.1
            |        - 8.8.8.8
            |rules:
            |  - MATCH,DIRECT
            |""".trimMargin()

        assertEquals(
            """
            |dns:
            |  enable: true
            |  nameserver:
            |    - 1.1.1.1
            |    - 8.8.8.8
            |rules:
            |  - MATCH,DIRECT
            |""".trimMargin(),
            formatYaml(input),
        )
    }

    @Test
    fun `табы, хвостовые пробелы и лишние пустые строки`() {
        assertEquals("a:\n  b: 1\n\nc: 2\n", formatYaml("a:   \n\tb: 1\n\n\n\nc: 2"))
    }

    @Test
    fun `список на уровне ключа остаётся на уровне ключа`() {
        assertEquals("proxies:\n- a\n- b\n", formatYaml("proxies:\n- a\n- b\n"))
    }

    @Test
    fun `блок сдвигается вместе с родителем и хранит свой отступ`() {
        val input = "script:\n    code: |\n        def main():\n            return 1\n    other: x\n"

        // Родитель сдвинулся на 2 влево — и весь блок на 2, отступы внутри блока те же.
        assertEquals("script:\n  code: |\n      def main():\n          return 1\n  other: x\n", formatYaml(input))
    }

    @Test
    fun `строки внутри скобок не трогаются`() {
        val input = "proxies: [a,\n      b, c]\nmode: rule\n"

        assertEquals(input, formatYaml(input))
    }

    @Test
    fun `комментарий встаёт на уровень`() {
        assertEquals("a:\n  # про b\n  b: 1\n", formatYaml("a:\n      # про b\n    b: 1\n"))
    }

    @Test
    fun `разделы — блоки верхнего уровня, одиночные настройки одним пунктом`() {
        val input = """
            |# ducker
            |mode: rule
            |log-level: info
            |"dns":
            |  enable: true
            |proxies:
            |- name: a
            |rules: [MATCH,DIRECT]
            |""".trimMargin()

        assertEquals(listOf("" to 1, "dns" to 3, "proxies" to 5, "rules" to 7), sections(input))
    }

    @Test
    fun `без серверов — группы и правила на месте`() {
        val input = """
            |mode: rule
            |proxies:
            |- {name: a, server: 1.2.3.4, password: secret}
            |# старый
            |- name: b
            |  server: 5.6.7.8
            |
            |proxy-providers:
            |  sub:
            |    url: https://example.com/sub?token=secret
            |proxy-groups:
            |  - name: PROXY
            |    proxies: [a, b]
            |rules:
            |  - MATCH,PROXY
            |""".trimMargin()

        val parsed = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(withoutServers(input))
        assertEquals(emptyList<Any>(), parsed["proxies"])
        assertEquals(emptyMap<String, Any>(), parsed["proxy-providers"])
        assertEquals("rule", parsed["mode"])
        assertEquals(listOf("MATCH,PROXY"), parsed["rules"])
        val groups = parsed["proxy-groups"] as List<*>
        assertEquals(listOf("a", "b"), (groups[0] as Map<*, *>)["proxies"])
    }
}
