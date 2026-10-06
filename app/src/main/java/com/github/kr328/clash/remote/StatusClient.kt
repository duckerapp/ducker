package com.github.kr328.clash.remote

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.StatusProvider

class StatusClient(private val context: Context) {
    private val uri: Uri
        get() {
            return Uri.Builder()
                .scheme("content")
                .authority(Authorities.STATUS_PROVIDER)
                .build()
        }

    fun currentProfile(): String? = status().second

    /**
     * Запущена ли служба и имя профиля. Имени нет, пока профиль грузится: служба сообщает
     * о запуске раньше (D-147) — виджету в это время надо показывать «включено», а не «выключено».
     */
    fun status(): Pair<Boolean, String?> {
        // Отметку на диске служба держит, пока туннель поднят. Нет отметки — туннель выключен, и
        // запрос лишь поднимал бы выгруженный процесс службы, пока вызывающий ждёт: главный поток
        // на запуске приложения, плитка, виджет (аудит, perf 3.1).
        // ponytail: отметка не создалась (кончилось место) — интерфейс сочтёт туннель выключенным
        // до сообщения службы о запуске; понадобится — доспрашивать службу в фоне.
        if (!StatusProvider.shouldStartClashOnBoot) return false to null

        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_CURRENT_PROFILE,
                null,
                null
            )

            (result != null) to result?.getString("name")
        } catch (e: Exception) {
            Log.w("Query current profile: $e", e)

            false to null
        }
    }
}