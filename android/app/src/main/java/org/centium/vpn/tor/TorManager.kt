package org.centium.vpn.tor

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.centium.vpn.data.CentiumConfig

class TorManager(
    private val context: Context,
    private val onBootstrapProgress: (Int, String) -> Unit,
    private val onLog: (String) -> Unit
) {
    private var torProcess: Process? = null
    private val torrcGenerator = TorrcGenerator()
    val controller = TorController()

    val torDataDir: File by lazy {
        File(context.filesDir, "tor_data").apply { if (!exists()) mkdirs() }
    }
    val torrcFile: File by lazy { File(context.filesDir, "centium_torrc") }
    val pidFile: File by lazy { File(context.filesDir, "tor.pid") }
    val logFile: File by lazy { File(context.filesDir, "tor.log") }

    fun isRunning(): Boolean {
        val proc = torProcess
        return if (proc != null) {
            try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        } else {
            isPortBound(9050)
        }
    }

    suspend fun start(config: CentiumConfig): Boolean = withContext(Dispatchers.IO) {
        stop()

        onLog("[Tor] Preparing Tor runtime directory...")
        val torrcContent = torrcGenerator.generateTorrc(
            config = config,
            dataDir = torDataDir,
            pidFile = pidFile,
            logFile = logFile,
            appFilesDir = context.filesDir
        )
        torrcFile.writeText(torrcContent)

        val torBinary = resolveTorBinary()
        if (torBinary == null || !torBinary.exists()) {
            onLog("[Tor] Native Tor binary not found in native library path. Initializing built-in tor service wrapper...")
        }

        val cmd = mutableListOf<String>()
        if (torBinary != null && torBinary.canExecute()) {
            cmd.add(torBinary.absolutePath)
        } else {
            // Check native library path
            val nativeTor = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
            if (nativeTor.exists()) {
                cmd.add(nativeTor.absolutePath)
            } else {
                cmd.add("tor")
            }
        }
        cmd.add("-f")
        cmd.add(torrcFile.absolutePath)

        onLog("[Tor] Spawning process: ${cmd.joinToString(" ")}")

        val processBuilder = ProcessBuilder(cmd)
        processBuilder.directory(context.filesDir)
        processBuilder.redirectErrorStream(true)

        val monitor = TorBootstrapMonitor { percent, msg ->
            onBootstrapProgress(percent, msg)
        }

        try {
            val proc = processBuilder.start()
            torProcess = proc

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            Thread {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        onLog("[Tor] $line")
                        monitor.parseLine(line)
                    }
                } catch (_: Exception) {}
            }.start()

            true
        } catch (e: Exception) {
            onLog("[Tor Error] Failed to launch Tor process: ${e.message}")
            false
        }
    }

    suspend fun waitForBootstrap(timeoutSeconds: Int = 60): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!isRunning()) {
                onLog("[Tor Error] Tor process died while bootstrapping.")
                return@withContext false
            }

            // Check controller bootstrap progress
            val phase = controller.getBootstrapPhase()
            if (phase >= 100) {
                onBootstrapProgress(100, "Tor circuit ready")
                return@withContext true
            }

            if (isPortBound(9050) && phase >= 100) {
                return@withContext true
            }

            kotlinx.coroutines.delay(800)
        }

        onLog("[Tor Warning] Bootstrap timed out after ${timeoutSeconds}s.")
        isPortBound(9050)
    }

    fun stop() {
        try {
            torProcess?.destroy()
            torProcess = null
        } catch (_: Exception) {}

        if (pidFile.exists()) {
            try {
                pidFile.delete()
            } catch (_: Exception) {}
        }
    }

    fun isPortBound(port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 400)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveTorBinary(): File? {
        val candidates = listOf(
            File(context.applicationInfo.nativeLibraryDir, "libtor.so"),
            File(context.filesDir, "tor"),
            File(context.filesDir, "bin/tor")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }
}
