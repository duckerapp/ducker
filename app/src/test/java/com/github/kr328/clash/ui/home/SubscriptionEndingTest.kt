package com.github.kr328.clash.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Предупреждение «подписка кончается» на главной (D-119). */
class SubscriptionEndingTest {
    private val now = 1_000_000_000_000L
    private val gb = 1L shl 30

    @Test
    fun `дни — с округлением вверх`() {
        assertEquals(Ending.Days(3), ending(now + DAY_MS * 5 / 2, now, 0, 0))
        assertEquals(Ending.Days(1), ending(now + 1000, now, 0, 0))
    }

    @Test
    fun `срок прошёл — закончилась`() {
        assertEquals(Ending.Expired, ending(now - 1, now, 0, 0))
    }

    @Test
    fun `больше трёх дней и трафика вдоволь — тихо`() {
        assertNull(ending(now + DAY_MS * 4, now, 500 * gb, 134 * gb))
        assertNull(ending(0, now, 0, 0))
    }

    @Test
    fun `трафика меньше десятой части`() {
        assertEquals(Ending.Traffic(40 * gb), ending(0, now, 500 * gb, 460 * gb))
        assertEquals(Ending.Traffic(0), ending(0, now, 500 * gb, 510 * gb))
    }

    @Test
    fun `срок важнее трафика`() {
        assertEquals(Ending.Days(2), ending(now + DAY_MS * 2, now, 500 * gb, 490 * gb))
    }
}
