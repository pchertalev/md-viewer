package ru.mdview.ui

import ru.mdview.app.DocumentPipeline
import ru.mdview.app.SettingsStore
import ru.mdview.app.WelcomePage
import ru.mdview.core.MarkdownDocument
import ru.mdview.core.Theme
import ru.mdview.core.ViewOptions
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JTabbedPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

/**
 * Окно приложения: вкладки документов плюс меню и перетаскивание файлов.
 *
 * Всё оформление самого документа рисует страница (см. resources/mdview), поэтому здесь
 * только каркас. Пункты меню, которые меняют вид, не перерисовывают страницу, а зовут
 * ту же функцию в javascript: реализация темы, масштаба и содержания остаётся одной.
 */
class MainWindow(
    private val pipeline: DocumentPipeline = DocumentPipeline(),
    private val settingsStore: SettingsStore = SettingsStore(),
    initial: SettingsStore.Settings = SettingsStore.Settings(ViewOptions()),
    private val associationHint: String? = null,
) : JFrame("md-viewer"), PageActions {

    private val tabStrip = JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT)
    private val tabs = mutableListOf<DocumentTab>()

    private var view: ViewOptions = initial.view
    private var recent: List<Path> = initial.recent

    // движок поднимается лениво: команды реестра (--register) работают без Chromium
    private val workspaceLazy: Lazy<BrowserWorkspace> = lazy { BrowserWorkspace(this) }

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        contentPane.layout = BorderLayout()
        contentPane.add(tabStrip, BorderLayout.CENTER)
        jMenuBar = buildMenu()
        allowDropOn(tabStrip)

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) = shutdown()
        })

        preferredSize = Dimension(1180, 820)
        pack()
        setLocationRelativeTo(null)
    }

    // ---------- открытие ----------

    fun openFiles(paths: List<Path>) {
        SwingUtilities.invokeLater {
            paths.forEach { openPath(it) }
            if (tabs.isEmpty()) showWelcome()
        }
    }

    private fun openPath(path: Path) {
        try {
            focusOrAdd(pipeline.open(path))
        } catch (e: RuntimeException) {
            showError(path, e)
        }
    }

    private fun focusOrAdd(document: MarkdownDocument) {
        val existing = tabs.indexOfFirst { !it.isWelcome && it.document.path == document.path }

        if (existing >= 0) {
            tabStrip.selectedIndex = existing
            refresh(tabs[existing])
            return
        }

        val page = pipeline.render(document, view)
        val tab = DocumentTab(document, workspaceLazy.value.createBrowser(page.pageFile.toUri().toString()), page.pageFile)

        addTab(tab)
        replaceWelcomeTab(tab)
        rememberRecent(document.path)
    }

    private fun showWelcome() {
        val document = WelcomePage.document(recent, associationHint)
        val page = pipeline.render(document, view)
        addTab(DocumentTab(document, workspaceLazy.value.createBrowser(page.pageFile.toUri().toString()), page.pageFile, isWelcome = true))
    }

    /** Начальную страницу держим только пока нечего показывать. */
    private fun replaceWelcomeTab(newTab: DocumentTab) {
        val welcome = tabs.firstOrNull { it.isWelcome && it !== newTab } ?: return
        closeTab(tabs.indexOf(welcome))
    }

    private fun addTab(tab: DocumentTab) {
        tabs.add(tab)
        tabStrip.addTab(tab.tabTitle, tab.panel)
        tabStrip.selectedIndex = tabs.size - 1
        allowDropOn(tab.panel)
    }

    private fun closeCurrentTab() {
        if (tabStrip.selectedIndex >= 0) closeTab(tabStrip.selectedIndex)
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return

        val tab = tabs.removeAt(index)
        tabStrip.remove(index)
        runCatching { tab.browser.close(true) }

        if (tabs.isEmpty()) showWelcome()
    }

    private fun refresh(tab: DocumentTab) {
        try {
            val document = if (tab.isWelcome) {
                WelcomePage.document(recent, associationHint)
            } else {
                pipeline.open(tab.document.path)
            }

            val page = pipeline.render(document, view)
            tab.document = document
            tab.loadPage(page.pageFile)

            val index = tabs.indexOf(tab)
            if (index >= 0) tabStrip.setTitleAt(index, tab.tabTitle)
        } catch (e: RuntimeException) {
            showError(tab.document.path, e)
        }
    }

    // ---------- PageActions: то, о чём просит страница ----------

    override fun openDocument(path: Path) = openFiles(listOf(path))

    override fun reloadCurrent() {
        SwingUtilities.invokeLater { currentTab()?.let(::refresh) }
    }

    override fun openInEditor(path: String) = withDesktop("открыть в редакторе") { desktop ->
        desktop.edit(Path.of(path).toFile())
    }

    override fun openInExternalBrowser() {
        val tab = currentTab() ?: return
        withDesktop("открыть в браузере") { desktop -> desktop.browse(tab.pageFile.toUri()) }
    }

    override fun openExternalUrl(url: String) = withDesktop("открыть ссылку") { desktop ->
        desktop.browse(URI(url))
    }

    // страница уже применила изменение сама — здесь только запоминаем, без перезагрузки

    override fun persistTheme(theme: Theme) = updateSettings { it.copy(theme = theme) }

    override fun persistFontScale(scale: Double) = updateSettings { it.copy(fontScale = scale) }

    override fun persistShowToc(show: Boolean) = updateSettings { it.copy(showToc = show) }

    override fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // ---------- меню и клавиши ----------

    private fun buildMenu(): JMenuBar {
        val menu = JMenuBar()

        menu.add(
            JMenu("Файл").apply {
                add(item("Открыть…  Ctrl+O", "control O") { chooseFile() })
                add(item("Перечитать файл  R") { currentTab()?.let { refresh(it) } })
                addSeparator()
                add(item("Закрыть вкладку  Ctrl+W", "control W") { closeCurrentTab() })
                add(item("Выход  Ctrl+Q", "control Q") { shutdown() })
            },
        )

        menu.add(
            JMenu("Вид").apply {
                add(item("Содержание  T") { invokePage("toc") })
                add(item("Тема  D") { invokePage("theme") })
                addSeparator()
                add(item("Крупнее  +") { invokePage("font-plus") })
                add(item("Мельче  −") { invokePage("font-minus") })
                add(item("Обычный размер  0") { invokePage("font-reset") })
            },
        )

        menu.add(
            JMenu("Документ").apply {
                add(item("Открыть исходник в редакторе  E") { currentTab()?.let { openInEditor(it.document.path.toString()) } })
                add(item("Открыть в браузере  B") { openInExternalBrowser() })
                add(item("Показать в проводнике  P") { revealInExplorer() })
            },
        )

        menu.add(
            JMenu("Справка").apply {
                add(item("О программе") { showAbout() })
            },
        )

        return menu
    }

    private fun item(text: String, acceleratorKey: String? = null, action: () -> Unit): JMenuItem {
        val menuItem = JMenuItem(text)

        if (acceleratorKey != null) {
            menuItem.accelerator = KeyStroke.getKeyStroke(acceleratorKey)
        }

        menuItem.addActionListener { action() }

        return menuItem
    }

    /**
     * Файл, брошенный в окно.
     *
     * Обработчик вешается и на панель вкладок, и на панель каждой вкладки: компонент
     * Chromium — тяжёлый AWT-объект, и свинговые перетаскивания до него не доходят.
     */
    private fun allowDropOn(component: java.awt.Component) {
        DropTarget(component, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                val files = runCatching {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>?
                }.getOrNull()

                event.dropComplete(files != null)

                files?.mapNotNull { (it as? java.io.File)?.toPath() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { openFiles(it) }
            }
        })
    }

    private fun chooseFile() {
        val start = recent.lastOrNull()?.parent?.toFile() ?: Path.of(System.getProperty("user.home")).toFile()
        val chooser = JFileChooser(start)
        chooser.fileFilter = FileNameExtensionFilter("Markdown (*.md, *.markdown)", "md", "markdown", "mkd", "mdown")
        chooser.isMultiSelectionEnabled = true

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return

        openFiles(chooser.selectedFiles.map { it.toPath() })
    }

    private fun invokePage(action: String) {
        currentTab()?.browser?.executeJavaScript("window.mdview && mdview.invoke('$action')", "", 0)
    }

    private fun revealInExplorer() {
        val tab = currentTab() ?: return
        if (tab.isWelcome) return

        withDesktop("показать в проводнике") { desktop ->
            desktop.open(tab.document.path.parent.toFile())
        }
    }

    // ---------- служебное ----------

    private fun currentTab(): DocumentTab? =
        tabStrip.selectedIndex.takeIf { it >= 0 }?.let { tabs.getOrNull(it) }

    private fun updateSettings(change: (ViewOptions) -> ViewOptions) {
        view = change(view)
        saveSettings()
    }

    private fun rememberRecent(path: Path) {
        recent = (listOf(path) + recent.filter { it != path }).take(MAX_RECENT)
        saveSettings()
    }

    private fun saveSettings() {
        runCatching { settingsStore.save(SettingsStore.Settings(view, recent)) }
    }

    private fun withDesktop(what: String, action: (Desktop) -> Unit) {
        try {
            val desktop = Desktop.getDesktop()
            action(desktop)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Не удалось $what: ${e.message}", "md-viewer", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun showError(path: Path, e: Exception) {
        JOptionPane.showMessageDialog(
            this,
            "${e.message}\n\n$path",
            "md-viewer",
            JOptionPane.WARNING_MESSAGE,
        )
    }

    private fun showAbout() {
        JOptionPane.showMessageDialog(
            this,
            """
            md-viewer — просмотр markdown.
            Движок: JCEF (Chromium) внутри JetBrains Runtime.
            Разбор: flexmark (CommonMark + GitHub-расширения).
            """.trimIndent(),
            "О программе",
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    private fun shutdown() {
        tabs.forEach { tab -> runCatching { tab.browser.close(true) } }
        tabs.clear()
        tabStrip.removeAll()

        if (workspaceLazy.isInitialized()) {
            runCatching { workspaceLazy.value.dispose() }
        }

        dispose()
        // процессы Chromium держат JVM в живых, поэтому выходим явно
        exitProcess(0)
    }

    private companion object {
        const val MAX_RECENT = 12
    }
}
