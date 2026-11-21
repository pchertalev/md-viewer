package ru.mdview.app

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** Чтение ресурсов из jar: тексты держим в памяти, бинарные копируем на диск. */
object Resources {

    private val texts = ConcurrentHashMap<String, String>()

    fun read(path: String): String =
        texts.getOrPut(path) {
            open(path).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        }

    fun copyTo(path: String, target: Path) {
        Files.createDirectories(target.parent)
        open(path).use { stream ->
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun open(path: String): InputStream {
        val stream = Resources::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("Ресурс не найден в jar: $path")
        return stream
    }
}
