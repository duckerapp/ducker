package com.github.kr328.clash.ui.theme

import androidx.compose.material3.Typography
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

internal val DuckerTypography = with(Defaults) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Onest),
        displayMedium = displayMedium.copy(fontFamily = Onest),
        displaySmall = displaySmall.copy(fontFamily = Onest),
        headlineLarge = headlineLarge.copy(fontFamily = Onest),
        headlineMedium = headlineMedium.copy(fontFamily = Onest),
        headlineSmall = headlineSmall.copy(fontFamily = Onest),
        titleLarge = titleLarge.copy(fontFamily = Onest),
        titleMedium = titleMedium.copy(fontFamily = Onest),
        titleSmall = titleSmall.copy(fontFamily = Onest),
        bodyLarge = bodyLarge.copy(fontFamily = Onest),
        bodyMedium = bodyMedium.copy(fontFamily = Onest),
        bodySmall = bodySmall.copy(fontFamily = Onest),
        labelLarge = labelLarge.copy(fontFamily = Onest),
        labelMedium = labelMedium.copy(fontFamily = Onest),
        labelSmall = labelSmall.copy(fontFamily = Onest, fontWeight = FontWeight.Normal),
    )
}
