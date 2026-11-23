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

    /**
     * The screen as recorded for the glass dock, set by [DuckerScaffold]; a glass sheet over it shows it through itself.
     * Here, not in the scaffold's scope: Home's subscription, update and VPN sheets are composed beside the scaffold.
     */
    var screen by mutableStateOf<GlassBackdrop?>(null)

    private val wanting = mutableSetOf<Any>()

    /** Another glass pane sheet than [owner] is opening or open, not leaving. */
    fun othersOpen(owner: Any): Boolean = wanting.any { it != owner }

    /** A glass pane sheet is opening or open: the screen under it is drawn still for its panes before it moves. */
    var wanted by mutableStateOf(false)
        private set

    /** The still pictures of the screen under the sheets are made, see [SheetStillPictures]. */
    var ready by mutableStateOf(false)
        internal set

    /** The screen as it was when a sheet opened, half its size, for the blur behind the sheets ([sheetUnder]); null — none. */
    var under by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        internal set

    fun want(owner: Any, on: Boolean) {
        if (on) wanting += owner else wanting -= owner
        wanted = wanting.isNotEmpty()
    }
}

internal val LocalSheetGlass = staticCompositionLocalOf<SheetGlass?> { null }

/**
 * A frosted panel of the glass style, with no screen to show through (a sheet over another one, the QR scanner's panel
 * over the camera): its controls take the glass's tint and rim, see [sheetSurface].
 */
internal val LocalFrosted = staticCompositionLocalOf { false }

