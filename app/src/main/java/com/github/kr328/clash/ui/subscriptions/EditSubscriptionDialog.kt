package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.SheetField
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.FitLabel
import java.util.concurrent.TimeUnit

/** Варианты автообновления. Ноль — только вручную. */
private val INTERVALS = listOf(
    0L to R.string.interval_manual,
    TimeUnit.HOURS.toMillis(4) to R.string.interval_4h,
    TimeUnit.HOURS.toMillis(8) to R.string.interval_8h,
    TimeUnit.HOURS.toMillis(24) to R.string.interval_24h,
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
    /** Свой конфиг без ссылки (D-125): ни ссылки, ни автообновления у него нет. */
    local: Boolean,
    link: String,
    interval: Long,
    error: String,
    busy: Boolean,
    onName: (String) -> Unit,
    onLink: (String) -> Unit,
    onInterval: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
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
            )

            Text(
                text = error.ifEmpty { stringResource(R.string.subscription_link_edit_hint) },
                color = if (error.isNotEmpty()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!local) SubscriptionHttpWarning(link)

        if (!local) Labeled(stringResource(R.string.subscription_interval)) {
            IntervalPicker(interval = interval, enabled = !busy, onInterval = onInterval)
        }

        // Свои правила поверх правил провайдера (D-123) и сам конфиг (D-124) — своими экранами.
        SheetLink(Icons.Default.Tune, stringResource(R.string.rules_title) + if (rules > 0) " · $rules" else "", !busy, onRules)
        SheetLink(Icons.Default.Code, stringResource(R.string.config_open), !busy, onConfig)

        SheetButton(
            text = stringResource(R.string.save),
            busyText = stringResource(R.string.saving),
            busy = busy,
            // Пустое имя — не ошибка: вернётся название провайдера, а без него — домен (D-117).
            enabled = link.isNotBlank(),
            onClick = onConfirm,
        )
    }
}

/** Строка-переход на свой экран — белая плашка, как поля панели. */
@Composable
private fun SheetLink(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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
 * что подписка обновится без человека. Провайдер может прислать свой интервал
 * (`profile-update-interval`), которого среди сегментов нет, — тогда пилюли нет, а строка
 * под ней называет его.
 */
@Composable
private fun IntervalPicker(interval: Long, enabled: Boolean, onInterval: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val selected = INTERVALS.indexOfFirst { it.first == interval }

    // Интервал не из списка — пилюля гаснет.
    val position by animateFloatAsState(
        targetValue = selected.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "intervalPosition",
    )
    val shown by animateFloatAsState(if (selected >= 0) 1f else 0f, label = "intervalShown")
    val pill = scheme.primaryContainer
    // Пульт (этап 6) входит в переключатель на выбранный сегмент, а не на ближайший к центру.
    val segments = remember { List(INTERVALS.size) { FocusRequester() } }
    val tv = LocalTv.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(52.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(CircleShape)
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, CircleShape)
                .padding(4.dp)
                // Пилюля рисуется, а не компонуется: едет каждый кадр, и перекладывать
                // ради этого сегменты незачем.
                .drawBehind {
                    val width = size.width / INTERVALS.size
                    drawRoundRect(
                        color = pill,
                        topLeft = Offset(position * width, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                        alpha = shown,
                    )
                }
                .selectableGroup()
                .then(
                    if (tv) {
                        Modifier.focusProperties { onEnter = { if (selected >= 0) segments[selected].requestFocus() } }
                            .focusGroup()
                    } else Modifier,
                ),
        ) {
            INTERVALS.forEachIndexed { index, (value, label) ->
                val on = index == selected

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .focusRequester(segments[index])
                        .clip(CircleShape)
                        .selectable(
                            selected = on,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onInterval(value)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Белое с «разницей»: над фоном выходит чёрное, над пилюлей — белое, и
                    // так в каждом кадре, пока она едет. Перекраска по выбору гасила
                    // надпись на полпути — чёрное на чёрном. В тёмной теме так же, наоборот.
                    FitLabel(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.graphicsLayer { blendMode = BlendMode.Difference },
                        color = Color.White,
                    )
                }
            }
        }

        // Ноль часов — «вручную»; интервал короче часа округляется до часа.
        val hours = if (interval == 0L) 0 else TimeUnit.MILLISECONDS.toHours(interval).toInt().coerceAtLeast(1)

        Crossfade(hours, label = "intervalHint") { hours ->
            Text(
                text = if (hours == 0) stringResource(R.string.interval_manual_hint)
                else pluralStringResource(R.plurals.interval_auto_hint, hours, hours),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
