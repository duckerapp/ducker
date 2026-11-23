package com.github.kr328.clash.ui.components

import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import com.github.kr328.clash.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.mutableFloatStateOf
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/*
 * The liquid glass style, the second look beside Air (Settings → «Оформление» → «Стиль»). Phones with Android 13+
 * only (RuntimeShader): with Air, on a TV and on older phones [GlassStyle.enabled] is false and none of this draws.
 *
 * The shaders' rounded-rect distance field, the rim refraction with its circle map, the seven-band dispersion and
 * the rim highlight are adapted from Kyant's AndroidLiquidGlass (https://github.com/Kyant0/AndroidLiquidGlass,
 * backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt, commit 65ab177),
 * Copyright 2025 Kyant, licensed under the Apache License, Version 2.0; changed here: the inner bevel, the lens
 * dome, the frost and the corner gradient. Notice: assets/licenses/AndroidLiquidGlass-NOTICE.txt. The dock's lens
 * and bubble and the state wave are ours, from the general technique, not from Telegram's code (GPL v2).
 */

/** Whether this phone can draw the glass at all. */
internal val glassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * The glass style's settings (`SettingsStore.glass*`): Home's backdrop (`SettingsStore.BACKDROP_*`), the blur and the
 * tuning. [enabled] — the style is chosen and this device draws it; false, the app is Air.
 */
data class GlassStyle(
    val enabled: Boolean = false,
    val backdrop: String = "sunset",
    val blur: Int = 0,
    /** Appearance's «Размытие»: added to every pane's blur, not the dock's (0, 10, 20, 30). */
    val blurBoost: Int = 0,
    val image: Long = 0L,
    /** The own picture's top, under the status bar and the headers, is dark. */
    val imageTopDark: Boolean = false,
    // Appearance's defaults (SettingsStore's GLASS_*), the owner's (22.11).
    val refraction: Int = 50,
    val dispersion: Int = 0,
    val depth: Int = 65,
    val highlight: Int = 70,
    val noise: Int = 0,
    val vibrancy: Int = 40,
    val tint: Int = 50,
)

internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/** A backdrop: a gradient from [top] to [bottom] under three soft patches of colour. */
private class Backdrop(val top: Color, val bottom: Color, val patches: List<Color>, val strength: Float)

private fun backdropOf(key: String, dark: Boolean): Backdrop = when (key) {
    "grey" -> if (dark) Backdrop(Color(0xFF121315), Color(0xFF1E2024), listOf(Color(0xFF4A4F57), Color(0xFF5E636B), Color(0xFF3A3E45)), 0.45f)
    else Backdrop(Color(0xFFF3F4F6), Color(0xFFE1E3E7), listOf(Color(0xFF9EA3AA), Color(0xFFC7CBD1), Color(0xFF7B8188)), 0.30f)
    "green" -> if (dark) Backdrop(Color(0xFF0B1410), Color(0xFF0F1E17), listOf(Color(0xFF1F9E6A), Color(0xFF148C9E), Color(0xFF5FA83A)), 0.35f)
    else Backdrop(Color(0xFFECF9F1), Color(0xFFDDF3E6), listOf(Color(0xFF2ECC8F), Color(0xFF22B8CF), Color(0xFF9BE15D)), 0.35f)
    "pink" -> if (dark) Backdrop(Color(0xFF170C12), Color(0xFF22101A), listOf(Color(0xFFD9468A), Color(0xFFB8347A), Color(0xFF8A5BD6)), 0.35f)
    else Backdrop(Color(0xFFFFEEF5), Color(0xFFFCE0EC), listOf(Color(0xFFFF7EB6), Color(0xFFFF9FCB), Color(0xFFC59BFF)), 0.38f)
    "black_gradient" -> Backdrop(Color(0xFF000000), Color(0xFF262626), listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A), Color(0xFF4A4A4A)), 0.5f)
    "black" -> Backdrop(Color(0xFF000000), Color(0xFF000000), listOf(Color.Black, Color.Black, Color.Black), 0f)
    "black_white" -> Backdrop(Color(0xFF000000), Color(0xFFE8E8E8), listOf(Color(0xFF202020), Color(0xFF3A3A3A), Color(0xFFBDBDBD)), 0.35f)
    // Sunset, a little more scarlet than the first pastel orange.
    else -> if (dark) Backdrop(Color(0xFF1A0D0E), Color(0xFF250F14), listOf(Color(0xFFE8453A), Color(0xFFC81E4B), Color(0xFFE07A2E)), 0.35f)
    else Backdrop(Color(0xFFFFE6DA), Color(0xFFFFD5CC), listOf(Color(0xFFFF6B5A), Color(0xFFFF3F6E), Color(0xFFFFA45C)), 0.38f)
}

/**
 * Whether the backdrop's top, under the status bar and the tabs' headers, is dark: the black ones and a dark-topped
 * own picture. Null — the backdrop is drawn in the theme's own tones, so the headers follow the theme.
 */
internal fun backdropTopDark(key: String, imageTopDark: Boolean): Boolean? = when (key) {
    "black_gradient", "black", "black_white" -> true
    "image" -> imageTopDark
    else -> if (key in BACKDROP_PICTURES) true else null
}

/** The built-in pictures by their keys (`SettingsStore.BACKDROPS`), all dark. WebP: about 0.6 MB for both. */
internal val BACKDROP_PICTURES = mapOf("fern" to R.drawable.glass_wall_fern, "liquid" to R.drawable.glass_wall_liquid)

/**
 * The chosen backdrop behind a screen's cards, so glass cards have something to show. Its patches are fainter
 * while [running] is false. Still: a moving backdrop would redraw the screen without end (D-91). Drawn past the
 * element above and below, so it reaches under the status bar and the dock.
 */
@Composable
internal fun Modifier.glassBackground(key: String, running: Boolean): Modifier {
    if (key == "none") return this
    if (key == "image") return LocalGlassStyle.current.let { style ->
        glassImage(style.image, style.imageTopDark) { BitmapFactory.decodeFile(File(it.filesDir, "glass_backdrop.jpg").path) }
    }
    BACKDROP_PICTURES[key]?.let { picture -> return glassImage(picture, topDark = true) { BitmapFactory.decodeResource(it.resources, picture) } }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val target = backdropOf(key, dark)
    val spec = tween<Color>(700)
    val top by animateColorAsState(target.top, spec, label = "backTop")
    val bottom by animateColorAsState(target.bottom, spec, label = "backBottom")
    val a by animateColorAsState(target.patches[0], spec, label = "patchA")
    val b by animateColorAsState(target.patches[1], spec, label = "patchB")
    val c by animateColorAsState(target.patches[2], spec, label = "patchC")
    val strength by animateFloatAsState(target.strength * if (running) 1f else 0.65f, tween(700), label = "patches")

    return drawBehind {
        val reach = size.height

        drawRect(
            Brush.verticalGradient(0f to top, 1f to bottom, startY = 0f, endY = size.height),
            topLeft = Offset(0f, -reach),
            size = Size(size.width, size.height + 2 * reach),
        )

        if (strength <= 0f) return@drawBehind

        fun patch(color: Color, x: Float, y: Float, r: Float) = drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = strength),
                1f to color.copy(alpha = 0f),
                center = Offset(size.width * x, size.height * y),
                radius = size.width * r,
            ),
            radius = size.width * r,
            center = Offset(size.width * x, size.height * y),
        )

        patch(a, 0.15f, 0.18f, 0.85f)
        patch(b, 0.95f, 0.48f, 0.75f)
        patch(c, 0.25f, 0.85f, 0.8f)
    }
}

/**
 * A picture, the own one or a built-in, still behind the screen: it fills the whole window, cropped, wherever this
 * element sits, so it does not move as the cards scroll over it. [key] says when to [load] it again: the own file's
 * time, or the built-in's resource. Its top lies under a soft veil in its own tone, [topDark]: the headers stand
 * there, straight on the picture.
 */
@Composable
private fun Modifier.glassImage(key: Any, topDark: Boolean, load: (Context) -> Bitmap?): Modifier {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) { runCatching { load(context)?.asImageBitmap() }.getOrNull() }
    }
    val window = LocalWindowInfo.current.containerSize
    var at by remember { mutableStateOf(Offset.Zero) }
    val veilEnd = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HEADER_VEIL
    val tone = if (topDark) Color.Black else Color.White

    // Kept across recompositions: a new drawing counts as a change of the wallpaper (glassSourceChanges), and every screen
    // opened over the tabs recomposed Home's window, so the tabs' still pictures were made anew each time, their panes
    // drawing live meanwhile (F-582).
    return then(
        remember(window, veilEnd, tone) {
            Modifier.onGloballyPositioned { at = it.positionInRoot() }.drawBehind {
                val image = picture ?: return@drawBehind
                val scale = maxOf(window.width / image.width.toFloat(), window.height / image.height.toFloat())
                val width = image.width * scale
                val height = image.height * scale

                drawImage(
                    image,
                    dstOffset = IntOffset(((window.width - width) / 2f - at.x).toInt(), ((window.height - height) / 2f - at.y).toInt()),
                    dstSize = IntSize(width.toInt(), height.toInt()),
                )

                // White titles vanished in clouds, black ones in dark leaves; the veil evens the picture under them out.
                val end = veilEnd.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to tone.copy(alpha = 0.42f),
                        0.55f to tone.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                        startY = -at.y,
                        endY = end - at.y,
                    ),
                    topLeft = Offset(0f, -at.y),
                    size = Size(size.width, end),
                )
            }
        },
    )
}

/** A source layer recorded when its draw node is invalidated and sampled again inside the glass. */
class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    /** Counts recordings of [layer]: a still picture made from an older one no longer shows it. */
    internal var version by mutableIntStateOf(0)
    /** The backdrop drawn once per blur, see [StillBackdropPictures]; null — not made, or the backdrop has changed. */
    internal var still by mutableStateOf<StillBackdrop?>(null)
    /** Home's connect wave, see [screenWaveEffect]: from [waveCenter] (in the window), a ring at [waveRadius] pixels. */
    var waveCenter by mutableStateOf(Offset.Unspecified)
    var waveProgress by mutableFloatStateOf(0f)
    var waveRadius by mutableFloatStateOf(0f)
    var waveActive by mutableStateOf(false)
}

/**
 * What lies on a backdrop that is drawn still ([StillBackdropPictures]): only those panes' looks are drawn, each a
 * picture of the whole screen. Every backdrop drew every look, twice at full size (10.4 MB each on the OnePlus 12) for
 * the dock, which shows the screen live, and nine for each sheet, which reads three (F-582).
 */
internal enum class StillFor {
    /** The tabs over Home's picture: the cards' pane glass, and Appearance's own look for anything outside it. */
    Tabs,
    /** The screens over the tabs: their panes on the plain background, or on the picture in the review app. */
    Screens,
    /** The screen under a glass sheet: the sheets' panes, see [SheetStillPictures]. */
    Sheets,
}

@Composable
internal fun rememberGlassBackdrop(still: StillFor? = null): GlassBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !LocalGlassStyle.current.enabled) return null

    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }

    if (still != null) StillBackdropPictures(backdrop, still)

    return backdrop
}

/** Blur every pane in this scope adds to its own: the panes inside a glass sheet, so they are no clearer than it. */
internal val LocalGlassExtraBlur = staticCompositionLocalOf { 0.dp }

/**
 * The backdrop as recorded at [version], drawn once per pane blur (in pixels) and colour lift, keyed by [stillKey].
 * [held]: shown after the backdrop has changed too, see [SheetStillPictures].
 */
internal class StillBackdrop(val version: Int, val pictures: Map<Int, android.graphics.BitmapShader>, val held: Boolean = false)

/**
 * A still picture's key: its blur in pixels and its colour lift. By the blur alone, Home's pane glass and the dock (the
 * dock's lift, [DockGlass.VIBRANCY]) were handed a picture of Appearance's lift, unlike their live one (F-550).
 */
internal fun stillKey(blur: Int, vibrancy: Int): Int = blur * 1000 + vibrancy

/**
 * What a glass pane here shows of the backdrop under it, for a lens over the pane (a switch's, a slider's): the pane's
 * still picture and its veil, drawn at [position] in the window. Nothing where none is made: the lens is clear there.
 * [plain]: the picture alone, its colour lift undone — what lies under the panes before their own lift and tint, for
 * panes that then lift and tint it themselves (a floating search over a sheet's rows): with the veil they were tinted
 * twice, dark and grey beside the same pane at rest (the owner, 19.11).
 */
@Composable
internal fun rememberPaneUnder(plain: Boolean = false): (DrawScope.(position: Offset) -> Unit) {
    val backdrop = LocalGlassBackdrop.current
    val blur = with(LocalDensity.current) {
        (MAX_BLUR.toPx() * (LocalGlassStyle.current.blur / 100f) + LocalGlassExtraBlur.current.toPx()).roundToInt()
    }
    val vibrancy = LocalGlassStyle.current.vibrancy
    val veil = MaterialTheme.colorScheme.surface.copy(alpha = GlassMaterial.Surface.tint)
    // Saturation matrices multiply by their factors, so the lift's inverse is a saturation of its reciprocal.
    val unlift = remember(plain, vibrancy) {
        if (plain) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1f / liftSaturation(vibrancy)) },
        ) else null
    }
    return remember(backdrop, blur, vibrancy, veil, unlift) {
        { position ->
            val picture = backdrop?.still?.pictures?.get(stillKey(blur, vibrancy))
            if (picture != null) {
                val size = backdrop.layer.size
                translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) {
                    val whole = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                    drawRect(ShaderBrush(picture), size = whole, colorFilter = unlift)
                    // Over the whole picture: sized by the lens layer around the control's corner, the veil missed a
                    // wide control's far end, and the lens over the last pill showed the bare wallpaper (the owner, 18.11).
                    if (!plain) drawRect(veil, size = whole)
                }
            }
        }
    }
}

