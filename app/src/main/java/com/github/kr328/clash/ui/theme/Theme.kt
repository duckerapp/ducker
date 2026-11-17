package com.github.kr328.clash.ui.theme

import com.github.kr328.clash.ui.components.SheetGlass
import com.github.kr328.clash.ui.components.LocalSheetGlass
import com.github.kr328.clash.ui.components.LocalRingInk
import com.github.kr328.clash.ui.settings.SettingsStore
import com.github.kr328.clash.ui.components.GlassStyle
import com.github.kr328.clash.ui.components.LocalGlassStyle
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import kotlin.math.min

/**
 * Палитра ducker: чёрно-белая (D-65).
 *
 * Цветом ничего не обозначается — иерархию держат вес шрифта, размер и заливка.
 * Единственное, что остаётся цветным, — флаги стран и логотип провайдера: на сером
 * фоне они и работают как якоря для глаза.
 *
 * `primary` и `primaryContainer` здесь одинаковые и почти чёрные: выбранное состояние
 * в этом стиле — это сплошная заливка, а не оттенок. В тёмной теме они инвертируются
 * в белый, чтобы выбранное оставалось самым контрастным пятном.
 */
private val Ink = Color(0xFF101010)
private val Paper = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Ink,
    onPrimaryContainer = Paper,
    inversePrimary = Color(0xFFDCDCDC),
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    tertiary = Color(0xFF4A4A4A),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFE8E8E8),
    onTertiaryContainer = Ink,
    background = Color(0xFFF2F2F2),
    onBackground = Color(0xFF0A0A0A),
    surface = Paper,
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF6E6E6E),
    surfaceTint = Ink,
    outline = Color(0xFFB5B5B5),
    outlineVariant = Color(0xFFE4E4E4),
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerLowest = Paper,
    surfaceContainerHighest = Color(0xFFE6E6E6),
    // Отказ — единственное место, где цвет несёт смысл: чёрным его не отличить.
    error = Color(0xFFB3261E),
    onError = Paper,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Paper,
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Paper,
    onPrimaryContainer = Color(0xFF0A0A0A),
    inversePrimary = Color(0xFF3A3A3A),
    secondary = Paper,
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Paper,
    onSecondaryContainer = Color(0xFF0A0A0A),
    tertiary = Color(0xFFC7C7C7),
    onTertiary = Color(0xFF0A0A0A),
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color(0xFFF0F0F0),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F5),
    // Cards stand a step off the background, so what can be pressed reads at once; the greys
    // drawn on cards (pills, chips, skeletons, the check bubble) keep their step above them.
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceTint = Paper,
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFF0F0F0),
    inverseOnSurface = Color(0xFF141414),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerLow = Color(0xFF131313),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF2B2B2B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Цвета системы, но в нашей раскладке (D-139): our black and white tinted with the wallpaper's own
 * color (D-312). The system's palette was not used: on a grey-blue wallpaper the `VIBRANT` style
 * made a bright blue out of it (F-278). The greys keep their tones and take the wallpaper's hue
 * barely; everything filled and selected is one deep tone of that hue instead of our black (in the
 * dark theme, a light one instead of white). The chroma is capped and never above the wallpaper's
 * own, so a grey wallpaper gives a grey app. The error colors stay as they are.
 */
private fun wallpaperTinted(base: ColorScheme, wallpaper: Int, dark: Boolean): ColorScheme {
    val seed = FloatArray(3).also { ColorUtils.colorToM3HCT(wallpaper, it) }
    val hue = seed[0]
    val greyChroma = min(seed[1], 4f)

    fun hct(chroma: Float, tone: Float) = Color(ColorUtils.M3HCTToColor(hue, chroma, tone))
    fun grey(color: Color): Color {
        val own = FloatArray(3).also { ColorUtils.colorToM3HCT(color.toArgb(), it) }
        return hct(greyChroma, own[2])
    }

    val ink = hct(min(seed[1], 16f), if (dark) 90f else 20f)
    val onInk = grey(base.onPrimary)

    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = ink,
        onPrimaryContainer = onInk,
        inversePrimary = grey(base.inversePrimary),
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = ink,
        onSecondaryContainer = onInk,
        tertiary = grey(base.tertiary),
        onTertiary = grey(base.onTertiary),
        tertiaryContainer = grey(base.tertiaryContainer),
        onTertiaryContainer = grey(base.onTertiaryContainer),
        background = grey(base.background),
        onBackground = grey(base.onBackground),
        surface = grey(base.surface),
        onSurface = grey(base.onSurface),
        surfaceVariant = grey(base.surfaceVariant),
        onSurfaceVariant = grey(base.onSurfaceVariant),
        surfaceTint = ink,
        outline = grey(base.outline),
        outlineVariant = grey(base.outlineVariant),
        inverseSurface = grey(base.inverseSurface),
        inverseOnSurface = grey(base.inverseOnSurface),
        surfaceContainer = grey(base.surfaceContainer),
        surfaceContainerHigh = grey(base.surfaceContainerHigh),
        surfaceContainerLow = grey(base.surfaceContainerLow),
        surfaceContainerLowest = grey(base.surfaceContainerLowest),
        surfaceContainerHighest = grey(base.surfaceContainerHighest),
    )
}

