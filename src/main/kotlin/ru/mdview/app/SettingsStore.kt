package ru.mdview.app

import ru.mdview.core.Theme
import ru.mdview.core.ViewOptions
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Настройки приложения: вид просмотра и последние открытые файлы.
 *
 * Обычный properties-файл: набор настроек маленький, JSON-библиотека ради десяти
 * ключей не нужна, а файл остаётся читаемым — настройки правят и руками.
 */
class SettingsStore(
    private val file: Path = AppPaths.settingsFile,
) {

    fun load(): Settings {
        val properties = Properties()
        if (Files.exists(file)) {
            Files.newBufferedReader(file).use { properties.load(it) }
        }

        val view = ViewOptions(
            theme = Theme.fromId(properties.getProperty(KEY_THEME)),
            fontScale = properties.getProperty(KEY_FONT_SCALE)?.toDoubleOrNull() ?: 1.0,
            maxWidthCh = properties.getProperty(KEY_MAX_WIDTH)?.toIntOrNull() ?: ViewOptions.FULL_WIDTH,
            showToc = properties.getProperty(KEY_TOC, "true").toBoolean(),
        )

        return Settings(view = view, recent = readRecent(properties))
    }

    fun save(settings: Settings) {
        val properties = Properties().apply {
            setProperty(KEY_THEME, settings.view.theme.name)
            setProperty(KEY_FONT_SCALE, settings.view.fontScale.toString())
            setProperty(KEY_MAX_WIDTH, settings.view.maxWidthCh.toString())
            setProperty(KEY_TOC, settings.view.showToc.toString())

            settings.recent.forEachIndexed { index, path ->
                setProperty("$KEY_RECENT.$index", path.toString())
            }
        }

        Files.createDirectories(file.parent)
        Files.newBufferedWriter(file).use { writer ->
            properties.store(writer, "md-viewer")
        }
    }

    private fun readRecent(properties: Properties): List<Path> =
        (0 until MAX_RECENT)
            .mapNotNull { properties.getProperty("$KEY_RECENT.$it") }
            .map { Path.of(it) }

    data class Settings(
        val view: ViewOptions,
        val recent: List<Path> = emptyList(),
    )

    private companion object {
        const val KEY_THEME = "view.theme"
        const val KEY_FONT_SCALE = "view.fontScale"
        const val KEY_MAX_WIDTH = "view.maxWidth"
        const val KEY_TOC = "view.toc"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 20
    }
}
