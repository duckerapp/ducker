package com.github.kr328.clash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
        want(owner, false)
    }

    private val wanting = mutableSetOf<Any>()

    /** A glass pane sheet is opening or open: the screen under it is drawn still for its panes before it moves. */
    var wanted by mutableStateOf(false)
        private set

    /** The still pictures of the screen under the sheets are made, see [SheetStillPictures]. */
    var ready by mutableStateOf(false)
        internal set

    fun want(owner: Any, on: Boolean) {
        if (on) wanting += owner else wanting -= owner
        wanted = wanting.isNotEmpty()
    }
}

internal val LocalSheetGlass = staticCompositionLocalOf<SheetGlass?> { null }

/** The screen under the tabs as recorded for the glass dock; a glass sheet over it shows it through itself. */
internal val LocalScreenBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
