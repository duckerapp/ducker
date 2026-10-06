package com.github.kr328.clash.common.util

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Validate untrusted provider metadata before making an ACTION_VIEW or PendingIntent. */
fun providerLink(address: String): String? = runCatching {
    if (address.isEmpty() || address.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    val uri = URI(address)
    if (!uri.isAbsolute || uri.isOpaque) return null
    val scheme = uri.scheme.lowercase(Locale.ROOT)
    when (scheme) {
        "http", "https" -> {
            val authority = uri.rawAuthority ?: return null
            if (authority.count { it == '@' } > 1) return null
            val hostPort = authority.substringAfterLast('@')
            val host: String
            val port: String?
            if (hostPort.startsWith('[')) {
                val end = hostPort.indexOf(']')
                if (end < 0 || uri.host == null) return null // URI verifies the IPv6 literal.
                host = hostPort.substring(0, end + 1)
                val suffix = hostPort.substring(end + 1)
                port = if (suffix.isEmpty()) null else suffix.takeIf { it.startsWith(':') }?.drop(1) ?: return null
            } else {
                if (hostPort.count { it == ':' } > 1) return null
                val rawHost = hostPort.substringBefore(':')
                if (rawHost.isEmpty()) return null
                host = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES)
                if (host.trimEnd('.').length !in 1..253) return null
                // An IPv4-shaped address must contain valid octets.
                val labels = host.trimEnd('.').split('.')
                if (labels.size == 4 && labels.all { it.all(Char::isDigit) } &&
                    labels.any { (it.toIntOrNull() ?: 256) > 255 }) return null
                port = if (':' in hostPort) hostPort.substringAfter(':') else null
            }
            if (port != null && (port.isEmpty() || !port.all { it in '0'..'9' } ||
                    port.toIntOrNull() !in 1..65535)) return null
            val userInfo = if ('@' in authority) authority.substringBeforeLast('@') + "@" else ""
            val normalizedAuthority = userInfo + host + (port?.let { ":$it" } ?: "")
            "$scheme://$normalizedAuthority" + address.substring(address.indexOf("://") + 3 + authority.length)
        }
        "tg" -> {
            // Provider navigation: chat/bot, invite, post, business support or invoice.
            // Other Telegram operations (login/proxy/passport/etc.) are not navigation here.
            val command = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (uri.rawAuthority?.lowercase(Locale.ROOT) != command ||
                uri.rawPath !in setOf("", "/") || uri.rawFragment != null) return null
            val params = linkedMapOf<String, String>()
            for (entry in (uri.rawQuery ?: return null).split('&')) {
                val key = decodeLinkParameter(entry.substringBefore('='))
                val value = decodeLinkParameter(entry.substringAfter('=', ""))
                if (key.isEmpty() || params.put(key, value) != null ||
                    (key + value).any { it.isISOControl() || it == '\uFFFD' }) return null
            }
            val valid = when (command) {
                "resolve" -> if ("domain" in params) {
                    "phone" !in params && params["domain"]?.matches(Regex("[A-Za-z0-9_]+")) == true
                } else {
                    params["phone"]?.matches(Regex("\\+?[0-9]+")) == true
                }
                "join" -> params["invite"]?.matches(Regex("[A-Za-z0-9_-]+")) == true
                "message", "invoice" -> params["slug"]?.matches(Regex("[A-Za-z0-9_-]+")) == true
                "privatepost" -> listOf("channel", "post").all { key ->
                    params[key]?.let { it.all(Char::isDigit) && (it.toLongOrNull() ?: 0) > 0 } == true
                }
                else -> false
            }
            if (!valid) return null
            "tg://$command" + address.substring(address.indexOf("://") + 3 + (uri.rawAuthority?.length ?: 0))
        }
        else -> null
    }
}.getOrNull()

/** A malformed preferred URL must not hide a valid provider fallback. */
fun firstProviderLink(vararg addresses: String): String? = addresses.firstNotNullOfOrNull(::providerLink)

private fun decodeLinkParameter(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
