package ru.mdview.html

import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlPostProcessorTest {

    private val baseDir: Path = Files.createTempDirectory("md-viewer-html").also {
        Files.write(it.resolve("картинка.png"), byteArrayOf(1, 2, 3))
        Files.write(it.resolve("сосед.md"), ByteArray(0))
    }

    private fun process(html: String) = HtmlPostProcessor(baseDir).process(html)

    private fun bodyOf(html: String) = Jsoup.parseBodyFragment(html).body()

    /**
     * Путь в виде file-ссылки. toUri() на Windows даёт file:/C:/..., а jsoup приводит к
     * file:///C:/... — сравниваем путь ссылки, а не её запись.
     */
    private fun assertFileUrl(actual: String, expected: Path, what: String) {
        assertEquals(expected.toUri().path, URI(actual).path, "$what: $actual")
    }

    @Test
    fun `относительная картинка становится file-ссылкой от папки документа`() {
        val result = process("""<p><img src="картинка.png" alt="x"></p>""")

        val src = bodyOf(result.bodyHtml).selectFirst("img")!!.attr("src")
        assertFileUrl(src, baseDir.resolve("картинка.png"), "картинка")
        assertFalse(bodyOf(result.bodyHtml).selectFirst("img")!!.hasClass("md-missing"))
    }

    @Test
    fun `отсутствующий файл помечается, чтобы его было видно в тексте`() {
        val result = process("""<p><img src="нет.png" alt="x"></p>""")

        assertTrue(bodyOf(result.bodyHtml).selectFirst("img")!!.hasClass("md-missing"))
    }

    @Test
    fun `подпадки и экранированные пробелы в путях разбираются`() {
        Files.createDirectories(baseDir.resolve("вложенная папка"))
        Files.write(baseDir.resolve("вложенная папка").resolve("файл картинка.png"), ByteArray(0))

        val result = process("""<p><img src="вложенная%20папка/файл%20картинка.png"></p>""")

        val img = bodyOf(result.bodyHtml).selectFirst("img")!!
        assertFileUrl(img.attr("src"), baseDir.resolve("вложенная папка").resolve("файл картинка.png"), "картинка в подпадке")
        assertFalse(img.hasClass("md-missing"))
    }

    @Test
    fun `внешняя ссылка уходит наружу и не даёт страницу открыть чужой код`() {
        val result = process("""<p><a href="https://example.com/a">ссылка</a></p>""")

        val link = bodyOf(result.bodyHtml).selectFirst("a")!!
        assertTrue(link.hasClass("md-external"), "класс внешней ссылки: ${link.className()}")
        assertEquals("_blank", link.attr("target"), "внешняя ссылка открывается в новой вкладке")
        assertEquals("noopener noreferrer", link.attr("rel"), "новая вкладка не должна получить доступ к окну")
    }

    @Test
    fun `ссылка на соседний markdown открывается приложением, а не браузером`() {
        val result = process("""<p><a href="сосед.md">сосед</a> <a href="https://example.com">внешняя</a></p>""")

        val docLink = bodyOf(result.bodyHtml).select("a").first { it.text() == "сосед" }
        assertTrue(docLink.hasClass("md-doc-link"), "класс ссылки на документ: ${docLink.className()}")
        assertFalse(docLink.hasClass("md-external"), "ссылку на соседний файл не отправляем в браузер")
        assertFileUrl(docLink.attr("href"), baseDir.resolve("сосед.md"), "ссылка на соседний файл")
    }

    @Test
    fun `якорь внутри страницы остаётся якорем`() {
        val result = process("""<h2 id="раздел">Раздел</h2><p><a href="#раздел">перейти</a></p>""")

        val link = bodyOf(result.bodyHtml).selectFirst("a")!!
        assertEquals("#раздел", link.attr("href"))
        assertFalse(link.hasClass("md-external"))
    }

    @Test
    fun `заголовки получают id, а дубликаты — разные id`() {
        val result = process("<h1>Раздел</h1><h2>Раздел</h2><h2>Ещё раздел</h2>")

        val ids = bodyOf(result.bodyHtml).select("h1, h2").map { it.id() }
        assertEquals(listOf("раздел", "раздел-2", "ещё-раздел"), ids)
    }

    @Test
    fun `свой id заголовка не перезаписывается`() {
        val result = process("""<h2 id="custom">Раздел</h2>""")

        assertEquals("custom", bodyOf(result.bodyHtml).selectFirst("h2")!!.id())
    }

    @Test
    fun `оглавление собирает уровни и ссылки, без ссылки-якоря внутри текста`() {
        val result = process(
            """
            |<h1 id="первый"><a class="md-heading-anchor" href="#первый">Первый</a></h1>
            |<h2 id="второй">Второй <a class="md-heading-anchor" href="#второй">¶</a></h2>
            """.trimMargin(),
        )

        assertEquals(
            listOf(
                ru.mdview.core.TocEntry(1, "Первый", "первый"),
                ru.mdview.core.TocEntry(2, "Второй", "второй"),
            ),
            result.toc,
        )
    }

    @Test
    fun `заголовки из flexmark попадают в оглавление своим текстом`() {
        val html = ru.mdview.core.MarkdownRenderer().renderToHtml("# Первый раздел\n\n## Второй, с запятой\n")

        val rendered = HtmlPostProcessor(baseDir).process(html)

        assertEquals(listOf("Первый раздел", "Второй, с запятой"), rendered.toc.map { it.text })
        assertTrue(rendered.toc.all { it.anchor.isNotBlank() }, "у каждого пункта есть якорь")
    }

    @Test
    fun `счётчики документов считаются по тексту`() {
        val result = process("<h1>Раз</h1><p>два три четыре пять</p>")

        assertEquals(1, result.stats.headings)
        assertEquals(5, result.stats.words)
        assertTrue(result.stats.readingMinutes >= 1)
    }

    @Test
    fun `data-схемы и пустые ссылки не превращаются в пути`() {
        val result = process("""<p><img src="data:image/png;base64,AAA"><a href="">пусто</a></p>""")

        val body = bodyOf(result.bodyHtml)
        assertEquals("data:image/png;base64,AAA", body.selectFirst("img")!!.attr("src"))
        assertEquals("", body.selectFirst("a")!!.attr("href"))
    }
}
