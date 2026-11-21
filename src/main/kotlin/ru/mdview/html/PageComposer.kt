package ru.mdview.html

import ru.mdview.app.Resources
import ru.mdview.core.MarkdownDocument
import ru.mdview.core.RenderedDocument
import ru.mdview.core.TocEntry
import ru.mdview.core.ViewOptions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Собирает готовую страницу из шаблона, тела документа и оглавления.
 *
 * Шаблон — ресурс mdview/page.html, а не строка в коде: вёрстку правят чаще, чем логику,
 * и в файле её видно целиком. Подстановка простая и буквальная — @@ТОКЕН@@, без шаблонизатора:
 * в теле markdown могут встречаться фигурные скобки, а @@-маркеры там не встречаются.
 */
class PageComposer {

    fun compose(
        document: MarkdownDocument,
        rendered: RenderedDocument,
        options: ViewOptions,
    ): String =
        Resources.read(PageAssets.resourcePath(PAGE_TEMPLATE))
            .replace(TOKEN_TITLE, escapeHtml(document.title))
            .replace(TOKEN_THEME, options.theme.name.lowercase())
            .replace(TOKEN_FONT_SCALE, options.fontScale.toString())
            .replace(TOKEN_MAX_WIDTH, "${options.maxWidthCh}ch")
            .replace(TOKEN_LAYOUT_CLASS, if (options.showToc) "" else "md-no-toc")
            .replace(TOKEN_TOC, tocHtml(rendered.toc))
            .replace(TOKEN_BODY, rendered.bodyHtml)
            .replace(TOKEN_PATH, escapeHtml(document.path.toString()))
            .replace(TOKEN_STATS, statsText(rendered))
            .replace(TOKEN_GENERATED, LocalDateTime.now().format(TIMESTAMP))

    /**
     * Оглавление из плоского списка заголовков. Уровень может прыгать (h2 → h5),
     * поэтому вложенность считаем по стеку открытых уровней, а не по чётной вложенности.
     */
    private fun tocHtml(entries: List<TocEntry>): String {
        if (entries.isEmpty()) return """<p class="md-toc-empty">Заголовков нет</p>"""

        val html = StringBuilder("<ul class=\"md-toc-list\">")
        val openLevels = ArrayDeque<Int>()

        for (entry in entries) {
            while (openLevels.isNotEmpty() && openLevels.last() > entry.level) {
                html.append("</li></ul>")
                openLevels.removeLast()
            }

            if (openLevels.isEmpty() || openLevels.last() < entry.level) {
                if (openLevels.isNotEmpty()) html.append("<ul class=\"md-toc-list md-toc-sub\">")
                openLevels.add(entry.level)
            } else {
                html.append("</li>")
            }

            html
                .append("<li class=\"md-toc-item md-toc-level-").append(entry.level).append("\">")
                .append("<a href=\"#").append(entry.anchor).append("\">")
                .append(escapeHtml(entry.text))
                .append("</a>")
        }

        while (openLevels.isNotEmpty()) {
            html.append("</li></ul>")
            openLevels.removeLast()
        }

        return html.toString()
    }

    private fun statsText(rendered: RenderedDocument): String {
        val stats = rendered.stats
        val parts = buildList {
            if (stats.headings > 0) add("${stats.headings} заголовков")
            add("${stats.words} слов")
            add("≈${stats.readingMinutes} мин чтения")
        }
        return parts.joinToString(" · ")
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private companion object {
        const val PAGE_TEMPLATE = "page.html"

        const val TOKEN_TITLE = "@@TITLE@@"
        const val TOKEN_THEME = "@@THEME@@"
        const val TOKEN_FONT_SCALE = "@@FONT_SCALE@@"
        const val TOKEN_MAX_WIDTH = "@@MAX_WIDTH@@"
        const val TOKEN_LAYOUT_CLASS = "@@LAYOUT_CLASS@@"
        const val TOKEN_TOC = "@@TOC@@"
        const val TOKEN_BODY = "@@BODY@@"
        const val TOKEN_PATH = "@@PATH@@"
        const val TOKEN_STATS = "@@STATS@@"
        const val TOKEN_GENERATED = "@@GENERATED@@"

        val TIMESTAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
}
