package ru.mdview.platform

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Прописывает приложение как обработчик .md в реестре.
 *
 * Пишем в HKCU, а не в HKLM: не нужны права администратора, и настройка живёт в профиле
 * пользователя, который открыл файл.
 *
 * Один момент, который нельзя обойти из приложения: Explorer хранит выбор пользователя
 * в ключе FileExts\<расширение>\UserChoice, и этот ключ защищён — удалить его может только
 * сам пользователь (или утилита, которая сначала меняет права). Поэтому register()
 * делает всё, что можно сделать программно, и честно говорит, что осталось сделать руками.
 */
class FileAssociation(
    private val exe: Path = Path.of(PROG_ID),
    val progId: String = PROG_ID,
    private val extension: String = EXTENSION,
) {

    data class Report(
        val progId: String,
        val exe: Path,
        val steps: List<String>,
        val failures: List<String>,
        val userChoiceCleared: Boolean,
        val hints: List<String>,
    )

    /** Что сейчас реально открывает .md — полезно для диагностики после register(). */
    data class Status(
        val classDefault: String?,
        val userChoice: String?,
        val registered: Boolean,
    )

    fun register(): Report {
        val steps = mutableListOf<String>()
        val failures = mutableListOf<String>()

        fun step(description: String, result: Registry.Result) {
            if (result.ok) steps += description else failures += "$description: ${result.output.trim()}"
        }

        step("класс приложения $progId", Registry.importText(progIdRegistry()))
        step("подпись в меню «Открыть с помощью»", Registry.add(openWithKey, data = "", value = progId, type = "REG_NONE"))
        step("путь к приложению (AppPaths)", Registry.add(appPathsKey, exe.toString()))
        step("расширение .$extension → $progId", Registry.add(classKey, progId))

        val userChoiceCleared = Registry.delete(userChoiceKey).ok

        notifyShell()

        val hints = buildList {
            if (!userChoiceCleared) {
                add(
                    "Explorer держит прежний выбор в UserChoice — удалить его из приложения нельзя. " +
                        "Правый клик по .md-файлу → «Открыть с помощью» → «Выбрать другое приложение» → " +
                        "MdViewer → «Всегда использовать».",
                )
            }
            add("После смены выбора может потребоваться перезапустить Explorer или выйти из системы.")
        }

        return Report(
            progId = progId,
            exe = exe,
            steps = steps,
            failures = failures,
            userChoiceCleared = userChoiceCleared,
            hints = hints,
        )
    }

    /** Убирает всё, что добавили. Прежний обработчик не восстанавливаем — его выбирает пользователь. */
    fun unregister(): Report {
        val steps = mutableListOf<String>()
        val failures = mutableListOf<String>()

        fun step(description: String, result: Registry.Result) {
            if (result.ok) steps += description else failures += "$description: ${result.output.trim()}"
        }

        if (status().classDefault == progId) {
            step("удаление .$extension из HKCU", Registry.delete(classKey))
        }

        step("меню «Открыть с помощью»", Registry.deleteValue(openWithKey, progId))
        step("AppPaths", Registry.delete(appPathsKey))
        step("класс $progId", Registry.delete(progRoot))

        notifyShell()

        return Report(progId, exe, steps, failures, userChoiceCleared = false, hints = emptyList())
    }

    fun status(): Status = Status(
        classDefault = Registry.readValue(classKey),
        userChoice = Registry.readValue(userChoiceKey, "ProgId"),
        registered = Registry.query(progRoot).ok,
    )

    /** Сообщает Explorer'у, что ассоциации изменились, иначе он крутит старый кэш. */
    private fun notifyShell() {
        runCatching {
            ProcessBuilder("ie4uinit.exe", "-show").start().waitFor(5, TimeUnit.SECONDS)
        }
    }

    private val progRoot get() = """HKCU\Software\Classes\$progId"""

    /** Тот же корень, что и progRoot, но с полным именем улья — его требует формат .reg. */
    private val progRootHive get() = """HKEY_CURRENT_USER\Software\Classes\$progId"""

    /**
     * Класс приложения: подпись, иконка и команда открытия — одним файлом .reg.
     *
     * Через reg.exe аргументом эти кавычки не передать: разбор командной строки Windows
     * их съедает, и путь с пробелом в команде открытия потом расваливается на части.
     */
    private fun progIdRegistry(): String = buildString {
        appendLine("Windows Registry Editor Version 5.00")
        appendLine()
        appendLine("[$progRootHive]")
        appendLine("@=${regValue("Markdown Viewer")}")
        appendLine()
        appendLine("""[$progRootHive\DefaultIcon]""")
        appendLine("@=${regValue("$exe,0")}")
        appendLine()
        appendLine("""[$progRootHive\shell\open\command]""")
        appendLine("@=${regValue(""""$exe" "%1"""")}")
    }

    /** Значение по правилам .reg: кавычки и обратные слеши внутри удваиваются/экранируются. */
    private fun regValue(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private val classKey get() = """HKCU\Software\Classes\.$extension"""
    private val appPathsKey get() = """HKCU\Software\Classes\AppPaths\${exe.fileName}"""
    private val openWithKey get() = """HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.$extension\OpenWithProgids"""
    private val userChoiceKey get() = """HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.$extension\UserChoice"""

    companion object {
        const val PROG_ID = "mdview.MdViewer"
        const val EXTENSION = "md"
    }
}
