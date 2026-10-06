package com.github.kr328.clash.log

import java.util.Base64

/**
 * Чистка журнала перед выгрузкой (инвариант 4).
 *
 * Правило простое: лучше замазать лишнее, чем выпустить наружу адрес узла или ключ подписки.
 * Поэтому под замену идут все публичные адреса, пути ссылок, UUID, значения полей вроде
 * `password` и секреты в ссылках узлов (`vless://`, `trojan://` и подобных), а свои и служебные
 * адреса (127.*, 10.*, 192.168.*, fake-ip 198.18.*) остаются — без них по журналу нельзя
 * понять, что вообще происходило.
 *
 * Домены, которые открывал человек, **остаются**: именно по ним разбираются правила и DNS,
 * а ради них журнал и выгружают. Экран выгрузки об этом предупреждает.
 */
object LogScrubber {
    const val HIDDEN = "<hidden>"

    private const val IP = "<ip>"

    const val SERVER = "<server>"

    /**
     * @param secrets точные значения, которые мы знаем сами: идентификатор устройства,
     * секрет API ядра. Их вырезаем по совпадению, без догадок.
     * @param servers адреса серверов узлов из профилей ([serversIn]). Домен узла по виду
     * не отличить от сайта, поэтому прячем по списку, целым словом (F-162).
     */
    fun scrub(
        text: String,
        secrets: Collection<String> = emptyList(),
        servers: Regex? = null,
    ): String {
        // Redact complete values before DNS handling or any line/tail splitting.
        // A diagnostic may contain a multiline YAML value or end inside a quote.
        val safeText = scrubFields(text)
        // Адрес DNS-сервера в строке его отказа оставляем (F-163): это резолвер из профиля,
        // и строка пишется ради него. Если он совпал с сервером узла — прячется как узел.
        return buildString {
            var position = 0
            for (failure in DNS_FAILED.findAll(safeText)) {
                val address = requireNotNull(failure.groups[1])
                append(scrubAddresses(safeText.substring(position, address.range.first), secrets, servers))
                val kept = scrubCredentials(address.value, secrets)
                append(servers?.replace(kept, SERVER) ?: kept)
                position = address.range.last + 1
            }
            append(scrubAddresses(safeText.substring(position), secrets, servers))
        }
    }

    private fun scrubAddresses(text: String, secrets: Collection<String>, servers: Regex?): String {
        var result = scrubCredentials(text, secrets)
        result = IPV6.replace(result) { if (keepIpv6(it.value)) it.value else IP }
        // После ссылок — иначе `https://<server>/…` не узнать как ссылку и путь останется;
        // после IPv6 — `<server>:443:` он принял бы за адрес; до IPv4 — чтобы сервер,
        // заданный адресом, был виден как сервер.
        if (servers != null) result = servers.replace(result, SERVER)
        result = IPV4.replace(result) { if (keepIpv4(it.value)) it.value else IP }

        return result
    }

    private fun scrubCredentials(text: String, secrets: Collection<String>): String {
        var result = text

        for (secret in secrets) {
            // Короткие значения не трогаем: вырезать по ним — значит изрешетить весь журнал.
            if (secret.length >= 8) {
                result = result.replace(secret, HIDDEN, ignoreCase = true)
            }
        }

        // Хост ссылки оставляем, всё после него — нет: ключ подписки лежит именно там.
        result = URL.replace(result) { "${it.groupValues[1]}/$HIDDEN" }
        // A failed import may never have been saved in a profile. URI credentials
        // identify a node even when its hostname is absent from the known list.
        result = LINK_SERVER.replace(result) { it.groupValues[1] + SERVER }
        result = HYSTERIA_SERVER.replace(result) { it.groupValues[1] + SERVER }
        // После ссылок http(s): `https://<hidden>@…` правило выше как ссылку уже не узнает.
        result = LINK_USER.replace(result) { "${it.groupValues[1]}$HIDDEN@" }
        result = LINK_BODY.replace(result) { "${it.groupValues[1]}$HIDDEN" }
        result = UUID.replace(result, HIDDEN)
        return result
    }

