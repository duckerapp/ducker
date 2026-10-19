package com.github.kr328.clash.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Only a drag begun at the list's top pulls the circle, and every release puts it somewhere. */
@OptIn(ExperimentalMaterial3Api::class)
class PullGestureTest {
    private class Circle : PullToRefreshState {
        val calls = mutableListOf<String>()
        override var distanceFraction = 0f
        override val isAnimating = false
        override suspend fun animateToThreshold() { calls += "threshold"; distanceFraction = 1f }
        override suspend fun animateToHidden() { calls += "hidden"; distanceFraction = 0f }
        override suspend fun snapTo(targetValue: Float) { distanceFraction = targetValue }
    }

    private val circle = Circle()
    private var refreshes = 0
    private val pull = PullGesture(circle, CoroutineScope(Dispatchers.Unconfined)).apply {
        threshold = 100f
        onRefresh = { refreshes++ }
    }

    private fun finger(consumed: Float, available: Float) =
        pull.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput).y

    @Test
    fun `a drag from the top pulls, a short one hides on release`() {
        pull.start()
        assertEquals(40f, finger(0f, 40f))
        assertEquals(0.2f, circle.distanceFraction)

        pull.release()
        assertEquals(listOf("hidden"), circle.calls)
        assertEquals(0, refreshes)
    }

    @Test
    fun `a drag that scrolled up to the top leaves the rest to the list`() {
        pull.start()
        assertEquals(0f, finger(30f, 0f))
        assertEquals(0f, finger(10f, 40f))
        assertEquals(0f, circle.distanceFraction)
        assertFalse(pull.pulling)
    }

    @Test
    fun `a full pull refreshes and a refresh that never starts hides the circle`() = runBlocking {
        pull.start()
        finger(0f, 250f)
        pull.release()
        assertEquals(1, refreshes)
        assertEquals(listOf("threshold"), circle.calls)

        delay(700)
        assertEquals(listOf("threshold", "hidden"), circle.calls)
    }

    @Test
    fun `the finger going back up gives the pull back first, and a fling down after it is eaten`() = runBlocking {
        pull.start()
        finger(0f, 60f)
        val back = pull.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput).y
        assertEquals(-60f, back)
        assertFalse(pull.pulling)

        pull.start()
        finger(0f, 20f)
        pull.release()
        assertEquals(Velocity(0f, 300f), pull.onPreFling(Velocity(0f, 300f)))
        assertTrue(pull.onPreFling(Velocity(0f, 300f)) == Velocity.Zero)
    }
}
