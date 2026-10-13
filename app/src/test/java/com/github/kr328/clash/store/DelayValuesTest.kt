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
    fun `the last result of a node is a delay, no answer, or nothing yet`() {
        assertEquals(70, DelayStore.ofCore(delay = 70, tested = 5_000))

        // The core gives the same delay for a node it asked in vain and one it has not asked.
        assertEquals(DelayStore.NO_ANSWER, DelayStore.ofCore(delay = 65535, tested = 5_000))
        assertEquals(DelayStore.UNKNOWN, DelayStore.ofCore(delay = 65535, tested = 0))
    }

    @Test
    fun `what the core knows is shown as it is, the stored value only where it knows nothing`() {
        assertEquals(DelayStore.NO_ANSWER, DelayStore.shown(core = DelayStore.NO_ANSWER, stored = 120))
        assertEquals(70, DelayStore.shown(core = 70, stored = DelayStore.NO_ANSWER))
        assertEquals(120, DelayStore.shown(core = DelayStore.UNKNOWN, stored = 120))
        assertEquals(DelayStore.NO_ANSWER, DelayStore.shown(core = DelayStore.UNKNOWN, stored = DelayStore.NO_ANSWER))
    }

    @Test
    fun `a node that did not answer is marked after that one check and keeps its last delay`() {
        val known = DelayStore.Known(answers = mapOf("Paris" to 100, "Oslo" to 80))
            .after(mapOf("Paris" to DelayStore.NO_ANSWER, "Oslo" to 90))

        // A list shows the miss; "Главная" still has the delay the node last answered with.
        assertEquals(mapOf("Paris" to DelayStore.NO_ANSWER, "Oslo" to 90), known.listed)
        assertEquals(mapOf("Paris" to 100, "Oslo" to 90), known.answers)
    }

    @Test
    fun `an answer takes the mark off at once`() {
        val known = DelayStore.Known(answers = mapOf("Paris" to 100), silent = setOf("Paris", "Tokyo"))
            .after(mapOf("Paris" to 120, "Tokyo" to DelayStore.NO_ANSWER))

        assertEquals(mapOf("Paris" to 120, "Tokyo" to DelayStore.NO_ANSWER), known.listed)
    }

    @Test
    fun `a node the core has not tested is left as it was`() {
        val before = DelayStore.Known(answers = mapOf("Paris" to 100), silent = setOf("Tokyo"))

        assertEquals(before, before.after(mapOf("Paris" to DelayStore.UNKNOWN, "Tokyo" to DelayStore.UNKNOWN)))
    }

    @Test
    fun `a node never heard and silent has no delay to show on Home`() {
        val known = DelayStore.Known().after(mapOf("Tokyo" to DelayStore.NO_ANSWER))

        assertEquals(DelayStore.NO_ANSWER, known.listed["Tokyo"])
        assertEquals(null, known.answers["Tokyo"])
    }

    @Test
    fun `a node without a result is worth a check, a silent one is not`() {
        // Never measured, and measured before but not in this run of the core.
        assertEquals(true, DelayStore.undecided(core = DelayStore.UNKNOWN, stored = null))
        assertEquals(true, DelayStore.undecided(core = DelayStore.UNKNOWN, stored = 120))

        // "Did not answer" is a result.
        assertEquals(false, DelayStore.undecided(core = DelayStore.NO_ANSWER, stored = DelayStore.NO_ANSWER))
        assertEquals(false, DelayStore.undecided(core = DelayStore.UNKNOWN, stored = DelayStore.NO_ANSWER))

        assertEquals(false, DelayStore.undecided(core = 70, stored = null))
        assertEquals(false, DelayStore.undecided(core = 70, stored = DelayStore.NO_ANSWER))
    }

    @Test
    fun `the tab checks a group once, and a node that did not answer is not asked again`() {
        val alive = 70 to 70
        val unmeasured = DelayStore.UNKNOWN to 120
        val silent = DelayStore.NO_ANSWER to DelayStore.NO_ANSWER

        assertEquals(true, DelayStore.recheck(tried = 0, nodes = listOf(alive, unmeasured)))
        assertEquals(false, DelayStore.recheck(tried = 0, nodes = listOf(alive, silent)))

        // After the check somebody is silent, somebody answered: that is the result.
        assertEquals(false, DelayStore.recheck(tried = 1, nodes = listOf(alive, silent)))
        assertEquals(false, DelayStore.recheck(tried = 1, nodes = listOf(alive, unmeasured)))
    }

    @Test
    fun `a result taken before the last change of network belongs to another network`() {
        assertEquals(true, DelayStore.outdated(tested = 1_000, changedAt = 5_000))
        assertEquals(false, DelayStore.outdated(tested = 5_000, changedAt = 5_000))
        assertEquals(false, DelayStore.outdated(tested = 9_000, changedAt = 5_000))

        // The core has not tested the node; the network has not changed under this tunnel.
        assertEquals(false, DelayStore.outdated(tested = 0, changedAt = 5_000))
        assertEquals(false, DelayStore.outdated(tested = 1_000, changedAt = 0))
    }

    @Test
    fun `results of another network are worth one check, whatever they say`() {
        val alive = 70 to 70
        val silent = DelayStore.NO_ANSWER to DelayStore.NO_ANSWER

        assertEquals(false, DelayStore.recheck(tried = 0, nodes = listOf(alive, silent)))
        assertEquals(true, DelayStore.recheck(tried = 0, nodes = listOf(alive, silent), outdated = true))

        // What that check brought is the result on this network: no second one.
        assertEquals(false, DelayStore.recheck(tried = 1, nodes = listOf(alive, silent), outdated = true))
    }

    @Test
    fun `a check that brought nothing at all is made once more`() {
        // Lost while the tunnel was coming up: that says nothing about the nodes.
        val lost = DelayStore.NO_ANSWER to DelayStore.NO_ANSWER

        assertEquals(true, DelayStore.recheck(tried = 1, nodes = listOf(lost, lost)))
        assertEquals(false, DelayStore.recheck(tried = 1, nodes = emptyList()))
    }
}
