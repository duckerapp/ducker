package com.github.kr328.clash.ui.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
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
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.labelWeights
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets

/**
 * Журнал ядра (этап 8.3, замена экрана CMFA).
 *
 * Строки идут в память, только пока экран открыт: службы и файлов на диске здесь нет,
 * поэтому и чистить потом нечего. Человеку нужно ровно это — открыть, повторить проблему,
 * скопировать.
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

    // Свежие строки внизу, но только пока человек сам смотрит в конец. Иначе при
    // подробном журнале список дёргается по три раза в секунду и утаскивает вниз
    // ровно в тот момент, когда пытаешься прочитать строку выше.
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index

            last == null || last >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(state.lines.size) {
        // Прокрутка без анимации: обновления приходят чаще, чем анимация успевает дойти.
        if (atBottom && state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex)
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
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
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
        val weights = labelWeights(actions.map { it.first }, MaterialTheme.typography.labelMedium, ACTION_PADDING)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEachIndexed { i, (label, onClick, enabled) ->
                Action(label, onClick, enabled, Modifier.weight(weights[i]))
            }
        }

        Card(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollFor(listState),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.lines) { line ->
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
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ACTION_PADDING),
        // Выключенная — тем же фоном, только подпись бледнее. По умолчанию Material рисует её
        // тёмной плашкой, и «Очистить» при пустом журнале выглядела нажатой (D-140).
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        FitLabel(label, MaterialTheme.typography.labelMedium)
    }
}

private val ACTION_PADDING = 8.dp

@Composable
private fun colorOf(level: LogMessage.Level): Color = when (level) {
    LogMessage.Level.Error -> MaterialTheme.colorScheme.error
    LogMessage.Level.Warning -> Color(0xFFB26A00)
    LogMessage.Level.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}
