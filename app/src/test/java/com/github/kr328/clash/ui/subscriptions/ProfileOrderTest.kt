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
    fun `без сохранённого порядка — как пришли`() {
        // Пустая строка настроек даёт после split одну пустую строку.
        assertEquals(listOf("a", "b"), listOf("a", "b").inOrder("".split(',')) { it })
    }
}
