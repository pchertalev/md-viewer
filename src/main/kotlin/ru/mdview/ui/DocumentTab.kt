package ru.mdview.ui

import org.cef.browser.CefBrowser
import ru.mdview.core.MarkdownDocument
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Вкладка: документ, его браузер и панель с компонентом браузера.
 *
 * Компонент браузера — тяжёлый AWT-компонент (окно Chromium), поэтому он лежит в своей
 * панели с BorderLayout: JTabbedPane не умеет вешать тяжёлые компоненты напрямую.
 */
class DocumentTab(
    var document: MarkdownDocument,
    val browser: CefBrowser,
    var pageFile: Path,
    val isWelcome: Boolean = false,
) {

    val panel: JComponent = JPanel(BorderLayout()).apply {
        add(browser.uiComponent as Component, BorderLayout.CENTER)
    }

    val tabTitle: String
        get() = document.title.ifBlank { document.fileName }

    fun loadPage(file: Path) {
        pageFile = file
        browser.loadURL(file.toUri().toString())
    }
}
