package com.github.kr328.clash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.github.kr328.clash.R

/** One bundled variable font supplies the weights used by the existing Material text roles. */
@OptIn(ExperimentalTextApi::class)
private val Onest = FontFamily(
    listOf(400, 450, 500, 600, 700, 900).map { weight ->
        Font(
            resId = R.font.onest,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

/** Preserve the type scale; small labels carry quiet metadata rather than button emphasis. */
private val Defaults = Typography()

/**
 * Onest's own figures are proportional — «1» is half as wide as «0» — so speed, delays and counters
 * that change on screen would shift sideways; tabular figures keep them in place.
 */
private fun TextStyle.onest(weight: FontWeight? = fontWeight) =
    copy(fontFamily = Onest, fontWeight = weight, fontFeatureSettings = "tnum")

internal val DuckerTypography = with(Defaults) {
    Typography(
        displayLarge = displayLarge.onest(),
        displayMedium = displayMedium.onest(),
        displaySmall = displaySmall.onest(),
        headlineLarge = headlineLarge.onest(),
        headlineMedium = headlineMedium.onest(),
        headlineSmall = headlineSmall.onest(),
        titleLarge = titleLarge.onest(),
        titleMedium = titleMedium.onest(),
        titleSmall = titleSmall.onest(),
        bodyLarge = bodyLarge.onest(),
        bodyMedium = bodyMedium.onest(),
        bodySmall = bodySmall.onest(),
        labelLarge = labelLarge.onest(),
        labelMedium = labelMedium.onest(),
        labelSmall = labelSmall.onest(FontWeight.Normal),
    )
}
