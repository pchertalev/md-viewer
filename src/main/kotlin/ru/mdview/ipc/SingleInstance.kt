package ru.mdview.ipc

import ru.mdview.app.AppPaths
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * Держит приложение в одном экземпляре.
 *
 * Второй запуск (двойной клик по файлу) не поднимает вторую JVM: это и секунда ожидания,
 * и второе окно вместо вкладки. Первый экземпляр слушает сокет на loopback и пишет порт
 * в файл-метку; второй читает порт, передаёт пути и сразу выходит.
 *
 * Метка может остаться после падения процесса — поэтому её проверяем по pid, а не только
 * по наличию файла.
 */
class SingleInstance(
    private val instanceFile: Path = AppPaths.instanceFile,
) {

    /** Мы первые и поднимаем окно, либо пути ушли живому экземпляру и выходить можно сразу. */
    sealed interface Acquisition {
        data class Primary(val handle: InstanceHandle) : Acquisition
        data object Forwarded : Acquisition
    }

    fun acquire(): Acquisition {
        val existing = readInstance()

        if (existing != null && isAlive(existing.pid)) {
            return Acquisition.Forwarded
        }

        return Acquisition.Primary(acquirePrimary())
    }

    /** Занимает метку себе. Вызывается и когда метки нет, и когда она осталась от мёртвого процесса. */
    fun acquirePrimary(): InstanceHandle {
        Files.deleteIfExists(instanceFile)

        return try {
            startPrimary()
        } catch (e: IOException) {
            // сокет не поднялся — работаем без синхронизации, хуже от этого не станет
            InstanceHandle.disabled(instanceFile)
        }
    }

    /** Передаёт пути живому экземпляру. false — живого нет, caller поднимает своё окно. */
    fun send(paths: List<Path>): Boolean {
        val existing = readInstance() ?: return false

        return runCatching {
            Socket(InetAddress.getLoopbackAddress(), existing.port).use { socket ->
                socket.getOutputStream().bufferedWriter().use { writer ->
                    paths.forEach { path ->
                        writer.write(path.toString())
                        writer.newLine()
                    }
                    writer.flush()
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun startPrimary(): InstanceHandle {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())

        Files.createDirectories(instanceFile.parent)
        Files.writeString(
            instanceFile,
            """{"pid":${ProcessHandle.current().pid()},"port":${server.localPort}}""",
        )

        return InstanceHandle(server, instanceFile)
    }

    private fun readInstance(): RunningInstance? {
        val content = runCatching { Files.readString(instanceFile) }.getOrNull() ?: return null

        val pid = PID.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val port = PORT.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        return RunningInstance(pid, port)
    }

    private fun isAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private data class RunningInstance(val pid: Long, val port: Int)

    /** Сокет первичного экземпляра: принимает пути от повторных запусков и чистит метку при выходе. */
    class InstanceHandle internal constructor(
        private val server: ServerSocket?,
        private val instanceFile: Path,
    ) {
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "md-viewer-ipc").apply { isDaemon = true }
        }

        fun start(onOpen: (List<Path>) -> Unit) {
            val socket = server ?: return

            executor.execute {
                while (!socket.isClosed) {
                    val received = runCatching {
                        socket.accept().use { connection ->
                            connection.getInputStream().bufferedReader().lineSequence()
                                .filter { it.isNotBlank() }
                                .map(Path::of)
                                .toList()
                        }
                    }.getOrDefault(emptyList())

                    if (received.isNotEmpty()) onOpen(received)
                }
            }
        }

        fun release() {
            runCatching { server?.close() }
            runCatching { Files.deleteIfExists(instanceFile) }
            executor.shutdownNow()
        }

        internal companion object {
            fun disabled(instanceFile: Path) = InstanceHandle(null, instanceFile)
        }
    }

    private companion object {
        val PID = Regex("\"pid\"\\s*:\\s*(\\d+)")
        val PORT = Regex("\"port\"\\s*:\\s*(\\d+)")
    }
}
