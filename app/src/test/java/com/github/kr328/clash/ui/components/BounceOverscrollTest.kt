package com.github.kr328.clash.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
