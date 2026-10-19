package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** Shared direction hysteresis: small finger jitter must not toggle the floating search. */
internal class FloatingSearch(private val slop: Float) : NestedScrollConnection {
    var shown by mutableStateOf(false)
        private set

    private var run = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = consumed.y
        run = if (dy * run > 0) run + dy else dy
        if (run > slop) shown = true else if (run < -slop) shown = false
        return Offset.Zero
    }
}
