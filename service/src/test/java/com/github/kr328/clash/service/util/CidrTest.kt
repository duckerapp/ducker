package com.github.kr328.clash.service.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверка арифметики маршрутов (D-56). Ошибка здесь означает либо утечку трафика мимо
 * туннеля, либо втягивание в туннель сети, которую профиль просил обойти, — поэтому
 * проверяем и границы, и покрытие целиком.
 */
class CidrTest {
    @Test
    fun `без исключений весь интернет одним блоком`() {
        assertEquals(listOf(Cidr.Block("0.0.0.0", 0)), Cidr.complementV4(emptyList()))
    }

    @Test
    fun `исключённая сеть не покрыта, соседние адреса покрыты`() {
        val blocks = Cidr.complementV4(listOf("10.0.0.0/8"))

        assertTrue(!covers(blocks, "10.0.0.0"))
        assertTrue(!covers(blocks, "10.255.255.255"))
        assertTrue(covers(blocks, "9.255.255.255"))
        assertTrue(covers(blocks, "11.0.0.0"))
    }

    @Test
    fun `покрыто ровно то, что не исключено`() {
        val excluded = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/3",
        )
        val blocks = Cidr.complementV4(excluded)

        for (probe in listOf(
            "1.1.1.1", "8.8.8.8", "9.9.9.9", "10.1.2.3", "100.63.255.255", "100.64.0.1",
            "100.127.255.255", "100.128.0.1", "127.0.0.1", "169.254.1.1", "172.15.255.255",
            "172.20.0.1", "172.32.0.1", "192.167.255.255", "192.168.1.1", "192.169.0.1",
            "203.0.113.1", "223.255.255.255", "224.0.0.1", "255.255.255.255",
        )) {
            val shouldBeCovered = excluded.none { inside(it, probe) }

            assertEquals(shouldBeCovered, covers(blocks, probe), "адрес $probe")
        }
    }

    @Test
    fun `битые записи и IPv6 пропускаются`() {
        val blocks = Cidr.complementV4(listOf("10.0.0.0/8", "нет", "1.2.3", "fd00::/8", "1.2.3.4/33"))

        assertTrue(!covers(blocks, "10.0.0.1"))
        assertTrue(covers(blocks, "1.2.3.4"))
    }

    @Test
    fun `пересекающиеся и вложенные исключения не дают дыр`() {
        val blocks = Cidr.complementV4(listOf("10.0.0.0/8", "10.1.0.0/16", "9.0.0.0/8"))

        assertTrue(!covers(blocks, "9.1.1.1"))
        assertTrue(!covers(blocks, "10.1.1.1"))
        assertTrue(!covers(blocks, "10.200.1.1"))
        assertTrue(covers(blocks, "11.1.1.1"))
    }

    @Test
    fun `весь интернет в исключениях не даёт ни одного маршрута`() {
        assertEquals(emptyList(), Cidr.complementV4(listOf("0.0.0.0/0")))
    }

    private fun covers(blocks: List<Cidr.Block>, address: String): Boolean =
        blocks.any { inside("${it.address}/${it.prefix}", address) }

    private fun inside(cidr: String, address: String): Boolean {
        val start = ip(cidr.substringBefore('/'))
        val size = 1L shl (32 - cidr.substringAfter('/').toInt())
        val value = ip(address)

        return value >= start && value < start + size
    }

    private fun ip(text: String): Long =
        text.split('.').fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
}
