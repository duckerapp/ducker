package com.github.kr328.clash.update

import org.json.JSONArray
import org.json.JSONObject

/** Файл выпуска под один ABI: откуда качать, по порядку, и с чем сверить скачанное. */
data class Apk(val sha256: String, val size: Long, val urls: List<String>)

/** Запись одного канала в `update.json`. */
data class Entry(
    val version: String,
    val code: Long,
    /** Ниже этой версии Android выпуск не ставится — таким телефонам его не предлагаем. */
    val minSdk: Int,
    /** «Что нового» по языкам: `en`, `ru`. */
    val notes: Map<String, String>,
    val apk: Map<String, Apk>,
)

/**
 * `update.json` из корня репозитория: одна запись на канал. Бета — тот же выпуск на несколько
 * дней раньше, поэтому её запись бывает новее записи «для всех» или совпадает с ней.
 *
 * [mirrors] — дополнительные адреса самого `update.json`: приложение пробует их после зашитых,
 * так зеркало добавляется без перевыпуска. Подменить обновление через них нельзя: файл с чужой
 * подписью не поставит ни приложение, ни Android.
 */
data class Feed(val stable: Entry?, val beta: Entry?, val mirrors: List<String>)

/** Выпуск, выбранный под этот телефон. */
data class Release(val version: String, val code: Long, val notes: Map<String, String>, val apk: Apk)

/**
 * Что предложить человеку: самый новый выпуск его каналов, который новее установленного и
 * встанет на этот телефон. [abis] — по убыванию предпочтения, как `Build.SUPPORTED_ABIS`.
 */
fun Feed.pick(beta: Boolean, installed: Long, sdk: Int, abis: List<String>): Release? =
    listOfNotNull(stable, this.beta.takeIf { beta })
        .filter { it.code > installed && it.minSdk <= sdk }
        .sortedByDescending { it.code }
        .firstNotNullOfOrNull { entry ->
            abis.firstNotNullOfOrNull { entry.apk[it] }?.let { Release(entry.version, entry.code, entry.notes, it) }
        }

/** Бросает исключение на файле не того вида: вызывающий считает это неудачной проверкой. */
fun parseFeed(text: String): Feed {
    val root = JSONObject(text)

    return Feed(
        stable = root.optJSONObject("stable")?.let(::entry),
        beta = root.optJSONObject("beta")?.let(::entry),
        mirrors = root.optJSONArray("mirrors").strings(),
    )
}

private fun entry(json: JSONObject): Entry {
    val notes = json.optJSONObject("notes")
    val apk = json.getJSONObject("apk")

    return Entry(
        version = json.getString("version"),
        code = json.getLong("code"),
        minSdk = json.optInt("minSdk"),
        notes = notes?.keys()?.asSequence()?.associateWith { notes.getString(it) }.orEmpty(),
        apk = apk.keys().asSequence().associateWith { abi ->
            apk.getJSONObject(abi).let {
                Apk(it.getString("sha256").lowercase(), it.getLong("size"), it.getJSONArray("urls").strings())
            }
        },
    )
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
