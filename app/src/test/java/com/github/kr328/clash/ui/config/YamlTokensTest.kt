package com.github.kr328.clash.ui.config

import com.github.kr328.clash.ui.config.YamlToken.Anchor
import com.github.kr328.clash.ui.config.YamlToken.Comment
import com.github.kr328.clash.ui.config.YamlToken.Key
import com.github.kr328.clash.ui.config.YamlToken.Mark
import com.github.kr328.clash.ui.config.YamlToken.Number
import com.github.kr328.clash.ui.config.YamlToken.Plain
import com.github.kr328.clash.ui.config.YamlToken.Text
import kotlin.test.Test
import kotlin.test.assertEquals

/** Подсветка строк конфига mihomo (D-124). */
class YamlTokensTest {
    @Test
    fun `ключ и значение`() {
        assertEquals(listOf(0 to Key, 4 to Mark, 6 to Plain), yamlLine("mode: rule"))
        assertEquals(listOf(0 to Key, 10 to Mark, 12 to Number), yamlLine("mixed-port: 7890"))
        assertEquals(listOf(2 to Key, 8 to Mark, 10 to Number), yamlLine("  enable: true"))
    }

    @Test
    fun `комментарий — только после пробела`() {
        assertEquals(listOf(0 to Comment), yamlLine("# узлы"))
        assertEquals(listOf(0 to Key, 4 to Mark, 6 to Plain, 11 to Comment), yamlLine("name: a#b  # c"))
    }

    @Test
    fun `элемент списка и строка в кавычках`() {
        assertEquals(listOf(2 to Mark, 4 to Text), yamlLine("  - \"DOMAIN,ya.ru,DIRECT\""))
        assertEquals(listOf(0 to Mark, 2 to Key, 6 to Mark, 8 to Text), yamlLine("- name: 'Узел'"))
    }

    @Test
    fun `узел одной строкой`() {
        val line = "- {name: A, port: 443}"

        assertEquals(
            listOf(0 to Mark, 3 to Key, 7 to Mark, 9 to Plain, 10 to Mark, 12 to Key, 16 to Mark, 18 to Number, 21 to Mark),
            yamlLine(line),
        )
    }

    @Test
    fun `двоеточие не после ключа — разбор не зацикливается`() {
        assertEquals(listOf(0 to Mark, 1 to Plain), yamlLine("[:"))
        assertEquals(listOf(0 to Key, 1 to Mark, 3 to Plain), yamlLine("a: b: c"))
    }

    @Test
    fun `якорь и ссылка`() {
        assertEquals(listOf(0 to Key, 4 to Mark, 6 to Anchor), yamlLine("base: &b"))
        assertEquals(listOf(2 to Key, 4 to Mark, 6 to Anchor), yamlLine("  <<: *b"))
    }
}
