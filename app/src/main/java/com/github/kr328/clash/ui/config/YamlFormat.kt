package com.github.kr328.clash.ui.config

/**
 * «Выровнять» конфиг (D-124): отступы — по два пробела на уровень, табы в отступах — в
 * пробелы, хвостовые пробелы и лишние пустые строки — прочь, в конце — перевод строки.
 * Порядок, ключи, значения и комментарии не трогаются: это не пересборка YAML, а уборка.
 *
 * Осторожно там, где отступ — часть содержимого: строки блоков `|` и `>` сдвигаются
 * вместе с родителем, сохраняя свой отступ; строки внутри незакрытых скобок и кавычек
 * не переотступаются. Результат применяется, только если ядро его разбирает.
 */
internal fun formatYaml(text: String): String {
    val out = ArrayList<String>()
    // Исходные отступы открытых уровней: по ним глубина строки.
    val levels = ArrayList<Int>()
    // Блок `|`/`>`: отступ родителя до и после и сдвиг для строк блока.
    var blockParent = -1
    var blockShift = 0
    var flow = 0
    var blank = 0

    for (raw in text.replace("\r\n", "\n").split('\n')) {
        val line = expandTabs(raw).trimEnd()
        val indent = line.length - line.trimStart().length

        if (line.isEmpty()) {
            blank++
            continue
        }

        // Внутри блока `|`/`>` — всё, что глубже родителя, как есть, только со сдвигом.
        if (blockParent >= 0 && indent > blockParent) {
            flushBlank(out, blank)
            blank = 0
            out += " ".repeat((indent + blockShift).coerceAtLeast(0)) + line.trimStart()
            continue
        }
        blockParent = -1

        flushBlank(out, blank)
        blank = 0

        // Продолжение скобок `[…]`/`{…}` с прошлых строк — отступ не значит ничего, не трогаем.
        if (flow > 0) {
            out += line
            flow = (flow + bracketBalance(line)).coerceAtLeast(0)
            continue
        }

        val content = line.trimStart()

        // Комментарий — на уровне ближайшего открытого, без изменения уровней.
        if (content.startsWith("#")) {
            val depth = levels.count { it < indent }.coerceAtMost(levels.size)
            out += " ".repeat(depth * INDENT) + content
            continue
        }

        while (levels.isNotEmpty() && levels.last() > indent) levels.removeAt(levels.lastIndex)
        if (levels.isEmpty() || levels.last() < indent) levels += indent

        val target = (levels.size - 1) * INDENT

        out += " ".repeat(target) + content

        flow = bracketBalance(content).coerceAtLeast(0)

        if (BLOCK.containsMatchIn(content)) {
            blockParent = indent
            blockShift = target - indent
        }
    }

    // Исходные пробелы в конце файла не переносим, но файл заканчивается переводом строки.
    return out.joinToString("\n") + "\n"
}

/** Больше одной пустой строки подряд — лишнее. */
private fun flushBlank(out: MutableList<String>, blank: Int) {
    if (blank > 0 && out.isNotEmpty()) out += ""
}

/** Табы в отступе — по два пробела: YAML табов в отступах не разрешает вовсе. */
private fun expandTabs(line: String): String {
    val indent = line.takeWhile { it == ' ' || it == '\t' }

    return if ('\t' !in indent) line
    else indent.replace("\t", " ".repeat(INDENT)) + line.drop(indent.length)
}

/** Сколько скобок `[`/`{` осталось открыто в строке, без учёта кавычек и комментария. */
private fun bracketBalance(line: String): Int {
    var balance = 0
    var quote: Char? = null
    var i = 0

    while (i < line.length) {
        val c = line[i]

        when {
            quote != null -> if (c == quote) quote = null
            c == '"' || c == '\'' -> quote = c
            c == '#' && (i == 0 || line[i - 1] == ' ') -> return balance
            c == '[' || c == '{' -> balance++
            c == ']' || c == '}' -> balance--
        }
        i++
    }

    return balance
}

private const val INDENT = 2

/** Значение — блок: `key: |`, `key: >-`, `- |2`, с комментарием или без. */
private val BLOCK = Regex("(^|:\\s|^-\\s)\\s*[|>][-+0-9]*\\s*(#.*)?$")

/**
 * Разделы конфига — ключи верхнего уровня с блоком под ними и номера их строк с нуля (D-124):
 * по ним переход в длинном конфиге. Одиночные настройки (`mode: rule`, `log-level: info`)
 * разделом не считаются — они одним пунктом с пустым ключом, у первой из них.
 */
internal fun sections(text: String): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    var settings = false

    text.lineSequence().forEachIndexed { i, line ->
        val key = topKey(line) ?: return@forEachIndexed
        val value = line.substringAfter(':').substringBefore(" #").trim()

        if (value.isEmpty() || value[0] in "[{&!|>") {
            out += key to i
        } else if (!settings) {
            settings = true
            out += "" to i
        }
    }

    return out
}

/** Ключ верхнего уровня (`dns:`, `"rules":`) или `null`. */
private fun topKey(line: String): String? {
    if (line.isEmpty() || line[0].isWhitespace() || line[0] in "#-[]{}") return null

    return TOP_KEY.find(line)?.groupValues?.get(2)
}

private val TOP_KEY = Regex("""^(["']?)([^"':#]+)\1:(\s|$)""")
