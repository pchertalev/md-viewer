package ru.mdview.html

import ru.mdview.app.AppPaths
import ru.mdview.app.Resources
import ru.mdview.core.MarkdownDocument
import ru.mdview.core.RenderedDocument
import ru.mdview.core.ViewOptions
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.security.MessageDigest

/**
 * Кладёт страницу и ресурсы в кэш и возвращает путь для загрузки в браузер.
 *
 * Страница именно файл, а не loadHTML: страница на about:blank не может подтянуть
 * картинки по file:// — браузер запрещает доступ к локальным файлам из чужого источника.
 * Файл в кэше лежит по той же схеме file://, поэтому относительные вложения работают.
 *
 * Имя страницы детерминированное (хэш от файла и настроек): повторное открытие того же
 * документа не создаёт новый файл, и Chromium переиспользует свой кэш.
 */
class PageWriter(
    private val cacheDir: Path = AppPaths.cacheDir,
    private val composer: PageComposer = PageComposer(),
    private val keepPages: Int = 40,
) {

    @Synchronized
    fun write(
        document: MarkdownDocument,
        rendered: RenderedDocument,
        options: ViewOptions,
    ): Path {
        Files.createDirectories(cacheDir)
        syncAssets()

        val page = composer.compose(document, rendered, options)
        val target = cacheDir.resolve(pageName(document, options))

        Files.writeString(target, page)
        prune()

        return target
    }

    private fun syncAssets() {
        for (fileName in PageAssets.FILES) {
            val target = cacheDir.resolve(fileName)
            if (!Files.exists(target)) {
                Resources.copyTo(PageAssets.resourcePath(fileName), target)
            }
        }
    }

    /** Старые страницы чистим: за месяц их накапливаются сотни, а держим мы только последние. */
    private fun prune() {
        val pages = Files.list(cacheDir).use { stream -> stream.collect(Collectors.toList()) }
            .filter { it.fileName.toString().startsWith(PAGE_PREFIX) }
            .sortedByDescending { lastModified(it) }

        pages.drop(keepPages).forEach { page -> runCatching { Files.deleteIfExists(page) } }
    }

    private fun lastModified(path: Path): Long =
        runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)

    private fun pageName(document: MarkdownDocument, options: ViewOptions): String {
        val fingerprint = buildString {
            append(document.path)
            append('|').append(document.markdown.length)
            append('|').append(document.markdown.hashCode())
            append('|').append(options.theme)
            append('|').append(options.fontScale)
            append('|').append(options.maxWidthCh)
            append('|').append(options.showToc)
        }

        val hash = MessageDigest.getInstance("SHA-1")
            .digest(fingerprint.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return "$PAGE_PREFIX$hash.html"
    }

    private companion object {
        const val PAGE_PREFIX = "page-"
    }
}