/**
 * Every blur (pixels) and colour lift a pane on a backdrop for [kind] asks for: the panes and the controls on them, and on
 * the tabs the power button. The styles are the ones [GlassPanes], [BackLayer] and [DuckerSheet] give their panes. The
 * dock and its veil lie on the scaffold's backdrop and draw it live, so none is drawn for them.
 */
@Composable
private fun stillLooks(extra: Dp, kind: StillFor): List<Pair<Int, Int>> {
    val density = LocalDensity.current
    val style = LocalGlassStyle.current
    val tuning = LocalGlassTuning.current
    val pane = tuning.optics(GlassTuningGroup.Pane, style.asDock())
    val styles = when (kind) {
        StillFor.Tabs -> listOf(style, pane)
        StillFor.Screens ->
            listOf(if (screenWallMode(tuning) != WallMode.PICTURE) tuning.optics(GlassTuningGroup.PlainPane, pane.asPlain()) else pane)
        StillFor.Sheets -> listOf(tuning.optics(GlassTuningGroup.SheetPane, pane.asSheet()))
    }
    val looks = styles.map { it.blur to it.vibrancy }
    // The power button's extra blur is Home's only.
    val raises = if (kind == StillFor.Tabs) listOf(0.dp, INNER_BLUR, INNER_BLUR + POWER_BLUR_BOOST) else listOf(0.dp, INNER_BLUR)
    return remember(looks, raises, density, extra) {
        with(density) {
            looks.flatMap { (b, v) -> raises.map { (MAX_BLUR.toPx() * (b / 100f) + (it + extra).toPx()).roundToInt() to v } }.distinct()
        }
    }
}

/**
 * A backdrop that stands still under moving panes (Home's, under the tabs) is drawn once, blurred for each kind of
 * pane, and the panes take their part of it: each pane had blurred and bent its own copy of the backdrop in a layer
 * of its own every frame, about 10 ms of the phone's graphics per frame while a page moved (OnePlus, 17.11). Until the
 * backdrop has rested after a change, and while the connect wave runs through it, the panes draw it live as before.
 */
@Composable
internal fun StillBackdropPictures(backdrop: GlassBackdrop, kind: StillFor) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val looks = stillLooks(0.dp, kind)
    // The screens' plain background is one colour, the same at a quarter of the size: their sharp panes took two whole
    // pictures of it, 10.4 MB each.
    val smooth = kind == StillFor.Screens && screenWallMode(LocalGlassTuning.current) != WallMode.PICTURE

    LaunchedEffect(backdrop, looks, smooth) {
        backdrop.still = null
        snapshotFlow { backdrop.version }.collectLatest { version ->
            // A backdrop changing for a while (a theme's colours fading over) is drawn once it has settled.
            delay(STILL_SETTLE_MS)
            val size = backdrop.layer.size
            if (size.width <= 0 || size.height <= 0) return@collectLatest

            val pictures = looks.associate { (blur, vibrancy) -> stillKey(blur, vibrancy) to stillPicture(graphics, density, backdrop.layer, size, blur, vibrancy, smooth) }
            if (backdrop.version == version) backdrop.still = StillBackdrop(version, pictures)
        }
    }
}

/**
 * The screen under a glass sheet, drawn still for the sheet's panes when it is asked to open, before it moves (F-532):
 * the sheet and each of its rows had blurred their own copy of the screen every frame of the slide, 10-17 ms a frame on
 * the OnePlus at 120 Hz. Made at each open, and held while the sheet is open though the screen changes under it (the
 * tap's ripple, a number): blurred past reading, the older picture shows as well.
 */
