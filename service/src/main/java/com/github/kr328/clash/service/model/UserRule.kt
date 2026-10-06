package com.github.kr328.clash.service.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Своё правило человека поверх правил провайдера (D-123): что и куда. Строку правила
 * mihomo собирает ядро (`native/config/rules.go`) и проверяет значение по типу — кривое
 * правило пропускается, а не роняет разбор профиля.
 */
@Serializable
data class UserRule(
    val type: Type,
    /** Домен, имя пакета, адрес или сеть, слово, категория или код страны — по [type]. */
    val value: String,
    val target: Target,
) {
    @Serializable
    enum class Type {
        @SerialName("site")
        Site,

        @SerialName("app")
        App,

        @SerialName("ip")
        Ip,

        /** Слово в адресе сайта (D-126). */
        @SerialName("keyword")
        Keyword,

        /** Категория сайтов из базы geosite: `youtube`, `category-ru` (D-126). */
        @SerialName("geosite")
        Geosite,

        /** Страна адреса по базе GeoIP: `RU` (D-126). */
        @SerialName("geoip")
        Geoip,
    }

    @Serializable
    enum class Target {
        /** Мимо VPN. */
        @SerialName("direct")
        Direct,

        /** Через VPN — в первую группу с ручным выбором, ту, что на главной. */
        @SerialName("proxy")
        Proxy,

        @SerialName("block")
        Block,
    }
}
