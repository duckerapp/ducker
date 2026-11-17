package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Per-host ownership: an overlapping confirmation cannot release the editor's blur. */
internal class SheetGlass {
    private val sheets = mutableMapOf<Any, Float>()
    var shown by mutableFloatStateOf(0f)
        private set

    fun update(owner: Any, progress: Float) {
        val visible = progress.coerceIn(0f, 1f)
        if (visible > 0f) sheets[owner] = visible else sheets.remove(owner)
        shown = sheets.values.maxOrNull() ?: 0f
    }

    fun remove(owner: Any) {
        sheets.remove(owner)
        shown = sheets.values.maxOrNull() ?: 0f
    }
}

internal val LocalSheetGlass = staticCompositionLocalOf<SheetGlass?> { null }
