package com.github.kr328.clash.ui.logs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.log.LogcatFilter
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.StringWriter
import java.util.Date

data class LogsState(
    val lines: List<LogMessage> = emptyList(),
    val streaming: Boolean = false,
    /** The number of the first of [lines] among all this screen has shown: a row's key is `first + index`. */
    val first: Long = 0,
)

/** Дальше этого в памяти не держим: при уровне debug ядро пишет сотни строк в секунду. */
private const val LIMIT = 2000

/** Реже, чем раз в треть секунды, человеку и не нужно, а перерисовок это экономит много. */
private const val FLUSH_MS = 300L

class LogsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LogsState())
    val state: StateFlow<LogsState> = _state.asStateFlow()

    /** Where the service's last reply ended: the next request asks from there (D-290). */
    private var epoch = 0L
    private var after = 0L

    private var pump: Job? = null

    fun start() {
        if (pump != null) return

        pump = viewModelScope.launch {
            _state.update { it.copy(streaming = true) }

            while (isActive) {
                // The service holds the lines, and the screen asks for the new ones (D-290). A
                // request waits while the app is hidden — the interface is unbound from the service
                // then — and the first one after the return brings what was written meanwhile.
                val slice = runCatching { withClash { queryLogs(epoch, after) } }.getOrNull()

                if (slice != null) {
                    epoch = slice.epoch
                    after = slice.next

                    if (slice.lines.isNotEmpty()) _state.update {
                        val all = it.lines + slice.lines

                        it.copy(
                            lines = all.takeLast(LIMIT),
                            first = it.first + (all.size - LIMIT).coerceAtLeast(0),
                        )
                    }
                }

                delay(FLUSH_MS)
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null

        _state.update { it.copy(streaming = false) }
    }

    fun clear() {
        _state.update { it.copy(lines = emptyList(), first = it.first + it.lines.size) }

        // The service's copy too: otherwise the lines come back once this process is evicted.
        viewModelScope.launch { runCatching { withClash { clearLogs() } } }
    }

    /**
     * Текст для отправки в поддержку. Тем же фильтром, что и выгрузка в файл: адреса узлов,
     * ключи и идентификатор устройства прячутся (инвариант 4).
     *
     * Кроме строк с этого экрана — справка о телефоне и записи самописца службы (D-111):
     * сбой обычно случается не тогда, когда журнал открыт. Читается с диска, поэтому не в
     * главном потоке.
     */
    suspend fun report(context: Context): String = withContext(Dispatchers.IO) {
        val writer = StringWriter()

        LogcatFilter(writer, context).use { filter ->
            filter.writeHeader(Date())
            filter.writeDevice()
            filter.writeExitReasons()
            filter.writeFlight()
            filter.writeCrashes()

            _state.value.lines.forEach(filter::writeMessage)
        }

        writer.toString()
    }

    override fun onCleared() {
        stop()

        super.onCleared()
    }
}
