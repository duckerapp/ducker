package com.github.kr328.clash.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputShownTest {
    @Test
    fun `a text up to the limit is shown whole and stays editable`() {
        val state = SubscriptionsState(input = "a".repeat(FIELD_LIMIT))

        assertFalse(state.inputLong)
        assertEquals(state.input, state.inputShown)
    }

    @Test
    fun `a longer text is shown by its beginning`() {
        val state = SubscriptionsState(input = "a".repeat(FIELD_LIMIT + 1))

        assertTrue(state.inputLong)
        assertEquals("a".repeat(FIELD_LIMIT) + "…", state.inputShown)
    }

    @Test
    fun `the cut does not split a character that takes two`() {
        // Half of a flag, as in a node's name.
        val state = SubscriptionsState(input = "a".repeat(FIELD_LIMIT - 1) + "🇩" + "tail")

        assertEquals("a".repeat(FIELD_LIMIT - 1) + "…", state.inputShown)
    }
}
