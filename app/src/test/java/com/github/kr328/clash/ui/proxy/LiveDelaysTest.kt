package com.github.kr328.clash.ui.proxy

import com.github.kr328.clash.store.DelayStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** The result of each node shown while the check of its group is still running (D-282). */
class LiveDelaysTest {
    private val main = ProxyGroupState(
        name = "Main",
        now = "Paris",
        icon = "",
        proxies = listOf(
            NodeState("Paris", "Vless", 300),
            NodeState("Tokyo", "Vless", DelayStore.NO_ANSWER),
            NodeState("Oslo", "Vless", DelayStore.UNKNOWN),
        ),
    )

    private fun delays(groups: List<ProxyGroupState>) = groups.single().proxies.map { it.name to it.delay }

    @Test
    fun `a node shows its result the moment the core has tested it`() {
        // Paris was asked in vain, Oslo answered; Tokyo has not been asked in this run of the core.
        val list = overlay(
            listOf(main),
            mapOf("Paris" to DelayStore.NO_ANSWER, "Tokyo" to DelayStore.UNKNOWN, "Oslo" to 80),
        )

        assertEquals(
            listOf("Paris" to DelayStore.NO_ANSWER, "Tokyo" to DelayStore.NO_ANSWER, "Oslo" to 80),
            delays(list),
        )
    }

    @Test
    fun `a node the core has not tested keeps what it showed`() {
        val list = overlay(listOf(main), mapOf("Paris" to DelayStore.UNKNOWN, "Oslo" to DelayStore.UNKNOWN))

        assertEquals(listOf(main), list)
    }

    @Test
    fun `nodes outside the check and the order of the list are left alone`() {
        val list = overlay(listOf(main), mapOf("Oslo" to 10))

        assertEquals(listOf("Paris", "Tokyo", "Oslo"), list.single().proxies.map { it.name })
        assertEquals(main.proxies.take(2), list.single().proxies.take(2))
    }

    @Test
    fun `the same node is updated in every group that holds it`() {
        val other = main.copy(name = "Video")

        val list = overlay(listOf(main, other), mapOf("Oslo" to 80))

        assertEquals(listOf(80, 80), list.map { group -> group.proxies.first { it.name == "Oslo" }.delay })
    }
}
