package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.formatCount
import com.github.kr328.clash.ui.components.Symbols
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.SheetField
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.airShadow
import com.github.kr328.clash.ui.components.ringAnchor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import com.github.kr328.clash.ui.components.PillSegments
import com.github.kr328.clash.ui.components.formatDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** The stops of the own hours' slider (D-286). */
private val STOPS = listOf(2L, 3L, 4L, 6L, 8L, 12L, 24L, 48L, 72L, 144L).map { TimeUnit.HOURS.toMillis(it) }

/** Where “Свой” starts when it was not chosen before (D-286). */
private val OWN_START = TimeUnit.HOURS.toMillis(8)

/**
 * How the subscription is reached: the provider's backup addresses (Q-82) and its move of the link
 * (Q-80). The host only — the path carries the person's token.
 */
data class SubscriptionReach(
    val backups: Int = 0,
    val movedFrom: String = "",
    val movedAt: Long = 0,
)

/**
 * Правка подписки (этап 8.3, замена экрана свойств CMFA).
 *
 * Здесь только то, что человек действительно меняет: название, ссылка и частота
 * обновления. Ключа расшифровки и прочих полей CMFA нет — они нужны единицам, а ошибка
 * в них ломает подписку целиком.
 *
 * Панелью снизу, как добавление (D-100): «Отмена» не нужна — панель закрывается свайпом,
 * «назад» или касанием мимо, а «Сохранить» на время перекачки показывает кольцо.
 */
