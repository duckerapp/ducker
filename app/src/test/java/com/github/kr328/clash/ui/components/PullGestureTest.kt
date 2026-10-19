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

    private fun step(consumed: Float, available: Float) =
        pull.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput).y

    @Test
    fun `a drag from the top pulls, a short one hides at its fling`() = runBlocking {
        pull.down()
        assertEquals(40f, step(0f, 40f))
        assertEquals(0.2f, circle.distanceFraction)

        pull.up()
        // The drag's last step comes after the finger's up and still belongs to the pull.
        step(0f, 10f)
        assertEquals(0.25f, circle.distanceFraction)

        pull.onPreFling(Velocity.Zero)
        assertEquals(listOf("hidden"), circle.calls)
        assertEquals(0, refreshes)
        delay(200)
        assertEquals(listOf("hidden"), circle.calls)
    }

    @Test
    fun `a drag that scrolled up to the top leaves the rest to the list`() {
        pull.down()
        assertEquals(0f, step(30f, 0f))
        assertEquals(0f, step(10f, 40f))
        assertEquals(0f, circle.distanceFraction)
        assertFalse(pull.pulling)
    }

    @Test
    fun `a full pull refreshes, and a refresh that never starts hides the circle`() = runBlocking {
        pull.down()
        step(0f, 250f)
        pull.up()
        assertEquals(Velocity(0f, 300f), pull.onPreFling(Velocity(0f, 300f)))
        assertEquals(1, refreshes)
        assertEquals(listOf("threshold"), circle.calls)

        delay(700)
        assertEquals(listOf("threshold", "hidden"), circle.calls)
    }

    @Test
    fun `without a fling the finger's up releases a moment later`() = runBlocking {
        pull.down()
        step(0f, 60f)
        pull.up()
        assertEquals(emptyList(), circle.calls)

        delay(200)
        assertEquals(listOf("hidden"), circle.calls)
        assertEquals(Velocity.Zero, pull.onPreFling(Velocity(0f, 300f)))
    }

    @Test
    fun `a drag catches the circle on its way back and the finger going up gives the pull back`() {
        circle.distanceFraction = 0.5f
        pull.down()
        step(0f, 10f)
        assertEquals(0.55f, circle.distanceFraction, 1e-6f)

        assertEquals(-110f, pull.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput).y)
        assertFalse(pull.pulling)
    }

    @Test
    fun `a check started elsewhere stays off the circle, and a pull during it adopts it`() = runBlocking {
        pull.refreshing = true
        assertFalse(pull.own)

        pull.down()
        step(0f, 250f)
        pull.up()
        pull.onPreFling(Velocity.Zero)
        assertEquals(0, refreshes)
        assertTrue(pull.own)

        pull.refreshing = false
        pull.finished()
        assertFalse(pull.own)
        assertEquals(listOf("threshold", "hidden"), circle.calls)
    }
}
