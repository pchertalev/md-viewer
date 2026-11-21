package ru.mdview.core

import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Читает markdown с диска и определяет заголовок документа.
 *
 * Чтение отделено от рендера по двум причинам: тесты рендера не должны зависеть от файловой
 * системы, и путь файла нужен рендеру как база для относительных ссылок — а откуда открыли
 * файл, процесс узнаёт только здесь.
 */
class DocumentLoader(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    fun load(path: Path): MarkdownDocument {
        val absolute = path.toAbsolutePath().normalize()

        if (!Files.exists(absolute)) throw DocumentNotFoundException(absolute)
        if (Files.isDirectory(absolute)) throw NotAFileException(absolute)

        val bytes = Files.readAllBytes(absolute)
        if (bytes.size.toLong() > maxBytes) {
            throw DocumentTooLargeException(absolute, bytes.size.toLong(), maxBytes)
        }

        val markdown = decodeUtf8(absolute, bytes)

        return MarkdownDocument(
            path = absolute,
            markdown = markdown,
            title = titleFromMarkdown(markdown).ifBlank { nameFromPath(absolute) },
        )
    }

    private fun decodeUtf8(path: Path, bytes: ByteArray): String {
        val withoutBom = if (bytes.size >= 3 &&
            bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(withoutBom)).toString()
        } catch (e: CharacterCodingException) {
            throw DocumentDecodingException(path, e)
        }
    }

    /**
     * Заголовок для вкладки и заголовка окна: первый ATX-заголовок (`# Название`),
     * иначе первая непустая строка, иначе имя файла.
     */
    private fun titleFromMarkdown(document: String): String {
        var fallback: String? = null

        for (rawLine in document.lineSequence()) {
            val line = rawLine.trim()

            val heading = HEADING.matchEntire(line)
            if (heading != null) return heading.groupValues[1].trim()

            if (fallback == null && line.isNotEmpty() && line != "---") fallback = line
        }

        return fallback?.let { if (it.length > TITLE_FALLBACK_LENGTH) it.take(TITLE_FALLBACK_LENGTH) + "…" else it }
            ?: ""
    }

    private fun nameFromPath(path: Path): String =
        path.fileName.toString().removeSuffix(".md").removeSuffix(".markdown")

    companion object {
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
        private const val TITLE_FALLBACK_LENGTH = 60
        private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val HEADING = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?\\s*$")
    }
}
