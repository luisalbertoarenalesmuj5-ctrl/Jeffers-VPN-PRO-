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

data class SshCredential(
    val username: String,
    val password: String
)

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
    private val customSocketFactory: SocketFactory? = null,
    private val onLog: (String, String, LogLevel) -> Unit = { tag, msg, level ->
        VpnStateHolder.appendLog(tag, msg, level)
    },
    private val candidateCredentials: List<SshCredential> = emptyList()
) {
    constructor(
        vpnService: VpnService,
        host: String,
        port: Int,
        username: String,
        candidatePasswords: List<String>,
        customSocketFactory: SocketFactory? = null,
        onLog: (String, String, LogLevel) -> Unit = { tag, msg, level ->
            VpnStateHolder.appendLog(tag, msg, level)
        },
        candidateCredentials: List<SshCredential> = emptyList()
    ) : this(
        protectSocket = { sock -> vpnService.protect(sock) },
        host = host,
        port = port,
        username = username,
        candidatePasswords = candidatePasswords,
        customSocketFactory = customSocketFactory,
        onLog = onLog,
        candidateCredentials = candidateCredentials
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

    private fun resolveCredentialsToTry(): List<SshCredential> {
        val defaultUser = username.trim().ifBlank { "root" }
        val fromExplicit = candidateCredentials.mapNotNull { cred ->
            val u = cred.username.trim().ifBlank { defaultUser }
            val p = cred.password.trim()
            if (p.isNotEmpty()) SshCredential(u, p) else null
        }
        val fromPasswords = candidatePasswords.mapNotNull { pwd ->
            val clean = pwd.trim()
            if (clean.isNotEmpty()) SshCredential(defaultUser, clean) else null
        }
        return (fromExplicit + fromPasswords).distinct()
    }

    fun connect(timeoutMs: Int = 5500): Result<Int> {
        val credentialsToTry = resolveCredentialsToTry()
        if (credentialsToTry.isEmpty()) {
            val err = IllegalStateException("Sin contraseña SSH configurada")
            lastErrorMessage = err.message
            onLog("SSH", "Error: sin contraseña SSH configurada", LogLevel.ERROR)
            return Result.failure(err)
        }

        val usersSummary = credentialsToTry.map { it.username }.distinct().joinToString("/")
        onLog("SSH", "SSH → $usersSummary@$host:$port (${credentialsToTry.size} credencial(es))", LogLevel.INFO)

        var lastError: Exception? = null
        credentialsToTry.forEachIndexed { index, cred ->
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
                val created = jsch.getSession(cred.username, host, port)
                candidateSession = created
                created.setPassword(cred.password)
                created.setConfig("StrictHostKeyChecking", "no")
                created.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                created.setConfig("PubkeyAuthentication", "no")
                created.setConfig("compression.s2c", "none")
                created.setConfig("compression.c2s", "none")
                created.setServerAliveInterval(15_000)
                created.setServerAliveCountMax(3)
                created.setTimeout(30_000)
                created.setSocketFactory(
                    customSocketFactory ?: ProtectedSocketFactory(protectSocket, timeoutMs) { rtt ->
                        lastTcpConnectMs = rtt
                        onLog("SSH", "TCP $host:$port abierto en ${rtt} ms; negociando KEX/Auth…", LogLevel.INFO)
                    }
                )
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
                    "SSH autenticado ✓ (${cred.username}@$host:$port • ${lastTcpConnectMs} ms)",
                    LogLevel.SUCCESS
                )
                return Result.success(lastTcpConnectMs)
            } catch (e: Exception) {
                lastError = e
                val detail = e.localizedMessage ?: e.javaClass.simpleName
                lastErrorMessage = detail
                onLog(
                    "SSH",
                    "Intento SSH ${index + 1}/${credentialsToTry.size} (${cred.username}) falló en $host:$port ($detail)",
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

    /**
     * Verifies real end-to-end TCP forwarding (`direct-tcpip`) + real DNS/Internet egress through
     * the authenticated SSH session so we never report CONNECTED if `AllowTcpForwarding` is disabled
     * or VPS firewall/DNS blocks internet traffic.
     */
    fun verifyForwarding(timeoutMs: Int = 4500): Result<Int> {
        val activeSession = session
        if (activeSession == null || !activeSession.isConnected) {
            return Result.failure(IllegalStateException("Sesión SSH no conectada"))
        }

        val startNs = System.nanoTime()
        val httpStream = openDirectTcpIp("connectivitycheck.gstatic.com", 80, timeoutMs)
        if (httpStream != null) {
            try {
                httpStream.outputStream.write(
                    "GET /generate_204 HTTP/1.1\r\nHost: connectivitycheck.gstatic.com\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.UTF_8)
                )
                httpStream.outputStream.flush()
                val buf = ByteArray(256)
                val n = httpStream.inputStream.read(buf)
                if (n > 0) {
                    val statusLine = String(buf, 0, n, Charsets.UTF_8).lineSequence().firstOrNull().orEmpty()
                    if (statusLine.startsWith("HTTP/")) {
                        val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                        onLog("SSH", "Reenvío direct-tcpip + Internet verificados ✓ ($statusLine • ${ms}ms)", LogLevel.SUCCESS)
                        return Result.success(ms)
                    }
                }
            } catch (_: Exception) {
            } finally {
                httpStream.close()
            }
        }

        // Secondary check: HTTPS + DNS-over-HTTPS (1.1.1.1:443) or TCP DNS :53 via direct-tcpip
        val dohStartNs = System.nanoTime()
        val dohStream = openDirectTcpIp("1.1.1.1", 443, timeoutMs)
        if (dohStream != null) {
            try {
                val query = NetworkDiagnosticEngine.buildDnsAQueryPacket("google.com")
                val dohResp = DnsSslEngineHelper.executeDoHOverStreams(
                    input = dohStream.inputStream,
                    output = dohStream.outputStream,
                    sniHost = "1.1.1.1",
                    dnsQuery = query,
                    timeoutMs = timeoutMs
                )
                if (dohResp != null && dohResp.size >= 12) {
                    val ms = ((System.nanoTime() - dohStartNs) / 1_000_000L).toInt().coerceAtLeast(1)
                    onLog("SSH", "Reenvío direct-tcpip + HTTPS DoH 1.1.1.1:443 verificado ✓ (${ms}ms)", LogLevel.SUCCESS)
                    return Result.success(ms)
                }
            } catch (e: Exception) {
                lastErrorMessage = "1.1.1.1:443 abierto sin respuesta TLS/DoH (${e.localizedMessage ?: "sin salida"})"
            } finally {
                dohStream.close()
            }
        }

        val reason = lastErrorMessage ?: "AllowTcpForwarding deshabilitado o sin salida a Internet/DNS en VPS"
        return Result.failure(IOException("Fallo en reenvío SSH ($reason)"))
    }

    fun runExecCommand(command: String, timeoutMs: Int = 4000): Result<String> {
        val activeSession = session
        if (activeSession == null || !activeSession.isConnected) {
            return Result.failure(IllegalStateException("Sesión SSH no conectada"))
        }
        var channel: com.jcraft.jsch.ChannelExec? = null
        return try {
            channel = activeSession.openChannel("exec") as com.jcraft.jsch.ChannelExec
            channel.setCommand(command)
            val stdout = channel.inputStream
            val stderr = channel.errStream
            channel.connect(timeoutMs)

            val deadline = System.currentTimeMillis() + timeoutMs
            val outBuf = java.io.ByteArrayOutputStream()
            val tmp = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                while (stdout.available() > 0) {
                    val read = stdout.read(tmp)
                    if (read <= 0) break
                    if (outBuf.size() < 16_384) outBuf.write(tmp, 0, read)
                }
                while (stderr.available() > 0) {
                    val read = stderr.read(tmp)
                    if (read <= 0) break
                    if (outBuf.size() < 16_384) outBuf.write(tmp, 0, read)
                }
                if (channel.isClosed) {
                    while (stdout.available() > 0) {
                        val read = stdout.read(tmp)
                        if (read <= 0) break
                        if (outBuf.size() < 16_384) outBuf.write(tmp, 0, read)
                    }
                    break
                }
                Thread.sleep(40)
            }
            val text = outBuf.toString(Charsets.UTF_8.name()).trim()
            if (text.isNotEmpty() || channel.exitStatus == 0) {
                Result.success(text)
            } else {
                Result.failure(IOException("Exec salió con código ${channel.exitStatus}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                channel?.disconnect()
            } catch (_: Exception) {
            }
        }
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
            try {
                // Force native OS socket file descriptor allocation BEFORE VpnService.protect(socket)
                if (!socket.isBound) {
                    socket.bind(InetSocketAddress(0))
                }
            } catch (_: Exception) {
            }
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

