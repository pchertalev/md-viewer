package ru.mdview.app

import ru.mdview.core.DocumentLoader
import ru.mdview.core.MarkdownDocument
import ru.mdview.core.MarkdownRenderer
import ru.mdview.core.RenderedDocument
import ru.mdview.core.ViewOptions
import ru.mdview.html.HtmlPostProcessor
import ru.mdview.html.PageWriter
import java.nio.file.Path

/**
 * Полный путь «файл на диске → страница в браузере».
 *
 * Композиция собрана в одном месте, чтобы окно не знало ни про парсер, ни про постобработку:
 * у него на руках только [RenderedPage] с файлом страницы, который надо загрузить.
 */
class DocumentPipeline(
    private val loader: DocumentLoader = DocumentLoader(),
    private val renderer: MarkdownRenderer = MarkdownRenderer(),
    private val pageWriter: PageWriter = PageWriter(),
) {

    fun open(path: Path): MarkdownDocument = loader.load(path)

    fun render(document: MarkdownDocument, options: ViewOptions): RenderedPage {
        val bodyHtml = renderer.renderToHtml(document.markdown)
        val rendered = HtmlPostProcessor(document.directory).process(bodyHtml)
        val pageFile = pageWriter.write(document, rendered, options)

        return RenderedPage(document, pageFile, rendered)
    }
}

/** Результат рендера: файл страницы для браузера плюс оглавление и счётчики для интерфейса. */
data class RenderedPage(
    val document: MarkdownDocument,
    val pageFile: Path,
    val rendered: RenderedDocument,
)
