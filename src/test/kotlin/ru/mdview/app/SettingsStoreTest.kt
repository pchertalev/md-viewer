package ru.mdview.app

import ru.mdview.core.Theme
import ru.mdview.core.ViewOptions
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {

    private val tempFile: Path = Files.createTempDirectory("md-viewer-settings").resolve("settings.properties")

    @Test
    fun `настройки переживают перезапись`() {
        val store = SettingsStore(tempFile)

        store.save(
            SettingsStore.Settings(
                view = ViewOptions(theme = Theme.DARK, fontScale = 1.2, maxWidthCh = 100, showToc = false),
                recent = listOf(Path.of("D:/a.md"), Path.of("D:/б вложенная/б.md")),
            ),
        )

        val loaded = store.load()

        assertEquals(Theme.DARK, loaded.view.theme)
        assertEquals(1.2, loaded.view.fontScale)
        assertEquals(100, loaded.view.maxWidthCh)
        assertEquals(false, loaded.view.showToc)
        assertEquals(listOf(Path.of("D:/a.md"), Path.of("D:/б вложенная/б.md")), loaded.recent)
    }

    @Test
    fun `пути с переносом и двоеточием не ломают файл настроек`() {
        val store = SettingsStore(tempFile)
        val path = Path.of("D:/папка/файл с пробелом.md")

        store.save(SettingsStore.Settings(ViewOptions(), listOf(path)))

        assertEquals(listOf(path), store.load().recent)
    }

    @Test
    fun `без файла настроек берутся значения по умолчанию`() {
        val settings = SettingsStore(Files.createTempDirectory("md-viewer-settings").resolve("нет.properties")).load()

        assertEquals(ViewOptions(), settings.view)
        assertEquals(emptyList(), settings.recent)
    }

    @Test
    fun `битые значения не роняют загрузку`() {
        Files.write(tempFile, "view.fontScale = не число\nview.theme = ЧТО-ТО\n".toByteArray(StandardCharsets.UTF_8))

        val settings = SettingsStore(tempFile).load()

        assertEquals(1.0, settings.view.fontScale)
        assertEquals(Theme.AUTO, settings.view.theme)
    }
}
