package ru.mdview.platform

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
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

    fun deleteValue(key: String, value: String): Result =
        run(listOf("reg", "delete", key, "/v", value, "/f"))

    fun query(key: String, value: String? = null): Result =
        run(
            buildList {
                add("reg"); add("query"); add(key)
                if (value == null) { add("/ve") } else { add("/v"); add(value) }
            },
        )

    fun readValue(key: String, value: String? = null): String? {
        val result = query(key, value)
        if (!result.ok) return null

        return result.output
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(valueName(value)) }
            ?.split(Regex("\\s{2,}"))
            ?.lastOrNull()
            ?.trim()
    }

    private fun valueName(value: String?): String = value ?: "(Default)"

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
