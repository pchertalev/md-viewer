package ru.mdview.app

import ru.mdview.core.MarkdownDocument
import java.nio.file.Path

/**
 * Стартовая страница, когда файл не передан.
 *
 * Сделана markdown'ом, а не отдельной HTML-разметкой: рендер тот же, что у обычных
 * документов, поэтому ссылки на недавние файлы работают как обычные ссылки на .md и
 * оформление не приходится поддерживать в двух местах.
 */
object WelcomePage {

    private val pagePath: Path = AppPaths.cacheDir.resolve("_start.md")

    fun document(recent: List<Path>, associationHint: String?): MarkdownDocument {
        val markdown = buildString {
            appendLine("# md-viewer")
            appendLine()
            appendLine("Просмотр markdown-файлов: двойной клик по `.md` открывает файл здесь.")
            appendLine()

            val existing = recent.filter { it.toFile().exists() }
            if (existing.isNotEmpty()) {
                appendLine("## Недавние файлы")
                appendLine()
                existing.forEach { path ->
                    appendLine("- [${path.fileName}](${path.toUri()}) — `$path`")
                }
                appendLine()
            }

            appendLine("## Клавиши")
            appendLine()
            appendLine("| Клавиша | Действие |")
            appendLine("|---|---|")
            appendLine("| `Ctrl+O` | открыть файл |")
            appendLine("| `Ctrl+W` | закрыть вкладку |")
            appendLine("| `T` | содержание |")
            appendLine("| `D` | тема |")
            appendLine("| `+` / `-` / `0` | масштаб текста |")
            appendLine("| `R` | перечитать файл с диска |")
            appendLine("| `E` | открыть исходник в редакторе |")
            appendLine("| `B` | открыть эту страницу в браузере |")
            appendLine()

            if (associationHint != null) {
                appendLine("## Ассоциация .md")
                appendLine()
                appendLine(associationHint)
                appendLine()
            }
        }

        return MarkdownDocument(
            path = pagePath,
            markdown = markdown,
            title = "Начальная страница",
        )
    }
}
