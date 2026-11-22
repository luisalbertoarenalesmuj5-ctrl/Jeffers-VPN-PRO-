package com.example.vpn

import android.net.VpnService
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

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
    private val vpnService: VpnService,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val candidatePasswords: List<String>
) {
    private val sessionLock = Any()

    @Volatile
    private var session: Session? = null

    @Volatile
    var lastTcpConnectMs: Int = 1
        private set

    fun isConnected(): Boolean = session?.isConnected == true

    fun connect(timeoutMs: Int = 5500): Result<Int> {
        val passwordsToTry = candidatePasswords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (passwordsToTry.isEmpty()) {
            return Result.failure(IllegalStateException("Sin contraseña SSH configurada"))
        }

        var lastError: Exception? = null
        for (pwd in passwordsToTry) {
            var candidateSession: Session? = null
            try {
                val jsch = JSch()
                val created = jsch.getSession(username.ifBlank { "root" }, host, port)
                candidateSession = created
                created.setPassword(pwd)
                created.setConfig("StrictHostKeyChecking", "no")
                created.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                created.setServerAliveInterval(15_000)
                created.setServerAliveCountMax(3)
                created.setTimeout(15_000)
                created.setSocketFactory(ProtectedSocketFactory(vpnService, timeoutMs) { rtt ->
                    lastTcpConnectMs = rtt
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
                return Result.success(lastTcpConnectMs)
            } catch (e: Exception) {
                lastError = e
                try {
                    candidateSession?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        return Result.failure(lastError ?: IOException("Error conectando túnel SSH a $host:$port"))
    }

    fun openDirectTcpIp(dstHost: String, dstPort: Int, timeoutMs: Int = 4500): SshTcpStream? {
        val activeSession = session ?: return null
        if (!activeSession.isConnected) return null

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
        } catch (_: Exception) {
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
        private val vpnService: VpnService,
        private val connectTimeoutMs: Int,
        private val onTcpConnected: (Int) -> Unit
    ) : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val socket = Socket()
            if (!vpnService.protect(socket)) {
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