/**
 * The home screen wallpaper's main color, and again whenever it changes; `null` when the wallpaper
 * gives none (a live one may not) — then the palette stays black and white. API 27, no permission.
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
private fun rememberWallpaperColor(): Int? {
    val context = LocalContext.current
    val manager = remember(context) { WallpaperManager.getInstance(context) }
    var color by remember(manager) {
        mutableStateOf(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() }.getOrNull())
    }

    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) color = colors?.primaryColor?.toArgb()
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }

    return color
}

/**
 * The failure red that reads on a fill whose letters are [content]: the light red under light letters, the dark one
 * under dark. The scheme's own `error` assumes the screen's background and vanished on a selected card's fill.
 */
@Composable
fun failureOn(content: Color): Color = when {
    !LocalGlassStyle.current.enabled -> MaterialTheme.colorScheme.error
    content.luminance() > 0.5f -> DarkColors.error
    else -> LightColors.error
}

/**
 * The glass style: whether the picture under the tabs' headers is dark, by its own top. Null — the headers follow
 * the theme (no backdrop, or one drawn in the theme's own tones).
 */
val LocalBackdropTone = staticCompositionLocalOf<Boolean?> { null }

/** Whether the app's theme is dark, which [BackdropTone] does not change. */
val LocalThemeDark = staticCompositionLocalOf { false }

/** The app's light and dark schemes, as [DuckerTheme] built them. */
private val LocalSchemes = staticCompositionLocalOf<Pair<ColorScheme, ColorScheme>?> { null }

/**
 * Content drawn straight on the glass style's picture: in the picture's tone ([LocalBackdropTone]) rather than the
 * theme's, as a phone's clock follows its wallpaper. The cards keep the theme.
 */
@Composable
fun BackdropTone(content: @Composable () -> Unit) {
    val tone = LocalBackdropTone.current
    val schemes = LocalSchemes.current
    if (tone == null || schemes == null || tone == LocalThemeDark.current) return content()

    val scheme = if (tone) schemes.second else schemes.first
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

/** Скругления, общие для карточек, кнопок и нижней панели. */
private val DuckerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DuckerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    oled: Boolean = false,
    /**
     * Цвета системы: наша раскладка в оттенке обоев ([wallpaperTinted]).
     * По умолчанию выключены (D-21).
     */
    dynamicColor: Boolean = false,
    /**
     * Переключение светлой и тёмной темы. Задано — палитра меняется накатом волны
     * от кнопки (D-66); не задано — экран просто не умеет её переключать.
     */
    onToggleDark: (() -> Unit)? = null,
    /** Телевизор: включает всё, что сделано под пульт (этап 6), см. [LocalTv]. */
    tv: Boolean = false,
    /** The liquid glass style; disabled — Air. */
    glass: GlassStyle = GlassStyle(),
    /** Whether the status bar's icons stand on something dark; null — on the theme's background. */
    statusBarDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val wallpaper = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) rememberWallpaperColor() else null
    fun schemeFor(dark: Boolean): ColorScheme {
        val base = if (dark) DarkColors else LightColors
        val palette = if (wallpaper != null) wallpaperTinted(base, wallpaper, dark) else base
        // OLED применяется и к системной палитре: это две независимые настройки.
        val scheme = if (dark && oled) {
            palette.copy(background = Color.Black, surface = Color.Black)
        } else palette
        // On glass the picture shows through behind the letters: the secondary grey goes halfway to the main colour,
        // or captions such as «Безлимит» merged with leaves and snow.
        return if (glass.enabled) scheme.copy(onSurfaceVariant = lerp(scheme.onSurfaceVariant, scheme.onSurface, 0.5f)) else scheme
    }
    val schemes = remember(wallpaper, oled, glass.enabled) { schemeFor(false) to schemeFor(true) }
    val colors = if (dark) schemes.second else schemes.first

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The bars stay transparent (themes.xml) and the app draws under them (HomeActivity): a colour set
            // here would switch at once, ahead of the theme wave (D-66). No dark scrim behind the three-button
            // bar either; the list fades out there instead (DuckerScaffold).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !(statusBarDark ?: dark)
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val sheetGlass = if (glass.enabled) remember { SheetGlass() } else null

    MaterialTheme(colorScheme = colors, shapes = DuckerShapes, typography = DuckerTypography) {
        CompositionLocalProvider(
            LocalTv provides tv,
            LocalRingInk provides colors.onBackground,
            LocalGlassStyle provides glass,
            LocalSheetGlass provides sheetGlass,
            LocalThemeDark provides dark,
            LocalSchemes provides schemes,
        ) {
            if (onToggleDark == null) content()
            else ThemeReveal(onToggle = onToggleDark, content = content)
        }
    }
}