@Composable
internal fun SheetStillPictures(backdrop: GlassBackdrop, sheets: SheetGlass, extra: Dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val looks = stillLooks(extra, StillFor.Sheets)

    LaunchedEffect(backdrop, sheets, looks) {
        backdrop.still = null
        snapshotFlow { sheets.wanted }.collectLatest { wanted ->
            sheets.ready = false
            if (!wanted) return@collectLatest
            val version = backdrop.version
            val size = backdrop.layer.size
            // Made anew at every open: the version counts the screen's own recordings only, not its pages' and lists'
            // layers, so a picture kept from a sheet opened on «Подписки» showed them under a sheet on Home (the owner,
            // 23.11).
            if (size.width > 0 && size.height > 0) {
                val pictures = looks.associate { (blur, vibrancy) -> stillKey(blur, vibrancy) to stillPicture(graphics, density, backdrop.layer, size, blur, vibrancy) }
                backdrop.still = StillBackdrop(version, pictures, held = true)
            }
            sheets.ready = true
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun stillPicture(
    graphics: GraphicsContext,
    density: Density,
    source: GraphicsLayer,
    size: IntSize,
    blur: Int,
    vibrancy: Int,
    /** The backdrop has no detail to lose (one colour): drawn at the smallest size whatever the blur. */
    smooth: Boolean = false,
): android.graphics.BitmapShader {
    // Drawn smaller as the blur hides detail: sharp at none, a quarter of the pixels from a blur of 16.
    val factor = when {
        smooth -> 4
        blur < 4 -> 1
        blur < 16 -> 2
        else -> 4
    }
    val layer = graphics.createGraphicsLayer()

    try {
        layer.renderEffect = paneLift(blur / factor.toFloat(), vibrancy)?.asComposeRenderEffect()
        layer.record(density, LayoutDirection.Ltr, IntSize((size.width + factor - 1) / factor, (size.height + factor - 1) / factor)) {
            scale(1f / factor, 1f / factor, pivot = Offset.Zero) { drawLayer(source) }
        }

        return android.graphics.BitmapShader(layer.toImageBitmap().asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(android.graphics.Matrix().apply { setScale(factor.toFloat(), factor.toFloat()) }) }
    } finally {
        graphics.releaseGraphicsLayer(layer)
    }
}

/**
 * Records what this draws into [backdrop] and shows it as before. Kept across recompositions: a new modifier drew and
 * recorded again, and the still pictures were made anew after every tab switch.
 */
@Composable
internal fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else then(remember(backdrop) {
        Modifier.onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                backdrop.layer.record { this@drawWithContent.drawContent() }
                // Not a read of this draw: it would invalidate itself and draw again forever.
                Snapshot.withoutReadObservation { backdrop.version++ }
                drawLayer(backdrop.layer)
            }
    })

/**
 * As [glassSource], with [under] — the screen's backdrop beneath this content — recorded first into [backdrop]: a pane
 * floating over a list (Proxies' search) then shows the cards moving under it on the wallpaper, not the wallpaper alone,
 * which read as frozen glass (the owner, 19.11). Its panes draw it live, as the list moves.
 */
@Composable
internal fun Modifier.glassSourceOver(
    backdrop: GlassBackdrop?,
    under: GlassBackdrop?,
    /**
     * Drawn beneath the content instead of [under] as it is, at the content's place in the window: on a sheet the sheet
     * as it looks ([rememberPaneUnder]), so a floating search shows the sheet and its rows, not the bare screen below.
     */
    beneath: (DrawScope.(position: Offset) -> Unit)? = null,
): Modifier {
    if (backdrop == null || under == null) return glassSource(backdrop)
    val content = rememberGraphicsLayer()

    return then(remember(backdrop, under, content, beneath) {
        Modifier.onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                content.record { this@drawWithContent.drawContent() }
                backdrop.layer.record {
                    if (beneath != null) beneath(backdrop.origin)
                    else translate(under.origin.x - backdrop.origin.x, under.origin.y - backdrop.origin.y) { drawLayer(under.layer) }
                    drawLayer(content)
                }
                // Not a read of this draw: it would invalidate itself and draw again forever.
                Snapshot.withoutReadObservation { backdrop.version++ }
                drawLayer(content)
            }
    })
}

/**
 * Inside a layer of a [glassSource]'s content: its drawing changed there (a picture loaded, colours fading over), which
 * the source's own recording, holding the layer, shows without drawing again; the still pictures must be made anew.
 */
@Composable
internal fun Modifier.glassSourceChanges(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else then(remember(backdrop) { Modifier.drawBehind { Snapshot.withoutReadObservation { backdrop.version++ } } })

internal fun extractCornerRadii(outline: Outline, size: Size): FloatArray {
    return when (outline) {
        is Outline.Rounded -> {
            val rr = outline.roundRect
            val maxR = size.minDimension / 2f
            floatArrayOf(
                rr.topLeftCornerRadius.x.coerceAtMost(maxR),
                rr.topRightCornerRadius.x.coerceAtMost(maxR),
                rr.bottomRightCornerRadius.x.coerceAtMost(maxR),
                rr.bottomLeftCornerRadius.x.coerceAtMost(maxR),
            )
        }
        is Outline.Rectangle -> floatArrayOf(0f, 0f, 0f, 0f)
        else -> {
            val maxR = size.minDimension / 2f
            floatArrayOf(maxR, maxR, maxR, maxR)
        }
    }
}

/**
 * Draws [backdrop] as seen through a pane of [shape] the size of this element: blurred by the setting plus
 * [blurBoost], bent near the rim, under a veil of [tint]. [base] fills first, so empty parts of the backdrop do
 * not let a shadow show through.
 */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    base: Color,
    tint: Color,
    shape: Shape = CircleShape,
    blurBoost: Dp = 0.dp,
    /** How much more it bends and splits light at the rim than the settings say: Home's card when connected. */
    bendBoost: Float = 1f,
    /** How much brighter its rim catches the light. */
    rimBoost: Float = 1f,
    /** How far below the top corners the rim light fades out; 0 — all around. A sheet's sides and bottom. */
    rimFade: Dp = 0.dp,
    /**
     * How much a layer around this pane scales it (the glass dock's breath): the picture is drawn back so the glass
     * still shows what lies right under it; drawn plainly, it slid by the growth, ~40 px at the dock's far end.
     */
    zoom: (() -> ScaleFactor)? = null,
    /** The pane glass: a finger on it eases its tint, see [glassPress]. */
    press: GlassPress? = null,
    /** The dock and side rail keep their own refraction and tint. */
    dockOptics: Boolean = false,
    /**
     * The pane glass: [tint]'s alpha is final, not scaled by the glass's tint and density — a chosen card's accent, which
     * thinning the panes made too clear to read (the owner, 22.11).
     */
    fixedTint: Boolean = false,
    /** The review app's tuning group of this pane's glass: the cards', or the dock body's second mode («Док 2»). */
    paneGroup: GlassTuningGroup = LocalPaneGroup.current,
    /** Its settings, if not [LocalGlassStyle]'s: the dock's body in its second mode inside the dock's own style. */
    glassStyle: GlassStyle? = null,
    /**
     * A smooth clip of [shape] lies around it already (`Modifier.clip`): its own clip, a path's, has no smoothing, and
     * its steps showed along the round edge inside the smooth one (the power button, the owner, 22.11).
     */
    clipped: Boolean = false,
    /** Its own veil instead of a flat [tint], drawn under the rim. */
    veil: (DrawScope.() -> Unit)? = null,
): Modifier {
    val style = glassStyle ?: LocalGlassStyle.current
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !style.enabled) return this
    val extra = LocalGlassExtraBlur.current
    val panes = !dockOptics
    val tuning = LocalGlassTuning.current
    val ownRim = if (tuning?.enabled == true) tuning.ownRimOf(paneGroup) else !PaneGlass.DOCK_RIM
    val paneHighlight = tuning?.value(paneGroup, "highlight", DockGlass.HIGHLIGHT.toFloat())?.roundToInt() ?: DockGlass.HIGHLIGHT
    fun paneValue(key: String, fallback: Float) = tuning.paneKnob(paneGroup, key, fallback)

    val glass = rememberGraphicsLayer()
    // The pane glass's live picture is bent in a layer of its own, drawn whole: drawn straight under the pane's clip, its
    // bend was handed the blurred backdrop only up to the clip's edge, and the rim, drawn from beyond it, came out empty
    // on the right and at the bottom, the dark ground showing through whenever it drew live: the first ~0.3 s after a
    // start or a return, the connect wave (the owner, 19.11).
    val glassFrame = if (panes) rememberGraphicsLayer() else null
    // The dock's body in its own optics («Док 1») with the cards' own light line: drawn over its finished glass by a pass
    // of its own, so its bend and reflection stay as they are (the owner, 22.11). Its body alone passes [paneGroup] DockPane.
    val dockLine = !panes && paneGroup == GlassTuningGroup.DockPane &&
        if (tuning?.enabled == true) tuning.ownRimOf(GlassTuningGroup.Dock) else DockGlass.OWN_LINE
    val lineShader = remember(dockLine) { if (dockLine) RuntimeShader(DOCK_LINE_SHADER) else null }
    val lineStillShader = remember(dockLine) { if (dockLine) RuntimeShader(DOCK_LINE_SHADER) else null }
    val lineStillBrush = remember(lineStillShader) { lineStillShader?.let(::ShaderBrush) }
    // The panes share one shader of each kind ([PaneShaders]); the dock's glass keeps its own.
    val shader = remember(panes) { if (panes) PaneShaders.glass else RuntimeShader(GLASS_SHADER) }
    // The same glass drawn straight from a still backdrop's picture: no layer of its own, no blur of its own.
    val stillShader = remember(panes) { if (panes) PaneShaders.glass else RuntimeShader(GLASS_SHADER) }
    val stillBrush = remember(stillShader) { ShaderBrush(stillShader) }
    // The pane glass draws its own light line, unless it takes the dock's rim ([PaneGlass.DOCK_RIM]).
    val highlightShader = remember(panes, ownRim, dockLine) { if (panes && ownRim || dockLine) null else RuntimeShader(HIGHLIGHT_SHADER) }
    val highlightBrush = remember(highlightShader) { highlightShader?.let(::ShaderBrush) }
    // The pane glass's veil, thinner over the rim so its roll and light line show through: a flat tint drawn by it, an
    // own [veil] masked by the second.
    val ownVeil = veil != null
    val veilShader = remember(panes) { if (panes) PaneShaders.veil else null }
    val veilBrush = remember(veilShader) { veilShader?.let(::ShaderBrush) }
    val maskShader = remember(panes, ownVeil) { if (panes && ownVeil) PaneShaders.veil else null }
    val maskBrush = remember(maskShader) { maskShader?.let(::ShaderBrush) }
    var position by remember { mutableStateOf(Offset.Zero) }
    // How much the layers around it scale it (a pressed card around its button): measured, so its glass still shows what
    // lies right under it without every caller passing [zoom].
    var layerScale by remember { mutableStateOf(ScaleFactor(1f, 1f)) }
    val screenBackground = MaterialTheme.colorScheme.background
    val blurPart = style.blur / 100f

    return onGloballyPositioned {
        position = it.positionInRoot()
        val width = it.size.width
        val height = it.size.height
        if (width > 0 && height > 0) {
            fun plain(s: Float) = if (kotlin.math.abs(s - 1f) < 0.0005f) 1f else s
            layerScale = ScaleFactor(
                plain((it.localToRoot(Offset(width.toFloat(), 0f)).x - position.x) / width),
                plain((it.localToRoot(Offset(0f, height.toFloat())).y - position.y) / height),
            )
        }
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val radii = extractCornerRadii(outline, size)
        val blur = MAX_BLUR.toPx() * blurPart + (blurBoost + extra).toPx()
        val amount = MAX_REFRACTION_AMOUNT.toPx() * (style.refraction / 100f) * bendBoost
        val bent = if (bendBoost != 1f) style.copy(dispersion = (style.dispersion * bendBoost).roundToInt()) else style
        // The live picture reaches as far as the pane's rim draws from, and its blur beyond: Home's card connected bends
        // from ~35 dp out, past the 24 dp drawn, and until the still picture was ready (the app just opened) its rim
        // showed a sharp line over nothing there (the owner, 23.11).
        val margin = if (panes) {
            val reach = paneValue("push", PaneGlass.PUSH.value) * (style.refraction / 50f) * bendBoost * 1.06f +
                paneValue("chroma", PaneGlass.CHROMA.value) * (bent.dispersion / 40f)
            maxOf(GLASS_MARGIN.toPx(), reach.dp.toPx() + blur)
        } else GLASS_MARGIN.toPx()

        if (panes) {
            // Set right before the effect is made, which keeps them: the shader is every pane's.
            paneBend(shader, margin, size.width, size.height, radii, bent, bendBoost, ownRim, tuning, paneGroup)
            val bend = RenderEffect.createRuntimeShaderEffect(shader, "img")
            glass.renderEffect = (paneLift(blur, style.vibrancy)?.let { RenderEffect.createChainEffect(bend, it) } ?: bend)
                .asComposeRenderEffect()
        } else {
            val effect = glassEffect(shader, margin, size.width, size.height, radii, BEZEL.toPx(), amount, blur, bent)
            glass.renderEffect = (if (lineShader != null) {
                dockLine(lineShader, margin, size.width, size.height, radii, bent, tuning)
                RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(lineShader, "img"), effect)
            } else effect).asComposeRenderEffect()
            bend(stillShader, 0f, size.width, size.height, radii, BEZEL.toPx(), amount, bent)
            if (lineStillShader != null) dockLine(lineStillShader, 0f, size.width, size.height, radii, bent, tuning)
        }
        val stillBlur = blur.roundToInt()
        val paneSize = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())
        val clip = Path().apply { addOutline(outline) }
        val paneBounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)

        onDrawBehind {
            val scaled = zoom?.invoke() ?: layerScale
            val plain = scaled.scaleX == 1f && scaled.scaleY == 1f
            // The pane glass reads the still picture scaled as well: a press drew it live, and the spring's last swings
            // switched between the two every frame.
            val still = backdrop.still?.takeIf { (it.held || it.version == backdrop.version) && (plain || panes) }?.pictures?.get(stillKey(stillBlur, style.vibrancy))
            val pressed = press?.light() ?: 0f

            if (still == null) {
                glass.record(paneSize) {
                    // [position] is where the scaled pane's corner lies; a point of it [scaled]× as far from there.
                    translate(margin, margin) {
                        scale(1f / scaled.scaleX, 1f / scaled.scaleY, pivot = Offset.Zero) {
                            translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) {
                                drawLayer(backdrop.layer)
                            }
                        }
                    }
                }
                glassFrame?.let {
                    it.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    it.record(paneSize) { drawLayer(glass) }
                }
            } else {
                // The panes' shader is shared: all of its uniforms set again for this pane, right before it draws.
                if (panes) paneBend(stillShader, 0f, size.width, size.height, radii, bent, bendBoost, ownRim, tuning, paneGroup)
                stillShader.setInputShader("img", still)
                stillShader.setFloatUniform("shift", position.x - backdrop.origin.x, position.y - backdrop.origin.y)
                if (panes) stillShader.setFloatUniform("zoom", scaled.scaleX, scaled.scaleY)
            }

            // The pane glass: a faint dark line just outside the rim (iOS's, F-550).
            if (panes) drawOutline(outline, Color.Black.copy(alpha = paneValue("ring", PaneGlass.RING)), style = Stroke(paneValue("ringWidth", PaneGlass.RING_WIDTH.value).dp.toPx()))

            fun DrawScope.inside(block: DrawScope.() -> Unit) = if (clipped) block() else clipPath(clip, block = block)
            inside {
                drawRect(base)
                if (still == null) {
                    translate(-margin, -margin) { drawLayer(glassFrame ?: glass) }
                } else if (lineStillShader != null && lineStillBrush != null) {
                    // Set again each frame: the line's input holds the still glass as its uniforms were when set.
                    lineStillShader.setInputShader("img", stillShader)
                    drawRect(lineStillBrush)
                } else drawRect(stillBrush)
                val shadowAlpha = 0.12f * (style.depth / 100f)
                if (!panes && shadowAlpha > 0.005f) {
                    val shadowHeight = minOf(16.dp.toPx(), size.height * 0.4f)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = shadowAlpha),
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = shadowHeight,
                        )
                    )
                }
                val tintProgress = (style.tint / 50f).coerceIn(0f, 2f)
                // A press eases the tint, gentler than the dock's (F-550).
                val clear = 1f - paneValue("pressClear", PaneGlass.PRESS_CLEAR) * pressed
                if (veil != null) {
                    if (maskShader != null && maskBrush != null) {
                        drawContext.canvas.saveLayer(paneBounds, androidx.compose.ui.graphics.Paint().apply { alpha = clear })
                        veil()
                        paneVeil(maskShader, size.width, size.height, radii, tuning, paneGroup)
                        maskShader.setFloatUniform("color", 1f, 1f, 1f, 1f)
                        drawRect(maskBrush, blendMode = BlendMode.DstIn)
                        drawContext.canvas.restore()
                    } else veil()
                } else if ((tintProgress > 0f || fixedTint && panes) && tint.alpha > 0f) {
                    // A chosen card keeps its colour under a finger and only swells (the owner, 22.11).
                    val a = (if (fixedTint && panes) tint.alpha
                    else tint.alpha * tintProgress * (if (panes) paneValue("denser", PaneGlass.DENSER) * clear else 1f)).coerceIn(0f, 0.92f)
                    // The panes on a plain background may take a share of white, to part from black (the owner, 23.11).
                    val lift = if (panes && !fixedTint && paneGroup.fromCards) paneValue("lift", 0f) else 0f
                    val veilColor = if (lift > 0f) androidx.compose.ui.graphics.lerp(tint.copy(alpha = 1f), Color.White, lift) else tint
                    if (veilShader != null && veilBrush != null) {
                        paneVeil(veilShader, size.width, size.height, radii, tuning, paneGroup)
                        // Premultiplied, as the shader's output is.
                        veilShader.setFloatUniform("color", veilColor.red * a, veilColor.green * a, veilColor.blue * a, a)
                        drawRect(veilBrush)
                    } else drawRect(veilColor.copy(alpha = a))
                }
            }

            // Specular directional highlight; the pane glass draws its own light line, coloured by what lies under it, or
            // the dock's rim at the dock's strength.
            val highlight = if (panes) paneHighlight else style.highlight
            if (highlightShader == null || highlightBrush == null) {
                // The pane glass: drawn by its shader.
            } else if (highlight > 0) {
                val isLight = (if (base != Color.Transparent) base else screenBackground).luminance() > 0.5f
                // At 100 % on a light screen the rim is as white as it was drawn before the fix below.
                val hAlpha = ((if (isLight) 1f else 0.55f) * (highlight / 100f) * rimBoost).coerceAtMost(1f)
                highlightShader.setFloatUniform("size", size.width, size.height)
                highlightShader.setFloatUniform("cornerRadii", radii)
                // Premultiplied, as the shader's output is: white at (1, 1, 1, a) added near-full white whatever a was.
                highlightShader.setFloatUniform("color", hAlpha, hAlpha, hAlpha, hAlpha)
                highlightShader.setFloatUniform("angle", HIGHLIGHT_ANGLE)
                highlightShader.setFloatUniform("falloff", HIGHLIGHT_FALLOFF)
                highlightShader.setFloatUniform("fade", rimFade.toPx())
                drawOutline(outline, highlightBrush, style = Stroke(1.2.dp.toPx()))
            } else {
                val rimBase = if (base != Color.Transparent) base else screenBackground
                drawOutline(outline, rimFadeBrush(glassRim(rimBase), rimFade.toPx(), maxOf(radii[0], radii[1])), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

/** The screen's backdrop behind the tabs, which their cards and buttons show through as glass; null — no glass. */
internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** How much more a button inside a glass card blurs, so it does not merge with the card around it. */
internal val INNER_BLUR = 6.dp

/** How much more again Home's power button blurs than a button on a card. */
internal val POWER_BLUR_BOOST = 4.dp

// How long a changed still backdrop rests before it is drawn again for the panes.
private const val STILL_SETTLE_MS = 250L

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun glassEffect(
    shader: RuntimeShader,
    margin: Float,
    width: Float,
    height: Float,
    cornerRadii: FloatArray,
    bezel: Float,
    amount: Float,
    blur: Float,
    style: GlassStyle,
): RenderEffect {
    bend(shader, margin, width, height, cornerRadii, bezel, amount, style)
    val bend = RenderEffect.createRuntimeShaderEffect(shader, "img")
    val inner = paneLift(blur, style.vibrancy)

    return if (inner != null) RenderEffect.createChainEffect(bend, inner) else bend
}

/** [DOCK_LINE_SHADER]'s uniforms from «Док»'s light line knobs: as the cards' line, by the dock's own highlight. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Density.dockLine(
    shader: RuntimeShader, margin: Float, width: Float, height: Float, cornerRadii: FloatArray, style: GlassStyle, tuning: GlassTuning?,
) {
    fun value(key: String, fallback: Float) = tuning?.value(GlassTuningGroup.Dock, key, fallback) ?: fallback
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("offset", -margin, -margin)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    shader.setFloatUniform("line", value("line", DockGlass.LINE) * (style.highlight / 60f))
    shader.setFloatUniform("lineWidth", value("lineWidth", DockGlass.LINE_WIDTH.value).dp.toPx())
    shader.setFloatUniform("lineSpread", value("lineSpread", DockGlass.LINE_SPREAD.value).dp.toPx())
    shader.setFloatUniform("lineGlow", value("lineGlow", DockGlass.LINE_GLOW))
    shader.setFloatUniform("lineFall", value("lineFall", DockGlass.LINE_FALL))
    shader.setFloatUniform("lineDirect", value("lineDirect", DockGlass.LINE_DIRECT))
}

/** The glass's bend near the rim, for a pane [width] × [height] whose layer starts [margin] before it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun bend(shader: RuntimeShader, margin: Float, width: Float, height: Float, cornerRadii: FloatArray, bezel: Float, amount: Float, style: GlassStyle) {
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("offset", -margin, -margin)
    shader.setFloatUniform("shift", 0f, 0f)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    shader.setFloatUniform("refractionHeight", bezel)
    shader.setFloatUniform("refractionAmount", amount)
    shader.setFloatUniform("depthEffect", style.depth / 100f)
    shader.setFloatUniform("chromaticAberration", style.dispersion / 100f)
    shader.setFloatUniform("noiseStrength", style.noise / 100f * 0.05f)
}

/** The saturation a pane's colour lift of [vibrancy] gives. */
private fun liftSaturation(vibrancy: Int): Float = 1f + 0.45f * (vibrancy / 100f)

/** What a pane does to the backdrop before bending it: blurs it by [blur] pixels and lifts its colour by [vibrancy] %. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun paneLift(blur: Float, vibrancy: Int): RenderEffect? {
    val sat = liftSaturation(vibrancy)
    val colour = if (sat > 1.001f) RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix().apply { setSaturation(sat) }
        )
    ) else null

    return when {
        blur >= 0.5f && colour != null -> RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            colour,
        )
        blur >= 0.5f -> RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
        else -> colour
    }
}

/**
 * Frosted glass under the status bar: [backdrop] blurred, solid over [bar] and fading out over the rest of
 * this element, under a veil of [color] that fades the same way.
 */
@Composable
internal fun Modifier.glassVeil(backdrop: GlassBackdrop?, bar: Dp, color: Color): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this

    val blurred = rememberGraphicsLayer()
    val veil = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }

    return onGloballyPositioned { position = it.positionInRoot() }.drawBehind {
        val solid = (bar.toPx() / size.height).coerceIn(0f, 1f)
        val blur = VEIL_BLUR.toPx()

        blurred.renderEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP).asComposeRenderEffect()
        blurred.record {
            translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) { drawLayer(backdrop.layer) }
        }
        // Offscreen, so the mask below fades the blurred picture itself and the sharp one shows through.
        veil.compositingStrategy = CompositingStrategy.Offscreen
        veil.record {
            drawRect(color)
            drawLayer(blurred)
            drawRect(
                Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
        drawLayer(veil)
        drawRect(
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.55f),
                solid to color.copy(alpha = 0.55f),
                1f to color.copy(alpha = 0f),
            ),
        )
    }
}

