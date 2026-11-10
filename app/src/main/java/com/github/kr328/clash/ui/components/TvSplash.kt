package com.github.kr328.clash.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.R

/**
 * ducker's own splash on a TV (D-379): the duck and «ducker» side by side, as the banner (the owner's choice, D-382) —
 * the first frame, with Home built behind it, fading into Home once its data is there. The system's splash before it
 * shows only the window colour (`values-television/splash.xml`).
 *
 * The duck is the system splash's own image, drawn at its 288 dp canvas, so every density gets its bitmap pixel for
 * pixel — 1152 px at 4K; it is tinted, as its colour is flat and its shape is in the alpha (D-342).
 *
 * The duck and «ducker» fade first, the backing after them: faded together, the white mark stayed over Home after the
 * black was gone on a real TV, whose frames stall as Home starts drawing.
 */
@Composable
internal fun TvSplash(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(BACKING_FADE_MS, delayMillis = MARK_FADE_MS)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            val color = MaterialTheme.colorScheme.onBackground

            Row(
                modifier = Modifier.animateEnterExit(exit = fadeOut(tween(MARK_FADE_MS))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // The duck's own bounds; its canvas spills over them, transparent.
                Box(Modifier.size(width = 124.dp, height = 132.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.splash_duck),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(288.dp),
                        colorFilter = ColorFilter.tint(color),
                    )
                }

                Text(
                    text = "ducker",
                    color = color,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 72.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val MARK_FADE_MS = 150
private const val BACKING_FADE_MS = 250