    private fun scrubFields(text: String): String = buildString {
        var position = 0
        while (position < text.length) {
            val field = SECRET_FIELD.find(text, position) ?: break
            val start = field.range.last + 1
            if (start == text.length) break
            var end = start
            val quote = text[start]
            val quoted = quote == '"' || quote == '\''
            val lineStart = text.lastIndexOf('\n', field.range.first) + 1
            val prefix = text.substring(lineStart, field.range.first)
            val flow = prefix.lastIndexOf('{') > prefix.lastIndexOf('}') ||
                prefix.lastIndexOf('[') > prefix.lastIndexOf(']')
            // Значение до конца строки и на строках с отступом ниже — только у ключа YAML: он
            // стоит в начале строки или внутри скобок. В строке журнала («Invalid uuid: 123»)
            // под это попадал стек падения, поэтому там значение идёт до пробела (D-205).
            val yaml = field.groupValues[2].contains(':') && (flow || YAML_KEY_PREFIX.matches(prefix))
            val headerEnd = if (quote in "|>") text.indexOf('\n', start).let { if (it < 0) text.length else it } else start
            val block = yaml && quote in "|>" && BLOCK_HEADER.matches(text.substring(start, headerEnd))
            when {
                quoted -> {
                    end++
                    while (end < text.length) {
                        val char = text[end++]
                        if (quote == '"' && char == '\\' && end < text.length) end++
                        else if (char == quote) {
                            if (quote == '\'' && end < text.length && text[end] == '\'') end++
                            else break
                        }
                    }
                }
                block -> end = headerEnd
                yaml -> while (end < text.length && text[end] !in "\r\n" &&
                    !(flow && text[end] in ",}]")) end++
                else -> while (end < text.length && !text[end].isWhitespace() &&
                    text[end] !in ",\"'}]&#") end++
            }
            if (block || (yaml && !quoted && !flow && end < text.length && text[end] in "\r\n")) {
                val indent = prefix.takeWhile { it == ' ' || it == '\t' }.length +
                    if (prefix.trimStart().startsWith("- ")) 2 else 0
                end = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
                while (end < text.length) {
                    val next = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
                    val line = text.substring(end, next)
                    if (line.isNotBlank() && line.takeWhile { it == ' ' || it == '\t' }.length <= indent) break
                    end = next
                }
            }
            append(text, position, start)
            if (end == start) {
                position = start
            } else {
                append(HIDDEN)
                if (!quoted && yaml && end > start && text[end - 1] == '\n') append('\n')
                position = end
            }
        }
        append(text, position, text.length)
    }

