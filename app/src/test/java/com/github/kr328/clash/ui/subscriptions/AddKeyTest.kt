package com.github.kr328.clash.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddKeyTest {
    private val a = InstallLink("https://a.example/sub", key = "AGE-SECRET-KEY-A")
    private val b = InstallLink("https://b.example/sub")

    private fun SubscriptionsState.take(link: InstallLink) = copy(input = link.link, given = link).keyOf(link, given)

    @Test
    fun linkWithoutKeyDropsThePreviousLinksKey() {
        val state = SubscriptionsState().take(a).take(b)

        assertEquals("", state.addKey)
        assertNull(state.addedKey(b.link))
    }

    @Test
    fun linkWithoutKeyKeepsATypedKey() {
        val state = SubscriptionsState().copy(addKey = "AGE-SECRET-KEY-TYPED").take(b)

        assertEquals("AGE-SECRET-KEY-TYPED", state.addedKey(b.link))
    }

    @Test
    fun keyGoesOnlyWithItsLink() {
        val state = SubscriptionsState().take(a)

        assertEquals(a.key, state.addedKey(a.link))
        // The person typed another link over the scanned one.
        assertNull(state.copy(input = b.link).addedKey(b.link))
    }
}
