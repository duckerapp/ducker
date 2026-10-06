package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.service.model.UserRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Свои правила по подпискам (D-123): файл на подписку в каталоге приложения. Не в каталоге
 * профиля — тот пересоздаётся при каждом обновлении подписки, а правила должны его пережить.
 * Пишет экран в процессе приложения, читает служба перед загрузкой профиля.
 */
class UserRulesStore(context: Context) {
    private val dir = context.filesDir.resolve("user-rules")

    fun read(uuid: UUID): List<UserRule> = runCatching {
        json.decodeFromString(serializer, file(uuid).readText())
    }.getOrDefault(emptyList())

    /** Как есть — строкой для ядра; пусто, если правил нет. */
    fun raw(uuid: UUID): String = runCatching { file(uuid).readText() }.getOrDefault("")

    fun write(uuid: UUID, rules: List<UserRule>) {
        if (rules.isEmpty()) {
            delete(uuid)

            return
        }

        dir.mkdirs()

        // Через временный файл: служба может читать в ту же секунду.
        val temp = File(dir, "$uuid.json.tmp")

        temp.writeText(json.encodeToString(serializer, rules))
        temp.renameTo(file(uuid))
    }

    fun delete(uuid: UUID) {
        file(uuid).delete()
    }

    private fun file(uuid: UUID) = File(dir, "$uuid.json")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(UserRule.serializer())
    }
}