    /**
     * Адреса серверов из текста конфига или файла `proxy-providers`: ключ `server` в блочном
     * и строчном YAML и хост ссылок вида `vless://…@host:443`. Список ссылок в base64
     * сначала раскрываем — в нём нет ни одного двоеточия. Лишнее (`ntp.server`) спрятать
     * не страшно.
     */
    fun serversIn(config: String): Set<String> {
        val text = if (':' in config) config
        else runCatching { String(Base64.getMimeDecoder().decode(config.trim())) }.getOrDefault("")

        return (SERVER_KEY.findAll(text) + LINK_HOST.findAll(text))
            .map { it.groupValues[1].trim('[', ']') }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Одна регулярка на все серверы, длинные первыми: `a.b.io` не должен съесть кусок `x.a.b.io`. */
    fun serversRegex(servers: Collection<String>): Regex? {
        if (servers.isEmpty()) return null

        val names = servers.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        return Regex("""(?i)(?<![\w.-])(?:$names)(?![\w-]|\.\w)""")
    }

    private val SERVER_KEY = Regex("""(?<![\w-])server["']?\s*:\s*["']?([^\s,"'}\]#]+)""")

    private val LINK_HOST = Regex("""\b[a-z][a-z0-9+.-]*://[^@\s/]+@(\[[0-9a-fA-F:]+]|[^:/?#\s]+)""")

    /** Строка своего mihomo об отказе одного DNS-сервера (`dns/util.go`, `batchExchange`). */
    private val DNS_FAILED = Regex("""\[DNS] (\S+) failed: """)

    private val URL = Regex("""(?i)(https?://[^\s"'<>/?#]+)([/?#](?:<hidden>|[^\s"'<>]+)*)""")

    private val LINK_SERVER = Regex("""(?i)\b((?!https?://)[a-z][a-z0-9+.-]*://[^\s?#"'<>]+@)(\[[0-9a-f:]+]|[^:/?#\s"'<>]+)""")
    private val HYSTERIA_SERVER = Regex("""(?i)\b(hysteria://)(?![^\s?#"'<>]*@)(\[[0-9a-f:]+]|[^:/?#\s"'<>]+)""")

    /**
     * Пароль или UUID перед `@` в ссылке любого протокола: `trojan://пароль@…`, `tuic://uuid:пароль@…`.
     * `/` в нём допускаем: ключ shadowsocks-2022 в base64 его содержит, а кодируют его не все.
     */
    private val LINK_USER = Regex("""(?i)\b([a-z][a-z0-9+.-]*://)[^\s?#"'<>]+@""")

    /** Ссылки, у которых адрес и ключ целиком в base64: `vmess://eyJ…`, `ssr://…`, старый `ss://…`. */
    private val LINK_BODY = Regex("""(?i)\b((?:vmess|ssr|ss)://)[A-Za-z0-9+/=_-]+""")

    private val UUID =
        Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")

    /**
     * Значение секретного поля. `pbk` и `sid` — ключи REALITY в ссылке, `public-key` и
     * `short-id` — они же в конфиге; `obfs-password` и `x-hwid` ловятся по последнему слову.
     * В конфиге (`key: value`) значение идёт до пробела, запятой или скобки, в параметрах
     * ссылки (`key=value`) — ещё и до `&` и `#`: дальше другие параметры и имя узла.
     */
    private val SECRET_FIELD = Regex(
        """(?i)\b(password|secret|token|psk|uuid|private-key|auth-str|username|api-key|hwid|auth|obfsParam""" +
            """|public-key|short-id|pbk|sid)""" +
            """(['"]?[ \t]*[:=][ \t]*)"""
    )

    private val BLOCK_HEADER = Regex("""[|>][1-9+\-]{0,2}[ \t]*(?:#.*)?""")

    /** Перед ключом YAML в строке — только отступ, `- ` и начало его же имени (`obfs-`password). */
    private val YAML_KEY_PREFIX = Regex("""[ \t]*(?:-[ \t]+)*["']?[\w.-]*""")

    private val IPV4 = Regex("""(?<![\w.])\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?![\w.])""")

    private val IPV6 =
        Regex("""(?<![\w:.])[0-9a-fA-F]{0,4}(?::[0-9a-fA-F]{0,4}){2,7}(?![\w:.])""")

    private val TIME = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

    private fun keepIpv4(value: String): Boolean {
        val octets = value.split('.').map { it.toIntOrNull() ?: return true }

        if (octets.any { it > 255 }) return true

        val (a, b) = octets

        return when {
            a == 0 || a == 127 -> true                  // свой и неопределённый
            a == 10 -> true                             // локальная сеть
            a == 192 && b == 168 -> true                // локальная сеть
            a == 172 && b in 16..31 -> true             // локальная сеть
            a == 169 && b == 254 -> true                // link-local
            a == 198 && b in 18..19 -> true             // fake-ip по умолчанию у mihomo
            a >= 224 -> true                            // multicast и broadcast
            else -> false
        }
    }

    private fun keepIpv6(value: String): Boolean {
        // В сообщение попадает время вида `18:54:27` — оно из тех же символов, что адрес.
        if (TIME.matches(value)) return true

        return value.trim(':').all { it == ':' || it == '0' }
    }
}
