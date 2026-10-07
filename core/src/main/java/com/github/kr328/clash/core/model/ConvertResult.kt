package com.github.kr328.clash.core.model

import kotlinx.serialization.Serializable

/**
 * Ответ ядра на попытку превратить набор ссылок протоколов в конфиг по встроенному
 * шаблону (D-19). Заполнено ровно одно поле: либо готовый конфиг, либо причина отказа
 * текстом для человека.
 */
@Serializable
data class ConvertResult(
    val yaml: String = "",
    val error: String = "",
    /** Узлы взяты не из ссылок, а из конфига mihomo, который ядро не приняло. */
    val fromConfig: Boolean = false,
)
