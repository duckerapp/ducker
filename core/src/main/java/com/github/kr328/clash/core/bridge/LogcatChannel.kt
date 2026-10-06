package com.github.kr328.clash.core.bridge

import com.github.kr328.clash.core.model.LogMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json

/** Закрытие канала останавливает подписку Go через существующий протокол ошибок JNI. */
internal class LogcatChannel(private val channel: SendChannel<LogMessage>) : LogcatInterface {
    override fun received(jsonPayload: String) {
        val result = channel.trySend(Json.decodeFromString(LogMessage.serializer(), jsonPayload))
        if (result.isClosed) throw CancellationException("Log channel closed")
        // Заполненный буфер только пропускает строку, подписка остаётся рабочей.
    }
}
