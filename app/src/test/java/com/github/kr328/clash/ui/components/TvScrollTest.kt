package com.github.kr328.clash.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Прокрутка к фокусу на телевизоре (D-161): когда список стоит, а когда подъезжает, и пружина,
 * общая у списка и рамки. Рамка у края списка стоит на месте, только пока обе считают одинаково.
 */
class TvScrollTest {
    private val list = 1000f

    @Test
    fun `элемент в средней части — список стоит`() {
        assertEquals(0f, tvScrollDistance(offset = 300f, size = 200f, containerSize = list))
        // Ровно на границах запаса — тоже.
        assertEquals(0f, tvScrollDistance(offset = 200f, size = 200f, containerSize = list))
        assertEquals(0f, tvScrollDistance(offset = 600f, size = 200f, containerSize = list))
    }

    @Test
    fun `у нижнего края список подъезжает ровно до запаса`() {
        assertEquals(100f, tvScrollDistance(offset = 700f, size = 200f, containerSize = list), absoluteTolerance = 0.01f)
    }

    @Test
    fun `у верхнего края — в обратную сторону`() {
        assertEquals(-150f, tvScrollDistance(offset = 50f, size = 200f, containerSize = list), absoluteTolerance = 0.01f)
    }

    @Test
    fun `крупный элемент встаёт по центру, а не уезжает верхом за край`() {
        // Запас 20 % не помещается: остаётся по 50 с каждой стороны.
        assertEquals(0f, tvScrollDistance(offset = 50f, size = 900f, containerSize = list), absoluteTolerance = 0.01f)
        assertEquals(250f, tvScrollDistance(offset = 300f, size = 900f, containerSize = list), absoluteTolerance = 0.01f)
    }

    @Test
    fun `элемент выше списка — показывается ближний край, а закрывает список целиком — стоит`() {
        assertEquals(40f, tvScrollDistance(offset = 40f, size = 1200f, containerSize = list), absoluteTolerance = 0.01f)
        assertEquals(-100f, tvScrollDistance(offset = -300f, size = 1200f, containerSize = list), absoluteTolerance = 0.01f)
        assertEquals(0f, tvScrollDistance(offset = -40f, size = 1200f, containerSize = list))
    }

    @Test
    fun `у начала списка — до самого начала, если элемент остаётся в средней части`() {
        // Шапка первой группы: запаса ей хватает, но до начала списка осталось 80 — доезжаем.
        assertEquals(-80f, tvScrollDistance(offset = 220f, size = 160f, containerSize = list, toStart = 80f))
        // Ряд ниже после этого вылез бы за нижний запас — список стоит.
        assertEquals(0f, tvScrollDistance(offset = 600f, size = 200f, containerSize = list, toStart = 80f))
        // Список уже в начале, и когда нужно ехать вперёд, начало ни при чём.
        assertEquals(0f, tvScrollDistance(offset = 300f, size = 160f, containerSize = list, toStart = 0f))
        assertEquals(100f, tvScrollDistance(offset = 700f, size = 200f, containerSize = list, toStart = 80f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `пружина стартует с полного пути и доезжает за четверть секунды без возврата`() {
        assertEquals(1f, springLeft(0f), absoluteTolerance = 0.0001f)

        var before = 1f

        for (ms in 16..400 step 16) {
            val left = springLeft(ms / 1000f)

            assertTrue(left in 0f..before, "на $ms мс осталось $left после $before")
            before = left
        }

        assertTrue(springLeft(0.25f) < 0.02f)
    }
}
