package ru.mdview.core

/** Пункт оглавления. [anchor] — id заголовка, на который ссылается ссылка в сайдбаре. */
data class TocEntry(
    val level: Int,
    val text: String,
    val anchor: String,
)

/** Счётчики по документу — показываем в подвале страницы. */
data class DocumentStats(
    val headings: Int,
    val words: Int,
    val readingMinutes: Int,
)

/**
 * Результат разбора markdown и постобработки HTML.
 *
 * [bodyHtml] — содержимое без обёртки страницы: его подставляет [ru.mdview.html.PageComposer],
 * а оглавление и счётчики отдаём отдельно, потому что они живут в сайдбаре и подвале, а не в тексте.
 */
data class RenderedDocument(
    val bodyHtml: String,
    val toc: List<TocEntry>,
    val stats: DocumentStats,
)
