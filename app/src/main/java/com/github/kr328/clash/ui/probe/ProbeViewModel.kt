package com.github.kr328.clash.ui.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.NO_SERVICE
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Итог серии замеров.
 *
 * Средняя задержка без разброса и доли неответов бесполезна: под шейпингом оператора узел
 * отвечает, но то за 60 мс, то за 2000, то не отвечает вовсе. Поэтому считаем всё сразу.
 */
data class ProbeStats(
    val total: Int = 0,
    val delays: List<Int> = emptyList(),
    /** Причины неудач в порядке появления — по ним видно, что именно ломается. */
    val failures: List<String> = emptyList(),
) {
    val lost: Int get() = total - delays.size
    val lossPercent: Int get() = if (total == 0) 0 else lost * 100 / total
    val min: Int get() = delays.minOrNull() ?: 0
    val max: Int get() = delays.maxOrNull() ?: 0
    val average: Int get() = if (delays.isEmpty()) 0 else delays.sum() / delays.size

    /** Средний разброс между соседними замерами — он и означает «дёргается». */
    val jitter: Int
        get() = if (delays.size < 2) 0
        else delays.zipWithNext { a, b -> kotlin.math.abs(a - b) }.sum() / (delays.size - 1)
}

data class ProbeState(
    val running: Boolean = false,
    val nodes: List<String> = emptyList(),
    val selected: String = "",
    val probing: Boolean = false,
    val done: Int = 0,
    val planned: Int = ROUNDS,
    val stats: ProbeStats = ProbeStats(),
    val loaded: Boolean = false,
)

private const val ROUNDS = 20

class ProbeViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProbeState())
    val state: StateFlow<ProbeState> = _state.asStateFlow()

    private var probe: Job? = null

    fun refresh() {
        viewModelScope.launch {
            val running = Remote.broadcasts.clashRunning

            // Замер идёт через работающее ядро, поэтому и список узлов берём у него:
            // из файла профиля пришли бы имена, которых в туннеле может не быть.
            val nodes = if (!running) emptyList() else runCatching {
                withClash {
                    queryProxyGroupNames(false).flatMap { group ->
                        queryProxyGroup(group, ProxySort.Default).proxies.map { it.name }
                    }
                }.distinct()
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    running = running,
                    nodes = nodes,
                    selected = it.selected.takeIf { name -> name in nodes }.orEmpty(),
                    loaded = true,
                )
            }
        }
    }

    fun select(name: String) {
        stop()

        _state.update { it.copy(selected = name, done = 0, stats = ProbeStats()) }
    }

    fun start() {
        val name = _state.value.selected

        if (name.isEmpty() || _state.value.probing) return

        _state.update { it.copy(probing = true, done = 0, stats = ProbeStats()) }

        probe = viewModelScope.launch {
            repeat(ROUNDS) { round ->
                val result = runCatching {
                    withContext(Dispatchers.IO) { withClash { probeProxy(name) } }
                }.getOrNull()

                _state.update { current ->
                    val stats = current.stats.copy(
                        total = current.stats.total + 1,
                        delays = if (result != null && result.ok) {
                            current.stats.delays + result.delay
                        } else {
                            current.stats.delays
                        },
                        failures = if (result == null || !result.ok) {
                            current.stats.failures + (result?.error ?: NO_SERVICE)
                        } else {
                            current.stats.failures
                        },
                    )

                    current.copy(done = round + 1, stats = stats)
                }

                // Пауза между замерами: подряд они мерили бы одно и то же соединение,
                // а нам нужна картина во времени.
                if (round < ROUNDS - 1) delay(1000)
            }

            _state.update { it.copy(probing = false) }
        }
    }

    fun stop() {
        probe?.cancel()
        probe = null

        _state.update { it.copy(probing = false) }
    }

    /** Текст для отправки в поддержку: без адресов, только имя узла и цифры. */
    fun report(): String {
        val state = _state.value
        val stats = state.stats

        return buildString {
            appendLine("Узел: ${state.selected}")
            appendLine("Замеров: ${stats.total}, без ответа: ${stats.lost} (${stats.lossPercent}%)")

            if (stats.delays.isNotEmpty()) {
                appendLine("Задержка: мин ${stats.min}, средняя ${stats.average}, макс ${stats.max} мс")
                appendLine("Разброс между замерами: ${stats.jitter} мс")
                appendLine("Все замеры: ${stats.delays.joinToString(", ")}")
            }

            if (stats.failures.isNotEmpty()) {
                appendLine("Причины неудач:")
                stats.failures.distinct().forEach { appendLine("  $it") }
            }
        }
    }
}
