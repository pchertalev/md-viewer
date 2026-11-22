package ru.mdview.platform

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Обёртка над reg.exe.
 *
 * Специальной библиотеки для реестра не берём: операций немного, а свой нативный код
 * или JNA ради нескольких ключей не нужны. Аргументы передаём списком — экранирование
 * кавычек делает ProcessBuilder, поэтому путь с пробелами не разваливается.
 */
object Registry {

    data class Result(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
    }

    fun add(key: String, data: String, value: String? = null, type: String = "REG_SZ"): Result =
        run(
            buildList {
                add("reg"); add("add"); add(key)
                if (value == null) { add("/ve") } else { add("/v"); add(value) }
                add("/t"); add(type); add("/d"); add(data); add("/f")
            },
        )

    fun delete(key: String): Result = run(listOf("reg", "delete", key, "/f"))

    /**
     * Пачку ключей пишем файлом .reg.
     *
     * reg.exe принимает значение с кавычками только через файл: при передаче аргументом
     * кавычки теряются на разборе командной строки Windows, и команда открытия вида
     * "C:\Путь с пробелом\app.exe" "%1" превращается в нерабочую.
     */
    fun importText(text: String): Result {
        val file = Files.createTempFile("mdview-association", ".reg")
        try {
            // BOM + UTF-16LE: так reg.exe читает и кириллицу в пути
            Files.writeString(file, "\uFEFF$text", StandardCharsets.UTF_16LE)
            return run(listOf("reg", "import", file.toString()))
        } finally {
            runCatching { Files.deleteIfExists(file) }
        }
    }

    fun deleteValue(key: String, value: String): Result =
        run(listOf("reg", "delete", key, "/v", value, "/f"))

    fun query(key: String, value: String? = null): Result =
        run(
            buildList {
                add("reg"); add("query"); add(key)
                if (value == null) { add("/ve") } else { add("/v"); add(value) }
            },
        )

    /**
     * Значение ключа из вывода reg.exe.
     *
     * Имя значения в выводе зависит от языка Windows (у значения по умолчанию это
     * «(по умолчанию)» в русской локали), поэтому ищем не его, а тип REG_SZ/REG_NONE —
     * он не переводится, и после него идёт само значение.
     */
    fun readValue(key: String, value: String? = null): String? {
        val result = query(key, value)
        if (!result.ok) return null

        val typeMarker = Regex("REG_\\w+")

        return result.output
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { typeMarker.containsMatchIn(it) }
            ?.let { line -> typeMarker.find(line)?.let { line.substring(it.range.last + 1).trim() } }
            ?.trim('"')
    }

    private fun run(command: List<String>): Result {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            .use { it.readText() }

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return Result(-1, "reg.exe не завершился за 10 секунд")
        }

        return Result(process.exitValue(), output)
    }
}
