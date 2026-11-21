package ru.mdview.ipc

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingleInstanceTest {

    private val instanceFile: Path = Files.createTempDirectory("md-viewer-ipc").resolve("instance.json")

    @Test
    fun `первый запуск становится основным`() {
        val instance = SingleInstance(instanceFile)

        assertIs<SingleInstance.Acquisition.Primary>(instance.acquire())
        assertTrue(Files.exists(instanceFile), "метка запущенного экземпляра должна появиться")
    }

    @Test
    fun `второй запуск передаёт пути живому и выходит`() {
        val first = SingleInstance(instanceFile)
        val handle = (first.acquire() as SingleInstance.Acquisition.Primary).handle

        val received = mutableListOf<Path>()
        val latch = CountDownLatch(1)
        handle.start { paths ->
            received += paths
            latch.countDown()
        }

        val second = SingleInstance(instanceFile)

        assertIs<SingleInstance.Acquisition.Forwarded>(second.acquire())
        assertTrue(second.send(listOf(Path.of("D:/один.md"), Path.of("D:/два.md"))))
        assertTrue(latch.await(5, TimeUnit.SECONDS), "окно не получило пути")

        assertEquals(listOf(Path.of("D:/один.md"), Path.of("D:/два.md")), received)

        handle.release()
    }

    @Test
    fun `метка от мёртвого процесса не мешает стать основным`() {
        Files.write(instanceFile, """{"pid":999999,"port":1}""".toByteArray(StandardCharsets.UTF_8))

        assertIs<SingleInstance.Acquisition.Primary>(SingleInstance(instanceFile).acquire())
    }

    @Test
    fun `битая метка тоже не мешает`() {
        Files.write(instanceFile, "не json вообще".toByteArray(StandardCharsets.UTF_8))

        assertIs<SingleInstance.Acquisition.Primary>(SingleInstance(instanceFile).acquire())
    }

    @Test
    fun `отправка живому экземпляру после его остановки не удаётся`() {
        val handle = (SingleInstance(instanceFile).acquire() as SingleInstance.Acquisition.Primary).handle
        handle.release()

        // метку release() стирает, поэтому второй экземпляр становится основным, а не отправляет в пустоту
        val second = SingleInstance(instanceFile)
        assertIs<SingleInstance.Acquisition.Primary>(second.acquire())
    }
}
