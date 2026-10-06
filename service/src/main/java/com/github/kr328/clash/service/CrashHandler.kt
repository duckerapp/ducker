package com.github.kr328.clash.service

import java.util.concurrent.atomic.AtomicBoolean

/** Record synchronously, then preserve Android's existing crash handling. */
internal class CrashHandler(
    private val previous: Thread.UncaughtExceptionHandler,
    private val record: (Thread, Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
    private val recording = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, failure: Throwable) {
        try {
            if (recording.compareAndSet(false, true)) {
                try {
                    runCatching { record(thread, failure) }
                } finally {
                    recording.set(false)
                }
            }
        } finally {
            previous.uncaughtException(thread, failure)
        }
    }
}