/** Opaque to legacy callers: even a null RuntimeShader cast resolves the missing class on API26/28. */
internal interface GlassWave {
    fun effect(center: Offset, radius: Float, width: Float, strength: Float, dispersion: Float, highlight: Float, alpha: Float): androidx.compose.ui.graphics.RenderEffect?
}

@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassWave : GlassWave {
    private val shader = RuntimeShader(HERO_WAVE_SHADER)
    override fun effect(center: Offset, radius: Float, width: Float, strength: Float, dispersion: Float, highlight: Float, alpha: Float) =
        shaderWaveEffect(shader, center, radius, width, strength, dispersion, highlight, alpha)
}

internal fun heroWaveShader(): GlassWave? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else Api33GlassWave()

internal fun heroWaveEffect(shader: GlassWave, center: Offset, currentRadius: Float, waveWidth: Float, strength: Float, dispersion: Float, highlight: Float, alpha: Float) =
    shader.effect(center, currentRadius, waveWidth, strength, dispersion, highlight, alpha)

/**
 * Home's connect wave (the owner, 22.11): one ring from the power button's centre over the whole screen, the card's
 * change inside it, bending all it crosses — the cards, their letters, the dock — strongly, its colours parted as light
 * through a lens. [RING]: its width; [STRENGTH], [CHROMA], [LIGHT]: the bend, the colours' parting, the light on its crest.
 */
internal object HeroWave {
    // The owner's (23.11, the panel's export).
    val RING = 25.dp
    const val STRENGTH = 2f
    const val CHROMA = 1f
    const val LIGHT = 0.2f
    /** Its way: how long, and how it slows (1 − (1 − t)^[BRAKE]: out of the button fast, slowing as a ripple). */
    const val DURATION = 1150f
    const val BRAKE = 2.2f
    /** It comes in over [FADE_IN] rings of radius, weakens by [DECAY] as it spreads, and fades out from [FADE_FROM] of its way. */
    const val FADE_IN = 1.5f
    const val DECAY = 0.7f
    const val FADE_FROM = 0.72f
    /** The glass power button's swell as the state changes: how much, its rise, and the spring it settles by. */
    const val SWELL = 0.1f
    const val SWELL_MS = 280f
    const val SWELL_BOUNCE = 0.3f
    const val SWELL_SPEED = 150f
    /** The connected glow round the button, its strength and reach in its radii: none, the owner's choice (23.11). */
    const val GLOW = 0f
    const val GLOW_REACH = 1f
    /** Connected: how much more the card's rim bends and shines. */
    const val ON_BEND = 2.65f
    const val ON_RIM = 1.6f

    val defaults: Map<String, Float> get() = mapOf(
        "ring" to RING.value, "strength" to STRENGTH, "chroma" to CHROMA, "light" to LIGHT, "duration" to DURATION,
        "brake" to BRAKE, "fadeIn" to FADE_IN, "decay" to DECAY, "fadeFrom" to FADE_FROM, "swell" to SWELL,
        "swellMs" to SWELL_MS, "swellBounce" to SWELL_BOUNCE, "swellSpeed" to SWELL_SPEED, "glow" to GLOW,
        "glowReach" to GLOW_REACH, "onBend" to ON_BEND, "onRim" to ON_RIM,
    )
}

/** A wave knob: the tuning panel's in the review app, else [HeroWave]'s. */
internal fun GlassTuning?.wave(key: String): Float =
    this?.value(GlassTuningGroup.Wave, key, HeroWave.defaults.getValue(key)) ?: HeroWave.defaults.getValue(key)

/**
 * The wave of [wave] over a layer at [at] in the window; null while none runs. It comes in as the ring leaves the
 * button's middle, narrower while it is small, and weakens as it spreads, gone before it leaves the screen.
 */
internal fun Density.screenWaveEffect(shader: GlassWave, wave: GlassBackdrop, at: Offset, tuning: GlassTuning?): androidx.compose.ui.graphics.RenderEffect? {
    val radius = wave.waveRadius
    if (!wave.waveActive || !wave.waveCenter.isSpecified || radius <= 0f) return null
    val ring = tuning.wave("ring").dp.toPx()
    val travel = wave.waveProgress
    fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val fadeIn = tuning.wave("fadeIn")
    val fadeFrom = tuning.wave("fadeFrom")
    val coming = if (fadeIn > 0f) smooth(radius / (ring * fadeIn)) else 1f
    val going = if (fadeFrom < 1f) 1f - smooth((travel - fadeFrom) / ((1f - fadeFrom) * 0.93f)) else 1f
    val amount = coming * (1f - tuning.wave("decay") * travel) * going
    if (amount <= 0.001f) return null
    return heroWaveEffect(
        shader, wave.waveCenter - at, radius, minOf(ring, radius * 0.9f + 1f),
        tuning.wave("strength"), tuning.wave("chroma"), tuning.wave("light") * amount, amount,
    )
}

internal interface GlassBubble {
    fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, presence: Float, zoom: Float, base: Color, tuning: GlassTuning?, under: DrawScope.() -> Unit)
}

@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassBubble : GlassBubble {
    private val shader = RuntimeShader(BUBBLE_SHADER)
    override fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, presence: Float, zoom: Float, base: Color, tuning: GlassTuning?, under: DrawScope.() -> Unit) {
        scope.drawShaderBubble(layer, shader, center, halfWidth, halfHeight, presence, zoom, base, tuning, under)
    }
}

/** One for every slider: it sets all its uniforms each time it draws, and the effect it makes keeps them (F-582). */
private val sharedBubble: GlassBubble? by lazy(LazyThreadSafetyMode.NONE) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else Api33GlassBubble()
}

internal fun bubbleShader(): GlassBubble? = sharedBubble

/**
 * The switch's lens, after iOS's (the owner's video, 18.11): thick clear glass that draws the surroundings in from
 * beyond its edge, so what lies under it looks smaller and the rim holds what lies outside; a thin band at the very rim
 * mirrors what lies inside, in its colour; the colours split where the glass bends most; a light line along the
 * edge, brightest on the diagonal; a soft shadow under it. [lift] 0…1 brings the shadow in as the lens grows;
 * [frost] 0…1 clouds it as it settles back into the thumb.
 */
internal interface GlassLens {
    /** [drawFrom]: [LensLook.drawFrom] for this frame, so a moving lens can magnify more than a resting one. */
    fun draw(
        scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, lift: Float, frost: Float, dark: Boolean,
        look: LensLook = LensLook.Switch, drawFrom: Float = look.drawFrom, under: DrawScope.() -> Unit,
    )
}

/**
 * How a [GlassLens] looks: its whole tuning in one place. Lengths are in the lens's half-height r. [Switch] is the
 * switch's, fitted to the owner's iOS video (D-542, F-543): the reference the other lenses start from.
 */
internal data class LensLook(
    /** The picture inside is drawn from (1 + this)× as far out, easing to none at the rim: above 0 it looks smaller, below 0 magnified. */
    val drawFrom: Float,
    /** Over how much of r from the rim that eases in: wide, the middle bulges like a fish-eye; narrow, a flat magnifier. */
    val zoomEdge: Float,
    /** How far the rim draws the surroundings in: the push outwards along its normal at the rim itself. */
    val push: Float,
    /** How steeply the push falls off inwards: (1 − depth / r) to this power; steeper squeezes more into a narrower rim. */
    val pushPower: Float,
    /**
     * Above 0, the push falls off instead over this band of r as a circle's edge, steepest at the rim: much of what lies
     * past it squeezed into the rim itself, the middle flat (iOS's dock lens); 0 — [pushPower]'s.
     */
    val pushBand: Float,
    /** The push's share along the lens and at its top and bottom. */
    val pushAlong: Float,
    val pushAcross: Float,
    /** Over which share of r into an end the push at the top and bottom fades: earlier, the drawn-in bands end sooner. */
    val pushEndFrom: Float,
    val pushEndTo: Float,
    /** How far red and blue part at the rim; [disperse] — and by this share of the rim's push, as the push carries them. */
    val chroma: Float,
    val disperse: Float,
    /** Above 0, the rim's picture is spread over this share of its push along the normal: soft streaks, not sharp copies. */
    val smear: Float,
    /** The band at the rim that mirrors what lies above or below it: along the sides, and at the ends. */
    val bandSide: Float,
    val bandEnd: Float,
    /** How far beyond the band the mirror looks. */
    val mirror: Float,
    /**
     * The mirror softened over this share of r ([mirrorSoft], 0 — one sharp look), its colours parted by this share of
     * the rim's ([mirrorChroma]), and laid over the picture this strongly ([mirrorMix]).
     */
    val mirrorSoft: Float = 0f,
    val mirrorChroma: Float = 0.15f,
    val mirrorMix: Float = 1f,
    /** The thin dark line round the rim, its opacity. */
    val ring: Float,
    /** How much darker the glass gets towards its rim. */
    val rimShade: Float,
    /** How much lighter, towards white, the glass is than what lies under it. */
    val lighten: Float,
    /** The light line along the rim, on a dark and on a light screen; [shineEnds] — its share at the ends. */
    val shineDark: Float,
    val shineLight: Float,
    val shineEnds: Float,
    /** Soft colours along the inside of the rim, turning with its angle, on a dark and on a light screen. */
    val irisDark: Float,
    val irisLight: Float,
    /** The shadow under the lens, on a dark and on a light screen. */
    val shadowDark: Float,
    val shadowLight: Float,
    /** The blur it never loses, and the blur its frost adds. */
    val clearBlur: Dp,
    val frostBlur: Dp,
) {
    companion object {
        /** Tuned by the owner on the dark theme (22.11, the panel's export). */
        val Switch = LensLook(
            drawFrom = -0.18f, zoomEdge = 0.6f, push = 1.4f, pushPower = 1.5f, pushBand = 0.7f, pushAlong = 1f, pushAcross = 1f, pushEndFrom = 0.1f, pushEndTo = 0.6f, chroma = 0.05f, disperse = 0.15f, smear = 0.5f, bandSide = 0.2f, bandEnd = 0.22f, mirror = 0.98f, mirrorSoft = 0.16f, mirrorChroma = 0.15f, mirrorMix = 0.25f, ring = 0.08f, rimShade = 0f, lighten = 0.04f, shineDark = 0f, shineLight = 0.6f, shineEnds = 0f, irisDark = 0f, irisLight = 0f, shadowDark = 0f, shadowLight = 0.12f, clearBlur = 0.dp, frostBlur = 6.dp,
        )

        /** The light theme's, as before the dark one was tuned. */
        val SwitchLight = LensLook(
            drawFrom = 0.12f, zoomEdge = 0.35f, push = 0.3f, pushPower = 1.5f, pushBand = 0f, pushAlong = 0.6f, pushAcross = 1f, pushEndFrom = 0.25f, pushEndTo = 0.7f, chroma = 0.1f, disperse = 0f, smear = 0f, bandSide = 0.04f, bandEnd = 0.22f, mirror = 0.9f, mirrorSoft = 0f, mirrorChroma = 0.15f, mirrorMix = 1f, ring = 0.6f, rimShade = 0.28f, lighten = 0.07f, shineDark = 0.18f, shineLight = 0.6f, shineEnds = 0.1f, irisDark = 0f, irisLight = 0f, shadowDark = 0.3f, shadowLight = 0.12f, clearBlur = 0.3.dp, frostBlur = 6.dp,
        )
    }
}

@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassLens : GlassLens {
    private val shader = RuntimeShader(LIQUID_LENS_SHADER)
    override fun draw(
        scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, lift: Float, frost: Float, dark: Boolean,
        look: LensLook, drawFrom: Float, under: DrawScope.() -> Unit,
    ) =
        with(scope) {
            val margin = GLASS_MARGIN.toPx()
            val left = center.x - halfWidth - margin
            val top = center.y - halfHeight - margin
            val radius = minOf(halfWidth, halfHeight)
            shader.setFloatUniform("center", halfWidth + margin, halfHeight + margin)
            shader.setFloatUniform("halfSize", halfWidth, halfHeight)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("frost", frost)
            shader.setFloatUniform("shine", if (dark) look.shineDark else look.shineLight)
            shader.setFloatUniform("shadow", lift * if (dark) look.shadowDark else look.shadowLight)
            shader.setFloatUniform("shadowBlur", 0.25f * radius)
            shader.setFloatUniform("edge", 0.6.dp.toPx())
            shader.setFloatUniform("inner", 0.15f * radius)
            shader.setFloatUniform("lift", lift)
            shader.setFloatUniform("drawFrom", drawFrom)
            shader.setFloatUniform("pushAmount", look.push)
            shader.setFloatUniform("pushAlong", look.pushAlong)
            shader.setFloatUniform("pushPower", look.pushPower)
            shader.setFloatUniform("pushBand", look.pushBand)
            shader.setFloatUniform("disperse", look.disperse)
            shader.setFloatUniform("smear", look.smear)
            shader.setFloatUniform("zoomEdge", look.zoomEdge)
            shader.setFloatUniform("pushAcrossScale", look.pushAcross)
            shader.setFloatUniform("pushEnd", look.pushEndFrom, look.pushEndTo)
            shader.setFloatUniform("chromaAmount", look.chroma)
            shader.setFloatUniform("bandSide", look.bandSide)
            shader.setFloatUniform("bandEnd", look.bandEnd)
            shader.setFloatUniform("mirrorReach", look.mirror)
            shader.setFloatUniform("mirrorSoft", look.mirrorSoft)
            shader.setFloatUniform("mirrorChroma", look.mirrorChroma)
            shader.setFloatUniform("mirrorMix", look.mirrorMix)
            shader.setFloatUniform("ringAlpha", look.ring)
            shader.setFloatUniform("rimShade", look.rimShade)
            shader.setFloatUniform("lighten", look.lighten)
            shader.setFloatUniform("shineEnds", look.shineEnds)
            shader.setFloatUniform("iris", if (dark) look.irisDark else look.irisLight)
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "img")
            // Never quite clear: a touch of blur as the video's even when held.
            val blur = look.clearBlur.toPx() + frost * look.frostBlur.toPx()
            layer.renderEffect = (
                if (blur < 0.5f) lens
                else RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
            ).asComposeRenderEffect()
            layer.record(Density(density, fontScale), layoutDirection, IntSize((2 * (halfWidth + margin)).toInt(), (2 * (halfHeight + margin)).toInt())) {
                translate(-left, -top) { under() }
            }
            // Unclipped: outside the lens the shader leaves only its shadow.
            translate(left, top) { drawLayer(layer) }
        }
}

