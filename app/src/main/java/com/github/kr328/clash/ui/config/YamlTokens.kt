package com.github.kr328.clash.ui.config

/** Что за кусок строки YAML — для подсветки (D-124). */
internal enum class YamlToken { Plain, Key, Text, Number, Comment, Mark, Anchor }

/**
 * Разметка одной строки YAML: пары «с какой колонки — что», по возрастанию колонок. Не
 * полный разбор YAML, а то, что нужно глазу в конфиге mihomo: ключи, строки, числа и
 * `true/false/null`, комментарии, знаки (`-`, `:`, скобки, запятые), якоря и теги. Узлы
 * провайдеры часто пишут одной строкой `- {name: …, type: …}` — ключи внутри скобок тоже.
 */
internal fun yamlLine(line: String): List<Pair<Int, YamlToken>> {
    val out = ArrayList<Pair<Int, YamlToken>>()
    var i = 0
    val n = line.length

    fun mark(at: Int, token: YamlToken) {
        if (out.isEmpty() || out.last().second != token) out += at to token
    }

    // Отступ и маркеры списка.
    while (i < n && line[i] == ' ') i++
    while (i < n && line[i] == '-' && (i + 1 == n || line[i + 1] == ' ')) {
        mark(i, YamlToken.Mark)
        i++
        while (i < n && line[i] == ' ') i++
    }

    var expectKey = true
    var depth = 0

    while (i < n) {
        val c = line[i]

        when {
            c == '#' && (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t') -> {
                mark(i, YamlToken.Comment)
                return out
            }

            c == '"' || c == '\'' -> {
                val end = closing(line, i, c)
                val key = keyAt(line, end + 1) && expectKey

                mark(i, if (key) YamlToken.Key else YamlToken.Text)
                i = end + 1

                if (key) {
                    mark(i, YamlToken.Mark)
                    i++
                    expectKey = false
                }
            }

            c == '{' || c == '[' || c == '}' || c == ']' || c == ',' -> {
                mark(i, YamlToken.Mark)
                if (c == '{' || c == '[') depth++
                if (c == '}' || c == ']') depth = (depth - 1).coerceAtLeast(0)
                // В скобках после «{» и «,» снова может идти ключ.
                expectKey = c == '{' || c == ','
                i++
            }

            c == ' ' || c == '\t' -> i++

            else -> {
                val end = scalarEnd(line, i, depth > 0)
                val word = line.substring(i, end)

                when {
                    expectKey && keyAt(line, end) -> {
                        mark(i, YamlToken.Key)
                        mark(end, YamlToken.Mark)
                        i = end + 1
                        expectKey = false
                        continue
                    }

                    c == '&' || c == '*' || c == '!' -> mark(i, YamlToken.Anchor)
                    word.isNumberLike() -> mark(i, YamlToken.Number)
                    else -> mark(i, YamlToken.Plain)
                }

                // Хоть на знак вперёд: у «: » не после ключа («[:», «a: b: c») значение пустое,
                // и разбор стоял на месте — поток подсветки крутился вечно на целом ядре.
                i = maxOf(end, i + 1)
                expectKey = false
            }
        }
    }

    return out
}

/** Двоеточие сразу после ключа, дальше пробел или конец строки. */
private fun keyAt(line: String, at: Int): Boolean =
    at < line.length && line[at] == ':' && (at + 1 == line.length || line[at + 1] == ' ' || line[at + 1] == '\t')

private fun closing(line: String, from: Int, quote: Char): Int {
    var i = from + 1

    while (i < line.length) {
        if (line[i] == '\\' && quote == '"') {
            i += 2
            continue
        }
        if (line[i] == quote) {
            // В одинарных кавычках '' — это кавычка внутри строки.
            if (quote == '\'' && i + 1 < line.length && line[i + 1] == '\'') {
                i += 2
                continue
            }

            return i
        }
        i++
    }

    return line.length - 1
}

/** Конец голого значения: до «: », « #», а в скобках — ещё до запятой и закрывающей скобки. */
private fun scalarEnd(line: String, from: Int, inFlow: Boolean): Int {
    var i = from

    while (i < line.length) {
        val c = line[i]

        if (keyAt(line, i)) return i
        if (c == ' ' && i + 1 < line.length && line[i + 1] == '#') return i
        if (inFlow && (c == ',' || c == '}' || c == ']')) return i
        i++
    }

    return line.length
}

private fun String.isNumberLike(): Boolean {
    val t = trim()

    return t == "true" || t == "false" || t == "null" || t == "~" ||
        t.toDoubleOrNull() != null || (t.startsWith("0x") && t.drop(2).toLongOrNull(16) != null)
}
