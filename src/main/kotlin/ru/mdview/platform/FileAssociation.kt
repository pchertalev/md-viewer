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

        val quoted = "\"${exe}\""

        step("класс приложения $progId", Registry.add(progRoot, "Markdown Viewer"))
        step("иконка", Registry.add("$progRoot\\DefaultIcon", "${exe},0"))
        step("команда открытия", Registry.add("$progRoot\\shell\\open\\command", "$quoted \"%1\""))
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
    private val classKey get() = """HKCU\Software\Classes\.$extension"""
    private val appPathsKey get() = """HKCU\Software\Classes\AppPaths\${exe.fileName}"""
    private val openWithKey get() = """HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.$extension\OpenWithProgids"""
    private val userChoiceKey get() = """HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.$extension\UserChoice"""

    companion object {
        const val PROG_ID = "mdview.MdViewer"
        const val EXTENSION = "md"
    }
}
