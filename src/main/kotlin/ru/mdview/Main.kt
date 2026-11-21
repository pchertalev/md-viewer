package ru.mdview

import ru.mdview.app.AppPaths
import ru.mdview.app.SettingsStore
import ru.mdview.core.ViewOptions
import ru.mdview.ipc.SingleInstance
import ru.mdview.platform.FileAssociation
import ru.mdview.ui.MainWindow
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Точка входа.
 *
 * Один и тот же запуск обслуживает и двойной клик по файлу (проводник передаёт путь
 * аргументом), и служебные команды реестра. Поэтому аргументы разбираем вручную:
 * библиотека для CLI здесь дороже, чем десять строк разбора.
 */
fun main(args: Array<String>) {
    when (val command = CommandLine.parse(args)) {
        is CommandLine.Help -> out.println(command.text)

        is CommandLine.Association -> System.exit(runAssociation(command))

        is CommandLine.Open -> runViewer(command)
    }
}

/**
 * Консоль Windows живёт в своей кодовой странице (обычно cp866), а приложение пишет
 * в UTF-8 — иначе вывод служебных команд нечитаемый. Печатаем в кодировке консоли.
 */
private val out: PrintStream by lazy {
    val consoleCharset: Charset = sequenceOf(System.getProperty("stdout.encoding"), System.getProperty("sun.stdout.encoding"))
        .filterNotNull()
        .mapNotNull { code -> runCatching { Charset.forName(code) }.getOrNull() }
        .firstOrNull()
        ?: Charsets.UTF_8

    PrintStream(java.io.BufferedOutputStream(FileOutputStream(FileDescriptor.out)), true, consoleCharset)
}

private fun runAssociation(command: CommandLine.Association): Int {
    AppPaths.ensureDirectories()

    val association = FileAssociation(command.exe ?: resolveLauncher())

    return when (command.action) {
        CommandLine.Action.REGISTER -> {
            val report = association.register()

            report.steps.forEach { out.println("  готово: $it") }
            report.failures.forEach { out.println("  не вышло: $it") }
            report.hints.forEach { out.println("  → $it") }

            if (report.failures.isEmpty()) 0 else 1
        }

        CommandLine.Action.UNREGISTER -> {
            val report = association.unregister()

            report.steps.forEach { out.println("  готово: $it") }
            report.failures.forEach { out.println("  не вышло: $it") }

            0
        }

        CommandLine.Action.STATUS -> {
            val status = association.status()

            out.println("класс приложения:    ${if (status.registered) association.progId else "не зарегистрирован"}")
            out.println("HKCU\\.md:            ${status.classDefault ?: "не задано"}")
            out.println("выбор пользователя:  ${status.userChoice ?: "не задано"}")

            0
        }
    }
}

private fun runViewer(command: CommandLine.Open) {
    if (!isJcefAvailable()) {
        out.println("Приложение запущено на рантайме без модуля jcef: ${System.getProperty("java.home")}")
        out.println("Нужен JetBrains Runtime с JCEF — он указан в gradle.properties и используется в собранном образе.")

        System.exit(2)
    }

    AppPaths.ensureDirectories()

    val store = SettingsStore()
    val settings = runCatching { store.load() }.getOrDefault(SettingsStore.Settings(ViewOptions()))

    var handle: SingleInstance.InstanceHandle? = null

    if (command.singleInstance) {
        val instance = SingleInstance()

        when (val acquisition = instance.acquire()) {
            is SingleInstance.Acquisition.Forwarded -> {
                if (instance.send(command.paths)) {
                    out.println("Файл открыт в уже запущенном окне md-viewer.")
                    return
                }
                // живой экземпляр успел умереть между проверкой и отправкой
                handle = instance.acquirePrimary()
            }

            is SingleInstance.Acquisition.Primary -> handle = acquisition.handle
        }
    }

    val instanceHandle = handle

    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

        val frame = MainWindow(
            settingsStore = store,
            initial = settings,
            associationHint = associationHint(),
        )

        instanceHandle?.start { paths -> frame.openFiles(paths) }
        frame.openFiles(command.paths)
        frame.isVisible = true
    }
}

/** Модуль jcef есть только в JBR: без него окно осталось бы пустым, разумнее сказать об этом сразу. */
private fun isJcefAvailable(): Boolean = runCatching { Class.forName("org.cef.CefApp") }.isSuccess

/** Подсказка на начальной странице: кто сейчас открывает .md и что сделать, если не мы. */
private fun associationHint(): String {
    val status = FileAssociation().status()
    val handler = status.userChoice ?: status.classDefault

    return when {
        handler == FileAssociation.PROG_ID -> "`.md` открывается этим приложением."

        handler == null ->
            "Ассоциация не настроена: `MdViewer.exe --register`."

        else ->
            "Сейчас `.md` открывает `$handler`. Переключить на md-viewer: правый клик по файлу → " +
                "«Открыть с помощью» → «Выбрать другое приложение» → MdViewer → «Всегда использовать»."
    }
}

/**
 * Исполняемый файл, который проводник сможет запустить двойным кликом.
 *
 * В собранном образе это MdViewer.exe — его и пишем в реестр. При запуске из gradle
 * процессом командой считается java.exe, а ассоциировать java.exe с .md нельзя: проводник
 * передаст файл первым аргументом, и java примет его за имя главного класса. Поэтому для
 * разработки генерируем cmd-обёртку с готовым classpath.
 */
private fun resolveLauncher(): Path {
    val running = ProcessHandle.current().info().command().orElse(null)?.let { Path.of(it) }
    val isJava = running == null || running.fileName.toString().startsWith("java", ignoreCase = true)

    if (!isJava) return running!!

    val javaHome = Path.of(System.getProperty("java.home"))
    val javaw = javaHome.resolve("bin").resolve("javaw.exe")

    val launcher = AppPaths.root.resolve("MdViewer.cmd")
    Files.createDirectories(launcher.parent)

    Files.writeString(
        launcher,
        """
            |@echo off
            |"$javaw" --add-modules jcef -cp "${System.getProperty("java.class.path")}" ru.mdview.MainKt %%*
            |
        """.trimMargin(),
    )

    out.println("Собранного образа нет — ассоциация ведёт на обёртку: $launcher")

    return launcher
}
