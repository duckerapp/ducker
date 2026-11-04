package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficNoticeTest {
    private val total = 100L * GB

    @Test
    fun `each threshold is told once, in order`() {
        var seen: TrafficSeen? = decideTraffic("a", 10 * GB, total, null).seen

        val told = listOf(50L, 79L, 80L, 85L, 94L, 95L, 99L, 100L, 120L).mapNotNull { used ->
            decideTraffic("a", used * GB, total, seen).also { seen = it.seen }.notify
        }

        assertEquals(listOf(80, 95), told)
    }

    @Test
    fun `a jump past both thresholds tells the higher one only`() {
        val decision = decideTraffic("a", 97 * GB, total, TrafficSeen("a", 0))

        assertEquals(95, decision.notify)
        assertEquals(TrafficSeen("a", 95), decision.seen)
    }

    @Test
    fun `a new or another active subscription stays silent about passed thresholds`() {
        val first = decideTraffic("a", 90 * GB, total, null)
        assertNull(first.notify)
        assertFalse(first.clear)
        assertEquals(TrafficSeen("a", 80), first.seen)

        val other = decideTraffic("b", 96 * GB, total, TrafficSeen("a", 80))
        assertNull(other.notify)
        assertTrue(other.clear)
        assertEquals(TrafficSeen("b", 95), other.seen)
    }

    @Test
    fun `a reset counter or bought traffic clears and tells again in the new period`() {
        val reset = decideTraffic("a", 1 * GB, total, TrafficSeen("a", 95))
        assertNull(reset.notify)
        assertTrue(reset.clear)
        assertEquals(TrafficSeen("a", 0), reset.seen)

        assertEquals(80, decideTraffic("a", 81 * GB, total, reset.seen).notify)

        // A bigger plan: 96 of 100 → 96 of 200 drops below both.
        assertEquals(TrafficSeen("a", 0), decideTraffic("a", 96 * GB, 2 * total, TrafficSeen("a", 95)).seen)
    }

    @Test
    fun `a huge unlimited total does not overflow`() {
        assertNull(decideTraffic("a", 500 * GB, Long.MAX_VALUE, TrafficSeen("a", 0)).notify)
    }

    @Test
    fun `stored text survives a round trip and junk is ignored`() {
        assertEquals(TrafficSeen("a", 80), TrafficSeen.parse(TrafficSeen("a", 80).toString()))
        assertNull(TrafficSeen.parse(""))
        assertNull(TrafficSeen.parse("a|x"))
    }

    private companion object {
        const val GB = 1L shl 30
    }
}
