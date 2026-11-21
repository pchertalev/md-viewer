package ru.mdview.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.mdview.core.DocumentStats
import ru.mdview.core.RenderedDocument
import ru.mdview.core.TocEntry
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.max

/**
 * Наводит порядок в HTML, который выдал flexmark.
 *
 * Каждый шаг — отдельная маленькая правка, и все они покрыты тестами отдельно.
 * Через SPI flexmark это делать можно, но каждый шаг превратился бы в отдельный
 * NodeRenderer с обходом узлов; здесь же мы работаем с уже готовым HTML.
 *
 * [baseDir] — папка открытого файла. Относительные ссылки и картинки считаются
 * относительно неё: при запуске двойным кликом процесс стартует из папки exe, и
 * относительные пути из файла иначе развалились бы.
 */
class HtmlPostProcessor(
    private val baseDir: Path,
) {

    fun process(bodyHtml: String): RenderedDocument {
        val body = Jsoup.parseBodyFragment(bodyHtml).body()

        ensureHeadingIds(body)
        val toc = collectToc(body)
        absolutizeResources(body)
        markDocLinks(body)

        return RenderedDocument(
            bodyHtml = body.html(),
            toc = toc,
            stats = statsOf(body),
        )
    }

    /**
     * Заголовки без id остаются без id: flexmark генерирует их сам, но дубликаты и
     * заголовки только с форматированием он может пропустить, а без id на заголовок
     * нельзя сослаться из оглавления.
     */
    private fun ensureHeadingIds(body: Element) {
        val used = mutableSetOf<String>()

        for (heading in body.select(HEADING_SELECTOR)) {
            val existing = heading.id()
            if (existing.isNotBlank()) {
                used += existing
                continue
            }

            val id = uniqueSlug(headingHeadingText(heading), used)
            heading.id(id)
        }
    }

    private fun collectToc(body: Element): List<TocEntry> =
        body.select(HEADING_SELECTOR)
            .filter { it.id().isNotBlank() }
            .map { heading ->
                TocEntry(
                    level = heading.tagName().removePrefix("h").toInt(),
                    text = headingHeadingText(heading),
                    anchor = heading.id(),
                )
            }

    /**
     * Текст заголовка для оглавления. flexmark делает весь заголовок ссылкой-якорем,
     * поэтому ссылку разбираем, но оставляем её текст; отдельно встречающийся значок
     * ссылки (¶) убираем, иначе в сайдбаре после каждого пункта висит символ.
     */
    private fun headingHeadingText(heading: Element): String {
        val clone = heading.clone()

        for (anchor in clone.select("a.md-heading-anchor")) {
            if (anchor.text().trim().length <= 1) anchor.remove() else anchor.unwrap()
        }

        return clone.text().trim()
    }

    /**
     * Относительные пути превращаем в file:// от папки документа, чтобы Chromium
     * подхватил картинки и вложения. Внешние оставляем как есть, но помечаем — по классу
     * их видно и в CSS, и в обработчике кликов.
     */
    private fun absolutizeResources(body: Element) {
        for (element in body.select("a[href], img[src], source[src], video[src], iframe[src]")) {
            val attribute = if (element.hasAttr("href")) "href" else "src"
            val raw = element.attr(attribute).trim()

            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("data:")) continue

            if (hasScheme(raw)) {
                // file:// уже абсолютная ссылка: соседний документ откроем мы сами, наружу не гоним
                if (element.tagName() == "a" && !raw.startsWith("file:")) {
                    element.addClass("md-external")
                    element.attr("target", "_blank")
                    element.attr("rel", "noopener noreferrer")
                }
                continue
            }

            val target = resolveLocal(raw) ?: continue
            element.attr(attribute, target.toUri().toString())

            if (!Files.exists(target)) element.addClass("md-missing")
        }
    }

    /** Ссылки на соседние markdown-файлы помечаем: их перехватывает приложение, а не браузер. */
    private fun markDocLinks(body: Element) {
        for (link in body.select("a[href]")) {
            val href = link.attr("href")
            if (href.startsWith("file://") && DOC_EXTENSIONS.any { href.endsWith(it, ignoreCase = true) }) {
                link.addClass("md-doc-link")
            }
        }
    }

    private fun resolveLocal(raw: String): Path? {
        val pathPart = raw.substringBefore('#').substringBefore('?')
        if (pathPart.isBlank()) return null

        val decoded = runCatching {
            URLDecoder.decode(URI(raw).rawPath ?: pathPart, StandardCharsets.UTF_8)
        }.getOrElse {
            runCatching { URLDecoder.decode(pathPart, StandardCharsets.UTF_8) }.getOrDefault(pathPart)
        }

        return runCatching { baseDir.resolve(decoded).normalize() }.getOrNull()
    }

    /**
     * Ссылка с протоколом (http:, mailto:, file:). Проверяем начало строки: Regex.matches
     * сравнивает её целиком, и https://example.com/page выглядел бы обычной относительной
     * ссылкой — такую приложение отправило бы в соседнюю папку вместо браузера.
     */
    private fun hasScheme(value: String): Boolean = SCHEME.containsMatchIn(value)

    private fun statsOf(body: Element): DocumentStats {
        val words = body.text()
            .split(WORD_SEPARATOR)
            .count { it.isNotBlank() }

        return DocumentStats(
            headings = body.select(HEADING_SELECTOR).size,
            words = words,
            readingMinutes = max(1, ceil(words / WORDS_PER_MINUTE.toDouble()).toInt()),
        )
    }

    private fun uniqueSlug(text: String, used: MutableSet<String>): String {
        val base = text
            .lowercase()
            .replace(WHITESPACE, "-")
            .replace(NOT_SLUG, "")
            .replace(MULTI_DASH, "-")
            .trim('-', ' ')
            .ifBlank { "section" }

        var candidate = base
        var index = 2
        while (!used.add(candidate)) {
            candidate = "$base-$index"
            index++
        }
        return candidate
    }

    private companion object {
        const val HEADING_SELECTOR = "h1, h2, h3, h4, h5, h6"
        const val WORDS_PER_MINUTE = 200
        val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        val NOT_SLUG = Regex("[^a-z0-9\u0400-\u04ff-]", RegexOption.IGNORE_CASE)
        val MULTI_DASH = Regex("-{2,}")
        val WHITESPACE = Regex("\\s+")
        val WORD_SEPARATOR = Regex("\\s+")
        val DOC_EXTENSIONS = listOf(".md", ".markdown", ".mdown", ".mkd")
    }
}
