package com.github.kr328.clash.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Палец у края (D-137): тянет за край, а обратным ходом сначала возвращает содержимое, потом листает. */
class BounceOverscrollTest {
    @Test
    fun `обратный ход сначала гасит сдвиг`() {
        val bounce = BounceOverscroll(CoroutineScope(Dispatchers.Unconfined))
        var scrolled = 0f
        // Список у края: сам ничего не берёт, пока палец тянет наружу; внутрь — берёт всё.
        val list: (Offset) -> Offset = { delta -> if (delta.y > 0f) delta.also { scrolled += it.y } else Offset.Zero }

        bounce.applyToScroll(Offset(0f, -100f), NestedScrollSource.UserInput, list)
        assertEquals(-25f, bounce.offset)

        bounce.applyToScroll(Offset(0f, 15f), NestedScrollSource.UserInput, list)
        assertEquals(-10f, bounce.offset)
        assertEquals(0f, scrolled)

        bounce.applyToScroll(Offset(0f, 20f), NestedScrollSource.UserInput, list)
        assertEquals(0f, bounce.offset)
        assertEquals(10f, scrolled)
    }

    @Test
    fun `a release past the edge still reaches the nested parents`() = runBlocking {
        // A cancelled scope: the spring back is not under test here.
        val bounce = BounceOverscroll(CoroutineScope(Job().apply { cancel() }))
        bounce.applyToScroll(Offset(0f, 100f), NestedScrollSource.UserInput) { Offset.Zero }
        assertTrue(bounce.offset > 0f)

        var released: Velocity? = null
        bounce.applyToFling(Velocity.Zero) { velocity -> velocity.also { released = it } }
        assertEquals(Velocity.Zero, released)
    }
}
