package com.example.vpn

import android.net.VpnService
import com.example.data.model.LogLevel
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SshTcpStream(
    private val channel: ChannelDirectTCPIP,
    val inputStream: InputStream,
    val outputStream: OutputStream
) {
    fun shutdownOutput() {
        try {
            outputStream.close()
        } catch (_: Exception) {
        }
    }

    fun close() {
        try {
            inputStream.close()
        } catch (_: Exception) {
        }
        try {
            outputStream.close()
        } catch (_: Exception) {
        }
        try {
            channel.disconnect()
        } catch (_: Exception) {
        }
    }
}

class SshTunnelClient(
    private val protectSocket: (Socket) -> Boolean,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val candidatePasswords: List<String>,
    private val onLog: (String, String, LogLevel) -> Unit = { tag, msg, level ->
        VpnStateHolder.appendLog(tag, msg, level)
    }
) {
    constructor(
        vpnService: VpnService,
        host: String,
        port: Int,
        username: String,
        candidatePasswords: List<String>,
        onLog: (String, String, LogLevel) -> Unit = { tag, msg, level ->
            VpnStateHolder.appendLog(tag, msg, level)
        }
    ) : this(
        protectSocket = { sock -> vpnService.protect(sock) },
        host = host,
        port = port,
        username = username,
        candidatePasswords = candidatePasswords,
        onLog = onLog
    )

    private val sessionLock = Any()
    private val channelErrorCount = AtomicInteger(0)
    private val lastChannelErrorLogAt = AtomicLong(0L)

    @Volatile
    private var session: Session? = null

    @Volatile
    var lastTcpConnectMs: Int = 1
        private set

    @Volatile
    var lastErrorMessage: String? = null
        private set

    fun isConnected(): Boolean = session?.isConnected == true

    fun connect(timeoutMs: Int = 5500): Result<Int> {
        val passwordsToTry = candidatePasswords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (passwordsToTry.isEmpty()) {
            val err = IllegalStateException("Sin contraseña SSH configurada")
            lastErrorMessage = err.message
            onLog("SSH", "Error: sin contraseña SSH configurada", LogLevel.ERROR)
            return Result.failure(err)
        }

        val effectiveUser = username.ifBlank { "root" }
        onLog("SSH", "SSH → $effectiveUser@$host:$port (${passwordsToTry.size} clave(s))", LogLevel.INFO)

        var lastError: Exception? = null
        passwordsToTry.forEachIndexed { index, pwd ->
            var candidateSession: Session? = null
            try {
                JSch.setLogger(object : com.jcraft.jsch.Logger {
                    override fun isEnabled(level: Int): Boolean = level >= com.jcraft.jsch.Logger.WARN
                    override fun log(level: Int, message: String?) {
                        if (!message.isNullOrBlank()) {
                            try {
                                android.util.Log.w("JeffersSSH", message)
                            } catch (_: Throwable) {
                            }
                            if (level >= com.jcraft.jsch.Logger.ERROR ||
                                message.contains("fail", ignoreCase = true) ||
                                message.contains("refused", ignoreCase = true) ||
                                message.contains("reset", ignoreCase = true) ||
                                message.contains("algorithm", ignoreCase = true)
                            ) {
                                onLog("SSH-CORE", message.take(180), LogLevel.WARNING)
                            }
                        }
                    }
                })
                val jsch = JSch()
                val created = jsch.getSession(effectiveUser, host, port)
                candidateSession = created
                created.setPassword(pwd)
                created.setConfig("StrictHostKeyChecking", "no")
                created.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                created.setConfig("PubkeyAuthentication", "no")
                created.setConfig("compression.s2c", "none")
                created.setConfig("compression.c2s", "none")
                created.setServerAliveInterval(15_000)
                created.setServerAliveCountMax(3)
                created.setTimeout(30_000)
                created.setSocketFactory(ProtectedSocketFactory(protectSocket, timeoutMs) { rtt ->
                    lastTcpConnectMs = rtt
                    onLog("SSH", "TCP $host:$port abierto en ${rtt} ms; negociando KEX/Auth…", LogLevel.INFO)
                })
                created.connect(timeoutMs)

                synchronized(sessionLock) {
                    session?.let { previous ->
                        try {
                            previous.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                    session = created
                }
                lastErrorMessage = null
                onLog(
                    "SSH",
                    "SSH autenticado ✓ ($effectiveUser@$host:$port • ${lastTcpConnectMs} ms)",
                    LogLevel.SUCCESS
                )
                return Result.success(lastTcpConnectMs)
            } catch (e: Exception) {
                lastError = e
                val detail = e.localizedMessage ?: e.javaClass.simpleName
                lastErrorMessage = detail
                onLog(
                    "SSH",
                    "Intento SSH ${index + 1}/${passwordsToTry.size} falló en $host:$port ($detail)",
                    LogLevel.WARNING
                )
                try {
                    candidateSession?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        val finalDetail = lastError?.localizedMessage ?: lastError?.javaClass?.simpleName ?: "Sin respuesta"
        VpnStateHolder.updateDiagnostics {
            it.copy(sshFailCount = it.sshFailCount + 1, lastSshDetail = finalDetail)
        }
        return Result.failure(lastError ?: IOException("Error conectando túnel SSH a $host:$port"))
    }

    fun openDirectTcpIp(dstHost: String, dstPort: Int, timeoutMs: Int = 4500): SshTcpStream? {
        val activeSession = session ?: return null
        if (!activeSession.isConnected) {
            onLog("SSH", "Sesión SSH desconectada antes de abrir canal a $dstHost:$dstPort", LogLevel.WARNING)
            return null
        }

        var channel: ChannelDirectTCPIP? = null
        return try {
            channel = activeSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(dstHost)
            channel.setPort(dstPort)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(0)
            val inStream = channel.inputStream
            val outStream = channel.outputStream
            channel.connect(timeoutMs)
            SshTcpStream(channel, inStream, outStream)
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            lastErrorMessage = "direct-tcpip $dstHost:$dstPort -> $detail"
            val count = channelErrorCount.incrementAndGet()
            val now = System.currentTimeMillis()
            val lastLog = lastChannelErrorLogAt.get()
            if (count <= 4 || now - lastLog > 5000L) {
                lastChannelErrorLogAt.set(now)
                onLog("SSH", "Canal direct-tcpip $dstHost:$dstPort rechazado: $detail", LogLevel.ERROR)
            }
            VpnStateHolder.updateDiagnostics {
                it.copy(sshFailCount = it.sshFailCount + 1, lastSshDetail = "$dstHost:$dstPort ($detail)")
            }
            try {
                channel?.disconnect()
            } catch (_: Exception) {
            }
            null
        }
    }

    fun disconnect() {
        synchronized(sessionLock) {
            try {
                session?.disconnect()
            } catch (_: Exception) {
            }
            session = null
        }
    }

    private class ProtectedSocketFactory(
        private val protectSocket: (Socket) -> Boolean,
        private val connectTimeoutMs: Int,
        private val onTcpConnected: (Int) -> Unit
    ) : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val socket = Socket()
            if (!protectSocket(socket)) {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
                throw IOException("No se pudo proteger el socket SSH")
            }
            socket.tcpNoDelay = true
            socket.keepAlive = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            onTcpConnected(rtt)
            return socket
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }
}

