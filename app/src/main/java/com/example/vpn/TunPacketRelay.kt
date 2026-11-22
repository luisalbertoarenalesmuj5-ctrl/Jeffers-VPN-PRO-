package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class TunPacketRelay(
    private val vpnService: VpnService,
    private val tunFd: ParcelFileDescriptor,
    private val dnsPrimaryIp: String,
    private val dnsSecondaryIp: String,
    private val vpsHost: String,
    private val vpsPort: Int,
    private val vpsPassword: String,
    private val scope: CoroutineScope,
    private val mtu: Int = 1400
) {
    val txBytes = AtomicLong(0L)
    val rxBytes = AtomicLong(0L)

    private val maxTcpPayload = (mtu - 40).coerceIn(512, 1460)
    private val maxUdpPayload = (mtu - 28).coerceIn(512, 1472)

    private val writeLock = Any()
    private val tcpSessions = ConcurrentHashMap<TcpSessionKey, TcpSession>()
    private val udpSessions = ConcurrentHashMap<UdpSessionKey, UdpSession>()

    @Volatile
    private var vpsConnectCooldownUntil: Long = 0L

    @Volatile
    private var loggedDirectFallback: Boolean = false

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

    private class TcpSession(
        val key: TcpSessionKey,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        var clientNextSeq: Long,
        var serverSeq: Long,
        val seqLock: Any = Any(),
        val outboundChannel: Channel<OutboundOp> = Channel(Channel.UNLIMITED),
        val outOfOrder: LinkedHashMap<Long, ByteArray> = LinkedHashMap(),
        var pendingFinSeq: Long? = null,
        var clientFinReceived: Boolean = false,
        var serverFinSent: Boolean = false,
        @Volatile var socket: Socket? = null,
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
        val input = FileInputStream(tunFd.fileDescriptor)
        val output = FileOutputStream(tunFd.fileDescriptor)
        val buffer = ByteArray(32767)

        val reaperJob = scope.launch(Dispatchers.IO) {
            while (scope.isActive) {
                kotlinx.coroutines.delay(15_000L)
                reapIdleUdpSessions()
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
    }

    private fun closeTcpSession(session: TcpSession) {
        session.closed = true
        session.outboundChannel.close()
        session.writerJob?.cancel()
        try { session.socket?.close() } catch (_: Exception) {}
    }

    private fun handleOutboundPacket(packet: ByteArray, output: FileOutputStream) {
        if (packet.size < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
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

    private fun handleTcpPacket(packet: ByteArray, ihlBytes: Int, output: FileOutputStream) {
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = readUShort(packet, ihlBytes)
        val dstPort = readUShort(packet, ihlBytes + 2)
        val seqNum = readUInt(packet, ihlBytes + 4)
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
            tcpSessions.remove(key)?.let { old -> closeTcpSession(old) }
            if (tcpSessions.size >= MAX_TCP_SESSIONS) {
                reapOldestTcpSession()
            }

            val initialServerSeq = Random.nextLong(1000L, 999_999_999L)
            val session = TcpSession(
                key = key,
                srcIp = srcIp,
                dstIp = dstIp,
                clientNextSeq = (seqNum + 1L) and 0xFFFFFFFFL,
                serverSeq = initialServerSeq
            )
            tcpSessions[key] = session

            scope.launch(Dispatchers.IO) {
                connectAndBridgeTcpSession(session, output)
            }
            return
        }

        val session = tcpSessions[key] ?: return
        session.lastActivityAt = System.currentTimeMillis()

        if (isRst) {
            closeTcpSession(session)
            tcpSessions.remove(key)
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

    private fun connectAndBridgeTcpSession(session: TcpSession, output: FileOutputStream) {
        val dstHost = InetAddress.getByAddress(session.dstIp).hostAddress ?: return
        var activeSocket: Socket? = null
        try {
            val now = System.currentTimeMillis()
            val shouldTryVpsConnect = vpsPort != 22 && now >= vpsConnectCooldownUntil

            if (shouldTryVpsConnect) {
                val proxySocket = Socket()
                try {
                    if (!vpnService.protect(proxySocket)) {
                        closeSessionWithRst(session, output)
                        return
                    }
                    proxySocket.tcpNoDelay = true
                    proxySocket.keepAlive = true
                    proxySocket.connect(InetSocketAddress(vpsHost, vpsPort), 2500)
                    proxySocket.soTimeout = 2500

                    val connectReq = buildString {
                        append("CONNECT $dstHost:${session.key.dstPort} HTTP/1.1\r\n")
                        append("Host: $dstHost:${session.key.dstPort}\r\n")
                        append("X-VPS-Auth: $vpsPassword\r\n")
                        append("Proxy-Connection: Keep-Alive\r\n\r\n")
                    }
                    proxySocket.getOutputStream().write(connectReq.toByteArray(Charsets.UTF_8))
                    proxySocket.getOutputStream().flush()

                    val headerText = readHttpHeadersExact(proxySocket.getInputStream())
                    if (headerText != null && headerText.lineSequence().firstOrNull()?.contains(" 200 ") == true) {
                        activeSocket = proxySocket
                    } else {
                        try { proxySocket.close() } catch (_: Exception) {}
                        vpsConnectCooldownUntil = System.currentTimeMillis() + 20_000L
                    }
                } catch (_: Exception) {
                    try { proxySocket.close() } catch (_: Exception) {}
                    vpsConnectCooldownUntil = System.currentTimeMillis() + 20_000L
                }
            }

            if (activeSocket == null) {
                if (!loggedDirectFallback && vpsPort != 22) {
                    loggedDirectFallback = true
                    VpnStateHolder.appendLog(
                        "RELAY",
                        "Puerto $vpsPort no actúa como proxy CONNECT; enrutando tráfico por socket protegido",
                        LogLevel.INFO
                    )
                }
                val directSocket = Socket()
                if (!vpnService.protect(directSocket)) {
                    closeSessionWithRst(session, output)
                    return
                }
                directSocket.tcpNoDelay = true
                directSocket.keepAlive = true
                directSocket.connect(InetSocketAddress(dstHost, session.key.dstPort), 4500)
                activeSocket = directSocket
            }

            activeSocket.soTimeout = 0
            activeSocket.tcpNoDelay = true
            activeSocket.keepAlive = true
            session.socket = activeSocket
            session.connected = true

            session.writerJob = scope.launch(Dispatchers.IO) {
                val out = activeSocket.getOutputStream()
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
                                try {
                                    activeSocket.shutdownOutput()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    closeSessionWithRst(session, output)
                }
            }

            // Send SYN+ACK with clamped MSS option so client respects TUN MTU
            sendTcpSynAckPacket(session, maxTcpPayload, output)

            // Relay downstream bytes from upstream socket back to TUN
            val inStream = activeSocket.getInputStream()
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
        } catch (_: Exception) {
            closeSessionWithRst(session, output)
        } finally {
            closeTcpSession(session)
            tcpSessions.remove(session.key)
        }
    }

    private fun readHttpHeadersExact(input: java.io.InputStream): String? {
        val bytes = ByteArray(4096)
        var count = 0
        while (count < bytes.size) {
            val b = input.read()
            if (b < 0) return null
            bytes[count++] = b.toByte()
            if (count >= 4 &&
                bytes[count - 4] == '\r'.code.toByte() &&
                bytes[count - 3] == '\n'.code.toByte() &&
                bytes[count - 2] == '\r'.code.toByte() &&
                bytes[count - 1] == '\n'.code.toByte()
            ) {
                return String(bytes, 0, count, Charsets.UTF_8)
            }
        }
        return null
    }

    private fun sendTcpSynAckPacket(session: TcpSession, mss: Int, output: FileOutputStream) {
        val packet = synchronized(session.seqLock) {
            val pkt = buildIpv4TcpPacketWithMss(
                srcIp = session.dstIp,
                dstIp = session.srcIp,
                srcPort = session.key.dstPort,
                dstPort = session.key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = FLAG_SYN or FLAG_ACK,
                mss = mss
            )
            session.serverSeq = (session.serverSeq + 1L) and 0xFFFFFFFFL
            pkt
        }
        writePacketToTun(packet, output)
    }

    private fun sendTcpDataPacket(session: TcpSession, payload: ByteArray, output: FileOutputStream) {
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

    private fun sendTcpFinAckPacket(session: TcpSession, output: FileOutputStream) {
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

    private fun closeSessionWithRst(session: TcpSession, output: FileOutputStream) {
        if (!session.closed) {
            sendTcpControlPacket(session, FLAG_RST or FLAG_ACK, ByteArray(0), output)
        }
        closeTcpSession(session)
        tcpSessions.remove(session.key)
    }

    private fun sendTcpControlPacket(
        session: TcpSession,
        flags: Int,
        payload: ByteArray,
        output: FileOutputStream
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

    private fun writePacketToTun(packet: ByteArray, output: FileOutputStream) {
        synchronized(writeLock) {
            try {
                output.write(packet)
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
        output: FileOutputStream
    ) {
        val udpOffset = ihlBytes
        val udpLength = readUShort(packet, udpOffset + 4)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtMost(packet.size - payloadOffset)
        if (payloadLength <= 0 || payloadLength > maxUdpPayload) return

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
                    if (!vpnService.protect(it)) {
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
                created.readerJob = scope.launch(Dispatchers.IO) {
                    val buf = ByteArray(maxUdpPayload)
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
                        udpSessions.remove(key)
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

    private fun forwardDnsQueryWithFailover(
        requestPacket: ByteArray,
        ihlBytes: Int,
        output: FileOutputStream
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

        val candidates = listOf(dnsPrimaryIp, dnsSecondaryIp, "8.8.8.8", "1.1.1.1").distinct()
        for (dnsIp in candidates) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                if (!vpnService.protect(socket)) {
                    VpnStateHolder.appendLog("DNS", "No se pudo proteger socket UDP DNS", LogLevel.WARNING)
                    return
                }
                socket.soTimeout = 1800
                val targetAddr = InetAddress.getByName(dnsIp)
                socket.send(DatagramPacket(dnsPayload, dnsPayload.size, targetAddr, DNS_PORT))

                val respBuf = ByteArray(maxUdpPayload)
                val inDp = DatagramPacket(respBuf, respBuf.size)
                socket.receive(inDp)

                val responsePayload = respBuf.copyOf(inDp.length)
                val ipPacket = buildIpv4UdpPacket(
                    srcIp = dstIp,
                    dstIp = srcIp,
                    srcPort = dstPort,
                    dstPort = srcPort,
                    payload = responsePayload
                )
                writePacketToTun(ipPacket, output)
                return
            } catch (_: Exception) {
                // Failover to next DNS server
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        // Fallback: DNS-over-TCP when carrier blocks UDP port 53
        for (dnsIp in candidates) {
            var tcpSocket: Socket? = null
            try {
                tcpSocket = Socket()
                if (!vpnService.protect(tcpSocket)) return
                tcpSocket.tcpNoDelay = true
                tcpSocket.connect(InetSocketAddress(dnsIp, DNS_PORT), 2200)
                tcpSocket.soTimeout = 2200

                val framedQuery = ByteArray(2 + dnsPayload.size)
                framedQuery[0] = ((dnsPayload.size ushr 8) and 0xFF).toByte()
                framedQuery[1] = (dnsPayload.size and 0xFF).toByte()
                System.arraycopy(dnsPayload, 0, framedQuery, 2, dnsPayload.size)
                tcpSocket.getOutputStream().write(framedQuery)
                tcpSocket.getOutputStream().flush()

                val inStream = tcpSocket.getInputStream()
                val lenHi = inStream.read()
                val lenLo = inStream.read()
                if (lenHi < 0 || lenLo < 0) continue
                val respLen = ((lenHi and 0xFF) shl 8) or (lenLo and 0xFF)
                if (respLen <= 0 || respLen > maxUdpPayload) continue

                val responsePayload = ByteArray(respLen)
                var readTotal = 0
                while (readTotal < respLen) {
                    val r = inStream.read(responsePayload, readTotal, respLen - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                if (readTotal == respLen) {
                    val ipPacket = buildIpv4UdpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        payload = responsePayload
                    )
                    writePacketToTun(ipPacket, output)
                    return
                }
            } catch (_: Exception) {
            } finally {
                try { tcpSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
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
