package ru.mdview.html

/**
 * Ресурсы страницы: стили, скрипты, подсветка кода.
 *
 * Не встраиваем их внутрь каждой страницы — страниц в кэше десятки, а содержимое
 * одинаковое. Кладём их один раз рядом со страницами в кэш, и Chromium кеширует
 * по обычным относительным ссылкам.
 */
object PageAssets {

    /** Путь ресурса в jar → имя файла в кэше. Порядок фиксированный: так проще читать <head> страницы. */
    val FILES: List<String> = listOf(
        "mdview.css",
        "mdview.js",
        "vendor/highlight.min.js",
        "vendor/github.min.css",
        "vendor/github-dark.min.css",
    )

    private const val RESOURCE_ROOT = "mdview"

    fun resourcePath(fileName: String): String = "$RESOURCE_ROOT/$fileName"
}
