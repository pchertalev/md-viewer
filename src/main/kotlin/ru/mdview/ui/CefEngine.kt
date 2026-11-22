package ru.mdview.ui

import org.cef.CefApp
import org.cef.CefSettings
import ru.mdview.app.AppPaths
import java.nio.file.Files
import java.nio.file.Path

/**
 * Запуск движка Chromium (JCEF).
 *
 * Движок один на приложение: браузеры вкладок делят общий CefApp и общий CefClient.
 *
 * jcef — модуль JetBrains Runtime, рядом с ним лежат jcef.dll и jcef_helper.exe. Отсюда
 * требование к запуску: java.home должен быть JBR с JCEF (см. gradle.properties и README),
 * иначе движок не поднимется.
 */
object CefEngine {

    private var app: CefApp? = null

    @Synchronized
    fun start(): CefApp {
        app?.let { return it }

        if (!CefApp.startup(switches())) {
            throw BrowserStartupException("CefApp.startup вернул false")
        }

        val cachePath = AppPaths.cacheDir.resolve("cef")
        Files.createDirectories(cachePath)

        val settings = CefSettings().apply {
            // Chromium поднимает процессы рендера и видео через jcef_helper.exe. Если путь
            // не указать, он перезапустит тот файл, который поднял приложение: в разработке
            // это java.exe, и вместо процесса рендера получаем «Could not find or load main class».
            helperExecutable()?.let { browser_subprocess_path = it.toString() }

            // песочница изолирует процессы рендера; для локальных страниц она не нужна,
            // а с ней JCEF не находит jcef_helper.exe, когда приложение собрано jpackage
            no_sandbox = true
            cache_path = cachePath.toString()

            // pak-файлы Chromium (resources.pak, locales/*.pak) в JBR лежат в lib, а не рядом
            // с jcef.dll; без этого пути движок стартует с «Could not load resources.pak»
            resourcesDir()?.let { resources_dir_path = it }
            localesDir()?.let { locales_dir_path = it }
        }

        return CefApp.getInstance(arrayOf(), settings).also { app = it }
    }

    @Synchronized
    fun shutdown() {
        app?.dispose()
        app = null
    }

    /** Переключатели CEF можно поддать через -Dmdview.cef.args="..." — например для отладки. */
    private fun switches(): Array<String> =
        System.getProperty("mdview.cef.args", "")
            .split(" ")
            .filter { it.isNotBlank() }
            .toTypedArray()

    /** jcef_helper.exe лежит рядом с java.exe в том же рантайме, на котором мы запущены. */
    private fun helperExecutable(): Path? {
        val name = if (System.getProperty("os.name", "").lowercase().contains("windows")) "jcef_helper.exe" else "jcef_helper"
        val candidate = Path.of(System.getProperty("java.home"), "bin", name)

        return candidate.takeIf { Files.isExecutable(it) }
    }

    private fun resourcesDir(): String? =
        Path.of(System.getProperty("java.home"), "lib")
            .takeIf { Files.exists(it.resolve("resources.pak")) }
            ?.toString()

    private fun localesDir(): String? =
        Path.of(System.getProperty("java.home"), "lib", "locales")
            .takeIf { Files.isDirectory(it) }
            ?.toString()

    class BrowserStartupException(message: String) : RuntimeException("Не удалось запустить браузерный движок: $message")
}
