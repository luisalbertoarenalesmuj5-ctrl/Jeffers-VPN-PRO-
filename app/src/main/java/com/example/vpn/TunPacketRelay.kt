package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.data.model.LogLevel
import com.example.data.model.TunnelDiagnosticsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TunPacketRelay internal constructor(
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    private val tunInputProvider: () -> InputStream,
    private val tunOutputProvider: () -> OutputStream,
    private val dnsPrimaryIp: String,
    private val dnsSecondaryIp: String,
    private val vpsHost: String,
    private val vpsPort: Int,
    private val vpsUsername: String,
    private val vpsPassword: String,
    private val sshCandidatePasswords: List<String> = listOf(vpsPassword),
    private val initialSshClient: SshTunnelClient? = null,
    private val scope: CoroutineScope,
    private val mtu: Int = 1400,
    private val initialProxyVerified: Boolean = false,
    private val allowDirectFallback: Boolean = vpsHost.isBlank(),
    private val proxySniHost: String = "",
    private val sshClientFactory: ((Int) -> SshTunnelClient)? = null,
    private val onLog: (String, String, LogLevel) -> Unit = { tag, msg, level ->
        VpnStateHolder.appendLog(tag, msg, level)
    },
    private val onStatusMessage: (String) -> Unit = { msg ->
        VpnStateHolder.updateTelemetry { it.copy(statusMessage = msg) }
    },
    private val onDiagnostics: ((TunnelDiagnosticsState) -> TunnelDiagnosticsState) -> Unit = { transform ->
        VpnStateHolder.updateDiagnostics(transform)
    },
    private val sshCandidateCredentials: List<SshCredential> = emptyList()
) {
    constructor(
        vpnService: VpnService,
        tunFd: ParcelFileDescriptor,
        dnsPrimaryIp: String,
        dnsSecondaryIp: String,
        vpsHost: String,
        vpsPort: Int,
        vpsUsername: String,
        vpsPassword: String,
        sshCandidatePasswords: List<String> = listOf(vpsPassword),
        initialSshClient: SshTunnelClient? = null,
        scope: CoroutineScope,
        mtu: Int = 1400,
        initialProxyVerified: Boolean = false,
        allowDirectFallback: Boolean = false
    ) : this(
        protectSocket = { sock -> vpnService.protect(sock) },
        protectDatagramSocket = { sock -> vpnService.protect(sock) },
        tunInputProvider = { FileInputStream(tunFd.fileDescriptor) },
        tunOutputProvider = { FileOutputStream(tunFd.fileDescriptor) },
        dnsPrimaryIp = dnsPrimaryIp,
        dnsSecondaryIp = dnsSecondaryIp,
        vpsHost = vpsHost,
        vpsPort = vpsPort,
        vpsUsername = vpsUsername,
        vpsPassword = vpsPassword,
        sshCandidatePasswords = sshCandidatePasswords,
        initialSshClient = initialSshClient,
        scope = scope,
        mtu = mtu,
        initialProxyVerified = initialProxyVerified,
        allowDirectFallback = allowDirectFallback,
        sshClientFactory = { targetPort ->
            SshTunnelClient(
                vpnService = vpnService,
                host = vpsHost,
                port = targetPort,
                username = vpsUsername,
                candidatePasswords = sshCandidatePasswords
            )
        }
    )

    val txBytes = AtomicLong(0L)
    val rxBytes = AtomicLong(0L)

    private val maxTcpPayload = (mtu - 40).coerceIn(512, 1460)
    private val advertisedMss = (mtu - 60).coerceIn(512, 1440)
    private val maxUdpPayload = (mtu - 28).coerceIn(512, 1472)

    private val writeLock = Any()
    private val tcpSessions = ConcurrentHashMap<TcpSessionKey, TcpSession>()
    private val udpSessions = ConcurrentHashMap<UdpSessionKey, UdpSession>()
    private val dnsCache = ConcurrentHashMap<String, CachedDnsEntry>()
    private val proxyProbeMutex = Mutex()
    private val sshConnectMutex = Mutex()

    @Volatile
    private var startupProbeJob: Job? = null

    @Volatile
    private var activeSshClient: SshTunnelClient? = initialSshClient

    @Volatile
    private var ownedFallbackSshClient: SshTunnelClient? = null

    @Volatile
    private var sshFallbackCooldownUntil: Long = 0L

    private val sshReconnectCooldownUntil = AtomicLong(0L)

    @Volatile
    private var vpsSupportsConnect: Boolean = initialProxyVerified

    @Volatile
    private var vpsConnectCooldownUntil: Long = 0L

    @Volatile
    private var loggedDirectFallback: Boolean = false

    @Volatile
    private var lastDirectFailureLogAt: Long = 0L

    private val loggedFirstDnsOk = AtomicBoolean(false)
    private val lastDnsFailLogAt = AtomicLong(0L)
    private val loggedFirstTcpOk = AtomicBoolean(false)
    private val loggedIpv6Reject = AtomicBoolean(false)
    private val loggedOrphanRst = AtomicBoolean(false)
    private val loggedQuicReject = AtomicBoolean(false)
    private val lastProxyFailLogAt = AtomicLong(0L)
    private val tcpFailStreak = AtomicLong(0L)

    private data class CachedDnsEntry(
        val payload: ByteArray,
        val expiresAtMs: Long
    )

    private data class TcpSessionKey(
        val srcPort: Int,
        val dstIpInt: Int,
        val dstPort: Int
    )

    private data class UdpSessionKey(
        val srcPort: Int,
        val dstIpInt: Int,
        val dstPort: Int
    )

    private sealed interface OutboundOp {
        data class Data(val bytes: ByteArray) : OutboundOp
        data object ShutdownOutput : OutboundOp
    }

    private interface UpstreamTcpStream {
        val inputStream: InputStream
        val outputStream: OutputStream
        fun configureConnected()
        fun shutdownOutput()
        fun close()
    }

    private class SocketUpstreamStream(private val socket: Socket) : UpstreamTcpStream {
        override val inputStream: InputStream get() = socket.getInputStream()
        override val outputStream: OutputStream get() = socket.getOutputStream()

        override fun configureConnected() {
            socket.soTimeout = 0
            socket.tcpNoDelay = true
            socket.keepAlive = true
        }

        override fun shutdownOutput() {
            try {
                socket.shutdownOutput()
            } catch (_: Exception) {
            }
        }

        override fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private class SshUpstreamStream(private val stream: SshTcpStream) : UpstreamTcpStream {
        override val inputStream: InputStream get() = stream.inputStream
        override val outputStream: OutputStream get() = stream.outputStream

        override fun configureConnected() = Unit

        override fun shutdownOutput() = stream.shutdownOutput()

        override fun close() = stream.close()
    }

    private class TcpSession(
        val key: TcpSessionKey,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val initialServerSeq: Long,
        var clientNextSeq: Long,
        var serverSeq: Long,
        val seqLock: Any = Any(),
        val outboundChannel: Channel<OutboundOp> = Channel(Channel.UNLIMITED),
        val outOfOrder: LinkedHashMap<Long, ByteArray> = LinkedHashMap(),
        var pendingFinSeq: Long? = null,
        var clientFinReceived: Boolean = false,
        var serverFinSent: Boolean = false,
        @Volatile var upstream: UpstreamTcpStream? = null,
        @Volatile var writerJob: Job? = null,
        @Volatile var connected: Boolean = false,
        @Volatile var closed: Boolean = false,
        @Volatile var lastActivityAt: Long = System.currentTimeMillis()
    )

    private class UdpSession(
        val key: UdpSessionKey,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val socket: DatagramSocket,
        @Volatile var readerJob: Job? = null,
        @Volatile var closed: Boolean = false,
        @Volatile var lastActivityAt: Long = System.currentTimeMillis()
    )

    fun runLoop() {
        val input = tunInputProvider()
        val output = tunOutputProvider()
        val buffer = ByteArray(32767)

        val reaperJob = scope.launch(Dispatchers.IO) {
            while (scope.isActive) {
                kotlinx.coroutines.delay(15_000L)
                reapIdleUdpSessions()
            }
        }

        val initialMode = when {
            activeSshClient?.isConnected() == true -> "SSH Túnel ($vpsHost:$vpsPort)"
            vpsSupportsConnect -> "Proxy HTTP CONNECT ($vpsHost:$vpsPort)"
            vpsPort == 22 -> "SSH Directo ($vpsHost:22)"
            vpsHost.isNotBlank() && vpsPort > 0 -> "Sondeando :$vpsPort"
            else -> "Relay Local"
        }
        onDiagnostics {
            it.copy(
                tunnelMode = initialMode,
                activeTcpSessions = 0,
                activeUdpSessions = 0
            )
        }
        onLog(
            "TUN",
            "Relay TUN activo • MTU=$mtu MSS=$advertisedMss MaxUDP=$maxUdpPayload • DNS=$dnsPrimaryIp/$dnsSecondaryIp • Modo=$initialMode",
            LogLevel.INFO
        )

        if (activeSshClient == null && !vpsSupportsConnect && vpsPort != 22 && vpsHost.isNotBlank()) {
            startupProbeJob = scope.launch(Dispatchers.IO) {
                onLog("PROBE", "Sondeando soporte proxy HTTP CONNECT en $vpsHost:$vpsPort…", LogLevel.INFO)
                val proxySocket = probeVpsConnectSupport("1.1.1.1", 443)
                if (proxySocket != null) {
                    try { proxySocket.close() } catch (_: Exception) {}
                    onDiagnostics { it.copy(tunnelMode = "Proxy HTTP CONNECT (:$vpsPort)") }
                } else {
                    val ssh = ensureSshFallbackConnected()
                    if (ssh == null) {
                        onDiagnostics {
                            it.copy(
                                tunnelMode = if (allowDirectFallback) "Directo protegido"
                                else "Sin túnel VPS (:$vpsPort)"
                            )
                        }
                    }
                }
            }
        }

        try {
            while (scope.isActive) {
                val length = try {
                    input.read(buffer)
                } catch (_: Exception) {
                    break
                }
                if (length < 0) break
                if (length == 0) continue

                txBytes.addAndGet(length.toLong())
                val packetCopy = buffer.copyOf(length)
                handleOutboundPacket(packetCopy, output)
            }
        } finally {
            startupProbeJob?.cancel()
            reaperJob.cancel()
            closeAllSessions()
        }
    }

    fun closeAllSessions() {
        tcpSessions.values.forEach { session ->
            closeTcpSession(session)
        }
        tcpSessions.clear()

        udpSessions.values.forEach { session ->
            session.closed = true
            session.readerJob?.cancel()
            try { session.socket.close() } catch (_: Exception) {}
        }
        udpSessions.clear()

        ownedFallbackSshClient?.disconnect()
        ownedFallbackSshClient = null
        activeSshClient = null
    }

    private fun closeTcpSession(session: TcpSession) {
        session.closed = true
        session.outboundChannel.close()
        session.writerJob?.cancel()
        session.upstream?.close()
    }

    private fun handleOutboundPacket(packet: ByteArray, output: OutputStream) {
        if (packet.size < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version == 6) {
            handleIpv6Packet(packet, output)
            return
        }
        if (version != 4) return

        val ihlBytes = (packet[0].toInt() and 0x0F) * 4
        if (ihlBytes < 20 || packet.size < ihlBytes) return

        val protocol = packet[9].toInt() and 0xFF
        when (protocol) {
            PROTOCOL_UDP -> {
                if (packet.size >= ihlBytes + 8) {
                    val dstPort = readUShort(packet, ihlBytes + 2)
                    if (dstPort == DNS_PORT) {
                        scope.launch(Dispatchers.IO) {
                            forwardDnsQueryWithFailover(packet, ihlBytes, output)
                        }
                    } else {
                        forwardGeneralUdpPacket(packet, ihlBytes, output)
                    }
                }
            }
            PROTOCOL_TCP -> {
                if (packet.size >= ihlBytes + 20) {
                    handleTcpPacket(packet, ihlBytes, output)
                }
            }
        }
    }

    private fun handleIpv6Packet(packet: ByteArray, output: OutputStream) {
        if (packet.size < 40) return
        val nextHeader = packet[6].toInt() and 0xFF
        var rejected = false
        when (nextHeader) {
            PROTOCOL_TCP -> {
                buildIpv6TcpRstPacket(packet)?.let { rst ->
                    writePacketToTun(rst, output)
                    rejected = true
                }
            }
            PROTOCOL_UDP -> {
                buildIpv6IcmpPortUnreachablePacket(packet, maxTcpPayload)?.let { icmpv6 ->
                    writePacketToTun(icmpv6, output)
                    rejected = true
                }
            }
        }
        if (rejected) {
            onDiagnostics { it.copy(ipv6RejectedCount = it.ipv6RejectedCount + 1) }
            if (loggedIpv6Reject.compareAndSet(false, true)) {
                onLog(
                    "TUN",
                    "Tráfico IPv6 rechazado al instante (TCPv6 RST / ICMPv6) para forzar salida IPv4",
                    LogLevel.INFO
                )
            }
        }
    }

    private fun handleTcpPacket(packet: ByteArray, ihlBytes: Int, output: OutputStream) {
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = readUShort(packet, ihlBytes)
        val dstPort = readUShort(packet, ihlBytes + 2)
        val seqNum = readUInt(packet, ihlBytes + 4)
        val ackNum = readUInt(packet, ihlBytes + 8)
        val dataOffset = ((packet[ihlBytes + 12].toInt() ushr 4) and 0x0F) * 4
        val flags = packet[ihlBytes + 13].toInt() and 0x3F
        val tcpHeaderEnd = ihlBytes + dataOffset
        if (dataOffset < 20 || packet.size < tcpHeaderEnd) return

        val payloadLen = packet.size - tcpHeaderEnd
        val dstIpInt = ByteBuffer.wrap(dstIp).int
        val key = TcpSessionKey(srcPort, dstIpInt, dstPort)

        val isSyn = (flags and FLAG_SYN) != 0 && (flags and FLAG_ACK) == 0
        val isFin = (flags and FLAG_FIN) != 0
        val isRst = (flags and FLAG_RST) != 0

        if (isSyn) {
            val expectedClientNextSeq = (seqNum + 1L) and 0xFFFFFFFFL
            val existing = tcpSessions[key]
            if (existing != null && !existing.closed && existing.clientNextSeq == expectedClientNextSeq) {
                existing.lastActivityAt = System.currentTimeMillis()
                if (existing.connected) {
                    resendTcpSynAckPacket(existing, advertisedMss, output)
                }
                return
            }

            tcpSessions.remove(key)?.let { old -> closeTcpSession(old) }
            if (tcpSessions.size >= MAX_TCP_SESSIONS) {
                reapOldestTcpSession()
            }

            val initialServerSeq = Random.nextLong(1000L, 999_999_999L)
            val session = TcpSession(
                key = key,
                srcIp = srcIp,
                dstIp = dstIp,
                initialServerSeq = initialServerSeq,
                clientNextSeq = expectedClientNextSeq,
                serverSeq = initialServerSeq
            )
            tcpSessions[key] = session

            scope.launch(Dispatchers.IO) {
                connectAndBridgeTcpSession(session, output)
            }
            return
        }

        val session = tcpSessions[key]
        if (session == null) {
            if (!isRst) {
                val rstPacket = if ((flags and FLAG_ACK) != 0) {
                    buildIpv4TcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seqNum = ackNum,
                        ackNum = 0L,
                        flags = FLAG_RST,
                        payload = ByteArray(0)
                    )
                } else {
                    val ackVal = (seqNum + payloadLen.toLong() + (if (isFin) 1L else 0L)) and 0xFFFFFFFFL
                    buildIpv4TcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seqNum = 0L,
                        ackNum = ackVal,
                        flags = FLAG_RST or FLAG_ACK,
                        payload = ByteArray(0)
                    )
                }
                writePacketToTun(rstPacket, output)
                onDiagnostics { it.copy(orphanRstCount = it.orphanRstCount + 1) }
                if (loggedOrphanRst.compareAndSet(false, true)) {
                    onLog(
                        "TCP",
                        "Paquete huérfano sin sesión ($dstPort) -> RST enviado para resincronizar app",
                        LogLevel.INFO
                    )
                }
            }
            return
        }
        session.lastActivityAt = System.currentTimeMillis()

        if (isRst) {
            closeTcpSession(session)
            tcpSessions.remove(key, session)
            return
        }

        if (session.closed) return

        var ackNeeded = false
        synchronized(session.seqLock) {
            if (payloadLen > 0) {
                val rawData = packet.copyOfRange(tcpHeaderEnd, packet.size)
                val diff = (seqNum - session.clientNextSeq).toInt()
                when {
                    diff == 0 -> {
                        session.outboundChannel.trySend(OutboundOp.Data(rawData))
                        session.clientNextSeq = (session.clientNextSeq + payloadLen.toLong()) and 0xFFFFFFFFL
                        drainOutOfOrderLocked(session)
                        ackNeeded = true
                    }
                    diff < 0 -> {
                        val overlap = -diff
                        if (overlap < payloadLen) {
                            val freshSlice = rawData.copyOfRange(overlap, rawData.size)
                            session.outboundChannel.trySend(OutboundOp.Data(freshSlice))
                            session.clientNextSeq = (session.clientNextSeq + freshSlice.size.toLong()) and 0xFFFFFFFFL
                            drainOutOfOrderLocked(session)
                        }
                        ackNeeded = true
                    }
                    diff in 1..MAX_OOO_WINDOW -> {
                        if (session.outOfOrder.size < MAX_OOO_CHUNKS && !session.outOfOrder.containsKey(seqNum)) {
                            session.outOfOrder[seqNum] = rawData
                        }
                        ackNeeded = true
                    }
                }
            }

            if (isFin && !session.clientFinReceived) {
                val finSeq = (seqNum + payloadLen.toLong()) and 0xFFFFFFFFL
                if (finSeq == session.clientNextSeq) {
                    session.clientFinReceived = true
                    session.clientNextSeq = (session.clientNextSeq + 1L) and 0xFFFFFFFFL
                    session.outboundChannel.trySend(OutboundOp.ShutdownOutput)
                    ackNeeded = true
                } else if (((finSeq - session.clientNextSeq).toInt()) in 1..MAX_OOO_WINDOW) {
                    session.pendingFinSeq = finSeq
                    ackNeeded = true
                }
            }
        }

        if (ackNeeded && session.connected && !session.closed) {
            sendTcpControlPacket(
                session = session,
                flags = FLAG_ACK,
                payload = ByteArray(0),
                output = output
            )
        }
    }

    private fun drainOutOfOrderLocked(session: TcpSession) {
        while (true) {
            val nextChunk = session.outOfOrder.remove(session.clientNextSeq) ?: break
            session.outboundChannel.trySend(OutboundOp.Data(nextChunk))
            session.clientNextSeq = (session.clientNextSeq + nextChunk.size.toLong()) and 0xFFFFFFFFL
        }
        val pendingFin = session.pendingFinSeq
        if (pendingFin != null && pendingFin == session.clientNextSeq && !session.clientFinReceived) {
            session.pendingFinSeq = null
            session.clientFinReceived = true
            session.clientNextSeq = (session.clientNextSeq + 1L) and 0xFFFFFFFFL
            session.outboundChannel.trySend(OutboundOp.ShutdownOutput)
        }
    }

    private suspend fun ensureSshFallbackConnected(): SshTunnelClient? {
        activeSshClient?.let { current ->
            if (current.isConnected()) return current
        }
        if (sshCandidatePasswords.isEmpty() && sshCandidateCredentials.isEmpty()) {
            if (initialSshClient == null) return null
        }
        val now = System.currentTimeMillis()
        if (initialSshClient != null && now < sshReconnectCooldownUntil.get()) return null
        if (initialSshClient == null && now < sshFallbackCooldownUntil) return null

        return sshConnectMutex.withLock {
            activeSshClient?.let { locked ->
                if (locked.isConnected()) return@withLock locked
            }
            if (initialSshClient != null) {
                if (System.currentTimeMillis() < sshReconnectCooldownUntil.get()) return@withLock null
                val result = initialSshClient.connect(5000)
                return@withLock if (result.isSuccess) {
                    activeSshClient = initialSshClient
                    sshReconnectCooldownUntil.set(0L)
                    initialSshClient
                } else {
                    sshReconnectCooldownUntil.set(System.currentTimeMillis() + 10_000L)
                    null
                }
            }
            val factory = sshClientFactory ?: return@withLock null
            if (System.currentTimeMillis() < sshFallbackCooldownUntil) return@withLock null

            val targetPort = if (vpsPort == 22) 22 else 22
            onLog("SSH", "Probando túnel SSH en $vpsHost:$targetPort ($vpsUsername)…", LogLevel.INFO)
            val candidate = factory(targetPort)
            val result = candidate.connect(4500)
            if (result.isSuccess) {
                ownedFallbackSshClient?.disconnect()
                ownedFallbackSshClient = candidate
                activeSshClient = candidate
                onStatusMessage("Túnel SSH activo • $vpsHost:$targetPort")
                onDiagnostics { it.copy(tunnelMode = "SSH Respaldo ($vpsHost:$targetPort)") }
                onLog(
                    "SSH",
                    "Túnel SSH activo vía $vpsHost:$targetPort ($vpsUsername)",
                    LogLevel.SUCCESS
                )
                candidate
            } else {
                val err = result.exceptionOrNull()
                val errMsg = err?.localizedMessage ?: err?.javaClass?.simpleName ?: "Sin respuesta"
                onLog(
                    "SSH",
                    "Respaldo SSH en $vpsHost:$targetPort no disponible ($errMsg)",
                    LogLevel.WARNING
                )
                candidate.disconnect()
                sshFallbackCooldownUntil = System.currentTimeMillis() + 120_000L
                null
            }
        }
    }

    private suspend fun probeVpsConnectSupport(dstHost: String, dstPort: Int): Socket? {
        if (vpsPort == 22 || vpsHost.isBlank() || activeSshClient?.isConnected() == true) return null
        val now = System.currentTimeMillis()
        if (!vpsSupportsConnect && now < vpsConnectCooldownUntil) return null

        return proxyProbeMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!vpsSupportsConnect && lockedNow < vpsConnectCooldownUntil) {
                return@withLock null
            }
            openProxyConnectSocket(dstHost, dstPort)
        }
    }

    private fun bindAndProtectTcpSocket(socket: Socket): Boolean {
        try {
            if (!socket.isBound) {
                socket.bind(InetSocketAddress(0))
            }
        } catch (_: Exception) {
        }
        return protectSocket(socket)
    }

    private fun bindAndProtectUdpSocket(socket: DatagramSocket): Boolean {
        try {
            if (!socket.isBound) {
                socket.bind(null)
            }
        } catch (_: Exception) {
        }
        return protectDatagramSocket(socket)
    }

    private fun openProxyConnectSocket(dstHost: String, dstPort: Int): Socket? {
        val headerModes = if (vpsUsername.isNotBlank() || vpsPassword.isNotBlank()) {
            listOf(true, false)
        } else {
            listOf(false)
        }

        var lastRtt = 1
        var lastStatusLine: String? = null
        var lastException: Exception? = null

        for (includeAuthHeaders in headerModes) {
            val proxySocket = Socket()
            try {
                if (!bindAndProtectTcpSocket(proxySocket)) {
                    try { proxySocket.close() } catch (_: Exception) {}
                    onLog("PROXY", "No se pudo proteger socket proxy hacia $vpsHost:$vpsPort", LogLevel.WARNING)
                    return null
                }
                proxySocket.tcpNoDelay = true
                proxySocket.keepAlive = true
                val startNs = System.nanoTime()
                proxySocket.connect(InetSocketAddress(vpsHost, vpsPort), 3500)
                proxySocket.soTimeout = 4000

                val connectReq = buildString {
                    append("CONNECT $dstHost:$dstPort HTTP/1.1\r\n")
                    append("Host: $dstHost:$dstPort\r\n")
                    if (proxySniHost.isNotBlank()) {
                        append("X-Online-Host: $proxySniHost\r\n")
                    }
                    if (includeAuthHeaders && (vpsUsername.isNotBlank() || vpsPassword.isNotBlank())) {
                        val creds = "${vpsUsername.ifBlank { "root" }}:$vpsPassword"
                        append("Proxy-Authorization: Basic ${encodeBasicAuth(creds)}\r\n")
                    }
                    if (includeAuthHeaders && vpsPassword.isNotBlank()) {
                        append("X-VPS-Auth: $vpsPassword\r\n")
                    }
                    append("Proxy-Connection: Keep-Alive\r\n\r\n")
                }
                proxySocket.getOutputStream().write(connectReq.toByteArray(Charsets.UTF_8))
                proxySocket.getOutputStream().flush()

                val headerText = readHttpHeadersExact(proxySocket.getInputStream())
                lastRtt = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                lastStatusLine = headerText?.lineSequence()?.firstOrNull()?.trim()
                val statusCode = parseHttpStatusCode(lastStatusLine)
                if (statusCode in 200..299) {
                    if (!vpsSupportsConnect) {
                        onLog(
                            "PROXY",
                            "HTTP CONNECT $vpsHost:$vpsPort aceptado (${lastRtt}ms): $lastStatusLine",
                            LogLevel.SUCCESS
                        )
                    }
                    vpsSupportsConnect = true
                    return proxySocket
                }
                try { proxySocket.close() } catch (_: Exception) {}
                if (statusCode !in setOf(400, 403)) {
                    break
                }
            } catch (e: Exception) {
                try { proxySocket.close() } catch (_: Exception) {}
                lastException = e
                break
            }
        }

        if (!initialProxyVerified) {
            vpsSupportsConnect = false
            vpsConnectCooldownUntil = System.currentTimeMillis() + 120_000L
        }
        val now = System.currentTimeMillis()
        if (now - lastProxyFailLogAt.get() > 15_000L) {
            lastProxyFailLogAt.set(now)
            val detail = if (lastException != null) {
                "Error HTTP CONNECT $vpsHost:$vpsPort: ${lastException.localizedMessage ?: lastException.javaClass.simpleName}"
            } else {
                "HTTP CONNECT $vpsHost:$vpsPort rechazó $dstHost:$dstPort (${lastRtt}ms): ${lastStatusLine ?: "EOF sin cabecera HTTP"}"
            }
            onLog("PROXY", detail, LogLevel.WARNING)
        }
        return null
    }

    private suspend fun connectAndBridgeTcpSession(session: TcpSession, output: OutputStream) {
        val dstHost = InetAddress.getByAddress(session.dstIp).hostAddress ?: return
        var activeUpstream: UpstreamTcpStream? = null
        var transportLabel = if (allowDirectFallback) "DIRECTO" else "VPS"
        try {
            // 1. Prefer real SSH direct-tcpip tunnel when active (including auto-reconnect for SSH :22)
            var ssh = activeSshClient?.takeIf { it.isConnected() } ?: run {
                if (initialSshClient != null || vpsPort == 22) ensureSshFallbackConnected() else null
            }
            if (ssh != null) {
                val stream = ssh.openDirectTcpIp(dstHost, session.key.dstPort, 4500)
                if (stream != null) {
                    activeUpstream = SshUpstreamStream(stream)
                    transportLabel = "SSH"
                } else if (!ssh.isConnected()) {
                    ssh = ensureSshFallbackConnected()
                    ssh?.openDirectTcpIp(dstHost, session.key.dstPort, 4500)?.let { retried ->
                        activeUpstream = SshUpstreamStream(retried)
                        transportLabel = "SSH"
                    }
                }
            }

            val sshConnectedNow = ssh?.isConnected() == true || activeSshClient?.isConnected() == true

            // 2. Otherwise use verified HTTP CONNECT proxy on vpsHost:vpsPort (or fall back to SSH on :22)
            if (activeUpstream == null && !sshConnectedNow && vpsPort != 22 && initialSshClient == null) {
                val proxySocket = if (vpsSupportsConnect) {
                    openProxyConnectSocket(dstHost, session.key.dstPort)
                } else {
                    probeVpsConnectSupport(dstHost, session.key.dstPort)
                }
                if (proxySocket != null) {
                    activeUpstream = SocketUpstreamStream(proxySocket)
                    transportLabel = "HTTP-CONNECT"
                    onStatusMessage("Proxy HTTP CONNECT activo • $vpsHost:$vpsPort")
                    onDiagnostics { it.copy(tunnelMode = "Proxy HTTP CONNECT (:$vpsPort)") }
                } else if (!initialProxyVerified) {
                    val fallbackSsh = ensureSshFallbackConnected()
                    if (fallbackSsh != null) {
                        fallbackSsh.openDirectTcpIp(dstHost, session.key.dstPort, 4500)?.let { stream ->
                            activeUpstream = SshUpstreamStream(stream)
                            transportLabel = "SSH"
                        }
                    }
                }
            }

            // 3. Direct fallback ONLY when explicitly allowed (local loopback tests without VPS host)
            if (allowDirectFallback && activeUpstream == null && activeSshClient?.isConnected() != true && !vpsSupportsConnect) {
                val directSocket = Socket()
                if (!bindAndProtectTcpSocket(directSocket)) {
                    throw java.io.IOException("No se pudo proteger el socket directo hacia $dstHost:${session.key.dstPort}")
                }
                directSocket.tcpNoDelay = true
                directSocket.keepAlive = true
                directSocket.connect(InetSocketAddress(dstHost, session.key.dstPort), 4500)
                activeUpstream = SocketUpstreamStream(directSocket)
                transportLabel = "DIRECTO"
            }

            val upstream = activeUpstream ?: run {
                val errHint = activeSshClient?.lastErrorMessage ?: if (vpsSupportsConnect) {
                    "Proxy HTTP CONNECT rechazó $dstHost:${session.key.dstPort}"
                } else {
                    "Sin canal SSH/Proxy VPS disponible (fuga directa bloqueada)"
                }
                onDiagnostics {
                    it.copy(
                        tcpFailCount = it.tcpFailCount + 1,
                        lastTcpDetail = "$dstHost:${session.key.dstPort} ($errHint)",
                        activeTcpSessions = tcpSessions.size
                    )
                }
                val streak = tcpFailStreak.incrementAndGet()
                val now = System.currentTimeMillis()
                if (streak <= 3L || now - lastDirectFailureLogAt > 4000L) {
                    lastDirectFailureLogAt = now
                    onLog("TCP", "Canal bloqueado a $dstHost:${session.key.dstPort}: $errHint", LogLevel.ERROR)
                }
                closeSessionWithRst(session, output)
                return
            }

            if (session.closed) {
                upstream.close()
                return
            }

            upstream.configureConnected()
            session.upstream = upstream

            session.writerJob = scope.launch(Dispatchers.IO) {
                val out = upstream.outputStream
                try {
                    for (op in session.outboundChannel) {
                        if (session.closed) break
                        when (op) {
                            is OutboundOp.Data -> {
                                out.write(op.bytes)
                                out.flush()
                                session.lastActivityAt = System.currentTimeMillis()
                            }
                            is OutboundOp.ShutdownOutput -> {
                                upstream.shutdownOutput()
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (!session.closed && !session.clientFinReceived) {
                        closeSessionWithRst(session, output)
                    }
                }
            }

            // Send SYN+ACK with clamped MSS option so client respects TUN MTU
            sendTcpSynAckPacket(session, advertisedMss, output)
            tcpFailStreak.set(0L)
            onDiagnostics {
                it.copy(
                    tcpOkCount = it.tcpOkCount + 1,
                    lastTcpDetail = "$transportLabel → $dstHost:${session.key.dstPort}",
                    activeTcpSessions = tcpSessions.size
                )
            }
            if (loggedFirstTcpOk.compareAndSet(false, true)) {
                onLog(
                    "TCP",
                    "Primer enlace TCP activo [$transportLabel] → $dstHost:${session.key.dstPort}",
                    LogLevel.SUCCESS
                )
            }

            // Relay downstream bytes from upstream stream back to TUN
            val inStream = upstream.inputStream
            val buf = ByteArray(maxTcpPayload)
            while (scope.isActive && !session.closed) {
                val n = try {
                    inStream.read(buf)
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) break
                val chunk = buf.copyOf(n)
                sendTcpDataPacket(session, chunk, output)
                session.lastActivityAt = System.currentTimeMillis()
            }

            var sendFin = false
            synchronized(session.seqLock) {
                if (!session.closed && !session.serverFinSent) {
                    session.serverFinSent = true
                    sendFin = true
                }
            }
            if (sendFin) {
                sendTcpFinAckPacket(session, output)
            }
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            val streak = tcpFailStreak.incrementAndGet()
            val now = System.currentTimeMillis()
            onDiagnostics {
                it.copy(
                    tcpFailCount = it.tcpFailCount + 1,
                    lastTcpDetail = "Error $dstHost:${session.key.dstPort}: $detail"
                )
            }
            if (streak <= 3L || now - lastDirectFailureLogAt > 4_000L) {
                lastDirectFailureLogAt = now
                onLog(
                    "TCP",
                    "Fallo salida TCP [$transportLabel] → $dstHost:${session.key.dstPort} ($detail)",
                    LogLevel.ERROR
                )
            }
            closeSessionWithRst(session, output)
        } finally {
            closeTcpSession(session)
            tcpSessions.remove(session.key, session)
            onDiagnostics { it.copy(activeTcpSessions = tcpSessions.size) }
        }
    }

    private fun readHttpHeadersExact(input: java.io.InputStream): String? =
        VpsReachabilityTester.readHttpHeadersExact(input)

    private fun sendTcpSynAckPacket(session: TcpSession, mss: Int, output: OutputStream) {
        val packet = synchronized(session.seqLock) {
            val pkt = buildIpv4TcpPacketWithMss(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.initialServerSeq,
                ackNum = session.clientNextSeq,
                flags = FLAG_SYN or FLAG_ACK,
                mss = mss
            )
            session.serverSeq = (session.initialServerSeq + 1L) and 0xFFFFFFFFL
            session.connected = true
            pkt
        }
        writePacketToTun(packet, output)
    }

    private fun resendTcpSynAckPacket(session: TcpSession, mss: Int, output: OutputStream) {
        val packet = synchronized(session.seqLock) {
            buildIpv4TcpPacketWithMss(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.initialServerSeq,
                ackNum = session.clientNextSeq,
                flags = FLAG_SYN or FLAG_ACK,
                mss = mss
            )
        }
        writePacketToTun(packet, output)
    }

    private fun sendTcpDataPacket(session: TcpSession, payload: ByteArray, output: OutputStream) {
        val packet = synchronized(session.seqLock) {
            val pkt = buildIpv4TcpPacket(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = FLAG_PSH or FLAG_ACK,
                payload = payload
            )
            session.serverSeq = (session.serverSeq + payload.size.toLong()) and 0xFFFFFFFFL
            pkt
        }
        writePacketToTun(packet, output)
    }

    private fun sendTcpFinAckPacket(session: TcpSession, output: OutputStream) {
        val packet = synchronized(session.seqLock) {
            val pkt = buildIpv4TcpPacket(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = FLAG_FIN or FLAG_ACK,
                payload = ByteArray(0)
            )
            session.serverSeq = (session.serverSeq + 1L) and 0xFFFFFFFFL
            pkt
        }
        writePacketToTun(packet, output)
    }

    private fun closeSessionWithRst(session: TcpSession, output: OutputStream) {
        if (!session.closed) {
            sendTcpControlPacket(session, FLAG_RST or FLAG_ACK, ByteArray(0), output)
        }
        closeTcpSession(session)
        tcpSessions.remove(session.key, session)
    }

    private fun sendTcpControlPacket(
        session: TcpSession,
        flags: Int,
        payload: ByteArray,
        output: OutputStream
    ) {
        val packet = synchronized(session.seqLock) {
            buildIpv4TcpPacket(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = flags,
                payload = payload
            )
        }
        writePacketToTun(packet, output)
    }

    private fun writePacketToTun(packet: ByteArray, output: OutputStream) {
        synchronized(writeLock) {
            try {
                output.write(packet)
                output.flush()
                rxBytes.addAndGet(packet.size.toLong())
            } catch (_: Exception) {
            }
        }
    }

    private fun reapOldestTcpSession() {
        val oldest = tcpSessions.values.minByOrNull { it.lastActivityAt } ?: return
        closeTcpSession(oldest)
        tcpSessions.remove(oldest.key)
    }

    private fun reapIdleUdpSessions() {
        val cutoff = System.currentTimeMillis() - UDP_IDLE_TIMEOUT_MS
        val iterator = udpSessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastActivityAt < cutoff) {
                entry.value.closed = true
                entry.value.readerJob?.cancel()
                try { entry.value.socket.close() } catch (_: Exception) {}
                iterator.remove()
            }
        }
    }

    private fun forwardGeneralUdpPacket(
        packet: ByteArray,
        ihlBytes: Int,
        output: OutputStream
    ) {
        val udpOffset = ihlBytes
        val udpLength = readUShort(packet, udpOffset + 4)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtMost(packet.size - payloadOffset)
        if (payloadLength <= 0 || payloadLength > maxUdpPayload) return

        // When SSH tunnel or HTTP CONNECT proxy is active (TCP-only), reject QUIC/UDP with ICMP Port Unreachable
        // so browsers and apps switch immediately to TCP/HTTPS through the tunnel instead of stalling.
        if (activeSshClient?.isConnected() == true || vpsSupportsConnect) {
            buildIpv4IcmpPortUnreachablePacket(packet, ihlBytes)?.let { icmpPacket ->
                writePacketToTun(icmpPacket, output)
            }
            if (loggedQuicReject.compareAndSet(false, true)) {
                onLog(
                    "UDP",
                    "UDP/QUIC rechazado en modo túnel TCP para forzar tráfico HTTPS por TCP",
                    LogLevel.INFO
                )
            }
            return
        }

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = readUShort(packet, udpOffset)
        val dstPort = readUShort(packet, udpOffset + 2)
        val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)
        val dstIpInt = ByteBuffer.wrap(dstIp).int
        val key = UdpSessionKey(srcPort, dstIpInt, dstPort)

        var session = udpSessions[key]
        if (session == null || session.closed) {
            if (udpSessions.size >= MAX_UDP_SESSIONS) {
                reapIdleUdpSessions()
            }
            val sock = try {
                DatagramSocket().also {
                    if (!bindAndProtectUdpSocket(it)) {
                        it.close()
                        return
                    }
                    it.soTimeout = 0
                }
            } catch (_: Exception) {
                return
            }

            val created = UdpSession(
                key = key,
                srcIp = srcIp,
                dstIp = dstIp,
                socket = sock
            )
            val prev = udpSessions.putIfAbsent(key, created)
            if (prev != null && !prev.closed) {
                try { sock.close() } catch (_: Exception) {}
                session = prev
            } else {
                session = created
                onDiagnostics { it.copy(activeUdpSessions = udpSessions.size) }
                created.readerJob = scope.launch(Dispatchers.IO) {
                    val buf = ByteArray(4096)
                    val dp = DatagramPacket(buf, buf.size)
                    try {
                        while (scope.isActive && !created.closed) {
                            dp.length = buf.size
                            sock.receive(dp)
                            val len = dp.length
                            if (len <= 0 || len > maxUdpPayload) continue
                            created.lastActivityAt = System.currentTimeMillis()
                            val respPayload = buf.copyOf(len)
                            val ipPacket = buildIpv4UdpPacket(
                                srcIp = created.dstIp,
                                dstIp = created.srcIp,
                                srcPort = created.key.dstPort,
                                dstPort = created.key.srcPort,
                                payload = respPayload
                            )
                            writePacketToTun(ipPacket, output)
                        }
                    } catch (_: Exception) {
                    } finally {
                        created.closed = true
                        try { sock.close() } catch (_: Exception) {}
                        udpSessions.remove(key, created)
                        onDiagnostics { it.copy(activeUdpSessions = udpSessions.size) }
                    }
                }
            }
        }

        val activeSession = session
        activeSession.lastActivityAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                val target = InetAddress.getByAddress(dstIp)
                activeSession.socket.send(DatagramPacket(payload, payload.size, target, dstPort))
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun forwardDnsQueryWithFailover(
        requestPacket: ByteArray,
        ihlBytes: Int,
        output: OutputStream
    ) {
        val udpOffset = ihlBytes
        val udpLength = readUShort(requestPacket, udpOffset + 4)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtMost(requestPacket.size - payloadOffset)
        if (payloadLength <= 0) return

        val dnsPayload = requestPacket.copyOfRange(payloadOffset, payloadOffset + payloadLength)
        val srcIp = requestPacket.copyOfRange(12, 16)
        val dstIp = requestPacket.copyOfRange(16, 20)
        val srcPort = readUShort(requestPacket, udpOffset)
        val dstPort = readUShort(requestPacket, udpOffset + 2)

        val candidates = listOf(dnsPrimaryIp, dnsSecondaryIp, "1.1.1.1", "8.8.8.8")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val dnsStartNs = System.nanoTime()
        var lastDnsErr: String? = null
        val cacheKey = DnsSslEngineHelper.parseQuestionCacheKey(dnsPayload)

        fun emitDnsResponse(responsePayload: ByteArray, sourceLabel: String, storeInCache: Boolean = true) {
            if (storeInCache && cacheKey != null && responsePayload.size >= 12) {
                val rcode = responsePayload[3].toInt() and 0x0F
                if (rcode == 0) {
                    if (dnsCache.size > 256) {
                        val nowMs = System.currentTimeMillis()
                        dnsCache.entries.removeIf { it.value.expiresAtMs < nowMs }
                        if (dnsCache.size > 256) dnsCache.clear()
                    }
                    dnsCache[cacheKey] = CachedDnsEntry(
                        payload = responsePayload.copyOf(),
                        expiresAtMs = System.currentTimeMillis() + 30_000L
                    )
                }
            }
            val finalPayload = if (responsePayload.size <= maxUdpPayload) {
                responsePayload
            } else {
                responsePayload.copyOf(512.coerceAtMost(maxUdpPayload)).also { tcResp ->
                    if (tcResp.size > 2) tcResp[2] = (tcResp[2].toInt() or 0x02).toByte()
                }
            }
            val ipPacket = buildIpv4UdpPacket(
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = dstPort,
                dstPort = srcPort,
                payload = finalPayload
            )
            writePacketToTun(ipPacket, output)
            val ms = ((System.nanoTime() - dnsStartNs) / 1_000_000L).coerceAtLeast(1L)
            onDiagnostics {
                it.copy(
                    dnsOkCount = it.dnsOkCount + 1,
                    lastDnsDetail = "$sourceLabel (${ms}ms)"
                )
            }
            if (loggedFirstDnsOk.compareAndSet(false, true)) {
                onLog("DNS", "DNS resuelto vía $sourceLabel (${ms}ms)", LogLevel.SUCCESS)
            }
        }

        // 0. Fast path: short-TTL in-memory cache of previously verified DNS replies
        if (cacheKey != null) {
            val cached = dnsCache[cacheKey]
            if (cached != null) {
                if (System.currentTimeMillis() <= cached.expiresAtMs) {
                    emitDnsResponse(
                        responsePayload = DnsSslEngineHelper.rewriteTxId(cached.payload, dnsPayload),
                        sourceLabel = "Cache-DNS",
                        storeInCache = false
                    )
                    return
                } else {
                    dnsCache.remove(cacheKey, cached)
                }
            }
        }

        // If startup proxy/SSH probe is actively handshaking right now, wait briefly so first DNS queries use SSH
        if (activeSshClient == null && !vpsSupportsConnect) {
            val probe = startupProbeJob
            if (probe != null && probe.isActive) {
                kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    probe.join()
                }
            }
        }

        // When an SSH tunnel is active, resolve DNS over TCP :53 first, then DoH :443 over SSH direct-tcpip
        suspend fun resolveDnsViaSsh(ssh: SshTunnelClient): Boolean {
            for (dnsIp in candidates.take(3)) {
                var sshDns: SshTcpStream? = null
                var timeoutJob: Job? = null
                try {
                    sshDns = ssh.openDirectTcpIp(dnsIp, DNS_PORT, 2500)
                    if (sshDns != null) {
                        val activeChannel = sshDns
                        timeoutJob = scope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(2800L)
                            activeChannel.close()
                        }
                        val framedQuery = ByteArray(2 + dnsPayload.size)
                        framedQuery[0] = ((dnsPayload.size ushr 8) and 0xFF).toByte()
                        framedQuery[1] = (dnsPayload.size and 0xFF).toByte()
                        System.arraycopy(dnsPayload, 0, framedQuery, 2, dnsPayload.size)
                        activeChannel.outputStream.write(framedQuery)
                        activeChannel.outputStream.flush()

                        val inStream = activeChannel.inputStream
                        val lenHi = inStream.read()
                        val lenLo = inStream.read()
                        if (lenHi >= 0 && lenLo >= 0) {
                            val respLen = ((lenHi and 0xFF) shl 8) or (lenLo and 0xFF)
                            if (respLen in 12..4096) {
                                val responsePayload = ByteArray(respLen)
                                var readTotal = 0
                                while (readTotal < respLen) {
                                    val r = inStream.read(responsePayload, readTotal, respLen - readTotal)
                                    if (r <= 0) break
                                    readTotal += r
                                }
                                if (readTotal == respLen) {
                                    emitDnsResponse(responsePayload, "SSH-TCP $dnsIp")
                                    return true
                                }
                            }
                        }
                    } else {
                        lastDnsErr = "SSH direct-tcpip $dnsIp:53 rechazado"
                    }
                } catch (e: Exception) {
                    lastDnsErr = "SSH DNS $dnsIp: ${e.localizedMessage ?: e.javaClass.simpleName}"
                } finally {
                    timeoutJob?.cancel()
                    sshDns?.close()
                }
            }

            // Fallback inside SSH tunnel: DNS-over-HTTPS (port 443) when VPS or ISP blocks TCP :53
            for (dohHost in listOf("1.1.1.1", "8.8.8.8")) {
                val sshDoH = ssh.openDirectTcpIp(dohHost, 443, 3000) ?: continue
                try {
                    val dohResp = DnsSslEngineHelper.executeDoHOverStreams(
                        input = sshDoH.inputStream,
                        output = sshDoH.outputStream,
                        sniHost = dohHost,
                        dnsQuery = dnsPayload,
                        timeoutMs = 3500,
                        onTimeoutClose = { sshDoH.close() }
                    )
                    if (dohResp != null && dohResp.size >= 12) {
                        emitDnsResponse(dohResp, "SSH-DoH $dohHost:443")
                        return true
                    }
                } catch (e: Exception) {
                    lastDnsErr = "SSH DoH $dohHost: ${e.localizedMessage ?: e.javaClass.simpleName}"
                } finally {
                    sshDoH.close()
                }
            }
            return false
        }

        // 1. When SSH tunnel is active (or reconnectable for SSH :22), resolve DNS through SSH direct-tcpip
        var currentSsh = activeSshClient?.takeIf { it.isConnected() }
        if (currentSsh == null && (initialSshClient != null || vpsPort == 22)) {
            currentSsh = ensureSshFallbackConnected()
        }
        if (currentSsh != null && resolveDnsViaSsh(currentSsh)) {
            return
        }

        // 2. When HTTP CONNECT proxy is active, resolve DNS via proxy TCP:53 or DoH:443 INSIDE the VPS tunnel
        if (vpsSupportsConnect) {
            for (dnsIp in candidates.take(2)) {
                var tcpSocket: Socket? = null
                try {
                    tcpSocket = openProxyConnectSocket(dnsIp, DNS_PORT) ?: continue
                    tcpSocket.soTimeout = 2000
                    val framedQuery = ByteArray(2 + dnsPayload.size)
                    framedQuery[0] = ((dnsPayload.size ushr 8) and 0xFF).toByte()
                    framedQuery[1] = (dnsPayload.size and 0xFF).toByte()
                    System.arraycopy(dnsPayload, 0, framedQuery, 2, dnsPayload.size)
                    tcpSocket.getOutputStream().write(framedQuery)
                    tcpSocket.getOutputStream().flush()

                    val inStream = tcpSocket.getInputStream()
                    val lenHi = inStream.read()
                    val lenLo = inStream.read()
                    if (lenHi >= 0 && lenLo >= 0) {
                        val respLen = ((lenHi and 0xFF) shl 8) or (lenLo and 0xFF)
                        if (respLen in 12..4096) {
                            val responsePayload = ByteArray(respLen)
                            var readTotal = 0
                            while (readTotal < respLen) {
                                val r = inStream.read(responsePayload, readTotal, respLen - readTotal)
                                if (r <= 0) break
                                readTotal += r
                            }
                            if (readTotal == respLen) {
                                emitDnsResponse(responsePayload, "Proxy-TCP $dnsIp")
                                return
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastDnsErr = "Proxy-TCP $dnsIp: ${e.localizedMessage ?: e.javaClass.simpleName}"
                } finally {
                    try { tcpSocket?.close() } catch (_: Exception) {}
                }
            }

            // DoH over port 443 via proxy (works even when proxy blocks port 53)
            for (dohHost in listOf("1.1.1.1", "8.8.8.8")) {
                val dohPayload = resolveDnsViaProxyDoH(dnsPayload, dohHost)
                if (dohPayload != null) {
                    emitDnsResponse(dohPayload, "Proxy-DoH $dohHost:443")
                    return
                }
            }
        }

        // 3. Direct protected UDP/TCP fallback ONLY when allowDirectFallback is enabled
        if (allowDirectFallback) {
            for (dnsIp in candidates) {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    if (!bindAndProtectUdpSocket(socket)) {
                        onLog("DNS", "No se pudo proteger socket UDP DNS", LogLevel.WARNING)
                        return
                    }
                    socket.soTimeout = 750
                    val targetAddr = InetAddress.getByName(dnsIp)
                    socket.send(DatagramPacket(dnsPayload, dnsPayload.size, targetAddr, DNS_PORT))

                    val respBuf = ByteArray(4096)
                    val inDp = DatagramPacket(respBuf, respBuf.size)
                    socket.receive(inDp)

                    val receivedLen = inDp.length
                    if (receivedLen in 12..maxUdpPayload) {
                        emitDnsResponse(respBuf.copyOf(receivedLen), "UDP $dnsIp")
                        return
                    } else if (receivedLen > maxUdpPayload && dnsPayload.size >= 12) {
                        val tcResp = respBuf.copyOf(512.coerceAtMost(maxUdpPayload))
                        tcResp[2] = (tcResp[2].toInt() or 0x02).toByte()
                        emitDnsResponse(tcResp, "UDP-TC $dnsIp")
                        return
                    }
                } catch (e: Exception) {
                    lastDnsErr = "UDP $dnsIp: ${e.localizedMessage ?: e.javaClass.simpleName}"
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }

        // Return a real DNS SERVFAIL packet so Android apps don't stall waiting 10-15s per blocked domain
        DnsSslEngineHelper.buildServFailResponse(dnsPayload)?.let { servFail ->
            val ipPacket = buildIpv4UdpPacket(
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = dstPort,
                dstPort = srcPort,
                payload = servFail
            )
            writePacketToTun(ipPacket, output)
        }

        val reason = lastDnsErr ?: "Sin respuesta DNS por túnel en ${candidates.joinToString()}"
        onDiagnostics {
            it.copy(
                dnsFailCount = it.dnsFailCount + 1,
                lastDnsDetail = reason
            )
        }
        val now = System.currentTimeMillis()
        if (now - lastDnsFailLogAt.get() > 3_000L) {
            lastDnsFailLogAt.set(now)
            onLog("DNS", "Fallo DNS ($reason)", LogLevel.ERROR)
        }
    }

    private fun resolveDnsViaProxyDoH(dnsPayload: ByteArray, dohHost: String = "1.1.1.1"): ByteArray? {
        val rawProxy = openProxyConnectSocket(dohHost, 443) ?: return null
        var sslSocket: javax.net.ssl.SSLSocket? = null
        return try {
            val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            sslSocket = (factory.createSocket(rawProxy, dohHost, 443, true) as javax.net.ssl.SSLSocket).apply {
                soTimeout = 3000
                startHandshake()
            }
            val reqHeader = buildString {
                append("POST /dns-query HTTP/1.1\r\n")
                append("Host: $dohHost\r\n")
                append("Content-Type: application/dns-message\r\n")
                append("Accept: application/dns-message\r\n")
                append("Content-Length: ${dnsPayload.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = sslSocket.outputStream
            out.write(reqHeader.toByteArray(Charsets.US_ASCII))
            out.write(dnsPayload)
            out.flush()

            val inStream = sslSocket.inputStream
            val headerText = readHttpHeadersExact(inStream) ?: return null
            val statusCode = parseHttpStatusCode(headerText.lineSequence().firstOrNull())
            if (statusCode !in 200..299) return null

            val contentLenLine = headerText.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            val expectedLen = contentLenLine?.substringAfter(':')?.trim()?.toIntOrNull()
            if (expectedLen != null && expectedLen in 12..4096) {
                val body = ByteArray(expectedLen)
                var total = 0
                while (total < expectedLen) {
                    val r = inStream.read(body, total, expectedLen - total)
                    if (r <= 0) break
                    total += r
                }
                if (total == expectedLen) body else null
            } else {
                val body = inStream.readBytes()
                body.takeIf { it.size in 12..4096 }
            }
        } catch (_: Exception) {
            null
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawProxy.close() } catch (_: Exception) {}
        }
    }

    private fun parseHttpStatusCode(statusLine: String?): Int {
        if (statusLine.isNullOrBlank()) return -1
        val parts = statusLine.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) parts[1].toIntOrNull() ?: -1 else -1
    }

    private fun encodeBasicAuth(raw: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val input = raw.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(((input.size + 2) / 3) * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1
            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            out.append(if (b1 >= 0) alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)] else '=')
            out.append(if (b2 >= 0) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    companion object {
        private const val PROTOCOL_ICMP = 1
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        private const val PROTOCOL_ICMPV6 = 58
        private const val DNS_PORT = 53
        private const val MAX_TCP_SESSIONS = 256
        private const val MAX_UDP_SESSIONS = 128
        private const val UDP_IDLE_TIMEOUT_MS = 45_000L
        private const val MAX_OOO_WINDOW = 65_536
        private const val MAX_OOO_CHUNKS = 16

        private const val FLAG_FIN = 0x01
        private const val FLAG_SYN = 0x02
        private const val FLAG_RST = 0x04
        private const val FLAG_PSH = 0x08
        private const val FLAG_ACK = 0x10

        private fun readUShort(data: ByteArray, offset: Int): Int =
            ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

        private fun readUInt(data: ByteArray, offset: Int): Long =
            ((data[offset].toLong() and 0xFF) shl 24) or
                ((data[offset + 1].toLong() and 0xFF) shl 16) or
                ((data[offset + 2].toLong() and 0xFF) shl 8) or
                (data[offset + 3].toLong() and 0xFF)

        fun buildIpv6IcmpPortUnreachablePacket(
            originalPacket: ByteArray,
            maxQuotedTotal: Int = 512
        ): ByteArray? {
            if (originalPacket.size < 40) return null
            val quotedLen = originalPacket.size.coerceAtMost(maxQuotedTotal.coerceAtLeast(48))
            val ipHeaderLen = 40
            val icmpv6Len = 8 + quotedLen
            val buf = ByteBuffer.allocate(ipHeaderLen + icmpv6Len)

            val origSrcIp = originalPacket.copyOfRange(8, 24)
            val origDstIp = originalPacket.copyOfRange(24, 40)

            buf.putInt(0x60000000)
            buf.putShort(icmpv6Len.toShort())
            buf.put(PROTOCOL_ICMPV6.toByte())
            buf.put(64.toByte())
            buf.put(origDstIp)
            buf.put(origSrcIp)

            buf.put(1.toByte()) // Type 1: Destination Unreachable
            buf.put(4.toByte()) // Code 4: Port Unreachable
            buf.putShort(0.toShort())
            buf.putInt(0)
            buf.put(originalPacket, 0, quotedLen)

            val arr = buf.array()
            val checksum = computeIpv6TransportChecksum(
                packet = arr,
                offset = ipHeaderLen,
                length = icmpv6Len,
                nextHeader = PROTOCOL_ICMPV6,
                srcIp = origDstIp,
                dstIp = origSrcIp
            )
            arr[ipHeaderLen + 2] = ((checksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 3] = (checksum and 0xFF).toByte()
            return arr
        }

        fun buildIpv6TcpRstPacket(originalPacket: ByteArray): ByteArray? {
            if (originalPacket.size < 60) return null
            val ipHeaderLen = 40
            val origSrcIp = originalPacket.copyOfRange(8, 24)
            val origDstIp = originalPacket.copyOfRange(24, 40)
            val srcPort = readUShort(originalPacket, ipHeaderLen)
            val dstPort = readUShort(originalPacket, ipHeaderLen + 2)
            val seqNum = readUInt(originalPacket, ipHeaderLen + 4)
            val ackNum = readUInt(originalPacket, ipHeaderLen + 8)
            val dataOffset = ((originalPacket[ipHeaderLen + 12].toInt() ushr 4) and 0x0F) * 4
            if (dataOffset < 20 || originalPacket.size < ipHeaderLen + dataOffset) return null
            val flags = originalPacket[ipHeaderLen + 13].toInt() and 0x3F
            if ((flags and FLAG_RST) != 0) return null

            val origPayloadLen = originalPacket.size - (ipHeaderLen + dataOffset)
            val isSyn = (flags and FLAG_SYN) != 0
            val isFin = (flags and FLAG_FIN) != 0
            val hasAck = (flags and FLAG_ACK) != 0

            val respSeq = if (hasAck) ackNum else 0L
            val respAck = if (hasAck) 0L else (seqNum + origPayloadLen.toLong() + (if (isSyn) 1L else 0L) + (if (isFin) 1L else 0L)) and 0xFFFFFFFFL
            val respFlags = if (hasAck) FLAG_RST else (FLAG_RST or FLAG_ACK)

            val tcpHeaderLen = 20
            val buf = ByteBuffer.allocate(ipHeaderLen + tcpHeaderLen)
            buf.putInt(0x60000000)
            buf.putShort(tcpHeaderLen.toShort())
            buf.put(PROTOCOL_TCP.toByte())
            buf.put(64.toByte())
            buf.put(origDstIp)
            buf.put(origSrcIp)

            buf.putShort(dstPort.toShort())
            buf.putShort(srcPort.toShort())
            buf.putInt(respSeq.toInt())
            buf.putInt(respAck.toInt())
            buf.put(((5 shl 4) and 0xF0).toByte())
            buf.put((respFlags and 0x3F).toByte())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())

            val arr = buf.array()
            val checksum = computeIpv6TransportChecksum(
                packet = arr,
                offset = ipHeaderLen,
                length = tcpHeaderLen,
                nextHeader = PROTOCOL_TCP,
                srcIp = origDstIp,
                dstIp = origSrcIp
            )
            arr[ipHeaderLen + 16] = ((checksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 17] = (checksum and 0xFF).toByte()
            return arr
        }

        private fun computeIpv6TransportChecksum(
            packet: ByteArray,
            offset: Int,
            length: Int,
            nextHeader: Int,
            srcIp: ByteArray,
            dstIp: ByteArray
        ): Int {
            var sum = 0L
            for (i in 0 until 16 step 2) {
                sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
                sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
            }
            sum += (length.toLong() ushr 16) and 0xFFFF
            sum += length.toLong() and 0xFFFF
            sum += nextHeader.toLong()

            var i = offset
            val end = offset + length
            while (i + 1 < end) {
                sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (i < end) {
                sum += (packet[i].toInt() and 0xFF) shl 8
            }
            while ((sum ushr 16) != 0L) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            return (sum.inv() and 0xFFFF).toInt()
        }

        fun buildIpv4IcmpPortUnreachablePacket(
            originalPacket: ByteArray,
            originalIhlBytes: Int
        ): ByteArray? {
            val quotedLen = (originalIhlBytes + 8).coerceAtMost(originalPacket.size)
            if (quotedLen < 28) return null
            val ipHeaderLen = 20
            val icmpLen = 8 + quotedLen
            val totalLen = ipHeaderLen + icmpLen
            val buf = ByteBuffer.allocate(totalLen)

            val origSrcIp = originalPacket.copyOfRange(12, 16)
            val origDstIp = originalPacket.copyOfRange(16, 20)

            buf.put(0x45.toByte())
            buf.put(0x00.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0x0000.toShort())
            buf.putShort(0x4000.toShort())
            buf.put(64.toByte())
            buf.put(PROTOCOL_ICMP.toByte())
            buf.putShort(0.toShort())
            buf.put(origDstIp)
            buf.put(origSrcIp)

            buf.put(3.toByte()) // Type 3: Destination Unreachable
            buf.put(3.toByte()) // Code 3: Port Unreachable
            buf.putShort(0.toShort()) // Checksum placeholder
            buf.putInt(0) // Unused 4 bytes
            buf.put(originalPacket, 0, quotedLen)

            val arr = buf.array()
            val ipChecksum = computeChecksum(arr, 0, ipHeaderLen)
            arr[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
            arr[11] = (ipChecksum and 0xFF).toByte()

            val icmpChecksum = computeChecksum(arr, ipHeaderLen, icmpLen)
            arr[ipHeaderLen + 2] = ((icmpChecksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 3] = (icmpChecksum and 0xFF).toByte()

            return arr
        }

        fun buildIpv4UdpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            payload: ByteArray
        ): ByteArray {
            val ipHeaderLen = 20
            val udpLen = 8 + payload.size
            val totalLen = ipHeaderLen + udpLen
            val buf = ByteBuffer.allocate(totalLen)

            buf.put(0x45.toByte())
            buf.put(0x00.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0x0000.toShort())
            buf.putShort(0x4000.toShort())
            buf.put(64.toByte())
            buf.put(PROTOCOL_UDP.toByte())
            buf.putShort(0.toShort())
            buf.put(srcIp)
            buf.put(dstIp)

            buf.position(ipHeaderLen)
            buf.putShort(srcPort.toShort())
            buf.putShort(dstPort.toShort())
            buf.putShort(udpLen.toShort())
            buf.putShort(0.toShort())
            buf.put(payload)

            val arr = buf.array()
            val ipChecksum = computeChecksum(arr, 0, ipHeaderLen)
            arr[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
            arr[11] = (ipChecksum and 0xFF).toByte()

            val udpChecksum = computeTransportChecksum(
                packet = arr,
                offset = ipHeaderLen,
                length = udpLen,
                protocol = PROTOCOL_UDP,
                srcIp = srcIp,
                dstIp = dstIp
            ).let { if (it == 0) 0xFFFF else it }
            arr[ipHeaderLen + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 7] = (udpChecksum and 0xFF).toByte()

            return arr
        }

        private fun buildIpv4TcpPacketWithMss(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            seqNum: Long,
            ackNum: Long,
            flags: Int,
            mss: Int
        ): ByteArray {
            val ipHeaderLen = 20
            val tcpHeaderLen = 24
            val totalLen = ipHeaderLen + tcpHeaderLen
            val buf = ByteBuffer.allocate(totalLen)

            buf.put(0x45.toByte())
            buf.put(0x00.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0x0000.toShort())
            buf.putShort(0x4000.toShort())
            buf.put(64.toByte())
            buf.put(PROTOCOL_TCP.toByte())
            buf.putShort(0.toShort())
            buf.put(srcIp)
            buf.put(dstIp)

            buf.putShort(srcPort.toShort())
            buf.putShort(dstPort.toShort())
            buf.putInt(seqNum.toInt())
            buf.putInt(ackNum.toInt())
            buf.put(((6 shl 4) and 0xF0).toByte())
            buf.put((flags and 0x3F).toByte())
            buf.putShort(65535.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())
            // TCP Option: Maximum Segment Size (Kind=2, Length=4, Value=mss)
            buf.put(0x02.toByte())
            buf.put(0x04.toByte())
            buf.putShort(mss.coerceIn(512, 1460).toShort())

            val arr = buf.array()
            val ipChecksum = computeChecksum(arr, 0, ipHeaderLen)
            arr[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
            arr[11] = (ipChecksum and 0xFF).toByte()

            val tcpChecksum = computeTransportChecksum(arr, ipHeaderLen, tcpHeaderLen, PROTOCOL_TCP, srcIp, dstIp)
            arr[ipHeaderLen + 16] = ((tcpChecksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 17] = (tcpChecksum and 0xFF).toByte()

            return arr
        }

        fun buildIpv4TcpPacket(
            srcIp: ByteArray,
            dstIp: ByteArray,
            srcPort: Int,
            dstPort: Int,
            seqNum: Long,
            ackNum: Long,
            flags: Int,
            payload: ByteArray
        ): ByteArray {
            val ipHeaderLen = 20
            val tcpHeaderLen = 20
            val totalLen = ipHeaderLen + tcpHeaderLen + payload.size
            val buf = ByteBuffer.allocate(totalLen)

            buf.put(0x45.toByte())
            buf.put(0x00.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0x0000.toShort())
            buf.putShort(0x4000.toShort())
            buf.put(64.toByte())
            buf.put(PROTOCOL_TCP.toByte())
            buf.putShort(0.toShort())
            buf.put(srcIp)
            buf.put(dstIp)

            buf.putShort(srcPort.toShort())
            buf.putShort(dstPort.toShort())
            buf.putInt(seqNum.toInt())
            buf.putInt(ackNum.toInt())
            buf.put(((5 shl 4) and 0xF0).toByte())
            buf.put((flags and 0x3F).toByte())
            buf.putShort(65535.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())
            buf.put(payload)

            val arr = buf.array()
            val ipChecksum = computeChecksum(arr, 0, ipHeaderLen)
            arr[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
            arr[11] = (ipChecksum and 0xFF).toByte()

            val tcpChecksum = computeTransportChecksum(
                packet = arr,
                offset = ipHeaderLen,
                length = tcpHeaderLen + payload.size,
                protocol = PROTOCOL_TCP,
                srcIp = srcIp,
                dstIp = dstIp
            )
            arr[ipHeaderLen + 16] = ((tcpChecksum ushr 8) and 0xFF).toByte()
            arr[ipHeaderLen + 17] = (tcpChecksum and 0xFF).toByte()

            return arr
        }

        private fun computeTransportChecksum(
            packet: ByteArray,
            offset: Int,
            length: Int,
            protocol: Int,
            srcIp: ByteArray,
            dstIp: ByteArray
        ): Int {
            var sum = 0L
            sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
            sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
            sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
            sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
            sum += protocol.toLong()
            sum += length.toLong()

            var i = offset
            val end = offset + length
            while (i + 1 < end) {
                sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (i < end) {
                sum += (packet[i].toInt() and 0xFF) shl 8
            }
            while ((sum ushr 16) != 0L) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            return (sum.inv() and 0xFFFF).toInt()
        }

        private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var i = offset
            val end = offset + length
            while (i + 1 < end) {
                val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                sum += word
                i += 2
            }
            if (i < end) {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
            while ((sum ushr 16) != 0L) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            return (sum.inv() and 0xFFFF).toInt()
        }
    }
}
