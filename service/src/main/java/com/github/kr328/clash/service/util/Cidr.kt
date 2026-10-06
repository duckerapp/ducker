package com.github.kr328.clash.service.util

/**
 * Арифметика диапазонов IPv4 для маршрутов туннеля.
 *
 * Зачем это вообще. `VpnService` умеет только добавлять маршруты, а исключать их научился
 * лишь в Android 13 (`excludeRoute`), при минимальной версии 10 (D-13). Поэтому «всё, кроме
 * этих сетей» приходится выражать набором маршрутов-дополнений: вместо `0.0.0.0/0` в
 * интерфейс добавляются десятки блоков, обходящих исключённые сети. Так же поступает и CMFA,
 * только список исключений у неё зашит в ресурсы — а нам нужно собирать его из профиля
 * (`tun.route-exclude-address`, D-56).
 *
 * Адреса держим в `Long`: в `Int` старший бит уехал бы в знак, и сравнения сломались бы
 * на адресах от 128.0.0.0.
 */
object Cidr {
    data class Block(val address: String, val prefix: Int)

    private const val SPACE = 1L shl 32

    /**
     * Все адреса IPv4, кроме [excluded], минимальным числом блоков.
     *
     * Записи, которые не разобрались (в том числе IPv6), молча пропускаются: профиль пишет
     * человек, и одна опечатка не должна ронять туннель. Пустой список исключений даёт
     * единственный блок `0.0.0.0/0`.
     */
    fun complementV4(excluded: Collection<String>): List<Block> {
        val ranges = excluded.mapNotNull(::parse).sortedBy { it.first }

        val result = mutableListOf<Block>()
        var cursor = 0L

        for ((start, endInclusive) in ranges) {
            if (start > cursor) {
                result += blocksOf(cursor, start - 1)
            }

            if (endInclusive + 1 > cursor) {
                cursor = endInclusive + 1
            }
        }

        if (cursor < SPACE) {
            result += blocksOf(cursor, SPACE - 1)
        }

        return result
    }

    /** `10.0.0.0/8` → диапазон адресов включительно. */
    private fun parse(cidr: String): Pair<Long, Long>? {
        val text = cidr.trim()
        if (text.isEmpty() || text.contains(':')) return null

        val slash = text.indexOf('/')
        val addressPart = if (slash < 0) text else text.substring(0, slash)
        val prefix = if (slash < 0) 32 else text.substring(slash + 1).toIntOrNull() ?: return null

        if (prefix !in 0..32) return null

        val octets = addressPart.split('.')
        if (octets.size != 4) return null

        var address = 0L

        for (octet in octets) {
            val value = octet.toIntOrNull() ?: return null
            if (value !in 0..255) return null

            address = (address shl 8) or value.toLong()
        }

        val size = 1L shl (32 - prefix)
        val start = address and (size - 1).inv()

        return start to start + size - 1
    }

    /** Разбивает диапазон на выровненные блоки CIDR — так же, как это делает `ipcalc`. */
    private fun blocksOf(from: Long, to: Long): List<Block> {
        val result = mutableListOf<Block>()
        var start = from

        while (start <= to) {
            // Самый большой блок, который начинается ровно здесь и не выходит за конец.
            var size = if (start == 0L) SPACE else start and -start

            while (size > to - start + 1) {
                size = size shr 1
            }

            result += Block(text(start), 32 - java.lang.Long.numberOfTrailingZeros(size))

            start += size
        }

        return result
    }

    private fun text(address: Long): String =
        "${(address shr 24) and 0xff}.${(address shr 16) and 0xff}." +
            "${(address shr 8) and 0xff}.${address and 0xff}"
}
