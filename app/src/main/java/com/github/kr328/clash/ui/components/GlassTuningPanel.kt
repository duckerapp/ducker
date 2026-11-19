package com.github.kr328.clash.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlinx.coroutines.delay

internal fun GlassStyle.dockDefaults() = copy(
    blur = DockGlass.BLUR,
    tint = DockGlass.TINT,
    refraction = DockGlass.REFRACTION,
    dispersion = DockGlass.DISPERSION,
    depth = DockGlass.DEPTH,
    highlight = DockGlass.HIGHLIGHT,
    noise = DockGlass.NOISE,
    vibrancy = DockGlass.VIBRANCY,
)

private fun GlassStyle.numbers() = mapOf(
    "blur" to blur.toFloat(), "tint" to tint.toFloat(), "vibrancy" to vibrancy.toFloat(),
    "refraction" to refraction.toFloat(), "dispersion" to dispersion.toFloat(), "depth" to depth.toFloat(),
    "highlight" to highlight.toFloat(), "noise" to noise.toFloat(),
)

private fun GlassTuningGroup.defaults(style: GlassStyle, light: Boolean) = when (this) {
    GlassTuningGroup.Dock -> style.dockDefaults().numbers() + mapOf(
        "breath" to DockGlass.BREATH,
        "tintEase" to if (light) DockGlass.TINT_EASE_LIGHT else DockGlass.TINT_EASE,
        "tintNear" to if (light) DockGlass.TINT_NEAR_LIGHT else DockGlass.TINT_NEAR,
        "tintReach" to DockGlass.TINT_REACH,
    )
    GlassTuningGroup.Veil -> mapOf(
        "opacity" to DockGlass.VEIL_OPACITY,
        "shade" to if (light) DockGlass.VEIL_SHADE_LIGHT else DockGlass.VEIL_SHADE_DARK,
        "blur" to DockGlass.VEIL_BLUR.value,
    )
    GlassTuningGroup.DockLens -> lensDefaults(if (light) DockGlass.LookLight else DockGlass.Look) + mapOf(
        "height" to DockGlass.LENS_HEIGHT, "aspect" to DockGlass.LENS_ASPECT,
        "swellWide" to DockGlass.SWELL_WIDE, "swellFlat" to DockGlass.SWELL_FLAT, "swellZoom" to DockGlass.SWELL_ZOOM,
    )
    GlassTuningGroup.Pane -> style.asDock().numbers() + mapOf(
        "highlight" to DockGlass.HIGHLIGHT.toFloat(),
        "band" to PaneGlass.BAND.value, "push" to PaneGlass.PUSH.value, "chroma" to PaneGlass.CHROMA.value,
        "saturation" to PaneGlass.SATURATION, "vivid" to PaneGlass.VIVID, "roll" to PaneGlass.ROLL,
        "line" to PaneGlass.LINE, "lineWidth" to PaneGlass.LINE_WIDTH.value, "volume" to PaneGlass.VOLUME,
        "spare" to PaneGlass.SPARE, "ring" to PaneGlass.RING, "ringWidth" to PaneGlass.RING_WIDTH.value,
        "denser" to PaneGlass.DENSER, "pressClear" to PaneGlass.PRESS_CLEAR, "pressGrow" to PaneGlass.PRESS_GROW.value,
        "growMin" to PaneGlass.GROW_MIN, "growMax" to PaneGlass.GROW_MAX,
    )
    GlassTuningGroup.Switch -> lensDefaults(LensLook.Switch) + mapOf("swell" to SWITCH_LENS_SWELL)
    GlassTuningGroup.Slider -> mapOf(
        "swell" to LENS_SWELL, "zoom" to LENS_ZOOM, "bezel" to BUBBLE_BEZEL.value,
        "strength" to 0.28f, "spread" to 0.12f, "rim" to 1f, "rimWidth" to 1.2f,
    )
    GlassTuningGroup.Segments -> lensDefaults(if (light) SEGMENT_LOOK_LIGHT else SEGMENT_LOOK) + mapOf(
        "rise" to LENS_RISE.value, "widen" to LENS_WIDEN,
    )
    GlassTuningGroup.Toolbar -> lensDefaults(if (light) SEGMENT_LOOK_LIGHT else SEGMENT_LOOK) + mapOf(
        "height" to DockGlass.LENS_HEIGHT, "aspect" to DockGlass.LENS_ASPECT, "widen" to LENS_WIDEN,
    )
}

