package com.github.kr328.clash.service

import kotlin.test.Test
import kotlin.test.assertEquals

/** The numbering a reader of the log relies on (D-290). */
class LogTailTest {
    @Test
    fun `a reader gets what came after its place`() {
        val ring = LogRing<Int>(5)

        assertEquals(emptyList(), ring.from(0))

        repeat(3) { ring.add(it) }

        assertEquals(listOf(0, 1, 2), ring.from(0))
        assertEquals(listOf(2), ring.from(2))
        assertEquals(emptyList(), ring.from(ring.next))
    }

    @Test
    fun `lines pushed out are skipped and the numbers go on`() {
        val ring = LogRing<Int>(5)

        repeat(12) { ring.add(it) }

        assertEquals(12, ring.next)
        assertEquals(listOf(7, 8, 9, 10, 11), ring.from(0))
        assertEquals(listOf(7, 8, 9, 10, 11), ring.from(3))
        assertEquals(listOf(10, 11), ring.from(10))
    }

    @Test
    fun `clearing keeps the place and a place beyond the end gives nothing`() {
        val ring = LogRing<Int>(5)

        repeat(4) { ring.add(it) }
        ring.clear()

        assertEquals(4, ring.next)
        assertEquals(emptyList(), ring.from(2))

        ring.add(4)

        assertEquals(listOf(4), ring.from(4))
        assertEquals(listOf(4), ring.from(0))
        assertEquals(emptyList(), ring.from(100))
    }
}