@Composable
fun EditSubscriptionDialog(
    open: Boolean,
    name: String,
    /** Название от провайдера — подсказка в пустом поле имени. */
    title: String,
    /** Сколько своих правил у подписки (D-123). */
    rules: Int,
    onRules: () -> Unit,
    onConfig: () -> Unit,
    /** Opens «Адреса подписки» (Q-82). */
    onAddresses: () -> Unit,
    /** Свой конфиг без ссылки (D-125): ни ссылки, ни автообновления у него нет. */
    local: Boolean,
    link: String,
    /** The person's own hours; 0 — “Вручную”. */
    interval: Long,
    /** “Провайдер” is chosen; it counts only while [providerInterval] is above zero. */
    follow: Boolean,
    /** The provider's hours of the last download; 0 — none sent, and there is no “Провайдер”. */
    providerInterval: Long,
    error: String,
    reach: SubscriptionReach,
    busy: Boolean,
    /** The core asks a backup address of the subscription (stage 21): a line under the button says so. */
    backup: Boolean = false,
    onName: (String) -> Unit,
    onLink: (String) -> Unit,
    onInterval: (Long) -> Unit,
    onFollow: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    // «Готово» on a TV takes the frame to «Сохранить» (D-377).
    val save = remember { FocusRequester() }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.edit_subscription),
        busy = busy,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Labeled(stringResource(R.string.subscription_name)) {
            SheetField(
                value = name,
                onValue = onName,
                placeholder = title.ifEmpty { stringResource(R.string.subscription_name) },
                enabled = !busy,
                next = save,
            )
        }

        if (!local) Labeled(stringResource(R.string.subscription_link)) {
            SheetField(
                value = link,
                onValue = onLink,
                placeholder = stringResource(R.string.subscription_link_placeholder),
                enabled = !busy,
                error = error.isNotEmpty(),
                maxLines = 4,
                next = save,
                machine = true,
            )

            if (error.isNotEmpty()) {
                FailureText(error)
            } else {
                Text(
                    text = stringResource(R.string.subscription_link_edit_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            ReachLines(reach)
        }

        if (!local) SubscriptionHttpWarning(link)

        if (!local) Labeled(stringResource(R.string.subscription_interval)) {
            IntervalPicker(
                interval = interval,
                follow = follow,
                providerInterval = providerInterval,
                enabled = !busy,
                onInterval = onInterval,
                onFollow = onFollow,
            )
        }

        // Only where the provider gave mirrors (Q-82).
        if (!local && reach.backups > 0) {
            SheetLink(Symbols.AltRoute, stringResource(R.string.subscription_backups_row) + stringResource(R.string.separator) + formatCount(reach.backups), !busy, onAddresses)
        }

        // Свои правила поверх правил провайдера (D-123) и сам конфиг (D-124) — своими экранами.
        SheetLink(Symbols.Tune, stringResource(R.string.rules_title) + if (rules > 0) stringResource(R.string.separator) + formatCount(rules) else "", !busy, onRules)
        SheetLink(Symbols.Code, stringResource(R.string.config_open), !busy, onConfig)

        // The line's gap is its own padding, not the sheet's spacing: it unfolds with the line.
        Column {
            SheetButton(
                text = stringResource(R.string.save),
                busyText = stringResource(R.string.saving),
                busy = busy,
                // Пустое имя — не ошибка: вернётся название провайдера, а без него — домен (D-117).
                enabled = link.isNotBlank(),
                onClick = onConfirm,
                modifier = Modifier.focusRequester(save),
            )

            Unfold(visible = busy && backup) {
                Text(
                    text = stringResource(R.string.subscription_trying_backup),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The provider's move of the link, for a week (Q-80): the field above already shows the new one. */
@Composable
private fun ReachLines(reach: SubscriptionReach) {
    if (reach.movedFrom.isEmpty()) return

    Text(
        text = stringResource(R.string.subscription_moved, reach.movedFrom, formatDate(reach.movedAt)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Строка-переход на свой экран — белая плашка, как поля панели. */
@Composable
private fun SheetLink(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .airShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Symbols.KeyboardArrowRight, contentDescription = null)
    }
}

/** Подпись над полем: поля заполнены, и подсказка внутри них не видна. */
@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        content()
    }
}

/**
 * Частота обновления — сегментами в одну строку, как переключатель, а не чипами (D-101):
 * чипы разной ширины переносились на вторую строку и стояли вразнобой.
 *
 * Выбор — чёрная пилюля, которая переезжает к нажатому сегменту, как подсветка на панели
 * вкладок. Под переключателем — что выбор значит словами: «2 ч» сам по себе не говорит,
 * что подписка обновится без человека.
 *
 * “Вручную · Свой · Провайдер” (D-286): “Провайдер · N ч” only when the provider sent its hours, and
 * under a chosen “Свой · N ч” a slider over [STOPS].
 */
@Composable
private fun IntervalPicker(
    interval: Long,
    follow: Boolean,
    providerInterval: Long,
    enabled: Boolean,
    onInterval: (Long) -> Unit,
    onFollow: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sent = providerInterval > 0
    val count = if (sent) 3 else 2
    val selected = when {
        follow && sent -> 2
        interval == 0L -> 0
        else -> 1
    }

    // While it closes after “Вручную”, the slider keeps the last own hours instead of running to the
    // first stop: not state, as the picker recomposes with every change of [interval] anyway.
    val lastOwn = remember { LongArray(1) { OWN_START } }
    if (selected == 1) lastOwn[0] = interval

    val own = stringResource(R.string.interval_own) + stringResource(R.string.separator)
    // “Свой” always names its hours — its own, or the last ones after “Вручную” — and its place is sized
    // for the longest, so the segments do not shift while the slider moves.
    val labels = listOf(
        stringResource(R.string.interval_manual),
        own + hoursLabel(if (interval > 0) interval else lastOwn[0]),
        stringResource(R.string.interval_provider) + stringResource(R.string.separator) + hoursLabel(providerInterval),
    )
    val widest = listOf(labels[0], own + hoursLabel(STOPS.last()), labels[2])

    // On a TV the slider unfolds only while the hours are picked: OK on «Свой» opens it with the frame on it, OK or Back
    // folds it with the hours in «Свой», Up and Down leave it. The slider takes all four arrows, and the remote could
    // leave it only with Back (F-362, D-377).
    val tv = LocalTv.current
    var choosing by remember { mutableStateOf(false) }
    val pills = remember { FocusRequester() }
    val slider = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun fold() {
        choosing = false
        pills.requestFocus()
    }

    BackHandler(enabled = tv && choosing) { fold() }
    LaunchedEffect(choosing) { if (choosing) slider.requestFocus() }

    // The gaps are paddings, not the column's spacing: the slider's gap comes and goes with it,
    // instead of jumping when the slider leaves the layout.
    Column {
        PillSegments(
            labels = labels.take(count),
            widest = widest.take(count),
            selected = selected,
            enabled = enabled,
            onSelect = { index ->
                choosing = tv && index == 1
                when (index) {
                    0 -> onInterval(0)
                    1 -> onInterval(if (interval > 0) interval else lastOwn[0])
                    else -> onFollow()
                }
            },
            modifier = Modifier.focusRequester(pills),
        )

        Unfold(visible = selected == 1 && (!tv || choosing)) {
            OwnSlider(
                interval = if (selected == 1) interval else lastOwn[0],
                enabled = enabled,
                onInterval = onInterval,
                modifier = Modifier.padding(top = 8.dp).then(
                    if (tv) Modifier.focusRequester(slider).onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        when (event.key) {
                            Key.DirectionUp, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> fold()
                            Key.DirectionDown -> {
                                if (!focusManager.moveFocus(FocusDirection.Down)) pills.requestFocus()
                                choosing = false
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    } else Modifier,
                ),
            )
        }

        val hours = when (selected) {
            0 -> 0
            1 -> wholeHours(interval)
            else -> wholeHours(providerInterval)
        }

        Crossfade(hours, label = "intervalHint") { hours ->
            Text(
                text = if (hours == 0) stringResource(R.string.interval_manual_hint)
                else pluralStringResource(R.plurals.interval_auto_hint, hours, hours),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Opens and closes [content] on the spring of the sheet's height (`DuckerSheet`) and the same way: an
 * [Animatable] that an effect starts, so after a tap that turns them around halfway both start again
 * on the same frame. `AnimatedVisibility` restarted a frame earlier than the sheet, and on quick taps
 * the rows below jumped (D-313). In lookahead the block is at its final height at once, which the sheet
 * takes as its target. The content keeps its place on screen and is uncovered or covered from above,
 * the way the sheet's top moves.
 */
@Composable
private fun Unfold(visible: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val open by remember { derivedStateOf { progress.value > 0f } }
    val shadowOutsets = remember { LayerOutsets(left = 16.dp, top = 0.dp, right = 16.dp, bottom = 16.dp) }

    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f),
        )
    }

    if (!visible && !open) return

    Box(
        modifier = Modifier.fillMaxWidth()
            // Keep the content reveal, but let the outer shadow overhang its bounds.
            .drawWithContent {
                val overhang = 16.dp.toPx()
                clipRect(left = -overhang, top = 0f, right = size.width + overhang, bottom = size.height + overhang) {
                    this@drawWithContent.drawContent()
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = when {
                    isLookingAhead -> if (visible) placeable.height else 0
                    else -> (placeable.height * progress.value).roundToInt()
                }
                layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
            }
            .graphicsLayer {
                alpha = progress.value
                outsets = shadowOutsets
            },
    ) {
        content()
    }
}

/**
 * The own hours, stop by stop, with a tick under the finger at each. An interval that is not a stop
 * (an old one from the provider) sits at the nearest stop and stays as it is until the slider moves.
 */
@Composable
private fun OwnSlider(interval: Long, enabled: Boolean, onInterval: (Long) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val stop = STOPS.indices.minBy { abs(STOPS[it] - interval) }
    val shown = hoursLabel(interval)

    Box(
        modifier = modifier.fillMaxWidth()
            // On a TV the frame goes round the whole pill, not the slider inside its padding.
            .then(if (LocalTv.current) Modifier.ringAnchor(corner = 20.dp) { Rect(Offset.Zero, it.toSize()) } else Modifier)
            .airShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Slider(
            value = stop.toFloat(),
            onValueChange = { value ->
                val index = value.roundToInt().coerceIn(STOPS.indices)
                if (index != stop) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    onInterval(STOPS[index])
                }
            },
            valueRange = 0f..STOPS.lastIndex.toFloat(),
            steps = STOPS.size - 2,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                activeTickColor = scheme.onPrimary,
                inactiveTrackColor = scheme.surfaceContainerHighest,
                inactiveTickColor = scheme.onSurfaceVariant,
            ),
            // The stops are uneven hours: read them as hours, not as a share of the track.
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = shown },
        )
    }
}

/** Ноль часов — «вручную»; интервал короче часа округляется до часа. */
private fun wholeHours(interval: Long): Int =
    if (interval == 0L) 0 else TimeUnit.MILLISECONDS.toHours(interval).toInt().coerceAtLeast(1)

@Composable
private fun hoursLabel(interval: Long): String = stringResource(R.string.interval_hours, wholeHours(interval))
