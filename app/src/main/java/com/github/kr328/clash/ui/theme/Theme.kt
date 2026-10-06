package com.github.kr328.clash.ui.theme

import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

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
    surface = Color(0xFF161616),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceTint = Paper,
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFF0F0F0),
    inverseOnSurface = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A),
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
 * Цвета системы, но в нашей раскладке (D-139). Как есть Material You красил выбранное ярким
 * акцентом на бледной подложке: включённая карточка становилась сиреневой, кнопка — синей.
 * Здесь фон, текст и рамки — тонированные серые системы, а всё залитое и выбранное — один
 * глубокий тон обоев вместо нашего чёрного (в тёмной теме — светлый вместо белого).
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun softDynamic(context: Context, dark: Boolean): ColorScheme {
    fun tone(id: Int) = Color(context.getColor(id))

    val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val ink = tone(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_800)
    val onInk = tone(if (dark) android.R.color.system_accent1_900 else android.R.color.system_neutral1_10)

    return system.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = ink,
        onPrimaryContainer = onInk,
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = ink,
        onSecondaryContainer = onInk,
        surfaceTint = ink,
        // Карточки светлее фона, как в нашей палитре: у Material они одного тона.
        background = if (dark) system.surfaceContainerLowest else system.surfaceContainer,
        surface = if (dark) system.surfaceContainerLow else system.surfaceContainerLowest,
    )
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
     * Цвета системы (Material You), приглушённые до нашей раскладки ([softDynamic]).
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val palette = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> softDynamic(context, dark)
        dark -> DarkColors
        else -> LightColors
    }
    // OLED применяется и к системной палитре: это две независимые настройки.
    val colors = if (dark && oled) {
        palette.copy(background = Color.Black, surface = Color.Black)
    } else palette

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = colors, shapes = DuckerShapes) {
        CompositionLocalProvider(LocalTv provides tv) {
            if (onToggleDark == null) content()
            else ThemeReveal(onToggle = onToggleDark, content = content)
        }
    }
}
