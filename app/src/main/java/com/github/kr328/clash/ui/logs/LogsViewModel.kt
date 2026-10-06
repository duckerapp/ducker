package com.github.kr328.clash.ui.logs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.log.LogcatFilter
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.remote.ILogObserverDelegate
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
)

/** Дальше этого в памяти не держим: при уровне debug ядро пишет сотни строк в секунду. */
private const val LIMIT = 2000

/** Реже, чем раз в треть секунды, человеку и не нужно, а перерисовок это экономит много. */
private const val FLUSH_MS = 300L

class LogsViewModel : ViewModel() {
    private val _state = MutableStateFlow(LogsState())
    val state: StateFlow<LogsState> = _state.asStateFlow()

    private val incoming = Channel<LogMessage>(Channel.UNLIMITED)

    /**
     * Обёртка держится полем, и это не косметика.
     *
     * `setLogObserver` принимает интерфейс, а kaidl заворачивает его в `ILogObserverDelegate`
     * **внутри вызова**. Если этот `Binder` не держать, сборщик мусора убирает его, у службы
     * остаётся мёртвый прокси, и ядро закрывает подписку — на экран приходит ровно одна
     * строка, после чего тишина. Проверено на устройстве.
     *
     * `wrap()` возвращает сам объект, если он уже `IBinder`, поэтому передаём делегата.
     */
    private val observer = ILogObserverDelegate(object : ILogObserver {
        override fun newItem(log: LogMessage) {
            // Вызывается из потока биндера, поэтому только складываем в очередь.
            incoming.trySend(log)
        }
    })

    private var pump: Job? = null

    fun start() {
        if (pump != null) return

        pump = viewModelScope.launch {
            runCatching { withClash { setLogObserver(observer) } }

            _state.update { it.copy(streaming = true) }

            val batch = mutableListOf<LogMessage>()

            while (isActive) {
                delay(FLUSH_MS)

                while (true) {
                    val msg = incoming.tryReceive().getOrNull() ?: break

                    batch += msg
                }

                if (batch.isEmpty()) continue

                val appended = batch.toList()

                batch.clear()

                _state.update { it.copy(lines = (it.lines + appended).takeLast(LIMIT)) }
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null

        _state.update { it.copy(streaming = false) }

        // Наблюдатель живёт в службе, и его надо снять, иначе он продолжит слать строки
        // в очередь, пока приложение не закроют.
        viewModelScope.launch { runCatching { withClash { setLogObserver(null) } } }
    }

    fun clear() {
        _state.update { it.copy(lines = emptyList()) }
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
