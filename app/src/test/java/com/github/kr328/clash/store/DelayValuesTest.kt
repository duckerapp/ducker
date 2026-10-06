package com.github.kr328.clash.store

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Значения ядра и хранилища (D-72). Проверка нужна ровно потому, что 0xffff однажды
 * уже утёк в хранилище и показался на экране как «65535 мс».
 */
class DelayValuesTest {
    @Test
    fun `служебные значения ядра не считаются замером`() {
        assertEquals(DelayStore.UNKNOWN, DelayStore.ofCore(65535))
        assertEquals(DelayStore.UNKNOWN, DelayStore.ofCore(0))
        assertEquals(DelayStore.UNKNOWN, DelayStore.ofCore(70000))
        assertEquals(70, DelayStore.ofCore(70))
    }

    @Test
    fun `на экран идёт свежий замер, иначе прошлый`() {
        assertEquals(70, DelayStore.shown(core = 70, stored = 120))
        assertEquals(120, DelayStore.shown(core = 65535, stored = 120))
        assertEquals(DelayStore.NO_ANSWER, DelayStore.shown(core = 65535, stored = DelayStore.NO_ANSWER))
        assertEquals(DelayStore.UNKNOWN, DelayStore.shown(core = 65535, stored = null))
    }

    @Test
    fun `один промах не считается отказом`() {
        // Замер мог уйти в момент переключения туннеля — узел при этом живой.
        val (first, misses) = DelayStore.verdict(core = 65535, misses = 0)

        assertEquals(null, first)
        assertEquals(1, misses)

        val (second, next) = DelayStore.verdict(core = 65535, misses = misses)

        assertEquals(DelayStore.NO_ANSWER, second)
        assertEquals(2, next)
    }

    @Test
    fun `удачный замер обнуляет промахи`() {
        val (value, misses) = DelayStore.verdict(core = 70, misses = 1)

        assertEquals(70, value)
        assertEquals(0, misses)
    }
}