/**
 * One for every lens (the switches, the segments, the dock, the config's bars): it sets all its uniforms each time it
 * draws and makes its effect before it records what lies under it, which keeps them (F-582). A Settings page compiled one
 * per switch, ~0.5 ms each.
 */
private val sharedLens: GlassLens? by lazy(LazyThreadSafetyMode.NONE) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else Api33GlassLens()
}

internal fun lensShader(): GlassLens? = sharedLens

internal fun DrawScope.drawGlassBubble(
    layer: GraphicsLayer,
    shader: GlassBubble,
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    presence: Float,
    base: Color,
    /** How much the middle is magnified: the dock's by default; a switch's lens over its thumb, little. */
    zoom: Float = 1f + BUBBLE_ZOOM * presence,
    tuning: GlassTuning? = null,
    under: DrawScope.() -> Unit,
) =
    shader.draw(this, layer, center, halfWidth, halfHeight, presence, zoom, base, tuning, under)

/**
 * A refractive liquid glass shockwave expanding from [center] with radius [currentRadius].
 * Refracts the layer content with a 3D fluid meniscus, 7-band spectral dispersion, and specular sheen.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun shaderWaveEffect(
    shader: RuntimeShader,
    center: Offset,
    currentRadius: Float,
    waveWidth: Float,
    strength: Float,
    dispersion: Float,
    highlight: Float,
    alpha: Float,
): androidx.compose.ui.graphics.RenderEffect? {
    if (alpha <= 0.001f || currentRadius <= 0f) return null

    shader.setFloatUniform("center", center.x, center.y)
    shader.setFloatUniform("currentRadius", currentRadius)
    shader.setFloatUniform("waveWidth", waveWidth)
    shader.setFloatUniform("strength", strength)
    shader.setFloatUniform("dispersion", dispersion)
    shader.setFloatUniform("highlight", highlight)
    shader.setFloatUniform("alpha", alpha)

    return RenderEffect.createRuntimeShaderEffect(shader, "img").asComposeRenderEffect()
}

/**
 * A clear bubble [halfWidth] × [halfHeight] around [center]: what [under] draws, magnified in the middle, bent
 * at the rim with a faint split of colours, under a light rim. [presence] 0…1 fades it in; [base] is the
 * screen's background, for the rim.
 */
private fun DrawScope.drawShaderBubble(
    layer: GraphicsLayer,
    shader: RuntimeShader,
    center: Offset,
    halfWidth: Float,
    halfHeight: Float,
    presence: Float,
    zoom: Float,
    base: Color,
    tuning: GlassTuning?,
    under: DrawScope.() -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val margin = GLASS_MARGIN.toPx()
    val left = center.x - halfWidth - margin
    val top = center.y - halfHeight - margin
    fun value(key: String, fallback: Float) =
        tuning?.value(GlassTuningGroup.Slider, key, fallback, base.luminance() >= 0.5f) ?: fallback
    val bezel = minOf(value("bezel", BUBBLE_BEZEL.value).dp.toPx(), halfHeight)

    shader.setFloatUniform("center", halfWidth + margin, halfHeight + margin)
    shader.setFloatUniform("halfSize", halfWidth, halfHeight)
    shader.setFloatUniform("radius", halfHeight)
    shader.setFloatUniform("zoom", zoom)
    shader.setFloatUniform("bezel", bezel)
    shader.setFloatUniform("strength", value("strength", 0.28f))
    shader.setFloatUniform("spread", value("spread", 0.12f))

    layer.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "img").asComposeRenderEffect()
    layer.alpha = presence
    // A density of its own: recorded with this scope as its density, a size in dp read inside [under] asked this
    // scope's drawing for it, which asked the scope again, until the stack ran out (the subscription interval slider).
    layer.record(Density(density, fontScale), layoutDirection, IntSize((2 * (halfWidth + margin)).toInt(), (2 * (halfHeight + margin)).toInt())) {
        translate(-left, -top) { under() }
    }

    val corner = CornerRadius(halfHeight)
    val topLeft = Offset(center.x - halfWidth, center.y - halfHeight)
    val size = androidx.compose.ui.geometry.Size(2 * halfWidth, 2 * halfHeight)
    val pill = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, corner))
    }

    clipPath(pill) { translate(left, top) { drawLayer(layer) } }
    drawRoundRect(
        glassRim(base).let { it.copy(alpha = (it.alpha * presence * value("rim", 1f)).coerceIn(0f, 1f)) },
        topLeft, size, corner, style = Stroke(value("rimWidth", 1.2f).dp.toPx()),
    )
}

/** How dense the accent colour lies over glass: Home's card when on, and a selected card. */
internal fun glassAccentTint(dark: Boolean): Float = if (dark) 0.68f else 0.80f

/**
 * The rim [color], fading out over [fade] pixels below the top corners of radius [corner]; 0 — all around. The same
 * fall as the highlight shader's, for a frosted sheet and a sheet with the highlight off.
 */
internal fun rimFadeBrush(color: Color, fade: Float, corner: Float): Brush =
    if (fade <= 0f) SolidColor(color)
    else Brush.verticalGradient(0f to color, 1f to color.copy(alpha = 0f), startY = corner, endY = corner + fade)

/** The glass's edge: a light line on a light screen, barely there on a dark one. */
internal fun glassRim(base: Color): Color =
    Color.White.copy(alpha = if (base.luminance() > 0.5f) 0.55f else 0.16f)

private val VEIL_BLUR = 10.dp
/** How far below the status bar the veil over an own picture's top reaches: past the tabs' headers. */
private val HEADER_VEIL = 120.dp
internal val BUBBLE_BEZEL = 14.dp
/** How much the bubble magnifies at its middle when fully grown. */
private const val BUBBLE_ZOOM = 0.22f

/** Room around the pane in the recorded picture: the rim samples from just outside it. */
private val GLASS_MARGIN = 24.dp
/** How wide the curved rim is; inside it the glass is flat and shows the picture unbent. */
private val BEZEL = 16.dp
/** The glass's blur at 100 % in settings; 0 % is clear glass, as FlClashX's, which the owner liked. */
private val MAX_BLUR = 24.dp
/** Maximum displacement distance for refraction when refraction tuning is 100%. */
private val MAX_REFRACTION_AMOUNT = 22.dp
/** Directional specular highlight angle (45 degrees: top-left to bottom-right). */
private const val HIGHLIGHT_ANGLE = 0.7853982f
/** Falloff power for specular highlight sheen. */
private const val HIGHLIGHT_FALLOFF = 1.3f

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        return (coord.y <= 0.0) ? radii.y : radii.z;
    } else {
        return (coord.y <= 0.0) ? radii.x : radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 m = max(cornerCoord, 0.0);
        float len = length(m);
        return (len > 0.0001) ? sign(coord) * (m / len) : float2(0.0);
    } else {
        float2 w = cornerCoord + float2(radius);
        // Farther than the radius from both edges (always so beside a square corner): the nearest edge's normal. The
        // clamped mix of both read as a diagonal, which left a sheet's lower half without its rim light on one side.
        if (w.x <= 0.0001 && w.y <= 0.0001) {
            return (cornerCoord.x > cornerCoord.y) ? float2(sign(coord.x), 0.0) : float2(0.0, sign(coord.y));
        }
        w = max(w, float2(0.0001));
        float len = length(w);
        return (len > 0.0001) ? sign(coord) * (w / len) : float2(0.0);
    }
}

// The light line's brightness at a pixel whose centre lies [depth] inside the rim: its fall over [width], averaged over
// the pixel, so a line thinner than a pixel lies smoothly along a round rim instead of in steps (the power button, the
// owner, 22.11); outside the rim, none.
float rimLine(float depth, float width) {
    float w = max(width, 0.001);
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        float x = depth + (float(i) - 1.5) * 0.25;
        sum += x < 0.0 ? 0.0 : 1.0 - smoothstep(0.0, w, x);
    }
    return sum * 0.25;
}

float circleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}
"""

/*
 * iOS-style Liquid Glass lens. Exact SDF rounded-rect profile with convex 3D curvature,
 * multi-band spectral chromatic aberration (dispersion) and subtle surface micro-frost.
 * Decoupled rim bevel and smooth analytical lens volume prevent centerline seams and stepped artifacts.
 */
private const val GLASS_SHADER = """
uniform shader img;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
uniform float noiseStrength;
// Where the backdrop's point under this pane's origin lies: zero in a pane's own layer, the pane's place on a still picture.
uniform float2 shift;

half4 sample(float2 p) { return img.eval(p + shift); }

$ROUNDED_RECT_SDF

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (sd > 0.0) return sample(coord);

    // Uniform concentric bezel maintaining >= 45% inner corner curvature
    float bezel = min(refractionHeight, max(radius * 0.55, 2.0));
    float t = clamp(-sd / max(bezel, 0.001), 0.0, 1.0);
    float bevelD = circleMap(1.0 - t) * refractionAmount;

    // Rim displacement: active strictly in the outer rim band, zero at inner bezel edge (t = 1)
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 sdfGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 bezelDisp = -bevelD * sdfGrad;

    // Center lens volume: smooth radial dome vanishing continuously at central axes (no sign flips)
    float2 safeHalfSize = max(halfSize, float2(1.0));
    float2 normCoord = centeredCoord / safeHalfSize;
    float2 depthDisp = -normCoord * (depthEffect * refractionAmount * 0.25);

    // Total displacement: seamless C1 blend between outer rim bevel and inner lens dome
    float blendT = smoothstep(0.0, 1.0, t);
    float2 totalDisp = bezelDisp + depthDisp * blendT;
    float2 refractedCoord = coord + totalDisp;

    half4 color;
    float totalDispLen = length(totalDisp);
    if (chromaticAberration > 0.0 && totalDispLen > 0.001) {
        float dispersionIntensity = chromaticAberration * abs(normCoord.x * normCoord.y);
        float2 dispersedCoord = totalDisp * dispersionIntensity;

        color = half4(0.0);

        half4 red = sample(refractedCoord + dispersedCoord);
        color.r += red.r / 3.5;
        color.a += red.a / 7.0;

        half4 orange = sample(refractedCoord + dispersedCoord * (2.0 / 3.0));
        color.r += orange.r / 3.5;
        color.g += orange.g / 7.0;
        color.a += orange.a / 7.0;

        half4 yellow = sample(refractedCoord + dispersedCoord * (1.0 / 3.0));
        color.r += yellow.r / 3.5;
        color.g += yellow.g / 3.5;
        color.a += yellow.a / 7.0;

        half4 green = sample(refractedCoord);
        color.g += green.g / 3.5;
        color.a += green.a / 7.0;

        half4 cyan = sample(refractedCoord - dispersedCoord * (1.0 / 3.0));
        color.g += cyan.g / 3.5;
        color.b += cyan.b / 3.0;
        color.a += cyan.a / 7.0;

        half4 blue = sample(refractedCoord - dispersedCoord * (2.0 / 3.0));
        color.b += blue.b / 3.0;
        color.a += blue.a / 7.0;

        half4 purple = sample(refractedCoord - dispersedCoord);
        color.r += purple.r / 7.0;
        color.b += purple.b / 3.0;
        color.a += purple.a / 7.0;
    } else {
        color = sample(refractedCoord);
    }

    if (noiseStrength > 0.0) {
        float n = hash(coord) * noiseStrength;
        color.rgb = clamp(color.rgb + half3(n), 0.0, 1.0);
    }

    return color;
}
"""

/** Directional specular highlight sheen across the perimeter normal. */
private const val HIGHLIGHT_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
uniform half4 color;
uniform float angle;
uniform float falloff;
// Over this many pixels below the top corners the light fades out; 0 — all around.
uniform float fade;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    float rim = (d < 0.0) ? intensity : intensity * 0.35;
    if (fade > 0.0) {
        float top = max(cornerRadii.x, cornerRadii.y);
        rim *= 1.0 - smoothstep(top, top + fade, coord.y);
    }
    return color * rim;
}
"""

/**
 * The bubble: the middle magnified, easing to none at the ends; the rim takes the picture from just outside,
 * with full 7-spectral rainbow dispersion, as thick curved glass splits light at its edge.
 */
