package ru.mdview.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentLoaderTest {

    private val loader = DocumentLoader()

    @Test
    fun `читает файл с BOM и кириллицей`() = withTempFile("﻿# Заголовок\n\nТекст") { path ->
        val document = loader.load(path)

        assertEquals("Заголовок", document.title)
        assertEquals("# Заголовок", document.markdown.lines().first())
    }

    @Test
    fun `заголовок берётся из первой ATX-строки`() = withTempFile("""

        ---

        ## Второй уровень ###
        """) { path ->
        assertEquals("Второй уровень", loader.load(path).title)
    }

    @Test
    fun `без заголовка титулом становится первая строка, а без неё — имя файла`() {
        withTempFile("просто текст без заголовка") { path ->
            assertEquals("просто текст без заголовка", loader.load(path).title)
        }

        withTempFile("---\n\n   ") { path ->
            assertEquals(path.fileName.toString().removeSuffix(".md"), loader.load(path).title)
        }
    }

    @Test
    fun `путь всегда абсолютный — от него резолвятся относительные ссылки`() = withTempFile("# t") { path ->
        assertEquals(path.toAbsolutePath().normalize(), loader.load(path).path)
    }

    @Test
    fun `не-UTF8 читается с внятной ошибкой, а не мусором`() {
        val bytes = "Заголовок в Windows-1251".toByteArray(charset("windows-1251"))

        Files.createTempDirectory("md-viewer-test").resolve("cp1251.md").also { Files.write(it, bytes) }.let { path ->
            assertFailsWith<DocumentDecodingException> { loader.load(path) }
        }
    }

    @Test
    fun `отсутствующий файл — отдельная ошибка`() {
        val missing = Files.createTempDirectory("md-viewer-test").resolve("нет-такого.md")

        assertFailsWith<DocumentNotFoundException> { loader.load(missing) }
    }

    @Test
    fun `пустой файл не роняет загрузку`() = withTempFile("") { path ->
        assertEquals(path.fileName.toString().removeSuffix(".md"), loader.load(path).title)
    }

    @Test
    fun `кодировку передаёт UTF-8, а не кодировку системы`() = withTempFile("обычный текст") { path ->
        assertEquals("обычный текст", String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim())
    }

    private fun withTempFile(content: String, block: (java.nio.file.Path) -> Unit) {
        val dir = Files.createTempDirectory("md-viewer-test")
        val file = dir.resolve("документ.md")
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))

        block(file)
    }
}
