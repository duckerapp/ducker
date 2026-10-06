package com.github.kr328.clash.ui.home

import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.proxy.NodeState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Отбор узлов для быстрой смены на главной (D-116). */
class QuickNodesTest {
    private val auto = NodeState("⚡ AUTO", "Fallback", 65)
    private val bridge = NodeState("Мост Auto", "URLTest", 67)
    private val fast = NodeState("Frankfurt", "Vless", 70)
    private val slow = NodeState("Barcelona", "Vless", 108)
    private val dead = NodeState("Tokyo", "Vless", DelayStore.NO_ANSWER)
    private val unknown = NodeState("Paris", "Vless", 0)
    private val all = listOf(slow, dead, auto, unknown, fast, bridge)

    private fun names(nodes: List<NodeState>) = nodes.map { it.name }

    @Test
    fun `сначала автовыбор, потом по порядку «Прокси», а не по скорости`() {
        assertEquals(
            listOf("⚡ AUTO", "Мост Auto", "Barcelona", "Tokyo", "Paris", "Frankfurt"),
            names(quickNodes(all, now = "⚡ AUTO", slots = 10)),
        )
    }

    @Test
    fun `избранное — сразу за автовыбором`() {
        val nodes = all.map { if (it == slow) it.copy(favorite = true) else it }

        assertEquals(
            listOf("⚡ AUTO", "Мост Auto", "Barcelona", "Tokyo"),
            names(quickNodes(nodes, now = "⚡ AUTO", slots = 4)),
        )
    }

    @Test
    fun `выбранный сейчас виден, даже если не влез`() {
        assertEquals(
            listOf("⚡ AUTO", "Мост Auto", "Tokyo"),
            names(quickNodes(all, now = "Tokyo", slots = 3)),
        )
    }

    @Test
    fun `без места — пусто`() {
        assertEquals(emptyList(), quickNodes(all, now = "⚡ AUTO", slots = 0))
    }
}