private const val BUBBLE_SHADER = """
uniform shader img;
uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float zoom;
uniform float bezel;
uniform float strength;
uniform float spread;

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 m = max(cornerCoord, 0.0);
        float len = length(m);
        return (len > 0.0001) ? sign(coord) * (m / len) : float2(0.0);
    } else {
        float2 w = max(cornerCoord + float2(radius), float2(0.0001));
        float len = length(w);
        return (len > 0.0001) ? sign(coord) * (w / len) : float2(0.0);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}

half4 main(float2 coord) {
    float2 p = coord - center;
    float d = sdRoundedRect(p, halfSize, radius);
    if (d > 0.0) return img.eval(coord);

    float x = p.x / halfSize.x;
    float z = 1.0 + (zoom - 1.0) * (1.0 - x * x);
    float2 uv = center + p / z;
    if (d < -bezel) return img.eval(uv);

    float2 grad = gradSdRoundedRect(p, halfSize, radius);
    float t = 1.0 + d / max(bezel, 0.001);
    float bend = circleMap(t);
    float2 off = -grad * bend * bezel * strength;

    float2 refractedCoord = uv + off;
    float2 dispersedCoord = off * spread;

    half4 color = half4(0.0);

    half4 red = img.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = img.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = img.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = img.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = img.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = img.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = img.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""

/**
 * [GlassLens], fitted to the owner's iOS video frame by frame (lens half-height r, depth from the rim): inside, the
 * picture is drawn from 1.12× as far out, easing to 1× over the outer 0.35 r, plus a push outwards along the rim's
 * normal, 0.3 r × (1 − depth / r)^1.5 — so the surroundings fill the top and bottom while
 * the ends stay nearly true; in the ends the vertical shrink fades as (cross-section / r)⁶ and the vertical push from
 * a quarter of the way in (the horizontal push there × 0.6); the outer 0.04 r (0.22 r at the ends) mirrors what lies
 * up to 0.9 r above or below it; red and blue part along the normal near the rim, up to ±0.1 r; one shadow from the
 * outline moved by (0.12, 0.3) r, outside below and inside as an arc, a thin dark line round the rim, a lighter glass,
 * light lines along the top and bottom.
 */
private const val LIQUID_LENS_SHADER = """
uniform shader img;
uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float frost;
uniform float shine;
uniform float shadow;
uniform float shadowBlur;
uniform float edge;
uniform float inner;
uniform float lift;
uniform float drawFrom;
uniform float pushAmount;
uniform float pushAlong;
uniform float pushPower;
uniform float pushBand;
uniform float disperse;
uniform float smear;
uniform float zoomEdge;
uniform float pushAcrossScale;
uniform float2 pushEnd;
uniform float chromaAmount;
uniform float bandSide;
uniform float bandEnd;
uniform float mirrorReach;
uniform float mirrorSoft;
uniform float mirrorChroma;
uniform float mirrorMix;
uniform float ringAlpha;
uniform float rimShade;
uniform float lighten;
uniform float shineEnds;
uniform float iris;

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 m = max(cornerCoord, 0.0);
        float len = length(m);
        return (len > 0.0001) ? sign(coord) * (m / len) : float2(0.0);
    } else {
        float2 w = max(cornerCoord + float2(radius), float2(0.0001));
        float len = length(w);
        return (len > 0.0001) ? sign(coord) * (w / len) : float2(0.0);
    }
}

// The picture at coord + shift, red and blue parted by ±chroma and ±3 % of the shift.
half4 split(float2 coord, float2 shift, float2 chroma) {
    half4 g = img.eval(coord + shift);
    half r = img.eval(coord + shift * 1.03 + chroma).r;
    half b = img.eval(coord + shift * 0.97 - chroma).b;
    return half4(r, g.g, b, g.a);
}

half4 main(float2 coord) {
    float2 p = coord - center;
    float d = sdRoundedRect(p, halfSize, radius);
    // One shadow, as iOS's (the owner's second video, 18.11): the lens's own outline moved down and a little right,
    // softened. Outside, it lies below the lens, from its lower left; inside, it runs as a soft arc up the left side
    // and in under the top, leaving the rim above it light and the bottom clear. A thin dark line rings the rim.
    float2 o = p - float2(0.12, 0.3) * radius;
    float s = sdRoundedRect(o, halfSize, radius);
    float ring = ringAlpha * lift * (1.0 - clamp(max(d, 0.0) / (edge * 1.6), 0.0, 1.0));
    half4 shade = half4(0.0, 0.0, 0.0, max(shadow * (1.0 - smoothstep(-shadowBlur, shadowBlur, s)), ring));
    // The rim's pixels partly glass, partly shadow: a hard cut showed steps along the curve (the owner, 18.11).
    float cover = clamp(0.5 - d, 0.0, 1.0);
    if (cover <= 0.0) return shade;
    float depth = max(-d, 0.0);
    float2 n = gradSdRoundedRect(p, halfSize, radius);
    float t = clamp(depth / radius, 0.0, 1.0);

    float deep = smoothstep(0.0, zoomEdge, t);
    float inBand = clamp(1.0 - t / max(pushBand, 0.0001), 0.0, 1.0);
    float push = pushAmount * radius * (pushBand > 0.0 ? 1.0 - sqrt(1.0 - inBand * inBand) : pow(1.0 - t, pushPower));
    // Towards the ends the glass is thinner across: there the picture keeps its height and the dark bands from above
    // and below end in rounded tips short of the rim, as iOS's (the owner, 18.11).
    float intoEnd = max(abs(p.x) - (halfSize.x - radius), 0.0);
    float across = pow(sqrt(max(radius * radius - intoEnd * intoEnd, 0.0)) / radius, 6.0);
    // The rim's push leaves off sooner, from pushEnd.x of the way into an end (the switch: a quarter): the dark bands from above and below end
    // rounded, the picture curving round them like a horseshoe, not in an S (the owner, 18.11).
    float pushAcross = 1.0 - smoothstep(pushEnd.x, pushEnd.y, intoEnd / radius);
    // Less push outwards at the ends: over the track its colour reaches their rim, dense; past the track's end they show
    // the dark beyond (the owner's iOS screenshots, 18.11).
    // The bend grows and fades with the lens: at the thumb's size it shows the track as it is. Bent fully as it shrank
    // back, it drew the dark beyond the track in at its top and bottom, "much black" as it settled where iOS's is soft
    // and milky (the owner's video of ours, 18.11).
    float bend = lift * lift;
    float2 rimShift = n * push * float2(pushAlong, pushAcross * pushAcrossScale);
    float2 drawn = center + p + (p * float2(drawFrom * deep, drawFrom * deep * across) + rimShift) * bend;
    float fringe = 1.0 - clamp(t / 0.25, 0.0, 1.0);
    // The colours part with the rim's push, not the middle's magnification: iOS's middle is clean, its rim rainbow.
    float2 chroma = n * (chromaAmount * radius * fringe * fringe * bend) + rimShift * (disperse * bend);
    half4 color = split(coord, drawn - coord, chroma);
    // What the rim takes in spread along its normal, five looks across the push: iOS's rim streaks are soft gradients
    // of what lies at it, ours were sharp squeezed copies (the owner, 18.11).
    if (smear > 0.0 && push > 0.5) {
        float2 along = rimShift * (smear * bend);
        float2 base = drawn - coord;
        color += split(coord, base - along * 0.5, chroma) + split(coord, base - along * 0.25, chroma)
            + split(coord, base + along * 0.25, chroma) + split(coord, base + along * 0.5, chroma);
        color *= 0.2;
    }

    // The rim mirrors what lies above or below it along the refracted picture: at the ends, where the mirror's reach
    // runs out, it meets the picture without a seam, and past the track's end it reflects nothing (the owner's iOS
    // screenshots, 18.11). Mirrored inwards along the normal it lit both ends alike; widened there it grew horns over
    // the track's end. Its reach is deep and fades evenly to the band's inner edge, so the track's colour in it thins
    // out to a line where the track curves away, as iOS's, not cut off. A thin line along the sides (0.04 r), thicker
    // from before the ends' curve (0.22 r); it looks 1.3× as far along as the picture does, so over the track's end its
    // colour has thinned away before the end's curve (the owner's second iOS video, 18.11).
    float band = radius * (bandSide + (bandEnd - bandSide) * smoothstep(-0.3, 0.5, (abs(p.x) - (halfSize.x - radius)) / radius));
    float rim = 1.0 - clamp(depth / band, 0.0, 1.0);
    float2 shift = drawn - coord;
    float2 mirrored = float2(shift.x + p.x * 0.3 * bend, -n.y * (mirrorReach * radius + band) * rim * bend);
    half4 mirror;
    // Softened over a 3 × 3 grid [mirrorSoft] r apart: a single look squeezed the labels into sharp bright specks along
    // the dock lens's top and bottom (the owner, 19.11: «резким»).
    if (mirrorSoft > 0.0) {
        mirror = half4(0.0);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                mirror += split(coord, mirrored + float2(float(i), float(j)) * (mirrorSoft * radius), chroma * mirrorChroma);
            }
        }
        mirror *= 1.0 / 9.0;
    } else {
        mirror = split(coord, mirrored, chroma * mirrorChroma);
    }
    color = mix(color, mirror, rim * mirrorMix);

    color.rgb = mix(color.rgb, half3(color.a), frost * 0.25);

    // The glass a little lighter than what lies under it (+7 % towards white), and darker towards its rim: a soft
    // shade over its outer 0.6 r, down to 0.78 at the rim, measured on the owner's second iOS video (18.11).
    color.rgb += (half3(color.a) - color.rgb) * lighten;
    color.rgb *= 1.0 - rimShade * lift * exp(-s * s / (2.0 * inner * inner));
    // Soft colours along the inside of the rim, turning with its angle: yellow to the right, blue to the left, violet
    // above, as iOS's on a light screen, where the picture under the rim is too plain to split into a rainbow of its
    // own (the owner, 18.11: «не все так радужно»).
    float irisBand = (1.0 - smoothstep(0.0, 0.12 * radius, depth)) * iris * lift;
    if (irisBand > 0.0) {
        float a = atan(n.y, n.x) + 1.047;
        half3 hue = half3(0.5 + 0.5 * cos(a), 0.5 + 0.5 * cos(a - 2.094), 0.5 + 0.5 * cos(a + 2.094));
        color.rgb = mix(color.rgb, hue * color.a, irisBand);
    }
    // A thin light line along the top and the bottom, none at the ends.
    float lobe = abs(n.y);
    float line = (1.0 - smoothstep(0.0, edge, depth)) * (shineEnds + (1.0 - shineEnds) * lobe * lobe) * shine;
    color.rgb = min(color.rgb + half3(line), half3(1.0));
    color.a = max(color.a, line);
    return mix(shade, color, cover);
}
"""

/**
 * Hero card state change shockwave: a moving 3D liquid meniscus ring that bends the picture,
 * splits colours into a 7-band rainbow dispersion at the wave crest, and casts a directional specular sheen.
 */
private const val HERO_WAVE_SHADER = """
uniform shader img;
uniform float2 center;
uniform float currentRadius;
uniform float waveWidth;
uniform float strength;
uniform float dispersion;
uniform float highlight;
uniform float alpha;

