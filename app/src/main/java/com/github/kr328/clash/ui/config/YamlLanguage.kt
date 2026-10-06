package com.github.kr328.clash.ui.config

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * YAML для редактора конфига (D-124): своя подсветка (`yamlLine`) вместо грамматики
 * TextMate — та тянет движок регулярных выражений и таблицы кодировок, мегабайты ради того
 * же результата. Отступ — два пробела; после «ключ:» строка начинается глубже.
 */
internal class YamlLanguage : EmptyLanguage() {
    private val analyzer = YamlAnalyzer()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun useTab(): Boolean = false

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        var i = column - 1

        while (i >= 0 && content.charAt(line, i) == ' ') i--

        return if (i >= 0 && content.charAt(line, i) == ':') 2 else 0
    }

    override fun destroy() {
        analyzer.destroy()
    }
}

/** Разметка заново целиком на каждую правку: построчный разбор — миллисекунды и на большом конфиге. */
private class YamlAnalyzer : SimpleAnalyzeManager<Unit>() {
    override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
        val builder = MappedSpans.Builder()
        var line = 0
        var start = 0

        while (!delegate.isCancelled) {
            val end = text.indexOf("\n", start).let { if (it < 0) text.length else it }
            val tokens = yamlLine(text.substring(start, end))

            if (tokens.isEmpty() || tokens.first().first > 0) builder.add(line, SpanFactory.obtain(0, PLAIN))
            tokens.forEach { (column, token) -> builder.add(line, SpanFactory.obtain(column, style(token))) }

            if (end >= text.length) break

            start = end + 1
            line++
        }

        builder.determine(line)

        return Styles(builder.build())
    }

    private fun style(token: YamlToken): Long = when (token) {
        YamlToken.Plain -> PLAIN
        YamlToken.Key -> TextStyle.makeStyle(EditorColorScheme.KEYWORD)
        YamlToken.Text -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
        YamlToken.Number -> TextStyle.makeStyle(EditorColorScheme.ANNOTATION)
        YamlToken.Comment -> TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
        YamlToken.Mark -> TextStyle.makeStyle(EditorColorScheme.OPERATOR)
        YamlToken.Anchor -> TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
    }

    private companion object {
        val PLAIN = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
    }
}
