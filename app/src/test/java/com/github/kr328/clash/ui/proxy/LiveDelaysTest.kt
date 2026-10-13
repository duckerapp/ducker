package com.github.kr328.clash.ui.proxy

import com.github.kr328.clash.store.DelayStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** Delays shown while a check is still running (F-271). */
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
    fun `a delay the core has by now replaces the one on screen`() {
        val shown = overlay(listOf(main), mapOf("Paris" to 120, "Oslo" to 80))

        assertEquals(listOf("Paris" to 120, "Tokyo" to DelayStore.NO_ANSWER, "Oslo" to 80), delays(shown))
    }

    @Test
    fun `a node without an answer keeps what it showed`() {
        // 65535 is both "not asked yet" and "did not answer": the verdict waits for the end.
        val shown = overlay(listOf(main), mapOf("Paris" to 65535, "Tokyo" to 65535))

        assertEquals(listOf(main), shown)
    }

    @Test
    fun `the order of the list is left alone`() {
        val shown = overlay(listOf(main), mapOf("Oslo" to 10, "Paris" to 900))

        assertEquals(listOf("Paris", "Tokyo", "Oslo"), shown.single().proxies.map { it.name })
    }

    @Test
    fun `the same node is updated in every group that holds it`() {
        val other = main.copy(name = "Video")

        val shown = overlay(listOf(main, other), mapOf("Oslo" to 80))

        assertEquals(listOf(80, 80), shown.map { group -> group.proxies.first { it.name == "Oslo" }.delay })
    }
}