half4 main(float2 coord) {
    float2 delta = coord - center;
    float len = length(delta);
    if (len < 0.001) return img.eval(coord);

    float d = len - currentRadius;
    if (abs(d) > waveWidth) return img.eval(coord);

    float2 dir = delta / len;
    float t = d / waveWidth;
    float bump = 1.0 - t * t;
    float ridge = bump * bump;

    // Wave slope pushing outward at crest front, inward at crest rear
    float slope = -2.0 * t * bump;
    float dispMag = slope * strength * waveWidth * 0.35;
    float2 disp = dir * dispMag;

    float2 refractedCoord = coord + disp;
    float2 dispersedCoord = dir * (slope * (strength * 0.35 + 0.18) * waveWidth * dispersion * 0.65);

    half4 color = half4(0.0);

    // 7-band spectral chromatic dispersion across the liquid meniscus
    half4 red = img.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = img.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = img.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = img.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = img.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = img.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = img.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    // Specular highlight along crest facing light (from top-left: normal = float2(-0.7071, -0.7071))
    float2 lightDir = float2(-0.7071, -0.7071);
    float align = dot(dir, -lightDir);
    float spec = pow(max(0.0, align), 1.8) * ridge * highlight;
    color.rgb += half3(spec * 0.55);

    return mix(img.eval(coord), color, alpha);
}
"""

/**
 * The screen under a sheet, blurred as it comes out: the blur grows through fixed steps of [radius] / [SHEET_STEPS] and
 * crossfades between the two around [shown]. A radius that followed the sheet changed the blur's kernel every frame and
 * the text behind it shimmered under a slow finger; one fixed radius faded in looked like soap (the owner, 17.11).
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun sheetBlurEffect(radius: Float, shown: Float): androidx.compose.ui.graphics.RenderEffect {
    val at = shown.coerceIn(0f, 1f) * SHEET_STEPS
    val step = at.toInt().coerceAtMost(SHEET_STEPS - 1)
    val part = at - step

    fun blurAt(level: Int): RenderEffect = if (level == 0) RenderEffect.createOffsetEffect(0f, 0f)
    else (radius * level / SHEET_STEPS).let { RenderEffect.createBlurEffect(it, it, Shader.TileMode.CLAMP) }

    val lower = blurAt(step)
    val effect = if (part < 0.004f) lower else RenderEffect.createBlendModeEffect(
        lower,
        RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, part, 0f,
                ),
            ),
            blurAt(step + 1),
        ),
        android.graphics.BlendMode.SRC_OVER,
    )
    return effect.asComposeRenderEffect()
}

private const val SHEET_STEPS = 6

/**
 * The glass style's materials, tuned here only: one glass everywhere (the owner, 17.11). [Surface] — panes lying on
 * the backdrop: cards, the dock, the search, «Features», Home's card at rest. [Control] — controls on a pane: buttons,
 * the node row, the delay check; clearer and blurred more than the pane under them, set off from it by their rim. At
 * 0.7 they read as grey buttons painted on the glass, at 0.3 a little too light (the owner, 17.11).
 */
internal enum class GlassMaterial(val tint: Float, val blurBoost: Dp) {
    Surface(0.55f, 0.dp),
    Control(0.45f, INNER_BLUR),
}

/** A pane of [material] in the colour [color] (the material's own by default) over [backdrop]; null backdrop — none. */
@Composable
internal fun Modifier.glass(
    backdrop: GlassBackdrop?,
    material: GlassMaterial,
    shape: Shape,
    color: Color = if (material == GlassMaterial.Surface) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    press: GlassPress? = null,
): Modifier = liquidGlass(
    backdrop,
    base = MaterialTheme.colorScheme.background,
    tint = color.copy(alpha = color.alpha * material.tint),
    shape = shape,
    blurBoost = material.blurBoost,
    press = press,
)

/**
 * The pane glass, after iOS's Control Centre and then the dock (the owner, 18.11; F-550): the dock's dark glass whose
 * rim rolls lighter and draws in the colours around it softly, a faint light line along it and a faint dark one outside,
 * and a press that swells it like jelly and eases its tint. The dock keeps its own optics.
 */
internal val LocalGlassPanes = staticCompositionLocalOf { false }

/**
 * The tuning group the panes here read their knobs from: the cards' ([GlassTuningGroup.Pane]), or on the screens over the
 * tabs on a plain background its own ([GlassTuningGroup.PlainPane]) — over black the cards' glass hardly parted from it
 * (the owner, 23.11).
 */
internal val LocalPaneGroup = staticCompositionLocalOf { GlassTuningGroup.Pane }

/**
 * A pane knob of [group]: [GlassTuningGroup.PlainPane]'s own default ([PlainPaneGlass.KNOBS]), or else the cards' as
 * they are tuned, so it starts where they stand.
 */
internal fun GlassTuning?.paneKnob(group: GlassTuningGroup, key: String, fallback: Float): Float {
    val own = group.ownKnobs[key]
    if (this == null) return own ?: fallback
    // Only the knobs the cards have: «Белая тонировка» is the plain panes' own.
    val base = own ?: if (group.fromCards && glassKnobs.getValue(GlassTuningGroup.Pane).any { it.key == key }) {
        value(GlassTuningGroup.Pane, key, fallback)
    } else fallback
    return value(group, key, base)
}

/**
 * The pane glass's settings: the dock's blur, tint and colour, so the cards and the dock read as one glass (the owner,
 * 18.11: the cards stood out from the dock in clarity and tint); the other sliders still tune the cards.
 */
/** The panes' tint density, as they read it: tuned in the review app. */
@Composable
internal fun paneDensity(): Float = LocalGlassTuning.current?.value(GlassTuningGroup.Pane, "denser", PaneGlass.DENSER) ?: PaneGlass.DENSER

internal fun GlassStyle.asDock(): GlassStyle = copy(blur = PaneGlass.BLUR + blurBoost, tint = PaneGlass.TINT, vibrancy = PaneGlass.VIBRANCY)

/** The panes' glass in the sheets: the cards' ([asDock]) with [SheetPaneGlass]'s own blur, tint, refraction and depth. */
internal fun GlassStyle.asSheet(): GlassStyle = copy(
    blur = SheetPaneGlass.BLUR + blurBoost, tint = SheetPaneGlass.TINT,
    refraction = SheetPaneGlass.REFRACTION, depth = SheetPaneGlass.DEPTH,
)

/**
 * The panes in the sheets as the owner tuned them (23.11, F-581, then the export of F-583): clearer than the cards (the
 * sheet under them is glass already), bent a little more and deeper, a narrower roll and a stronger own light line. The
 * rest is the cards'. Their blur is the sheet body's too, which lies in their style.
 */
internal object SheetPaneGlass {
    const val BLUR = 10
    const val TINT = 35
    const val REFRACTION = 60
    const val DEPTH = 87
    val KNOBS = mapOf("band" to 5.45f, "line" to 1.2f)
}

/** A pane group's own knob defaults, apart from the cards' ([GlassTuningGroup.fromCards]). */
internal val GlassTuningGroup.ownKnobs: Map<String, Float>
    get() = when (this) {
        GlassTuningGroup.PlainPane -> PlainPaneGlass.KNOBS
        GlassTuningGroup.SheetPane -> SheetPaneGlass.KNOBS
        else -> emptyMap()
    }

/** The panes' glass on the screens' plain background: the cards' ([asDock]) with [PlainPaneGlass]'s own blur, tint, light. */
internal fun GlassStyle.asPlain(): GlassStyle =
    copy(blur = PlainPaneGlass.BLUR + blurBoost, tint = PlainPaneGlass.TINT, highlight = PlainPaneGlass.HIGHLIGHT)

/**
 * The panes on the screens' plain background (iOS's black) as the owner tuned them (23.11, F-577): clearer, a little
 * denser and whiter than the cards, a stronger own light line, so they part from the black. The rest is the cards'.
 */
internal object PlainPaneGlass {
    const val BLUR = 0
    const val TINT = 55
    const val HIGHLIGHT = 60
    /** Its pane knobs that differ from the cards' ([PaneGlass]); «lift» is the veil towards white, its own only. */
    val KNOBS = mapOf(
        "line" to 1.3f, "lineGlow" to 0.35f, "lineDirect" to 1f, "ring" to 0f,
        "denser" to 0.16f, "pressClear" to 0.6f, "lift" to 0.5f,
    )
}

/**
 * The pane glass over [content] — Home's cards, Proxies' and Subscriptions' lists, a glass sheet and all on it — in one
 * place so they stay alike. [on] false: the content as it is (a frosted sheet, Air).
 */
@Composable
internal fun GlassPanes(on: Boolean = true, content: @Composable () -> Unit) {
    // One call either way: content called from another place when [on] changed was a new one to Compose, and a screen
    // over the tabs lost its state as Air became glass — Settings fell back to its first page and blinked (the owner,
    // 23.11).
    val style = LocalGlassStyle.current
    CompositionLocalProvider(
        LocalGlassPanes provides (on || LocalGlassPanes.current),
        LocalGlassStyle provides if (on) LocalGlassTuning.current.optics(GlassTuningGroup.Pane, style.asDock()) else style,
        content = content,
    )
}

/** The pane glass is drawn here: its panes answer a press by swelling ([glassPress]), not with a ripple. */
@Composable
internal fun glassPanesOn(): Boolean =
    LocalGlassBackdrop.current != null && LocalGlassPanes.current && LocalGlassStyle.current.enabled && glassSupported

/** A pane's click indication: none under the pane glass, whose press is the swell; the usual ripple elsewhere. */
@Composable
internal fun paneIndication(): Indication? = if (glassPanesOn()) null else LocalIndication.current

/**
 * The pane glass's whole tuning. Lengths stay the same on any pane, as iOS's rim is as wide on a small button as on
 * the player (F-550); the glass settings scale them around their defaults (refraction 50, dispersion 40, depth 30,
 * highlight 60, vibrancy 40).
 */
internal object PaneGlass {
    /** The panes' own blur, tint and colour lift (the dock's were theirs until the owner tuned both apart, 22.11). */
    const val BLUR = 13
    const val TINT = 50
    const val VIBRANCY = 15
    /**
     * How wide the rim's roll is: over it the picture is drawn in from beyond the rim, its colours livelier, and the
     * glass lighter towards the rim. iOS's player rolls over ~8 pt (F-550); at 9 dp ours read as a thin hard frame, so
     * wider and softer (the owner, 18.11: the colour diffused wider at the edges). Small panes keep 0.6 of their half.
     */
    val BAND = 6.01.dp
    /** How far beyond the rim the rim's picture comes from, at the rim itself. */
    val PUSH = 13.28.dp
    /** How far red and blue part where the rim bends most. */
    val CHROMA = 1.16.dp
    /**
     * The colours inside the pane, as a share of the picture's: the dock's own (1), so the two read as one glass; and how
     * much livelier they are at the rim.
     */
    const val SATURATION = 1f
    const val VIVID = 0.15f
    /**
     * How much lighter, towards white, the roll is at the rim, falling off as (1 − t)³: over the whole band, (1 − t)²
     * at 0.12, it laid a light haze over the cards' sides that read as the wallpaper's teal against the dark dock
     * (the owner, 18.11: «все равно не как докбар по цвету»).
     */
    const val ROLL = 0f
    /**
     * The dock's rim, the old glass's directional light line ([HIGHLIGHT_SHADER] at [DockGlass.HIGHLIGHT]), in place of
     * the pane's own [LINE]: tried and kept by the owner (19.11: «мне нравится, оставим»). False brings the pane's line back.
     */
    const val DOCK_RIM = false
    /** The light line along the rim, its strength and width. */
    const val LINE = 1f
    val LINE_WIDTH = 0.25.dp
    /** The line's glow inwards: its reach, strength, how fast it fades, how much it follows the light (top left). */
    val LINE_SPREAD = 5.92.dp
    const val LINE_GLOW = 0.3f
    const val LINE_FALL = 2.18f
    const val LINE_DIRECT = 0.83f
    /** The roll lighter at the top and darker at the bottom. */
    const val VOLUME = 0.1f
    /** How much of the material's dark tint the rim is spared, so its roll and light show; at 0.5 the cards were lighter than the dock. */
    const val SPARE = 0f
    /** The faint dark line just outside the rim. */
    const val RING = 0f
    val RING_WIDTH = 0.25.dp
    /**
     * The material's tint laid this much denser: the cards read lighter than the dock, whose glass is the same but whose
     * wallpaper at the screen's foot is darker (F-550; the owner, 18.11: «докбар темнее чем карточки»).
     */
    const val DENSER = 0.25f
    /**
     * A press eases the tint by [PRESS_CLEAR] over [PRESS_IN_MS] in and [PRESS_OUT_MS] back. Lifting the picture's
     * brightness and colours read as oversaturated beside the dock's restrained press, and even the dock's 0.3 eased
     * the cards' colours ×1.35-1.56 (the owner, 18.11: «сильновато тонировка спадает»); longer than the dock's 150 / 400,
     * as the owner asked a smoother press.
     */
    const val PRESS_CLEAR = 1f
    const val PRESS_IN_MS = 260
    const val PRESS_OUT_MS = 560
    /** It grows by this length, between these shares of its width. */
    val PRESS_GROW = 8.dp
    const val GROW_MIN = 0.04f
    const val GROW_MAX = 0.06f
    /**
     * The dock's breath springs, so a pane swells as the dock does: iOS's player's +3 % in ~70 ms with a quick bounce
     * (0.8 / 1200 in, 0.4 / 900 out) read as abrupt (the owner, 18.11: smoother, a little longer). A short tap still
     * swells for [PRESS_LEAST_MS] before it goes back, as the dock's breath outlasts a 60 ms finger.
     */
    val PRESS_IN = DockGlass.BREATH_SPRING
    val PRESS_OUT = DockGlass.BREATH_BACK
    const val PRESS_LEAST_MS = 160L
    /**
     * Held this long, a finger that moves takes the pane along like the dock's jelly (the owner, 18.11); moving sooner,
     * it is a swipe for the tabs or a scroll. The pull: rubber-banded over [PULL_GIVE], moved up to [PULL_MOVE],
     * stretched along it by [PULL_ALONG] and narrowed across by [PULL_ACROSS], on the dock's springs.
     */
    const val HOLD_MS = 160L
    val PULL_GIVE = 60.dp
    // Half of the first pass's 8 / 6 dp and 3 / 2 %: they slid too far (the owner, 18.11).
    val PULL_MOVE_X = 4.dp
    val PULL_MOVE_Y = 3.dp
    const val PULL_ALONG = 0.015f
    const val PULL_ACROSS = 0.01f
    val PULL_FOLLOW = spring<Float>(dampingRatio = 0.42f, stiffness = 320f)
    val PULL_BACK = spring<Float>(dampingRatio = 0.38f, stiffness = 260f)
}

/**
 * One of each of the pane glass's shaders for all its panes (F-582): compiling the pane's took the main thread ~1.9 ms on
 * the OnePlus 12, and each pane compiled two of them and a veil as it came into composition, ~4 ms a pane. A pane sets
 * all of a shader's uniforms right before it makes its effect or draws with it; an effect or a drawing keeps the uniforms
 * it was made with, so the next pane's do not reach it. The main thread only, as all drawing here.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object PaneShaders {
    val glass by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(PANE_SHADER) }
    val veil by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(PANE_VEIL_SHADER) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Density.paneBend(
    shader: RuntimeShader, margin: Float, width: Float, height: Float, cornerRadii: FloatArray, style: GlassStyle, boost: Float,
    ownRim: Boolean, tuning: GlassTuning?, group: GlassTuningGroup = GlassTuningGroup.Pane,
) {
    fun value(key: String, fallback: Float) = tuning.paneKnob(group, key, fallback)
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("offset", -margin, -margin)
    shader.setFloatUniform("shift", 0f, 0f)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    shader.setFloatUniform("band", value("band", PaneGlass.BAND.value).dp.toPx())
    shader.setFloatUniform("push", value("push", PaneGlass.PUSH.value).dp.toPx() * (style.refraction / 50f) * boost)
    shader.setFloatUniform("chroma", value("chroma", PaneGlass.CHROMA.value).dp.toPx() * (style.dispersion / 40f))
    shader.setFloatUniform("saturation", value("saturation", PaneGlass.SATURATION))
    shader.setFloatUniform("vivid", value("vivid", PaneGlass.VIVID) * (style.vibrancy / 40f))
    shader.setFloatUniform("roll", value("roll", PaneGlass.ROLL))
    shader.setFloatUniform("zoom", 1f, 1f)
    shader.setFloatUniform("line", if (ownRim) value("line", PaneGlass.LINE) * (style.highlight / 60f) else 0f)
    shader.setFloatUniform("lineWidth", value("lineWidth", PaneGlass.LINE_WIDTH.value).dp.toPx())
    // The line's glow inwards, none by default: its reach, strength, how fast it fades, how much it follows the light.
    shader.setFloatUniform("lineSpread", value("lineSpread", PaneGlass.LINE_SPREAD.value).dp.toPx())
    shader.setFloatUniform("lineGlow", value("lineGlow", PaneGlass.LINE_GLOW))
    shader.setFloatUniform("lineFall", value("lineFall", PaneGlass.LINE_FALL))
    shader.setFloatUniform("lineDirect", value("lineDirect", PaneGlass.LINE_DIRECT))
    shader.setFloatUniform("volume", value("volume", PaneGlass.VOLUME) * (style.depth / 30f))
    shader.setFloatUniform("noiseStrength", style.noise / 100f * 0.05f)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Density.paneVeil(
    shader: RuntimeShader, width: Float, height: Float, cornerRadii: FloatArray, tuning: GlassTuning?,
    group: GlassTuningGroup = GlassTuningGroup.Pane,
) {
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    val band = tuning.paneKnob(group, "band", PaneGlass.BAND.value)
    val spare = tuning.paneKnob(group, "spare", PaneGlass.SPARE)
    shader.setFloatUniform("band", band.dp.toPx())
    shader.setFloatUniform("spare", spare)
}

/** A finger on a pane of the pane glass: it swells, its tint eases, and, held, it follows the finger a little. */
@Stable
internal class GlassPress(private val scope: CoroutineScope, val tuning: GlassTuning?) {
    /** 0 at rest, 1 pressed, below 0 for a moment as it springs back. */
    val amount = Animatable(0f)
    /** The tint's easing, 0…1, on a tween: the bouncing spring made it flicker. */
    val ease = Animatable(0f)
    /** The finger's way since it took the pane, in pixels. */
    val pullX = Animatable(0f)
    val pullY = Animatable(0f)

    fun light(): Float = ease.value

    private var downAt = 0L
    private var back: kotlinx.coroutines.Job? = null

    fun down() {
        back?.cancel()
        downAt = android.os.SystemClock.uptimeMillis()
        scope.launch { amount.animateTo(1f, PaneGlass.PRESS_IN) }
        scope.launch { ease.animateTo(1f, androidx.compose.animation.core.tween(PaneGlass.PRESS_IN_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    }

    fun pull(way: Offset) {
        scope.launch { pullX.animateTo(way.x, PaneGlass.PULL_FOLLOW) }
        scope.launch { pullY.animateTo(way.y, PaneGlass.PULL_FOLLOW) }
    }

    fun up() {
        scope.launch { pullX.animateTo(0f, PaneGlass.PULL_BACK) }
        scope.launch { pullY.animateTo(0f, PaneGlass.PULL_BACK) }
        back = scope.launch {
            kotlinx.coroutines.delay((PaneGlass.PRESS_LEAST_MS - (android.os.SystemClock.uptimeMillis() - downAt)).coerceAtLeast(0L))
            launch { amount.animateTo(0f, PaneGlass.PRESS_OUT) }
            launch { ease.animateTo(0f, androidx.compose.animation.core.tween(PaneGlass.PRESS_OUT_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        }
    }
}

/** A press for a pane of the pane glass; null where it is not drawn (Air, older phones, the other screens for now). */
@Composable
internal fun rememberGlassPress(): GlassPress? {
    val on = glassPanesOn()
    val scope = rememberCoroutineScope()
    val tuning = LocalGlassTuning.current
    return remember(on, scope, tuning) { if (on) GlassPress(scope, tuning) else null }
}

/**
 * Swells this pane under a finger and springs it back past its size as it is let go, like jelly; held for
 * [PaneGlass.HOLD_MS], it then follows the finger a little, as the dock does, and the tabs and the page do not move.
 * [own] — the touch is this pane's own button's: a card ([own] false) does not swell for a touch a button inside it
 * took. Placed before the pane's [liquidGlass], whose glass then follows it.
 */
internal fun Modifier.glassPress(press: GlassPress?, own: Boolean, follow: Boolean = true): Modifier =
    if (press == null) this else pressTouch(press, own, PointerEventPass.Final, follow).glassPressSwell(press)

/**
 * A pane that is a button itself (a node's card, a group's header, a subscription's row): [glassPressSwell] before its
 * glass, [glassPressOwnTouch] after its own click in the same chain. It then swells for its own touches but not for a
 * button's inside it: the buttons take the touch before it sees it, its own click only after. [glassPress] with [own]
 * false never swelled here, as the pane's own click took every touch first.
 */
internal fun Modifier.glassPressOwnTouch(press: GlassPress?): Modifier =
    if (press == null) this else pressTouch(press, own = false, PointerEventPass.Main)

/**
 * [follow] false: it stays swollen while the finger is down, whatever it does, and never takes the move — for a
 * control that a finger drags itself (the segments' lens), as the dock breathes while its lens is dragged.
 */
private fun Modifier.pressTouch(press: GlassPress, own: Boolean, pass: PointerEventPass, follow: Boolean = true): Modifier =
    pointerInput(press, own, pass, follow) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
            if (!own && down.isConsumed) return@awaitEachGesture
            press.down()
            val slop = viewConfiguration.touchSlop
            var held = false
            // Let go however the gesture ends: cancelled mid-press (the pane recomposed with a new key), it stayed swollen.
            try {
                while (true) {
                    // The Main pass reaches it before the pager and the page's scroll above it, so a held pane keeps the move.
                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!follow) continue
                    val way = change.position - down.position
                    if (!held) {
                        if (change.isConsumed) break
                        if (way.getDistance() > slop) {
                            // Moved at once: a swipe for the tabs or a scroll, theirs.
                            if (change.uptimeMillis - down.uptimeMillis < PaneGlass.HOLD_MS) break
                            held = true
                        }
                    }
                    if (held) {
                        change.consume()
                        press.pull(way)
                    }
                }
            } finally {
                press.up()
            }
        }
    }

/** The swell and the pull of [press], placed before the pane's [liquidGlass], whose glass then follows it. */
internal fun Modifier.glassPressSwell(press: GlassPress?): Modifier =
    if (press == null) this else graphicsLayer {
        val tuning = press.tuning
        val growDp = tuning?.value(GlassTuningGroup.Pane, "pressGrow", PaneGlass.PRESS_GROW.value) ?: PaneGlass.PRESS_GROW.value
        val maxGrow = tuning?.value(GlassTuningGroup.Pane, "growMax", PaneGlass.GROW_MAX) ?: PaneGlass.GROW_MAX
        val minGrow = tuning?.value(GlassTuningGroup.Pane, "growMin", PaneGlass.GROW_MIN) ?: PaneGlass.GROW_MIN
        val grow = (growDp.dp.toPx() / size.width.coerceAtLeast(1f)).coerceIn(minOf(minGrow, maxGrow), maxGrow)
        val z = 1f + grow * press.amount.value
        val give = PaneGlass.PULL_GIVE.toPx()
        val x = press.pullX.value / (kotlin.math.abs(press.pullX.value) + give)
        val y = press.pullY.value / (kotlin.math.abs(press.pullY.value) + give)
        translationX = x * PaneGlass.PULL_MOVE_X.toPx()
        translationY = y * PaneGlass.PULL_MOVE_Y.toPx()
        scaleX = z * (1f + PaneGlass.PULL_ALONG * kotlin.math.abs(x)) * (1f - PaneGlass.PULL_ACROSS * kotlin.math.abs(y))
        scaleY = z * (1f + PaneGlass.PULL_ALONG * kotlin.math.abs(y)) * (1f - PaneGlass.PULL_ACROSS * kotlin.math.abs(x))
    }

/*
 * The pane glass (F-550): the blurred picture as it is; within [band] of the rim it is drawn from up to [push] beyond
 * it, easing in towards the rim, red and blue parted, its colours a little livelier, the glass rolling lighter towards
 * the rim, lighter at the top and darker at the bottom; a faint light line along it in the colour of what lies under it,
 * brightest at the top and the bottom. [zoom]: the still picture read under a pane its layers scale.
 */
private const val PANE_SHADER = """
uniform shader img;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float2 shift;
uniform float band;
uniform float push;
uniform float chroma;
uniform float saturation;
uniform float vivid;
uniform float roll;
uniform float line;
uniform float lineWidth;
uniform float lineSpread;
uniform float lineGlow;
uniform float lineFall;
uniform float lineDirect;
uniform float volume;
uniform float noiseStrength;
// How much the layers around the pane scale it: a point of it lies this many times as far from its corner on the screen.
uniform float2 zoom;

