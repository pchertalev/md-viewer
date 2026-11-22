package ru.mdview.core

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Разбор markdown в HTML по спецификации CommonMark плюс расширения GitHub.
 *
 * Здесь только разбор. Относительные ссылки, оглавление и оформление наводят
 * [ru.mdview.html.HtmlPostProcessor] через Jsoup: шаги правки HTML хочется видеть
 * по отдельности и покрывать тестами по отдельности, а SPI flexmark для этого
 * слишком громоздкий.
 *
 * Классы flexmark на выходе — свои, css пишем под них (см. resources/mdview/mdview.css).
 */
class MarkdownRenderer {

    private val parser: Parser
    private val renderer: HtmlRenderer

    init {
        val options = MutableDataSet().apply {
            set(
                Parser.EXTENSIONS,
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    TaskListExtension.create(),
                    AutolinkExtension.create(),
                    FootnoteExtension.create(),
                    // id у заголовков: на них ссылается оглавление и ссылки вида #раздел
                    AnchorLinkExtension.create(),
                ),
            )

            set(HtmlRenderer.SOFT_BREAK, "\n")
            set(HtmlRenderer.GENERATE_HEADER_ID, true)
            set(HtmlRenderer.RENDER_HEADER_ID, true)
            set(TablesExtension.CLASS_NAME, "md-table")
            set(AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "md-heading-anchor")
            // Значок якоря отдельным символом после текста, а не ссылка на весь заголовок:
            // по умолчанию flexmark оборачивает в <a> весь текст заголовка, и он красится
            // в цвет ссылки, а наш стиль значка (полупрозрачный до наведения) прячет заголовок целиком.
            set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false)
            // id у заголовка уже есть; второй тот же id на значке ломает переход по #раздел
            set(AnchorLinkExtension.ANCHORLINKS_SET_ID, false)
        }

        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

    fun renderToHtml(markdown: String): String =
        renderer.render(parser.parse(markdown))
}
