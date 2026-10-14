package com.github.kr328.clash.ui.subscriptions

import kotlin.test.Test
import kotlin.test.assertEquals

/** Порядок подписок, заданный перетаскиванием (этап 13, пакет 5). */
class ProfileOrderTest {
    @Test
    fun `известные — по сохранённому порядку, новые — в конце, как пришли`() {
        val fromService = listOf("a", "b", "new1", "c", "new2")

        assertEquals(listOf("c", "a", "b", "new1", "new2"), fromService.inOrder(listOf("c", "a", "b")) { it })
    }

    @Test
    fun `удалённые из сохранённого порядка ничего не ломают`() {
        assertEquals(listOf("b", "a"), listOf("a", "b").inOrder(listOf("gone", "b", "a")) { it })
    }

    @Test
    fun `on screen the active comes first, then favourites, each group in the stored order`() {
        val stored = listOf("a", "B", "c", "active", "D", "e")

        assertEquals(
            listOf("active", "B", "D", "a", "c", "e"),
            stored.grouped({ it == "active" }, { it.first().isUpperCase() }),
        )
    }

    @Test
    fun `a drag within a group keeps the other group's places in the stored order`() {
        val stored = listOf("a", "B", "c", "active", "D", "e")
        val favorite = { s: String -> s.first().isUpperCase() }

        // On screen “e” is dragged up over “c”, and “a” down over “c”: rows of the rest group.
        val up = stored.moved("e", "c") { it }
        val down = stored.moved("a", "c") { it }

        assertEquals(listOf("a", "B", "e", "c", "active", "D"), up)
        assertEquals(listOf("active", "B", "D", "a", "e", "c"), up.grouped({ it == "active" }, favorite))
        assertEquals(listOf("B", "c", "a", "active", "D", "e"), down)
        assertEquals(listOf("active", "B", "D", "c", "a", "e"), down.grouped({ it == "active" }, favorite))
        assertEquals(stored, stored.moved("gone", "c") { it })
    }

    @Test
    fun `без сохранённого порядка — как пришли`() {
        // Пустая строка настроек даёт после split одну пустую строку.
        assertEquals(listOf("a", "b"), listOf("a", "b").inOrder("".split(',')) { it })
    }
}
