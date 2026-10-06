package com.github.kr328.clash.ui.rules

import com.github.kr328.clash.service.model.UserRule.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ввод человека — в значение своего правила (D-123). */
class RuleValueTest {
    @Test
    fun `сайт можно вставить ссылкой`() {
        assertEquals("sberbank.ru", ruleValue(Type.Site, " https://www.sberbank.ru/ru/person?x=1 "))
        assertEquals("online.sberbank.ru", ruleValue(Type.Site, "online.sberbank.ru:443"))
        assertEquals("example.com", ruleValue(Type.Site, "*.Example.COM"))
    }

    @Test
    fun `кириллический домен — в punycode`() {
        assertEquals("xn--c1aapkosapc.xn--p1ai", ruleValue(Type.Site, "госуслуги.рф"))
    }

    @Test
    fun `не сайт`() {
        assertNull(ruleValue(Type.Site, "localhost"))
        assertNull(ruleValue(Type.Site, "bad,rule.ru"))
        assertNull(ruleValue(Type.Site, ""))
    }

    @Test
    fun `адрес и сеть`() {
        assertEquals("192.168.0.0/16", ruleValue(Type.Ip, "192.168.0.0/16"))
        assertEquals("1.2.3.4", ruleValue(Type.Ip, "1.2.3.4"))
        assertEquals("2001:db8::/32", ruleValue(Type.Ip, "2001:db8::/32"))
        assertNull(ruleValue(Type.Ip, "300.1.1.1"))
        assertNull(ruleValue(Type.Ip, "10.0.0.0/33"))
        assertNull(ruleValue(Type.Ip, "10.0.0"))
        assertNull(ruleValue(Type.Ip, "10.0.0.0/"))
    }

    @Test
    fun `имя пакета`() {
        assertEquals("ru.gosuslugi.app", ruleValue(Type.App, "ru.gosuslugi.app"))
        assertNull(ruleValue(Type.App, "Госуслуги"))
    }

    @Test
    fun `слово, категория, страна`() {
        assertEquals("yandex", ruleValue(Type.Keyword, " Yandex "))
        assertNull(ruleValue(Type.Keyword, "y"))
        assertNull(ruleValue(Type.Keyword, "bad,rule"))
        assertEquals("category-ru", ruleValue(Type.Geosite, "CATEGORY-RU"))
        assertNull(ruleValue(Type.Geosite, "bad rule"))
        assertEquals("RU", ruleValue(Type.Geoip, "ru"))
        assertNull(ruleValue(Type.Geoip, "XX"))
        assertNull(ruleValue(Type.Geoip, "RUS"))
    }

    @Test
    fun `имена категорий из базы geosite`() {
        // Две записи: имя и домен (поле 2), который читать не нужно.
        fun entry(name: String, domain: String): ByteArray {
            val body = byteArrayOf(0x0a, name.length.toByte()) + name.toByteArray() +
                byteArrayOf(0x12, domain.length.toByte()) + domain.toByteArray()

            return byteArrayOf(0x0a, body.size.toByte()) + body
        }

        val data = entry("YOUTUBE", "youtube.com") + entry("CATEGORY-RU", "ya.ru")

        assertEquals(setOf("youtube", "category-ru"), geositeCategories(data))
    }
}
