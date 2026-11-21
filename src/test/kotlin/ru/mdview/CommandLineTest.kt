package ru.mdview

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommandLineTest {

    @Test
    fun `путь без флагов — открытие файла`() {
        val command = CommandLine.parse(arrayOf("D:\\docs\\README.md"))

        assertIs<CommandLine.Open>(command)
        assertEquals(listOf(Path.of("D:\\docs\\README.md")), command.paths)
        assertTrue(command.singleInstance, "по умолчанию файл открывают в уже запущенном окне")
    }

    @Test
    fun `несколько файлов и флаги в любой последовательности`() {
        val command = CommandLine.parse(arrayOf("--no-single-instance", "a.md", "b.md"))

        assertIs<CommandLine.Open>(command)
        assertEquals(listOf(Path.of("a.md"), Path.of("b.md")), command.paths)
        assertEquals(false, command.singleInstance)
    }

    @Test
    fun `служебные команды не считают пути файлами для открытия`() {
        val command = CommandLine.parse(arrayOf("--register", "--exe", "D:\\app\\MdViewer.exe"))

        assertIs<CommandLine.Association>(command)
        assertEquals(CommandLine.Action.REGISTER, command.action)
        assertEquals(Path.of("D:\\app\\MdViewer.exe"), command.exe)
    }

    @Test
    fun `без пути после --exe возвращают справку вместо падения`() {
        val command = CommandLine.parse(arrayOf("--register", "--exe"))

        assertIs<CommandLine.Help>(command)
        assertTrue(command.text.contains("--exe"))
    }

    @Test
    fun `справка по любому из вариантов флага`() {
        listOf("--help", "-h", "/?").forEach { flag ->
            assertIs<CommandLine.Help>(CommandLine.parse(arrayOf(flag)), "флаг $flag")
        }
    }

    @Test
    fun `пустые аргументы — открыть окно без файла`() {
        val command = CommandLine.parse(emptyArray())

        assertIs<CommandLine.Open>(command)
        assertEquals(emptyList(), command.paths)
    }
}
