package com.github.kr328.clash.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
        "line" to DockGlass.LINE, "lineWidth" to DockGlass.LINE_WIDTH.value, "lineSpread" to DockGlass.LINE_SPREAD.value,
        "lineGlow" to DockGlass.LINE_GLOW, "lineFall" to DockGlass.LINE_FALL, "lineDirect" to DockGlass.LINE_DIRECT,
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
    GlassTuningGroup.Pane, GlassTuningGroup.DockPane -> style.asDock().numbers() + mapOf(
        "lineSpread" to PaneGlass.LINE_SPREAD.value, "lineGlow" to PaneGlass.LINE_GLOW, "lineFall" to PaneGlass.LINE_FALL,
        "lineDirect" to PaneGlass.LINE_DIRECT,
        "highlight" to DockGlass.HIGHLIGHT.toFloat(),
        "band" to PaneGlass.BAND.value, "push" to PaneGlass.PUSH.value, "chroma" to PaneGlass.CHROMA.value,
        "saturation" to PaneGlass.SATURATION, "vivid" to PaneGlass.VIVID, "roll" to PaneGlass.ROLL,
        "line" to PaneGlass.LINE, "lineWidth" to PaneGlass.LINE_WIDTH.value, "volume" to PaneGlass.VOLUME,
        "spare" to PaneGlass.SPARE, "ring" to PaneGlass.RING, "ringWidth" to PaneGlass.RING_WIDTH.value,
        "denser" to PaneGlass.DENSER, "pressClear" to PaneGlass.PRESS_CLEAR, "pressGrow" to PaneGlass.PRESS_GROW.value,
        "growMin" to PaneGlass.GROW_MIN, "growMax" to PaneGlass.GROW_MAX,
    )
    GlassTuningGroup.Chosen -> mapOf("opacity" to chosenDensity(!light), "white" to chosenWhite(!light))
    GlassTuningGroup.Switch -> lensDefaults(if (light) LensLook.SwitchLight else LensLook.Switch) + mapOf("swell" to SWITCH_LENS_SWELL)
    GlassTuningGroup.Slider -> mapOf(
        "swell" to LENS_SWELL, "zoom" to LENS_ZOOM, "bezel" to BUBBLE_BEZEL.value,
        "strength" to 0.28f, "spread" to 0.12f, "rim" to 1f, "rimWidth" to 1.2f,
    )
    GlassTuningGroup.Segments -> lensDefaults(if (light) SEGMENT_LOOK_LIGHT else SEGMENT_LOOK) + mapOf(
        "rise" to LENS_RISE.value, "widen" to LENS_WIDEN,
    )
    GlassTuningGroup.Toolbar -> lensDefaults(if (light) TOOLBAR_LOOK_LIGHT else TOOLBAR_LOOK) + mapOf(
        "height" to TOOLBAR_LENS_HEIGHT, "aspect" to TOOLBAR_LENS_ASPECT, "widen" to TOOLBAR_WIDEN,
    )
    GlassTuningGroup.Wall -> mapOf(
        "blur" to wallBlur(!light), "tint" to wallTint(!light), "tone" to if (light) 1f else 0f, "saturation" to 1f,
    )
}

/**
 * The floating tuning panel (D-548), compact so the glass under it stays in view and quick to tap (the owner, 22.11: the
 * first one was bulky): a round button when hidden; open, a header of icons, the groups as a row of chips, and per knob
 * one line of label and value over a slider with − and + steps. A changed knob's value is accented; tapping it resets the
 * knob. Knobs that change nothing with the others as they are stay hidden. Kept outside the backdrop capture so it never
 * appears reflected in the tested glass.
 */
