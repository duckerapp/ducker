package com.github.kr328.clash.ui.components

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
import android.graphics.BitmapFactory
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
    val image: Long = 0L,
    /** The own picture's top, under the status bar and the headers, is dark. */
    val imageTopDark: Boolean = false,
    val refraction: Int = 50,
    val dispersion: Int = 40,
    val depth: Int = 30,
    val highlight: Int = 60,
    val noise: Int = 15,
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

/** The black backdrops, and an own picture that is dark: on them the app takes the dark palette. */
internal fun backdropIsDark(key: String, imageDark: Boolean): Boolean =
    key == "black_gradient" || key == "black" || key == "black_white" || (key == "image" && imageDark)

/**
 * Whether the backdrop's top, under the status bar and the tabs' headers, is dark: the black ones and a dark-topped
 * own picture. Null — the backdrop is drawn in the theme's own tones, so the headers follow the theme.
 */
internal fun backdropTopDark(key: String, imageTopDark: Boolean): Boolean? = when (key) {
    "black_gradient", "black", "black_white" -> true
    "image" -> imageTopDark
    else -> null
}

/**
 * The chosen backdrop behind a screen's cards, so glass cards have something to show. Its patches are fainter
 * while [running] is false. Still: a moving backdrop would redraw the screen without end (D-91). Drawn past the
 * element above and below, so it reaches under the status bar and the dock.
 */
@Composable
internal fun Modifier.glassBackground(key: String, running: Boolean): Modifier {
    if (key == "none") return this
    if (key == "image") return LocalGlassStyle.current.let { glassImage(it.image, it.imageTopDark) }

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
 * The own picture, still behind the screen: it fills the whole window, cropped, wherever this element sits,
 * so it does not move as the cards scroll over it. [version] is the file's time, to load a new one. Its top lies
 * under a soft veil in its own tone, [topDark]: the headers stand there, straight on the picture.
 */
@Composable
private fun Modifier.glassImage(version: Long, topDark: Boolean): Modifier {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(File(context.filesDir, "glass_backdrop.jpg").path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val window = LocalWindowInfo.current.containerSize
    var at by remember { mutableStateOf(Offset.Zero) }
    val veilEnd = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HEADER_VEIL
    val tone = if (topDark) Color.Black else Color.White

    return onGloballyPositioned { at = it.positionInRoot() }.drawBehind {
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
}

/** A source layer recorded when its draw node is invalidated and sampled again inside the glass. */
class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    /** Counts recordings of [layer]: a still picture made from an older one no longer shows it. */
    internal var version by mutableIntStateOf(0)
    /** The backdrop drawn once per blur, see [StillBackdropPictures]; null — not made, or the backdrop has changed. */
    internal var still by mutableStateOf<StillBackdrop?>(null)
    var waveCenter by mutableStateOf(Offset.Unspecified)
    var waveProgress by mutableFloatStateOf(0f)
    var waveActive by mutableStateOf(false)
}

@Composable
internal fun rememberGlassBackdrop(still: Boolean = false): GlassBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !LocalGlassStyle.current.enabled) return null

    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }

    if (still) StillBackdropPictures(backdrop)

    return backdrop
}

/** Blur every pane in this scope adds to its own: the panes inside a glass sheet, so they are no clearer than it. */
internal val LocalGlassExtraBlur = staticCompositionLocalOf { 0.dp }

/**
 * The backdrop as recorded at [version], drawn once per pane blur (in pixels) with the panes' colour lift. [held]: shown
 * after the backdrop has changed too, see [SheetStillPictures].
 */
internal class StillBackdrop(val version: Int, val pictures: Map<Int, android.graphics.BitmapShader>, val held: Boolean = false)

/**
 * What a glass pane here shows of the backdrop under it, for a lens over the pane (a switch's, a slider's): the pane's
 * still picture and its veil, drawn at [position] in the window. Nothing where none is made: the lens is clear there.
 */
