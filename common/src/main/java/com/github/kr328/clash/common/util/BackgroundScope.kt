package com.github.kr328.clash.common.util

import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Независимая фоновая задача не отменяет соседей и не завершает процесс при ошибке. */
fun backgroundScope(
    name: String,
    reportFailure: (String, Throwable) -> Unit = { message, error -> Log.e(message, error) },
): CoroutineScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineName(name) + CoroutineExceptionHandler { _, error ->
        // Ошибка записи диагностики тоже не должна завершать процесс.
        runCatching { reportFailure("Background task failed in $name", error) }
    },
)
