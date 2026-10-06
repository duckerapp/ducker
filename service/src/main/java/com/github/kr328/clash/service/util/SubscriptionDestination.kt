package com.github.kr328.clash.service.util

import java.net.URI

/**
 * Данные устройства уходят только по https (аудит 2.11) — проверяем до того, как их собирать.
 * Списка «чужих» хостингов нет: панель провайдера может стоять где угодно, за адрес подписки
 * отвечает он (D-192). То же правило и переадресации — в ядре, `config/subscription_headers.go`.
 */
internal fun deviceHeadersAllowed(address: String): Boolean = runCatching {
    val uri = URI(address)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)
