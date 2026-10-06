package com.github.kr328.clash.ui.rules

import com.github.kr328.clash.service.model.UserRule
import java.net.IDN
import java.util.Locale

/**
 * Что человек ввёл — в значение правила (D-123), или `null`, если это не оно. Сайт можно
 * вставить ссылкой целиком: схема, путь, порт и «www.» отрезаются, кириллический домен
 * переводится в punycode — в таком виде его видит ядро. Ядро проверяет значение ещё раз
 * и кривое пропускает, но человеку лучше узнать об ошибке здесь.
 */
internal fun ruleValue(type: UserRule.Type, input: String): String? = when (type) {
    UserRule.Type.Site -> site(input)
    UserRule.Type.Ip -> ip(input)
    UserRule.Type.App -> input.trim().takeIf { PACKAGE.matches(it) }
    UserRule.Type.Keyword -> input.trim().lowercase().takeIf { it.length >= 2 && KEYWORD.matches(it) }
    UserRule.Type.Geosite -> input.trim().lowercase().takeIf { CATEGORY.matches(it) }
    UserRule.Type.Geoip -> input.trim().uppercase().takeIf { it in Locale.getISOCountries() }
}

/**
 * Категории сайтов из базы geosite (D-126), строчными. Неизвестную категорию ядро пропустит
 * молча — список нужен, чтобы опечатку увидеть при вводе и подсказать похожие.
 *
 * База — protobuf `GeoSiteList { repeated GeoSite entry = 1 }`, у `GeoSite` первое поле —
 * `country_code`. Читаются только имена: записи перепрыгиваются по длине, домены не
 * разбираются — 4 МБ за миллисекунды.
 */
internal fun geositeCategories(data: ByteArray): Set<String> {
    val out = HashSet<String>()
    var i = 0

    fun varint(): Int {
        var result = 0
        var shift = 0

        while (true) {
            val b = data[i++].toInt() and 0xff
            result = result or ((b and 0x7f) shl shift)
            if (b < 0x80) return result
            shift += 7
        }
    }

    while (i < data.size) {
        if (data[i++] != ENTRY) break
        val end = varint().let { it + i }

        if (data[i] == ENTRY) {
            i++
            val length = varint()
            out += String(data, i, length, Charsets.UTF_8).lowercase()
        }

        i = end
    }

    return out
}

/** Тег поля 1 с длиной — и запись списка, и имя в ней. */
private const val ENTRY: Byte = 0x0a

private fun site(input: String): String? {
    val host = input.trim().lowercase()
        .substringAfter("://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':')
        .removePrefix("*.").removePrefix(".").removePrefix("www.")

    val ascii = runCatching { IDN.toASCII(host) }.getOrNull() ?: return null

    return ascii.takeIf { '.' in it && DOMAIN.matches(it) && !it.endsWith('.') }
}

private fun ip(input: String): String? {
    val value = input.trim()
    val address = value.substringBefore('/')
    val prefix = value.substringAfter('/', "").takeIf { it.isNotEmpty() }?.toIntOrNull()

    if ('/' in value && prefix == null) return null

    return when {
        ':' in address -> value.takeIf { IPV6.matches(address) && (prefix ?: 0) in 0..128 }
        else -> value.takeIf {
            val octets = address.split('.')

            octets.size == 4 && octets.all { o -> o.toIntOrNull()?.let { it in 0..255 } == true && o.length <= 3 } &&
                (prefix ?: 0) in 0..32
        }
    }
}

private val DOMAIN = Regex("[a-z0-9.-]+")
private val IPV6 = Regex("[0-9a-fA-F:.]+")
private val PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
private val KEYWORD = Regex("[a-z0-9.-]+")
private val CATEGORY = Regex("[a-z0-9!@:_-]+")
