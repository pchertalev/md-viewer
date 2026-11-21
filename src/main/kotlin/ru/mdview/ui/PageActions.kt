package ru.mdview.ui

import ru.mdview.core.Theme
import java.nio.file.Path

/**
 * Команды, которые страница просит у приложения.
 *
 * Страница рисует интерфейс сама (тема, масштаб, содержание), но сама она не может
 * прочитать файл с диска, открыть редактор или запомнить настройку — для этого она
 * обращается в приложение через мост. Интерфейс держит эту границу в одном месте.
 */
interface PageActions {

    fun openDocument(path: Path)

    /** Перечитать файл с диска: пригодилось после правки в редакторе. */
    fun reloadCurrent()

    fun openInEditor(path: String)

    fun openInExternalBrowser()

    /** Внешняя ссылка: в окне просмотра её показываем, а в браузер отправляем наружу. */
    fun openExternalUrl(url: String)

    /** Настройки видны пользователю сразу — их меняет страница, приложение только запоминает. */
    fun persistTheme(theme: Theme)

    fun persistFontScale(scale: Double)

    fun persistShowToc(show: Boolean)

    fun copyToClipboard(text: String)
}
