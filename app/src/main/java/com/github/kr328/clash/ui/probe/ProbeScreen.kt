package com.github.kr328.clash.ui.probe

import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.TvLazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.util.nodeFailure
import com.github.kr328.clash.ui.components.DuckerCard as Card
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.ThinBar
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets

/**
 * Замер узла: серия запросов по одному узлу с разбросом и долей неответов (D-57).
 *
 * Отдельный экран, а не кнопка на вкладке «Прокси»: замер занимает полминуты и нужен
 * при разборе проблем, а не каждый день.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProbeScreen(
    state: ProbeState,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp + tvEdge()),
    ) {
        item {
            ScreenHeader(stringResource(R.string.probe), onBack = onBack)
        }

        item {
            Text(
                text = stringResource(
                    if (state.running) R.string.probe_hint else R.string.probe_needs_connection
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.running) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.nodes.forEach { node ->
                            Pill(
                                text = node,
                                onClick = { onSelect(node) },
                                selected = state.selected == node,
                                onCard = true,
                                role = Role.RadioButton,
                            )
                        }
                    }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The run is the screen's one action — black; the copy of its report white beside it.
                    Pill(
                        text = stringResource(if (state.probing) R.string.probe_stop else R.string.probe_start),
                        onClick = if (state.probing) onStop else onStart,
                        selected = true,
                    )

                    if (state.stats.total > 0 && !state.probing) {
                        Pill(text = stringResource(R.string.probe_copy), onClick = onCopy)
                    }
                }
            }
        }

        if (state.probing) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.probe_progress, state.done, state.planned),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ThinBar(state.done.toFloat() / state.planned)
                }
            }
        }

        if (state.stats.total > 0) {
            item { Results(state.stats) }
        }
    }
}

@Composable
private fun Results(stats: ProbeStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Line(stringResource(R.string.probe_loss),
                stringResource(R.string.probe_loss_value, stats.lost, stats.total, stats.lossPercent))

            if (stats.delays.isNotEmpty()) {
                Line(stringResource(R.string.probe_delay),
                    stringResource(R.string.probe_delay_value, stats.min, stats.average, stats.max))
                Line(stringResource(R.string.probe_jitter),
                    stringResource(R.string.delay_ms, stats.jitter))
            }

            if (stats.failures.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.probe_failures),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Знакомые причины — словами (D-227); в отчёт для поддержки идёт текст ядра.
                stats.failures.map { raw -> nodeFailure(raw)?.let { stringResource(it) } ?: raw }
                    .distinct().take(5).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
