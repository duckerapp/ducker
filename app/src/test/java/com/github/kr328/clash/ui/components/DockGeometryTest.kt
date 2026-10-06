package com.github.kr328.clash.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Подсветка вкладок (D-76). Считается в фазе отрисовки на каждом кадре жеста,
 * поэтому проверяется здесь: глазами такую ошибку ловить дорого.
 */
class DockGeometryTest {
    private val width = 300f

    @Test
    fun `на первой вкладке подсветка прижата к левому краю`() {
        assertEquals(0f, dockPillOffset(width, position = 0f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `на последней вкладке правый край совпадает с краем панели`() {
        val offset = dockPillOffset(width, position = 2f)

        assertEquals(width, offset + dockSlotWidth(width), absoluteTolerance = 0.01f)
    }

    @Test
    fun `на середине жеста подсветка ровно между вкладками`() {
        assertEquals(50f, dockPillOffset(width, position = 0.5f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `пружина за краем списка подсветку не двигает`() {
        assertEquals(0f, dockPillOffset(width, position = -0.4f), absoluteTolerance = 0.01f)
        assertEquals(200f, dockPillOffset(width, position = 2.6f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `в правостороннем письме подсветка зеркальна`() {
        assertEquals(200f, dockPillOffset(width, position = 0f, rtl = true), absoluteTolerance = 0.01f)
        assertEquals(0f, dockPillOffset(width, position = 2f, rtl = true), absoluteTolerance = 0.01f)
    }
}