@Composable
internal fun rememberPaneUnder(): (DrawScope.(position: Offset) -> Unit) {
    val backdrop = LocalGlassBackdrop.current
    val blur = with(LocalDensity.current) {
        (MAX_BLUR.toPx() * (LocalGlassStyle.current.blur / 100f) + LocalGlassExtraBlur.current.toPx()).roundToInt()
    }
    val veil = MaterialTheme.colorScheme.surface.copy(alpha = GlassMaterial.Surface.tint)
    return remember(backdrop, blur, veil) {
        { position ->
            val picture = backdrop?.still?.pictures?.get(blur)
            if (picture != null) {
                val size = backdrop.layer.size
                translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) {
                    val whole = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                    drawRect(ShaderBrush(picture), size = whole)
                    // Over the whole picture: sized by the lens layer around the control's corner, the veil missed a
                    // wide control's far end, and the lens over the last pill showed the bare wallpaper (the owner, 18.11).
                    drawRect(veil, size = whole)
                }
            }
        }
    }
}

@Composable
private fun stillBlurs(extra: Dp): List<Int> {
    val density = LocalDensity.current
    val blur = LocalGlassStyle.current.blur
    // Every blur a pane on this backdrop asks for: the panes, the controls on them, the power button.
    return remember(blur, density, extra) {
        with(density) {
            listOf(0.dp, INNER_BLUR, INNER_BLUR + POWER_BLUR_BOOST).map { (MAX_BLUR.toPx() * (blur / 100f) + (it + extra).toPx()).roundToInt() }.distinct()
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
internal fun StillBackdropPictures(backdrop: GlassBackdrop) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val style = LocalGlassStyle.current
    val blurs = stillBlurs(0.dp)

    LaunchedEffect(backdrop, blurs, style.vibrancy) {
        backdrop.still = null
        snapshotFlow { backdrop.version to backdrop.waveActive }.collectLatest { (version, wave) ->
            if (wave) return@collectLatest
            // A backdrop changing for a while (a theme's colours fading over) is drawn once it has settled.
            delay(STILL_SETTLE_MS)
            val size = backdrop.layer.size
            if (size.width <= 0 || size.height <= 0) return@collectLatest

            val pictures = blurs.associateWith { blur -> stillPicture(graphics, density, backdrop.layer, size, blur, style.vibrancy) }
            if (backdrop.version == version) backdrop.still = StillBackdrop(version, pictures)
        }
    }
}

/**
 * The screen under a glass sheet, drawn still for the sheet's panes when it is asked to open, before it moves (F-532):
 * the sheet and each of its rows had blurred their own copy of the screen every frame of the slide, 10-17 ms a frame on
 * the OnePlus at 120 Hz. Kept between opens while the screen is the same, and held while the sheet is open though the
 * screen changes under it (the tap's ripple, a number): blurred past reading, the older picture shows as well.
 */
@Composable
internal fun SheetStillPictures(backdrop: GlassBackdrop, sheets: SheetGlass, extra: Dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val vibrancy = LocalGlassStyle.current.vibrancy
    val blurs = stillBlurs(extra)

    LaunchedEffect(backdrop, sheets, blurs, vibrancy) {
        backdrop.still = null
        snapshotFlow { sheets.wanted }.collectLatest { wanted ->
            sheets.ready = false
            if (!wanted) return@collectLatest
            val version = backdrop.version
            val size = backdrop.layer.size
            if (backdrop.still?.version != version && size.width > 0 && size.height > 0) {
                val pictures = blurs.associateWith { blur -> stillPicture(graphics, density, backdrop.layer, size, blur, vibrancy) }
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
): android.graphics.BitmapShader {
    // Drawn smaller as the blur hides detail: sharp at none, a quarter of the pixels from a blur of 16.
    val factor = when {
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
    /** Its own veil instead of a flat [tint], drawn under the rim. */
    veil: (DrawScope.() -> Unit)? = null,
): Modifier {
    val style = LocalGlassStyle.current
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !style.enabled) return this
    val extra = LocalGlassExtraBlur.current

    val glass = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(GLASS_SHADER) }
    // The same glass drawn straight from a still backdrop's picture: no layer of its own, no blur of its own.
    val stillShader = remember { RuntimeShader(GLASS_SHADER) }
    val stillBrush = remember(stillShader) { ShaderBrush(stillShader) }
    val highlightShader = remember { RuntimeShader(HIGHLIGHT_SHADER) }
    val highlightBrush = remember(highlightShader) { ShaderBrush(highlightShader) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val screenBackground = MaterialTheme.colorScheme.background
    val blurPart = style.blur / 100f

    return onGloballyPositioned { position = it.positionInRoot() }.drawWithCache {
        val margin = GLASS_MARGIN.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val radii = extractCornerRadii(outline, size)
        val blur = MAX_BLUR.toPx() * blurPart + (blurBoost + extra).toPx()
        val amount = MAX_REFRACTION_AMOUNT.toPx() * (style.refraction / 100f) * bendBoost
        val bent = if (bendBoost != 1f) style.copy(dispersion = (style.dispersion * bendBoost).roundToInt()) else style

        glass.renderEffect = glassEffect(shader, margin, size.width, size.height, radii, BEZEL.toPx(), amount, blur, bent)
        bend(stillShader, 0f, size.width, size.height, radii, BEZEL.toPx(), amount, bent)
        val stillBlur = blur.roundToInt()
        val paneSize = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())
        val clip = Path().apply { addOutline(outline) }

        onDrawBehind {
            val still = backdrop.still?.takeIf { (it.held || it.version == backdrop.version) && !backdrop.waveActive }?.pictures?.get(stillBlur)

            if (still == null) {
                glass.record(paneSize) {
                    translate(backdrop.origin.x - position.x + margin, backdrop.origin.y - position.y + margin) {
                        drawLayer(backdrop.layer)
                    }
                }
            } else {
                stillShader.setInputShader("img", still)
                stillShader.setFloatUniform("shift", position.x - backdrop.origin.x, position.y - backdrop.origin.y)
            }

            clipPath(clip) {
                drawRect(base)
                if (still == null) translate(-margin, -margin) { drawLayer(glass) } else drawRect(stillBrush)
                val shadowAlpha = 0.12f * (style.depth / 100f)
                if (shadowAlpha > 0.005f) {
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
                if (veil != null) {
                    veil()
                } else if (tintProgress > 0f && tint.alpha > 0f) {
                    drawRect(tint.copy(alpha = (tint.alpha * tintProgress).coerceIn(0f, 0.92f)))
                }
            }

            // Specular directional highlight
            if (style.highlight > 0) {
                val isLight = (if (base != Color.Transparent) base else screenBackground).luminance() > 0.5f
                // At 100 % on a light screen the rim is as white as it was drawn before the fix below.
                val hAlpha = ((if (isLight) 1f else 0.55f) * (style.highlight / 100f) * rimBoost).coerceAtMost(1f)
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
): androidx.compose.ui.graphics.RenderEffect {
    bend(shader, margin, width, height, cornerRadii, bezel, amount, style)
    val bend = RenderEffect.createRuntimeShaderEffect(shader, "img")
    val inner = paneLift(blur, style.vibrancy)

    return (if (inner != null) RenderEffect.createChainEffect(bend, inner) else bend)
        .asComposeRenderEffect()
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

/** What a pane does to the backdrop before bending it: blurs it by [blur] pixels and lifts its colour by [vibrancy] %. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun paneLift(blur: Float, vibrancy: Int): RenderEffect? {
    val sat = 1f + 0.45f * (vibrancy / 100f)
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

/**
 * The dock's retained lens shader. Effect snapshots are cached by their geometry and zoom uniforms.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Keep
internal class DockLens {
    private val shader = RuntimeShader(LENS_SHADER)
    private var lastLeft = Float.NaN
    private var lastWidth = Float.NaN
    private var lastHeight = Float.NaN
    private var lastZoom = Float.NaN
    private var snapshot: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(left: Float, width: Float, height: Float, zoom: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (zoom <= 1.002f) return null
        if (left != lastLeft || width != lastWidth || height != lastHeight || zoom != lastZoom) {
            shader.setFloatUniform("center", left + width / 2f, height / 2f)
            shader.setFloatUniform("halfSize", width / 2f, height / 2f)
            shader.setFloatUniform("zoom", zoom)
            // RenderEffect snapshots uniforms: retain the shader, refresh the snapshot when they change.
            snapshot = RenderEffect.createRuntimeShaderEffect(shader, "img").asComposeRenderEffect()
            lastLeft = left
            lastWidth = width
            lastHeight = height
            lastZoom = zoom
        }
        return snapshot
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

internal interface GlassBubble {
    fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, presence: Float, zoom: Float, base: Color, under: DrawScope.() -> Unit)
}

@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassBubble : GlassBubble {
    private val shader = RuntimeShader(BUBBLE_SHADER)
    override fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, presence: Float, zoom: Float, base: Color, under: DrawScope.() -> Unit) {
        scope.drawShaderBubble(layer, shader, center, halfWidth, halfHeight, presence, zoom, base, under)
    }
}

internal fun bubbleShader(): GlassBubble? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else Api33GlassBubble()

/**
 * The switch's lens, after iOS's (the owner's video, 18.11): thick clear glass that draws the surroundings in from
 * beyond its edge, so what lies under it looks smaller and the rim holds what lies outside; a thin band at the very rim
 * mirrors what lies inside, in its colour; the colours split where the glass bends most; a light line along the
 * edge, brightest on the diagonal; a soft shadow under it. [lift] 0…1 brings the shadow in as the lens grows;
 * [frost] 0…1 clouds it as it settles back into the thumb.
 */
internal interface GlassLens {
    fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, lift: Float, frost: Float, dark: Boolean, under: DrawScope.() -> Unit)
}

@Keep
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33GlassLens : GlassLens {
    private val shader = RuntimeShader(LIQUID_LENS_SHADER)
    override fun draw(scope: DrawScope, layer: GraphicsLayer, center: Offset, halfWidth: Float, halfHeight: Float, lift: Float, frost: Float, dark: Boolean, under: DrawScope.() -> Unit) =
        with(scope) {
            val margin = GLASS_MARGIN.toPx()
            val left = center.x - halfWidth - margin
            val top = center.y - halfHeight - margin
            val radius = minOf(halfWidth, halfHeight)
            shader.setFloatUniform("center", halfWidth + margin, halfHeight + margin)
            shader.setFloatUniform("halfSize", halfWidth, halfHeight)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("frost", frost)
            shader.setFloatUniform("shine", if (dark) 0.18f else 0.6f)
            shader.setFloatUniform("shadow", lift * if (dark) 0.3f else 0.12f)
            shader.setFloatUniform("shadowBlur", 0.25f * radius)
                        shader.setFloatUniform("edge", 0.6.dp.toPx())
            shader.setFloatUniform("inner", 0.15f * radius)
            shader.setFloatUniform("lift", lift)
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "img")
            // Never quite clear: a touch of blur as the video's even when held.
            val blur = LENS_CLEAR_BLUR.toPx() + frost * LENS_FROST_BLUR.toPx()
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

internal fun lensShader(): GlassLens? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else Api33GlassLens()

private val LENS_CLEAR_BLUR = 0.3.dp
private val LENS_FROST_BLUR = 3.dp

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
    under: DrawScope.() -> Unit,
) =
    shader.draw(this, layer, center, halfWidth, halfHeight, presence, zoom, base, under)

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
    under: DrawScope.() -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val margin = GLASS_MARGIN.toPx()
    val left = center.x - halfWidth - margin
    val top = center.y - halfHeight - margin
    val bezel = minOf(BUBBLE_BEZEL.toPx(), halfHeight)

    shader.setFloatUniform("center", halfWidth + margin, halfHeight + margin)
    shader.setFloatUniform("halfSize", halfWidth, halfHeight)
    shader.setFloatUniform("radius", halfHeight)
    shader.setFloatUniform("zoom", zoom)
    shader.setFloatUniform("bezel", bezel)
    shader.setFloatUniform("strength", 0.28f)
    shader.setFloatUniform("spread", 0.12f)

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
    drawRoundRect(glassRim(base).let { it.copy(alpha = it.alpha * presence) }, topLeft, size, corner, style = Stroke(1.2.dp.toPx()))
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
private val BUBBLE_BEZEL = 14.dp
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
 * Inside the pill the picture is taken nearer its middle, so it shows larger; the zoom eases to none at the
 * pill's ends, where the picture meets the one beside it — a flat zoom lost the letters there.
 */
private const val LENS_SHADER = """
uniform shader img;
uniform float2 center;
uniform float2 halfSize;
uniform float zoom;

half4 main(float2 coord) {
    float2 p = coord - center;
    if (abs(p.x) > halfSize.x || abs(p.y) > halfSize.y) return img.eval(coord);
    float x = p.x / halfSize.x;
    float z = 1.0 + (zoom - 1.0) * (1.0 - x * x);
    return img.eval(center + p / z);
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
    float ring = 0.6 * lift * (1.0 - clamp(max(d, 0.0) / (edge * 1.6), 0.0, 1.0));
    half4 shade = half4(0.0, 0.0, 0.0, max(shadow * (1.0 - smoothstep(-shadowBlur, shadowBlur, s)), ring));
    // The rim's pixels partly glass, partly shadow: a hard cut showed steps along the curve (the owner, 18.11).
    float cover = clamp(0.5 - d, 0.0, 1.0);
    if (cover <= 0.0) return shade;
    float depth = max(-d, 0.0);
    float2 n = gradSdRoundedRect(p, halfSize, radius);
    float t = clamp(depth / radius, 0.0, 1.0);

    float deep = smoothstep(0.0, 0.35, t);
    float push = 0.3 * radius * pow(1.0 - t, 1.5);
    // Towards the ends the glass is thinner across: there the picture keeps its height and the dark bands from above
    // and below end in rounded tips short of the rim, as iOS's (the owner, 18.11).
    float intoEnd = max(abs(p.x) - (halfSize.x - radius), 0.0);
    float across = pow(sqrt(max(radius * radius - intoEnd * intoEnd, 0.0)) / radius, 6.0);
    // The rim's push leaves off sooner, from a quarter of the way into an end: the dark bands from above and below end
    // rounded, the picture curving round them like a horseshoe, not in an S (the owner, 18.11).
    float pushAcross = 1.0 - smoothstep(0.25, 0.7, intoEnd / radius);
    // Less push outwards at the ends: over the track its colour reaches their rim, dense; past the track's end they show
    // the dark beyond (the owner's iOS screenshots, 18.11).
    float2 drawn = center + p + p * float2(0.12 * deep, 0.12 * deep * across) + n * push * float2(0.6, pushAcross);
    float fringe = 1.0 - clamp(t / 0.25, 0.0, 1.0);
    float2 chroma = n * (0.1 * radius * fringe * fringe);
    half4 color = split(coord, drawn - coord, chroma);

    // The rim mirrors what lies above or below it along the refracted picture: at the ends, where the mirror's reach
    // runs out, it meets the picture without a seam, and past the track's end it reflects nothing (the owner's iOS
    // screenshots, 18.11). Mirrored inwards along the normal it lit both ends alike; widened there it grew horns over
    // the track's end. Its reach is deep and fades evenly to the band's inner edge, so the track's colour in it thins
    // out to a line where the track curves away, as iOS's, not cut off. A thin line along the sides (0.04 r), thicker
    // from before the ends' curve (0.22 r); it looks 1.3× as far along as the picture does, so over the track's end its
    // colour has thinned away before the end's curve (the owner's second iOS video, 18.11).
    float band = radius * (0.04 + 0.18 * smoothstep(-0.3, 0.5, (abs(p.x) - (halfSize.x - radius)) / radius));
    float rim = 1.0 - clamp(depth / band, 0.0, 1.0);
    float2 shift = drawn - coord;
    half4 mirror = split(coord, float2(shift.x + p.x * 0.3, -n.y * (0.9 * radius + band) * rim), chroma * 0.15);
    color = mix(color, mirror, rim);

    color.rgb = mix(color.rgb, half3(color.a), frost * 0.15);

    // The glass a little lighter than what lies under it (+7 % towards white), and darker towards its rim: a soft
    // shade over its outer 0.6 r, down to 0.78 at the rim, measured on the owner's second iOS video (18.11).
    color.rgb += (half3(color.a) - color.rgb) * 0.07;
    color.rgb *= 1.0 - 0.28 * lift * exp(-s * s / (2.0 * inner * inner));
    // A thin light line along the top and the bottom, none at the ends.
    float lobe = abs(n.y);
    float line = (1.0 - smoothstep(0.0, edge, depth)) * (0.1 + 0.9 * lobe * lobe) * shine;
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
): Modifier = liquidGlass(
    backdrop,
    base = MaterialTheme.colorScheme.background,
    tint = color.copy(alpha = color.alpha * material.tint),
    shape = shape,
    blurBoost = material.blurBoost,
)
