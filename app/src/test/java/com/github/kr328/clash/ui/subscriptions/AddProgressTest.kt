package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.core.model.FetchStatus.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddProgressTest {
    private fun status(action: Action, progress: Int = -1, max: Int = -1) =
        FetchStatus(action, emptyList(), progress, max)

    @Test
    fun `fill only grows through a normal download`() {
        val steps = listOf(status(Action.FetchConfiguration)) +
            (0 until 26).map { status(Action.FetchProviders, it, 26) } +
            status(Action.Verifying)
        val parts = steps.map { addProgress(it)!! }

        assertEquals(parts.sorted(), parts)
        assertTrue(parts.first() > 0f && parts.last() < 1f)
    }

    @Test
    fun `provider count out of range stays inside the bar`() {
        assertEquals(addProgress(status(Action.Verifying))!!, addProgress(status(Action.FetchProviders, 99, 26))!!, 0.001f)
        assertNull(addProgress(status(Action.FetchProviders, 0, 0)))
    }

    @Test
    fun `subscription headers are not progress`() {
        assertNull(addProgress(status(Action.SubscriptionInfo)))
        assertNull(addProgress(status(Action.SubscriptionHeaders)))
    }

    @Test
    fun `each try says whether it is a backup address`() {
        assertEquals(false, backupTry(FetchStatus(Action.FetchConfiguration, listOf("sub.example.com"), -1, -1)))
        assertEquals(true, backupTry(FetchStatus(Action.FetchConfiguration, listOf("b.example.org", "backup"), -1, -1)))
        assertNull(backupTry(status(Action.Verifying)))
    }
}
