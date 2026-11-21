package ru.mdview.core

import java.nio.file.Path

/** Файла нет по указанному пути. */
class DocumentNotFoundException(val path: Path) :
    RuntimeException("Файл не найден: $path")

/** Вместо файла передали директорию. */
class NotAFileException(val path: Path) :
    RuntimeException("Это не файл: $path")

/**
 * Файл больше порога. Рендерим в браузере внутри JVM, поэтому большой файл — это
 * и раздувшийся HTML на диске, и подвешенное окно; порог поднимают точечно, когда
 * такая необходимость реально появится.
 */
class DocumentTooLargeException(
    val path: Path,
    val sizeBytes: Long,
    val limitBytes: Long,
) : RuntimeException("Файл слишком большой: $path (${sizeBytes / 1024} КБ, предел ${limitBytes / 1024} КБ)")

/** Файл не читается как UTF-8. */
class DocumentDecodingException(val path: Path, cause: Throwable) :
    RuntimeException("Не читается как UTF-8: $path", cause)
