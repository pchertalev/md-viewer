package ru.mdview

import java.nio.file.Path

/**
 * Разбор аргументов запуска.
 *
 * Проводник передаёт путь к файлу первым аргументом, всё остальное — служебные команды.
 * Флаги и пути можно смешивать в любой последовательности.
 */
sealed interface CommandLine {

    data class Open(val paths: List<Path>, val singleInstance: Boolean) : CommandLine

    data class Association(val action: Action, val exe: Path?) : CommandLine

    data class Help(val text: String) : CommandLine

    enum class Action { REGISTER, UNREGISTER, STATUS }

    companion object {

        fun parse(args: Array<String>): CommandLine {
            val paths = mutableListOf<Path>()
            var action: Action? = null
            var exe: Path? = null
            var singleInstance = true

            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--help", "-h", "/?" -> return Help(USAGE)

                    "--register" -> action = Action.REGISTER
                    "--unregister" -> action = Action.UNREGISTER
                    "--status" -> action = Action.STATUS
                    "--no-single-instance" -> singleInstance = false

                    "--exe" -> {
                        val value = args.getOrNull(++index)
                        if (value == null) {
                            return Help("Не указан путь после --exe.\n\n$USAGE")
                        }
                        exe = Path.of(value)
                    }

                    else -> paths.add(Path.of(arg))
                }

                index++
            }

            return when (action) {
                null -> Open(paths, singleInstance)
                else -> Association(action, exe)
            }
        }

        private val USAGE = """
            |md-viewer — просмотр markdown-файлов
            |
            |  md-viewer [файл.md ...]        открыть файл (или несколько) во вкладках
            |  md-viewer --register           прописать .md за собой (HKCU, без прав администратора)
            |  md-viewer --unregister         убрать записи, которые добавили
            |  md-viewer --status             показать, кто сейчас открывает .md
            |
            |опции:
            |  --exe <путь>                   исполняемый файл для реестра (по умолчанию — текущий)
            |  --no-single-instance           не пытаться открыть файл в уже запущенном окне
            |
            |После --register Explorer может оставить прежний выбор пользователя: ключ UserChoice
            |защищён и удаляется только из настроек Windows.
            |
        """.trimMargin()
    }
}