half4 sample(float2 p) { return img.eval(p * zoom + shift); }

$ROUNDED_RECT_SDF

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = (coord + offset) - halfSize;
    float radius = radiusAt(p, cornerRadii);
    float sd = sdRoundedRect(p, halfSize, radius);
    // The pixels the rim crosses too: their share of the light line, see rimLine.
    if (sd > 0.5) return sample(coord);

    float depth = -sd;
    float b = clamp(band, 1.0, max(min(halfSize.x, halfSize.y) * 0.6, 1.0));
    // Past the roll and the light line with its glow the glass is flat: what the rest below gives there, with one read of
    // the picture instead of three (most of a pane, a sheet's whole body).
    if (depth >= b && depth >= lineWidth + 0.5 && depth >= lineSpread) {
        half4 inner = sample(coord);
        half innerLum = dot(inner.rgb, half3(0.299, 0.587, 0.114));
        inner.rgb = clamp(mix(half3(innerLum), inner.rgb, half(saturation)), half3(0.0), half3(inner.a));
        if (noiseStrength > 0.0) {
            inner.rgb = clamp(inner.rgb + half3(hash(coord) * noiseStrength), half3(0.0), half3(inner.a));
        }
        return inner;
    }
    float t = clamp(depth / b, 0.0, 1.0);
    float2 n = gradSdRoundedRect(p, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));
    float u = 1.0 - t;
    // Eased in over the band, not a circle's edge: that squeezed the drawn-in colours into a thin hard frame at the rim
    // (the owner, 18.11: «как у виндоус 7»); spread wide, they diffuse inwards as iOS's.
    float bent = u * u;
    float2 disp = n * (push * bent);
    float2 split = n * (chroma * bent);

    half4 c = sample(coord + disp);
    c.r = sample(coord + disp * 1.06 + split).r;
    c.b = sample(coord + disp * 0.94 - split).b;

    float rim = 1.0 - smoothstep(0.0, 1.0, t);
    half lum = dot(c.rgb, half3(0.299, 0.587, 0.114));
    c.rgb = clamp(mix(half3(lum), c.rgb, half(saturation + vivid * rim)), half3(0.0), half3(c.a));
    // The roll: lighter towards the rim, softly, as the glass curves away and catches the light.
    c.rgb += (half3(c.a) - c.rgb) * half(roll * u * u * u);
    c.rgb *= half(1.0 - volume * n.y * rim);

    float lw = rimLine(depth, lineWidth);
    // A glow from the line inwards over lineSpread, at lineGlow, fading by the power lineFall: the further, the weaker.
    if (lineSpread > 0.0) lw = max(lw, lineGlow * pow(clamp(1.0 - max(depth, 0.0) / lineSpread, 0.0, 1.0), lineFall) * rimLine(depth, 1.0e6));
    // Brightest where the light falls (top left), or, at lineDirect 0, evenly all round.
    float dir = mix(1.0, 0.3 + 0.7 * pow(abs(dot(n, float2(-0.447, -0.894))), 1.5), lineDirect);
    c.rgb = min(c.rgb + (c.rgb * 0.9 + half3(0.2) * c.a) * half(lw * line * dir), half3(c.a));

    if (noiseStrength > 0.0) {
        c.rgb = clamp(c.rgb + half3(hash(coord) * noiseStrength), half3(0.0), half3(c.a));
    }
    return c;
}
"""

/** The pane glass's veil: [color], thinner over the rim's band by [spare]. */
private const val PANE_VEIL_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float band;
uniform float spare;
uniform half4 color;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float depth = max(-sdRoundedRect(p, halfSize, radiusAt(p, cornerRadii)), 0.0);
    float b = clamp(band, 1.0, max(min(halfSize.x, halfSize.y) * 0.6, 1.0));
    // Thinned softly over the whole band, not cut away at the very rim: that bare strip read as a hard frame (the owner, 18.11).
    float keep = 1.0 - spare * (1.0 - smoothstep(0.0, 1.0, depth / b));
    return color * half(keep);
}
"""

/**
 * The cards' own light line (see [PANE_SHADER]) over glass already drawn: «Док 1»'s body, its bend and reflection left as
 * they are. Brightens what the glass shows along its rim, as the cards' line does.
 */
private const val DOCK_LINE_SHADER = """
uniform shader img;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float line;
uniform float lineWidth;
uniform float lineSpread;
uniform float lineGlow;
uniform float lineFall;
uniform float lineDirect;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    half4 c = img.eval(coord);
    float2 halfSize = size * 0.5;
    float2 p = (coord + offset) - halfSize;
    float radius = radiusAt(p, cornerRadii);
    float sd = sdRoundedRect(p, halfSize, radius);
    if (sd > 0.5 || line <= 0.0) return c;

    float depth = -sd;
    float2 n = gradSdRoundedRect(p, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));
    float lw = rimLine(depth, lineWidth);
    if (lineSpread > 0.0) lw = max(lw, lineGlow * pow(clamp(1.0 - max(depth, 0.0) / lineSpread, 0.0, 1.0), lineFall) * rimLine(depth, 1.0e6));
    float dir = mix(1.0, 0.3 + 0.7 * pow(abs(dot(n, float2(-0.447, -0.894))), 1.5), lineDirect);
    c.rgb = min(c.rgb + (c.rgb * 0.9 + half3(0.2) * c.a) * half(lw * line * dir), half3(c.a));
    return c;
}
"""

