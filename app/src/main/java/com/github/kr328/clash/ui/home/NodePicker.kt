package com.github.kr328.clash.ui.home

import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.ringCorner
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.proxy.NodeState

/** Основная группа профиля для быстрой смены узла на главной (D-116). */
@Immutable
data class NodePicker(
    val group: String,
    val now: String,
    val nodes: List<NodeState>,
)

/** Узлы-группы: выбирают узел сами, как AUTO. Имена типов — как их отдаёт ядро. */
private val GROUP_TYPES = setOf("selector", "urltest", "fallback", "loadbalance", "relay")

/**
 * Какие узлы показать в быстрой смене, не больше [slots] (D-116): сначала группы с
 * автовыбором, потом избранные, потом остальные в порядке «Прокси» (D-147: не по
 * скорости — порядок узлов задаёт провайдер). Выбранный сейчас есть в списке
 * всегда — иначе было бы не видно, что стоит сейчас; он выделен цветом, без галочки. Остальное — на вкладке «Прокси».
 */
internal fun quickNodes(nodes: List<NodeState>, now: String, slots: Int): List<NodeState> {
    if (slots <= 0) return emptyList()

    val groups = nodes.filter { it.type.lowercase() in GROUP_TYPES }
    val favorites = nodes.filter { it.favorite && it !in groups }
    val rest = nodes.filter { it !in groups && it !in favorites }

    val picked = (groups + favorites + rest).take(slots)
    val current = nodes.firstOrNull { it.name == now }

    return if (current == null || current in picked) picked else picked.dropLast(1) + current
}

/**
 * Быстрая смена узла прямо в карточке подключения (D-116). Серая плашка узла растёт вверх
 * и немного вниз, пока не зальёт всю карточку, и в ней выезжают узлы; после выбора всё идёт
 * в обратном порядке. Узлы, которым не хватило места, — строкой «Все узлы», на «Прокси».
 *
 * [from] — где стоит плашка узла, в координатах карточки. [progress] — 0 свёрнуто, 1
 * раскрыто; читается только в отрисовке, так что анимация не пересобирает панель.
 */
@Composable
internal fun NodePickerPanel(
    /** `null` — узлы ещё грузятся: панель уже раскрыта и показывает ожидание. */
    picker: NodePicker?,
    from: Rect,
    progress: () -> Float,
    fill: Color,
    onPick: (group: String, name: String) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Пульт (этап 6): сюда встаёт фокус при раскрытии — на выбранный узел. */
    current: FocusRequester? = null,
) {
    val content = LocalContentColor.current
    // Цвет плашки узла, только сплошной: под ним не должны просвечивать надписи карточки.
    val plate = content.copy(alpha = PLATE_ALPHA).compositeOver(fill)
    val corner = MaterialTheme.shapes.medium

    // Выбранный отмечается сразу, не дожидаясь службы: панель в это время уже сворачивается.
    var chosen by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .drawBehind {
                val shape = lerp(from, Rect(Offset.Zero, size), progress())
                val radius = corner.topStart.toPx(shape.size, this)

                drawRoundRect(plate, shape.topLeft, shape.size, CornerRadius(radius))
            }
            // Касания мимо узлов не проваливаются к кнопке питания под панелью.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
    ) {
        // Сколько узлов влезает в карточку: высота карточки не меняется, при крупном шрифте
        // узлов просто меньше, а остальные — за строкой «Все узлы».
        val slots = ((maxHeight - PADDING * 2) / ROW_HEIGHT).toInt() - 1
        val shown = remember(picker, slots) {
            if (picker == null) emptyList() else quickNodes(picker.nodes, picker.now, slots)
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .padding(PADDING)
                .graphicsLayer {
                    // Узлы выезжают следом за плашкой, когда та прошла больше трети пути.
                    val shownPart = ((progress() - 0.35f) / 0.65f).coerceIn(0f, 1f)

                    alpha = shownPart
                    translationY = (1f - shownPart) * 24.dp.toPx()
                },
        ) {
            if (picker == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = content, strokeWidth = 2.dp)
                }
            } else {
                shown.forEach { node ->
                    val selected = node.name == (chosen ?: picker.now)

                    NodeLine(
                        node = node,
                        selected = selected,
                        modifier = if (selected && current != null) Modifier.focusRequester(current) else Modifier,
                        onClick = {
                            chosen = node.name
                            onPick(picker.group, node.name)
                        },
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .clip(MaterialTheme.shapes.small)
                    .then(if (LocalTv.current) Modifier.ringCorner(ROW_CORNER) else Modifier)
                    .clickable(role = Role.Button, onClick = onAll)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.all_nodes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = content.copy(alpha = 0.7f),
                )

                Icon(
                    imageVector = Symbols.KeyboardArrowRight,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun NodeLine(node: NodeState, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current

    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.background(content.copy(alpha = 0.1f)) else Modifier)
            .then(if (LocalTv.current) Modifier.ringCorner(ROW_CORNER) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = node.name.asName(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.named(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = when {
                node.delay >= DelayStore.SLOW -> stringResource(R.string.delay_slow, node.delay / 1000)
                node.delay > 0 -> stringResource(R.string.delay_ms, node.delay)
                node.delay == DelayStore.NO_ANSWER -> stringResource(R.string.delay_failed)
                else -> stringResource(R.string.value_unknown)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (node.delay == DelayStore.NO_ANSWER) MaterialTheme.colorScheme.error
            else content.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** Та же прозрачность, что у плашки узла в карточке. */
internal const val PLATE_ALPHA = 0.08f

private val ROW_HEIGHT = 44.dp
/** Скругление строки (`shapes.small`) — рамке пульта: по высоте строка сошла бы за кнопку-пилюлю. */
private val ROW_CORNER = 12.dp
private val PADDING = 8.dp
