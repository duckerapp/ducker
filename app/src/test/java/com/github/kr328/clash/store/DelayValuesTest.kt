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

    @Test
    fun `a node without a verdict is worth another check, a silent one is not`() {
        // Never measured, and measured before but not in this run of the core.
        assertEquals(true, DelayStore.undecided(core = 65535, stored = null))
        assertEquals(true, DelayStore.undecided(core = 65535, stored = 120))

        // Written off after two misses: the core keeps no delay for it after any check.
        assertEquals(false, DelayStore.undecided(core = 65535, stored = DelayStore.NO_ANSWER))

        assertEquals(false, DelayStore.undecided(core = 70, stored = null))
        assertEquals(false, DelayStore.undecided(core = 70, stored = DelayStore.NO_ANSWER))
    }

    @Test
    fun `a first miss gets its second attempt`() {
        val alive = 70 to 70
        val missed = 65535 to 120
        val silent = 65535 to DelayStore.NO_ANSWER

        assertEquals(true, DelayStore.recheck(tried = 0, nodes = listOf(alive, missed)))
        assertEquals(true, DelayStore.recheck(tried = 1, nodes = listOf(alive, missed, silent)))

        // Nodes written off earlier do not ask for a check on their own.
        assertEquals(false, DelayStore.recheck(tried = 0, nodes = listOf(alive, silent)))
        assertEquals(false, DelayStore.recheck(tried = 1, nodes = listOf(alive, silent)))
    }

    @Test
    fun `there is no third check while somebody answers`() {
        // A list where nodes answer every other time always has a fresh miss.
        val alive = 70 to 70
        val missed = 65535 to 120

        assertEquals(false, DelayStore.recheck(tried = 2, nodes = listOf(alive, missed)))
    }

    @Test
    fun `a check that brought nothing at all is made once more`() {
        // Two checks lost while the tunnel was coming up have written everybody off.
        val lost = 65535 to DelayStore.NO_ANSWER

        assertEquals(true, DelayStore.recheck(tried = 2, nodes = listOf(lost, lost)))
        assertEquals(false, DelayStore.recheck(tried = 2, nodes = emptyList()))
    }
}
