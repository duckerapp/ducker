package com.github.kr328.clash.service

import android.os.SystemClock
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.model.LogSlice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The last lines of the core log, for the “Журнал” screen (D-290).
 *
 * One subscription for the whole service process, and memory only: nothing goes to disk. It
 * collects while “Подробный журнал” is on ([keep]) or while the interface reads ([read] until
 * [release]); when neither holds, the subscription ends and the lines are dropped.
 *
 * ponytail: every line crosses JNI on its way here, which costs about 0.4 ms of CPU on a phone
 * (F-279) — paid only while something collects. If that starts to matter, hold the lines in the Go
 * wrapper and carry them over only when asked.
 */
object LogTail {
    // Unconfined: a line is put away on the thread that brought it. A dispatcher would wake a
    // thread for every line, and at the debug level that is hundreds of wake-ups a second.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val lines = LogRing<LogMessage>(LIMIT)
    private val epoch = SystemClock.elapsedRealtimeNanos()

    private var kept = false
    private var reading = false
    private var feed: Job? = null

    @Synchronized
    fun keep(on: Boolean) {
        kept = on

        settle()
    }

    /** The interface is gone: without the verbose log there is nobody left to collect for. */
    @Synchronized
    fun release() {
        reading = false

        settle()
    }

    /** Lines numbered [after] and later; a reader from another life of the process gets them all. */
    @Synchronized
    fun read(epoch: Long, after: Long): LogSlice {
        reading = true

        settle()

        return LogSlice(this.epoch, lines.next, lines.from(if (epoch == this.epoch) after else 0))
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }

    @Synchronized
    private fun append(message: LogMessage) {
        lines.add(message)
    }

    private fun settle() {
        if (kept || reading) {
            if (feed == null) feed = scope.launch {
                val channel = Clash.subscribeLogcat()

                try {
                    for (message in channel) append(message)
                } finally {
                    channel.cancel()

                    Clash.forceGc()
                }
            }
        } else {
            feed?.cancel()
            feed = null

            lines.clear()
        }
    }
}

/** As many lines as the screen shows. */
private const val LIMIT = 2000

/** The last [limit] items of a stream, numbered from zero in the order they came. */
internal class LogRing<T>(private val limit: Int) {
    private val items = ArrayDeque<T>()

    /** The number the next item gets: how many came in all. */
    var next = 0L
        private set

    fun add(item: T) {
        if (items.size == limit) items.removeFirst()

        items.addLast(item)

        next++
    }

    /** Items numbered [from] and later; the ones already pushed out are skipped. */
    fun from(from: Long): List<T> {
        val skip = (from - (next - items.size)).coerceIn(0, items.size.toLong()).toInt()

        return items.subList(skip, items.size).toList()
    }

    fun clear() {
        items.clear()
    }
}