@Composable
internal fun GlassTuningPanel(tuning: GlassTuning, changeStyle: (() -> Unit)? = null) {
    val style = LocalGlassStyle.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    val light = scheme.background.luminance() >= 0.5f
    BackHandler(enabled = tuning.expanded) { tuning.expanded = false }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val bottomGap = if (maxHeight < 400.dp) 12.dp else 96.dp
        val alignment = if (tuning.lower) Alignment.BottomEnd else Alignment.TopEnd
        val edge = Modifier.align(alignment).padding(
            top = if (tuning.lower) 0.dp else 52.dp,
            bottom = if (tuning.lower) bottomGap else 0.dp,
            end = 12.dp,
            start = 12.dp,
        )
        if (!tuning.expanded) {
            Surface(
                onClick = { tuning.expanded = true },
                modifier = edge.size(44.dp).semantics { contentDescription = "Настроить стекло" },
                shape = CircleShape,
                color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Symbols.Tune, contentDescription = null, modifier = Modifier.size(22.dp)) }
            }
            return@BoxWithConstraints
        }

        val group = tuning.group
        val defaults = remember(group, style, light) { group.defaults(style, light) }
        val listHeight = (maxHeight * 0.36f).coerceIn(120.dp, 320.dp)
        Surface(
            modifier = edge.widthIn(max = 380.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                // The header: what the panel is, then its actions as icons.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Стекло", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    PanelIcon(if (tuning.lower) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown, if (tuning.lower) "Панель наверх" else "Панель вниз") {
                        tuning.lower = !tuning.lower
                    }
                    PanelIcon(if (copied) Symbols.Check else Symbols.ContentCopy, "Скопировать настройки") {
                        context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                            clipboard.setPrimaryClip(ClipData.newPlainText("Ducker Glass", tuning.export()))
                            copied = true
                        }
                    }
                    PanelIcon(Symbols.Close, "Скрыть панель") { tuning.expanded = false }
                }
                // The groups, one tap each.
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(GlassTuningGroup.entries) { choice ->
                        val on = choice == group
                        Surface(
                            onClick = { tuning.group = choice },
                            shape = CircleShape,
                            color = if (on) scheme.primary else scheme.surfaceVariant,
                            contentColor = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                        ) {
                            Text(
                                choice.title,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.heightIn(min = 36.dp).wrapContentHeight().padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
                // The group's actions and the whole panel's switches.
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Правки", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = tuning.enabled,
                        onCheckedChange = tuning::enable,
                        modifier = Modifier.scale(0.8f).semantics { contentDescription = "Применять правки" },
                    )
                    if (changeStyle != null) {
                        SmallButton(if (style.enabled) "Включить Air" else "Включить стекло", changeStyle)
                    }
                    if (group.lens) {
                        val soft = tuning.softOn(group, light)
                        SmallButton(if (soft) "Убрать мягкие края" else "Мягкие края") { tuning.soft(group, light, !soft) }
                    }
                    SmallButton("Сброс группы") { tuning.reset(group, light) }
                }
                if (!tuning.enabled) {
                    Text("Правки выключены: видно стекло по умолчанию.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (!style.enabled) {
                    Text("Сейчас Air: включи стекло, чтобы увидеть правки.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                androidx.compose.runtime.key(group) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = listHeight).padding(end = 8.dp)) {
                        if (group == GlassTuningGroup.DockPane) {
                            item { PanelToggle("Корпус дока как карточки", tuning.dockPane, tuning::useDockPane) }
                            if (!tuning.dockPane) item {
                                Text(
                                    "Выключено: у дока своё стекло (группа «Док»). Включи, чтобы его корпус стал стеклом карточек с этими ручками.",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (group == GlassTuningGroup.Wall) {
                            item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Фон", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    for ((mode, label) in listOf(WallMode.PICTURE to "Обои", WallMode.PLAIN to "Без фона", WallMode.IOS to "Как в iOS")) {
                                        SmallButton(if (tuning.wallMode == mode) "● $label" else label) { tuning.useWallMode(mode) }
                                    }
                                }
                            }
                            if (tuning.wallMode != WallMode.PICTURE) item {
                                Text(
                                    if (tuning.wallMode == WallMode.IOS) "Как в iOS: чёрный #000000 в тёмной теме, #F2F2F7 в светлой." else "Без фона: цвет темы приложения.",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (group == GlassTuningGroup.Dock) {
                            item { PanelToggle("Собственный блик дока 1 (как у карточек)", tuning.ownRimOf(group), tuning::dock1Rim) }
                        }
                        if (group == GlassTuningGroup.Pane || group == GlassTuningGroup.DockPane) {
                            item {
                                PanelToggle(
                                    if (group == GlassTuningGroup.Pane) "Собственный блик карточек" else "Собственный блик дока",
                                    tuning.ownRimOf(group),
                                    if (group == GlassTuningGroup.Pane) tuning::ownRim else tuning::dockRim,
                                )
                            }
                        }
                        fun current(key: String): Float =
                            tuning.saved(group, key, group.usesLightValues(key, light)) ?: defaults[key] ?: 0f
                        val shown = glassKnobs.getValue(group).filter { knob ->
                            when (knob.key) {
                                "frostBlur" -> group == GlassTuningGroup.Switch
                                "shineDark", "irisDark", "shadowDark" -> !light
                                "shineLight", "irisLight", "shadowLight" -> light
                                // The sharp rim's steepness counts only without a soft band (the shader's choice).
                                "pushPower" -> current("pushBand") <= 0f
                                // The panes' own light line draws only with their own highlight on; its glow only with a reach.
                                "line", "lineWidth", "lineSpread", "lineDirect" -> tuning.ownRimOf(group)
                                "lineGlow", "lineFall" -> tuning.ownRimOf(group) && current("lineSpread") > 0f
                                // The picture's blur and tint mean nothing on a plain background.
                                "blur", "tint", "tone", "saturation" -> group != GlassTuningGroup.Wall || tuning.wallMode == WallMode.PICTURE
                                else -> true
                            }
                        }
                        items(shown, key = { it.key }) { knob ->
                            val themed = group.usesLightValues(knob.key, light)
                            val saved = tuning.saved(group, knob.key, themed)
                            val fallback = if ((group == GlassTuningGroup.Pane || group == GlassTuningGroup.DockPane) && knob.key == "highlight" && tuning.ownRimOf(group)) {
                                style.highlight.toFloat()
                            } else defaults.getValue(knob.key)
                            GlassTuningRow(
                                knob = knob,
                                value = saved ?: fallback,
                                changed = saved != null,
                                onChange = { tuning.set(group, knob, themed, it) },
                                onReset = { tuning.clear(group, knob, themed) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelToggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
private fun PanelIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** One knob: its label and value on a line, the slider with − and + steps under it; a changed value resets on a tap. */
@Composable
private fun GlassTuningRow(knob: GlassKnob, value: Float, changed: Boolean, onChange: (Float) -> Unit, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val format = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 } }
    // A step of the − and + buttons: a fiftieth of the range, never finer than the knob's own step.
    val step = maxOf(knob.step, (knob.max - knob.min) / 50f)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                knob.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (changed) scheme.primary else scheme.onSurface, maxLines = 2,
            )
            Text(
                (if (changed) "↺ " else "") + format.format(value),
                style = MaterialTheme.typography.labelMedium,
                color = if (changed) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape)
                    .then(if (changed) Modifier.clickable(onClickLabel = "Вернуть по умолчанию", onClick = onReset) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange((value - step).coerceIn(knob.min, knob.max)) }
            Slider(
                value = value,
                valueRange = knob.min..knob.max,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = knob.label },
            )
            StepButton("+") { onChange((value + step).coerceIn(knob.min, knob.max)) }
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
