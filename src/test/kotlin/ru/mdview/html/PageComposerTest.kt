package ru.mdview.html

import ru.mdview.core.DocumentStats
import ru.mdview.core.MarkdownDocument
import ru.mdview.core.RenderedDocument
import ru.mdview.core.TocEntry
import ru.mdview.core.ViewOptions
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageComposerTest {

    private val composer = PageComposer()

    private val rendered = RenderedDocument(
        bodyHtml = "<p>привет</p>",
        toc = listOf(TocEntry(1, "Первый", "первый"), TocEntry(2, "Второй", "второй")),
        stats = DocumentStats(headings = 2, words = 10, readingMinutes = 1),
    )

    private fun document(title: String) = MarkdownDocument(Path.of("D:/docs/файл.md"), "", title)

    @Test
    fun `в готовой странице не остаётся неподставленных токенов`() {
        val page = composer.compose(document("Тест"), rendered, ViewOptions())

        assertFalse(Regex("@@[A-Z_]+@@").containsMatchIn(page), "остались токены: ${Regex("@@[A-Z_]+@@").findAll(page).map { it.value }.toList()}")
    }

    @Test
    fun `заголовок и путь экранируются — файл не должен подставить в страницу тег`() {
        val page = composer.compose(document("<script>alert(1)</script>"), rendered, ViewOptions())

        assertFalse(page.contains("<script>alert(1)</script>"))
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test
    fun `без содержания сетка не оставляет пустую колонку`() {
        val withToc = composer.compose(document("Т"), rendered, ViewOptions(showToc = true))
        val withoutToc = composer.compose(document("Т"), rendered, ViewOptions(showToc = false))

        assertTrue(withToc.contains("""class="md-layout """"))
        assertTrue(withoutToc.contains("""class="md-layout md-no-toc""""))
    }

    @Test
    fun `тема, масштаб и ширина уходят в атрибуты html-корня`() {
        val page = composer.compose(
            document("Т"),
            rendered,
            ViewOptions(theme = ru.mdview.core.Theme.DARK, fontScale = 1.3, maxWidthCh = 100),
        )

        assertTrue(page.contains("""data-theme="dark""""))
        assertTrue(page.contains("--max-width:100ch"))
        assertTrue(page.contains("--font-scale:1.3"))
    }

    @Test
    fun `ширина на всё окно — в странице без ограничения колонки`() {
        val page = composer.compose(document("Т"), rendered, ViewOptions(maxWidthCh = ViewOptions.FULL_WIDTH))

        assertTrue(page.contains("--max-width:none"))
    }

    @Test
    fun `оглавление строит вложенный список по уровням`() {
        val page = composer.compose(document("Т"), rendered, ViewOptions())

        assertTrue(page.contains("""<a href="#первый">Первый</a>"""))
        assertTrue(page.contains("md-toc-sub"), "вложенный уровень должен появиться")
    }

    @Test
    fun `заголовок в шапке не дублирует первый заголовок файла`() {
        val withH1 = rendered.copy(toc = listOf(TocEntry(1, "Первый раздел", "первый-раздел")))

        val page = composer.compose(document("Первый раздел"), withH1, ViewOptions())

        assertFalse(page.contains("md-doc-title"), "второй такой же заголовок не рисуем")
        assertTrue(page.contains("<title>Первый раздел</title>"), "в заголовке вкладки он остаётся")
        assertTrue(page.contains("md-doc-path"), "путь и счётчики из шапки не пропадают")
    }

    @Test
    fun `заголовок в шапке остаётся, если в файле своего заголовка нет`() {
        val page = composer.compose(document("файл"), rendered, ViewOptions())

        assertTrue(page.contains("""<h1 class="md-doc-title">файл</h1>"""))
    }
}
