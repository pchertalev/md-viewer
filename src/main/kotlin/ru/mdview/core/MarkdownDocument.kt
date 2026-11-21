package ru.mdview.core

import java.nio.file.Path

/**
 * Открытый документ.
 *
 * [path] всегда абсолютный и нормализованный: относительные ссылки и картинки внутри
 * markdown резолвятся от [directory], а не от папки, из которой запустили процесс.
 * При запуске двойным кликом из проводника рабочей папкой считается папка exe, так что
 * полагаться на CWD нельзя.
 */
data class MarkdownDocument(
    val path: Path,
    val markdown: String,
    val title: String,
) {
    val directory: Path
        get() = path.parent

    val fileName: String
        get() = path.fileName.toString()
}
