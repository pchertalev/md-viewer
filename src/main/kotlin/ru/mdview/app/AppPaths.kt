package ru.mdview.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Где приложение хранит свои файлы: настройки, кэш страниц, метку запущенного экземпляра.
 *
 * Берём LOCALAPPDATA, а не папку exe: при запуске из проводника писать рядом с exe
 * нельзя (программы часто лежат в Program Files без прав на запись), и профиль пользователя
 * переживает переустановку приложения.
 */
object AppPaths {

    val root: Path = resolveRoot()

    val cacheDir: Path = root.resolve("cache")

    val settingsFile: Path = root.resolve("settings.properties")

    val instanceFile: Path = root.resolve("instance.json")

    fun ensureDirectories() {
        Files.createDirectories(root)
        Files.createDirectories(cacheDir)
    }

    private fun resolveRoot(): Path {
        val appData = System.getenv("LOCALAPPDATA")
        val base = if (!appData.isNullOrBlank()) Paths.get(appData) else Paths.get(System.getProperty("user.home"), ".local", "share")
        return base.resolve("md-viewer")
    }
}