/** Kept outside the backdrop capture so this panel never appears reflected in the tested glass. */
@Composable
internal fun GlassTuningPanel(tuning: GlassTuning, changeStyle: (() -> Unit)? = null) {
    val style = LocalGlassStyle.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    BackHandler(enabled = tuning.expanded) { tuning.expanded = false }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val bottomGap = if (maxHeight < 400.dp) 16.dp else 100.dp
        val edgeGap = if (tuning.lower) bottomGap else 56.dp
        val listHeight = (maxHeight - edgeGap - 150.dp).coerceIn(48.dp, (maxHeight * 0.34f).coerceAtLeast(48.dp))
        val alignment = if (tuning.lower) Alignment.BottomEnd else Alignment.TopEnd
        val edge = Modifier.align(alignment).padding(
            top = if (tuning.lower) 0.dp else 56.dp,
            bottom = if (tuning.lower) bottomGap else 0.dp,
            end = 12.dp,
            start = 12.dp,
        )
        if (!tuning.expanded) {
            FilledTonalButton(onClick = { tuning.expanded = true }, modifier = edge) { Text("Настроить стекло") }
        } else {
            val group = tuning.group
            val defaults = remember(group, style, light) { group.defaults(style, light) }
            Surface(
                modifier = edge.widthIn(max = 344.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Стекло", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { tuning.lower = !tuning.lower }) {
                            Text(if (tuning.lower) "Наверх" else "Вниз")
                        }
                        TextButton(onClick = { tuning.expanded = false }) { Text("Скрыть") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (changeStyle != null) {
                            TextButton(onClick = changeStyle) { Text(if (style.enabled) "Стиль Glass" else "Стиль Air") }
                        }
                        Text("Правки", modifier = Modifier.weight(1f))
                        Switch(
                            checked = tuning.enabled,
                            onCheckedChange = tuning::enable,
                            modifier = Modifier.semantics { contentDescription = "Применять правки" },
                        )
                        TextButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ducker Glass", tuning.export()))
                                copied = true
                            }
                        }) {
                            Text(if (copied) "Скопировано" else "Копировать")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            var menu by remember { mutableStateOf(false) }
                            TextButton(onClick = { menu = true }) { Text(group.title) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                GlassTuningGroup.entries.forEach { choice ->
                                    DropdownMenuItem(text = { Text(choice.title) }, onClick = {
                                        tuning.group = choice
                                        menu = false
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { tuning.reset(group, light) }) { Text("Сбросить") }
                    }
                    androidx.compose.runtime.key(group) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
                        ) {
                            if (!style.enabled) {
                                item { Text("Нажми «Стиль Air», чтобы включить стекло.", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (group == GlassTuningGroup.Pane) {
                                item {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Собственный блик карточек", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = tuning.paneOwnRim, onCheckedChange = tuning::ownRim)
                                    }
                                }
                            }
                            val applicableKnobs = glassKnobs.getValue(group).filter { knob ->
                                when (knob.key) {
                                    "frostBlur" -> group == GlassTuningGroup.Switch
                                    "shineDark", "irisDark", "shadowDark" -> !light
                                    "shineLight", "irisLight", "shadowLight" -> light
                                    else -> true
                                }
                            }
                            items(applicableKnobs, key = { it.key }) { knob ->
                                val themed = group.usesLightValues(knob.key, light)
                                val fallback = if (group == GlassTuningGroup.Pane && knob.key == "highlight" && tuning.paneOwnRim) {
                                    style.highlight.toFloat()
                                } else defaults.getValue(knob.key)
                                GlassTuningRow(knob, tuning.saved(group, knob.key, themed) ?: fallback) { value ->
                                    tuning.set(group, knob, themed, value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTuningRow(knob: GlassKnob, value: Float, onChange: (Float) -> Unit) {
    val format = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(knob.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(format.format(value), style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = value,
        valueRange = knob.min..knob.max,
        onValueChange = onChange,
        modifier = Modifier.semantics { contentDescription = knob.label },
    )
}
