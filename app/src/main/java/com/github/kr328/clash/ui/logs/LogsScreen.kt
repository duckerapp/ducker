package com.github.kr328.clash.ui.logs

import com.github.kr328.clash.ui.components.Symbols
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.ui.components.DuckerCard as Card
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.labelWeights
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets

/**
 * Журнал ядра (этап 8.3, замена экрана CMFA).
 *
 * The lines live in memory only, the service's and this screen's (D-290): nothing goes to disk,
 * so there is nothing to clean up afterwards. Without “Подробный журнал” they exist only while the
 * screen is open; with it the service collects them all the time and the screen takes them on
 * opening and on the return to the app.
 */
@Composable
fun LogsScreen(
    state: LogsState,
    verbose: Boolean,
    onVerbose: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Following the stream is a state of its own (D-290), not a guess from where the list happens
    // to stand: on — the list keeps to the newest line. A scroll that comes to rest above the end
    // switches it off, so the text stays under the eyes; one that reaches the end, or the button,
    // switches it back on. Not saved: after an eviction the lines are read anew, and the old place
    // in the list would point at other ones.
    var follow by remember { mutableStateOf(true) }

    // A finger on the list takes it from the stream at once, without waiting for the scroll to
    // end: a batch arriving in the middle of a fling pulled the list back to the end.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }

    LaunchedEffect(listState) {
        var moving = false

        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (now, more) ->
            if (moving && !now) follow = !more

            moving = now
        }
    }

    // The number after the last line, not the count: the count stops changing at the cap.
    val end = state.first + state.lines.size

    LaunchedEffect(follow, end) {
        // Прокрутка без анимации: обновления приходят чаще, чем анимация успевает дойти.
        if (follow && state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex)
    }

    Column(
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(stringResource(R.string.logs), onBack = onBack, modifier = Modifier.padding(top = 16.dp))

        Text(
            text = stringResource(
                if (state.lines.isEmpty()) R.string.logs_empty else R.string.logs_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Переключатель виден всегда, а не только когда выключено: иначе включить можно,
        // а выключить нечем — человек остаётся с подробным журналом навсегда.
        Card(
            modifier = Modifier.fillMaxWidth(),
            surfaceModifier = Modifier.clip(MaterialTheme.shapes.large)
                .toggleable(value = verbose, role = Role.Switch, onValueChange = onVerbose),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debug_log),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            if (verbose) R.string.logs_level_on else R.string.logs_level_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(checked = verbose, onCheckedChange = null)
            }
        }

        // Выгрузка доступна и с пустым экраном: в ней справка о телефоне и записи
        // самописца — то, что было до того, как журнал открыли (D-111).
        // Кнопки делят ширину по длине подписей: без этого на узком экране или с крупным
        // шрифтом последние сжимались, и «Сохранить» переносилось по буквам (D-114).
        val actions = listOf(
            Triple(stringResource(R.string.logs_share), onShare, true),
            Triple(stringResource(R.string.logs_copy), onCopy, true),
            Triple(stringResource(R.string.logs_save), onSave, true),
            Triple(stringResource(R.string.logs_clear), onClear, state.lines.isNotEmpty()),
        )
        val weights = labelWeights(actions.map { it.first }, MaterialTheme.typography.labelLarge, ACTION_PADDING)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEachIndexed { i, (label, onClick, enabled) ->
                Pill(text = label, onClick = onClick, modifier = Modifier.weight(weights[i]), enabled = enabled, fit = true)
            }
        }

        Card(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollFor(listState),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // A key per line: at the cap every batch shifts the indices, and without keys
                    // the text ran away from under the finger.
                    items(state.lines.size, key = { state.first + it }) { index ->
                        val line = state.lines[index]
                        val row = rememberScrollState()

                        Row(Modifier.fillMaxWidth().horizontalScroll(row, overscrollEffect = overscrollFor(row))) {
                            Text(
                                text = line.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = colorOf(line.level),
                            )
                        }
                    }
                }

                // Fully qualified: the card's column scope would claim the short name.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !follow,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    FilledIconButton(onClick = { follow = true }) {
                        Icon(Symbols.KeyboardArrowDown, contentDescription = stringResource(R.string.logs_follow))
                    }
                }
            }
        }
    }
}

/** [Pill]'s room on each side of its label. */
private val ACTION_PADDING = 14.dp

@Composable
private fun colorOf(level: LogMessage.Level): Color = when (level) {
    LogMessage.Level.Error -> MaterialTheme.colorScheme.error
    LogMessage.Level.Warning -> Color(0xFFB26A00)
    LogMessage.Level.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}
