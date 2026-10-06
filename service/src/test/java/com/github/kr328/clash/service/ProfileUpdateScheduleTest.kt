package com.github.kr328.clash.service

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileUpdateScheduleTest {
    private fun hours(value: Long) = TimeUnit.HOURS.toMillis(value)
    private val now = hours(100)

    @Test
    fun `disabled updates stay disabled after failure`() {
        for (interval in listOf(0L, -1L, TimeUnit.MINUTES.toMillis(14))) {
            assertNull(nextProfileUpdateAt(interval, now, now))
            assertNull(nextProfileUpdateAt(interval, now, now, failures = 1))
        }
    }

    @Test
    fun `ordinary updates preserve the chosen interval`() {
        assertEquals(now + hours(20), nextProfileUpdateAt(hours(24), now - hours(4), now))
    }

    @Test
    fun `ordinary updates respect the four hour minimum`() {
        assertEquals(now + hours(3), nextProfileUpdateAt(hours(1), now - hours(1), now))
    }

    @Test
    fun `overdue ordinary update can run now`() {
        assertEquals(now, nextProfileUpdateAt(hours(4), now - hours(12), now))
    }

    @Test
    fun `failed update retries from now rather than the old file date`() {
        for (last in listOf(0L, now - hours(48), now, now + hours(48))) {
            val next = nextProfileUpdateAt(hours(24), last, now, failures = 1)
            assertEquals(now + hours(1), next)
            assertTrue(next!! > now)
        }
    }

    @Test
    fun `repeated failures back off and stop growing after four hours`() {
        for ((attempt, delay) in listOf(1 to 1L, 2 to 2L, 3 to 4L, 100 to 4L)) {
            assertEquals(now + hours(delay), nextProfileUpdateAt(hours(24), 0, now, attempt))
        }
    }

    @Test
    fun `success returns to the ordinary schedule`() {
        assertEquals(now + hours(4), nextProfileUpdateAt(hours(4), now, now, failures = 0))
    }
}
