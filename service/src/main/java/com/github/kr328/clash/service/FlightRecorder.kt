package com.github.kr328.clash.service

import android.content.Context
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Бортовой самописец службы (D-111).
 *
 * Экран «Журнал» записывает события, только пока открыт: выгрузка «после сбоя» содержала
 * причины завершения процессов, но не сам сбой. А жалобу присылают издалека, с чужого
 * телефона и один раз. Самописец пишет всегда — в два файла по очереди, по [LIMIT] каждый,
 * построчно, чтобы последние строки пережили и убийство процесса оболочкой телефона.
 *
 * Что пишет: сообщения службы уровней info, warning, error (смены сети, подъёмы и
 * падения туннеля, восстановления) и от ядра — предупреждения и ошибки, info без строк
 * «соединение пошло туда-то» (их сотни, и в них адреса сайтов), а из отладочных только
 * строки мультиплекса и перепроверки узлов (D-105, D-110). Уровень журнала профиля на
 * это не влияет: провайдеры ставят «только ошибки», и самописец остался бы пустым.
 * Адреса серверов и ключи вырезаются при выгрузке тем же чистильщиком, что и журнал.
 */
object FlightRecorder {
    const val DIR = "flight"
    const val CURRENT = "flight.log"
    const val PREVIOUS = "flight.1.log"

    /** На каждый из двух файлов — последние часы, а не недели. */
    private const val LIMIT = 256 * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coreAttached = AtomicBoolean(false)
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private var dir: File? = null
    private var writer: FlightLogWriter? = null

    /** Процесс службы запущен: писать сообщения службы. Ядро здесь ещё не нужно. */
    fun start(context: Context) {
        dir = context.filesDir.resolve(DIR).apply { mkdirs() }

        Log.sink = { level, message -> append(level, "ducker", message) }
    }

    /** Ядро понадобилось (поднимается туннель): писать и его события. Один раз на процесс. */
    fun attachCore() {
        if (!coreAttached.compareAndSet(false, true)) return

        // Отбор — в ядре (`recorded` в log.go): своя подписка, без оглядки на уровень
        // журнала профиля, и лишнее не идёт через JNI.
        scope.launch {
            for (message in Clash.subscribeRecorder()) {
                append(levelOf(message.level), "core", message.message)
            }
        }
    }

    private fun levelOf(level: LogMessage.Level): Char = when (level) {
        LogMessage.Level.Error -> 'E'
        LogMessage.Level.Warning -> 'W'
        LogMessage.Level.Info -> 'I'
        else -> 'D'
    }

    @Synchronized
    private fun append(level: Char, source: String, message: String) {
        val base = dir ?: return

        runCatching {
            val out = writer ?: FlightLogWriter(base, LIMIT).also { writer = it }
            out.append("${format.format(Date())} $level $source: $message\n")
        }
    }
}
