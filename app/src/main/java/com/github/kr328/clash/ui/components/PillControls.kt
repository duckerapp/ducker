package com.github.kr328.clash.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.floor

/**
 * One choice of a few, first made for «Вручную · Свой · Провайдер» (D-286): a white track and a black pill that
 * rides to the chosen segment. [widest] sizes the segments, so a label that changes while one looks keeps its
 * place; by default the labels themselves.
 */
@Composable
fun PillSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    widest: List<String> = labels,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pillPosition",
    )
    val pill = scheme.primaryContainer
    // Пульт (этап 6) входит в переключатель на выбранный сегмент, а не на ближайший к центру.
    val segments = remember(labels.size) { List(labels.size) { FocusRequester() } }
    val tv = LocalTv.current
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)

    // Left and width of every segment, from the last layout; the pill rides between them.
    var spans by remember { mutableStateOf(FloatArray(0)) }

    Layout(
        modifier = modifier.fillMaxWidth()
            .height(52.dp)
            // Dimmed together with its shadow, which would show through the faded surface.
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
                outsets = DuckerShadowOutsets
            }
            .airShadow(CircleShape)
            .clip(CircleShape)
            .background(scheme.surface)
            .padding(4.dp)
            // Пилюля рисуется, а не компонуется: едет каждый кадр, и перекладывать
            // ради этого сегменты незачем.
            .drawBehind {
                val (left, width) = pillSpan(spans, position)
                drawRoundRect(
                    color = pill,
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .selectableGroup()
            .then(
                if (tv) {
                    Modifier.focusProperties { onEnter = { segments[selected].requestFocus() } }
                        .focusGroup()
                } else Modifier,
            ),
        content = {
            labels.forEachIndexed { index, text ->
                val on = index == selected

                Box(
                    modifier = Modifier
                        .focusRequester(segments[index])
                        .clip(CircleShape)
                        .selectable(
                            selected = on,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Unseen, it only gives the segment its width: the longest label with even room
                    // at both ends.
                    Text(
                        text = widest.getOrElse(index) { text },
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 14.dp).alpha(0f).clearAndSetSemantics {},
                    )

                    // The label twice: in the backing's ink, and in the pill's over it, drawn only where
                    // the pill is — readable in every frame while the pill rides under it. Recoloring
                    // by choice blacked the label out halfway; “difference” of white, used before, gave
                    // the pill's complement, pink on a green-tinted pill (D-312).
                    val label: @Composable (Color) -> Unit = { color ->
                        FitLabel(
                            text = text,
                            style = style,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            color = color,
                        )
                    }

                    label(scheme.onSurface)

                    Box(
                        modifier = Modifier.matchParentSize()
                            .clearAndSetSemantics {}
                            .drawWithContent {
                                val (left, width) = pillSpan(spans, position)
                                val from = left - spans.getOrElse(index * 2) { 0f }
                                clipRect(left = from, right = from + width) { this@drawWithContent.drawContent() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        label(scheme.onPrimaryContainer)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Every segment is as wide as its label, and what is left over is shared equally: the same
        // room around every label. Too narrow a screen shares the width by the labels' widths, and
        // the labels shrink.
        val total = constraints.maxWidth
        val wanted = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val free = total - wanted.sum()
        val widths = wanted.map { if (free >= 0) it + free / wanted.size else it * total / wanted.sum() }
            .toMutableList()
        widths[widths.lastIndex] += total - widths.sum()

        val placeables = measurables.mapIndexed { i, it -> it.measure(Constraints.fixed(widths[i], constraints.maxHeight)) }
        val next = FloatArray(widths.size * 2)
        // The first segment at the layout's start: on the right in a right-to-left language.
        val rtl = layoutDirection == LayoutDirection.Rtl
        var x = 0
        widths.forEachIndexed { i, width ->
            next[i * 2] = (if (rtl) total - x - width else x).toFloat()
            next[i * 2 + 1] = width.toFloat()
            x += width
        }
        if (!next.contentEquals(spans)) spans = next

        layout(total, constraints.maxHeight) {
            placeables.forEachIndexed { i, it -> it.place(next[i * 2].toInt(), 0) }
        }
    }
}

/** The pill between the segments at [position]: left and width, from [spans] of [PillSegments]. */
private fun pillSpan(spans: FloatArray, position: Float): Pair<Float, Float> {
    val count = spans.size / 2
    if (count < 2) return (spans.getOrElse(0) { 0f }) to (spans.getOrElse(1) { 0f })

    val from = floor(position).toInt().coerceIn(0, count - 2)
    val part = position - from
    return lerp(spans[from * 2], spans[from * 2 + 2], part) to lerp(spans[from * 2 + 1], spans[from * 2 + 3], part)
}

/**
 * A small pill in place of Material's chips and buttons: white with the cards' shadow on the screen's grey,
 * grey without a shadow on a white card ([onCard]), black while [selected]. [role] says what it is: a choice
 * among others ([Role.RadioButton]), an on/off ([Role.Switch]) or an action ([Role.Button]). [fit] — the label
 * shrinks instead of being cut, for pills that share a row by their labels (D-114).
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onCard: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    role: Role = Role.Button,
    fit: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val rest = if (onCard) scheme.surfaceVariant else scheme.surface
    // The glass style: on the tabs every pill is the shared frosted button, on a card or off it.
    val backdrop = LocalGlassBackdrop.current
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(PILL_FADE_MS), label = "pillSelection")
    val style = MaterialTheme.typography.labelLarge
    val action = when (role) {
        Role.Button -> Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
        Role.Switch, Role.Checkbox -> Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
        else -> Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
    }

    Box(
        modifier = modifier.minimumInteractiveComponentSize()
            // Before the shadow, which would otherwise show through the faded pill.
            .graphicsLayer {
                alpha = if (enabled) 1f else DISABLED_ALPHA
                outsets = DuckerShadowOutsets
            }
            .then(
                when {
                    backdrop != null -> Modifier.glassButton(backdrop)
                    onCard -> Modifier
                    else -> Modifier.airShadow(CircleShape, rest)
                },
            )
            .clip(CircleShape)
            .then(action),
        propagateMinConstraints = true,
    ) {
        val content: @Composable (Color, Modifier) -> Unit = { ink, layer ->
            Row(
                modifier = layer.heightIn(min = 36.dp)
                    .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))

                if (fit) FitLabel(text, style, color = ink)
                else Text(text, color = ink, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        content(
            scheme.onSurface,
            Modifier.drawWithContent {
                val edge = size.width * selection
                val left = if (layoutDirection == LayoutDirection.Rtl) 0f else edge
                val right = if (layoutDirection == LayoutDirection.Rtl) size.width - edge else size.width
                clipRect(left = left, right = right) { this@drawWithContent.drawContent() }
            }.background(if (backdrop != null) Color.Transparent else rest),
        )
        // Reveal fill and its ink together, as in PillSegments: opposing color fades cross
        // at zero contrast. The second label is visual only and never sizes the pill.
        content(
            scheme.onPrimaryContainer,
            Modifier.matchParentSize().clearAndSetSemantics {}
                .drawWithContent {
                    val edge = size.width * selection
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - edge else 0f
                    clipRect(left = left, right = left + edge) { this@drawWithContent.drawContent() }
                }
                .background(scheme.primaryContainer),
        )
    }
}

private const val PILL_FADE_MS = 150
private const val DISABLED_ALPHA = 0.4f
