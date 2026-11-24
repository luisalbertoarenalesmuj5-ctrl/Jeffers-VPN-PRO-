package com.example.vpn

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

internal object DnsSslEngineHelper {

    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "jeffers-doh-timeout").apply { isDaemon = true }
    }

    fun executeDoHOverStreams(
        input: InputStream,
        output: OutputStream,
        sniHost: String,
        dnsQuery: ByteArray,
        timeoutMs: Int = 3500,
        sslContext: SSLContext = SSLContext.getDefault(),
        onTimeoutClose: (() -> Unit)? = null
    ): ByteArray? {
        val engine = sslContext.createSSLEngine(sniHost, 443).apply {
            useClientMode = true
        }

        val timeoutTask = if (onTimeoutClose != null) {
            timeoutScheduler.schedule({
                try {
                    onTimeoutClose()
                } catch (_: Exception) {
                }
            }, timeoutMs.toLong().coerceAtLeast(500L), TimeUnit.MILLISECONDS)
        } else {
            null
        }

        return try {
            val session = engine.session
            var myNetBuf = ByteBuffer.allocate(session.packetBufferSize.coerceAtLeast(16_384))
            var peerNetBuf = ByteBuffer.allocate(session.packetBufferSize.coerceAtLeast(16_384)).apply { limit(0) }
            var peerAppBuf = ByteBuffer.allocate(session.applicationBufferSize.coerceAtLeast(16_384))
            val emptyAppBuf = ByteBuffer.allocate(0)

            engine.beginHandshake()
            var hsStatus = engine.handshakeStatus
            val deadline = System.currentTimeMillis() + timeoutMs.toLong().coerceAtLeast(500L)

            while (hsStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                hsStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
            ) {
                if (System.currentTimeMillis() > deadline) return null
                when (hsStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                        myNetBuf.clear()
                        val res = engine.wrap(emptyAppBuf, myNetBuf)
                        hsStatus = res.handshakeStatus
                        when (res.status) {
                            SSLEngineResult.Status.OK -> {
                                myNetBuf.flip()
                                if (myNetBuf.hasRemaining()) {
                                    val chunk = ByteArray(myNetBuf.remaining())
                                    myNetBuf.get(chunk)
                                    output.write(chunk)
                                    output.flush()
                                }
                            }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                myNetBuf = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(myNetBuf.capacity() * 2))
                            }
                            SSLEngineResult.Status.CLOSED -> return null
                            else -> {}
                        }
                    }

                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                        if (!peerNetBuf.hasRemaining()) {
                            val readOk = readMoreIntoPeerNetBuffer(input, peerNetBuf)
                            if (!readOk) return null
                        }
                        while (true) {
                            peerAppBuf.clear()
                            val res = engine.unwrap(peerNetBuf, peerAppBuf)
                            hsStatus = res.handshakeStatus
                            when (res.status) {
                                SSLEngineResult.Status.OK -> break
                                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                                    if (peerNetBuf.capacity() < engine.session.packetBufferSize) {
                                        val grown = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(peerNetBuf.capacity() * 2))
                                        grown.put(peerNetBuf)
                                        grown.flip()
                                        peerNetBuf = grown
                                    }
                                    val readOk = readMoreIntoPeerNetBuffer(input, peerNetBuf)
                                    if (!readOk) return null
                                }
                                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                    peerAppBuf = ByteBuffer.allocate(engine.session.applicationBufferSize.coerceAtLeast(peerAppBuf.capacity() * 2))
                                }
                                SSLEngineResult.Status.CLOSED -> return null
                            }
                        }
                    }

                    SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                        var task = engine.delegatedTask
                        while (task != null) {
                            task.run()
                            task = engine.delegatedTask
                        }
                        hsStatus = engine.handshakeStatus
                    }

                    else -> break
                }
            }

            // Send HTTP POST /dns-query
            val headerBytes = buildString {
                append("POST /dns-query HTTP/1.1\r\n")
                append("Host: $sniHost\r\n")
                append("Content-Type: application/dns-message\r\n")
                append("Accept: application/dns-message\r\n")
                append("Content-Length: ${dnsQuery.size}\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)
            val requestBytes = ByteArray(headerBytes.size + dnsQuery.size)
            System.arraycopy(headerBytes, 0, requestBytes, 0, headerBytes.size)
            System.arraycopy(dnsQuery, 0, requestBytes, headerBytes.size, dnsQuery.size)

            val appOut = ByteBuffer.wrap(requestBytes)
            while (appOut.hasRemaining()) {
                myNetBuf.clear()
                val res = engine.wrap(appOut, myNetBuf)
                when (res.status) {
                    SSLEngineResult.Status.OK -> {
                        myNetBuf.flip()
                        if (myNetBuf.hasRemaining()) {
                            val chunk = ByteArray(myNetBuf.remaining())
                            myNetBuf.get(chunk)
                            output.write(chunk)
                        }
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        myNetBuf = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(myNetBuf.capacity() * 2))
                    }
                    else -> return null
                }
            }
            output.flush()

            // Read decrypted HTTP response
            val plainOut = ByteArrayOutputStream()
            var headerEndIdx = -1
            var contentLength = -1
            var statusCode = -1
            var isChunked = false

            while (System.currentTimeMillis() <= deadline) {
                if (!peerNetBuf.hasRemaining()) {
                    val readOk = readMoreIntoPeerNetBuffer(input, peerNetBuf)
                    if (!readOk) break
                }

                peerAppBuf.clear()
                val res = engine.unwrap(peerNetBuf, peerAppBuf)
                when (res.status) {
                    SSLEngineResult.Status.OK -> {
                        peerAppBuf.flip()
                        if (peerAppBuf.hasRemaining()) {
                            val plainChunk = ByteArray(peerAppBuf.remaining())
                            peerAppBuf.get(plainChunk)
                            plainOut.write(plainChunk)

                            val raw = plainOut.toByteArray()
                            if (headerEndIdx < 0) {
                                headerEndIdx = findHeaderEnd(raw)
                                if (headerEndIdx > 0) {
                                    val headerText = String(raw, 0, headerEndIdx, Charsets.US_ASCII)
                                    statusCode = VpsReachabilityTester.parseHttpStatusCode(
                                        headerText.lineSequence().firstOrNull()
                                    )
                                    if (statusCode !in 200..299) return null
                                    contentLength = headerText.lineSequence()
                                        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                                        ?.substringAfter(':')
                                        ?.trim()
                                        ?.toIntOrNull()
                                        ?: -1
                                    isChunked = headerText.lineSequence()
                                        .any {
                                            it.startsWith("Transfer-Encoding:", ignoreCase = true) &&
                                                it.contains("chunked", ignoreCase = true)
                                        }
                                }
                            }
                            if (headerEndIdx > 0 && contentLength in 12..4096) {
                                val availableBody = raw.size - headerEndIdx
                                if (availableBody >= contentLength) {
                                    return raw.copyOfRange(headerEndIdx, headerEndIdx + contentLength)
                                }
                            }
                        }
                    }
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        if (peerNetBuf.capacity() < engine.session.packetBufferSize) {
                            val grown = ByteBuffer.allocate(engine.session.packetBufferSize.coerceAtLeast(peerNetBuf.capacity() * 2))
                            grown.put(peerNetBuf)
                            grown.flip()
                            peerNetBuf = grown
                        }
                        val readOk = readMoreIntoPeerNetBuffer(input, peerNetBuf)
                        if (!readOk) break
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        peerAppBuf = ByteBuffer.allocate(engine.session.applicationBufferSize.coerceAtLeast(peerAppBuf.capacity() * 2))
                    }
                    SSLEngineResult.Status.CLOSED -> break
                }
            }

            val allBytes = plainOut.toByteArray()
            if (headerEndIdx <= 0 || statusCode !in 200..299) return null
            val body = allBytes.copyOfRange(headerEndIdx, allBytes.size)
            val resolvedBody = if (isChunked) decodeChunkedBody(body) else body
            return resolvedBody.takeIf { it.size in 12..4096 }
        } catch (_: Exception) {
            null
        } finally {
            timeoutTask?.cancel(false)
            try {
                engine.closeOutbound()
            } catch (_: Exception) {
            }
        }
    }

    private fun readMoreIntoPeerNetBuffer(input: InputStream, peerNetBuf: ByteBuffer): Boolean {
        peerNetBuf.compact()
        val tmp = ByteArray(peerNetBuf.remaining().coerceAtMost(8192))
        val n = try {
            input.read(tmp)
        } catch (_: Exception) {
            -1
        }
        if (n <= 0) {
            peerNetBuf.flip()
            return false
        }
        peerNetBuf.put(tmp, 0, n)
        peerNetBuf.flip()
        return true
    }

    private fun findHeaderEnd(raw: ByteArray): Int {
        for (i in 0..raw.size - 4) {
            if (raw[i] == '\r'.code.toByte() &&
                raw[i + 1] == '\n'.code.toByte() &&
                raw[i + 2] == '\r'.code.toByte() &&
                raw[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
        }
        for (i in 0..raw.size - 2) {
            if (raw[i] == '\n'.code.toByte() && raw[i + 1] == '\n'.code.toByte()) {
                return i + 2
            }
        }
        return -1
    }

    private fun decodeChunkedBody(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < raw.size) {
            var lineEnd = -1
            for (i in pos until raw.size - 1) {
                if (raw[i] == '\r'.code.toByte() && raw[i + 1] == '\n'.code.toByte()) {
                    lineEnd = i
                    break
                }
            }
            if (lineEnd < 0) break
            val sizeHex = String(raw, pos, lineEnd - pos, Charsets.US_ASCII).substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: break
            if (chunkSize == 0) break
            val dataStart = lineEnd + 2
            val dataEnd = (dataStart + chunkSize).coerceAtMost(raw.size)
            out.write(raw, dataStart, dataEnd - dataStart)
            pos = dataEnd + 2
        }
        return out.toByteArray()
    }

    fun parseQuestionCacheKey(dnsPayload: ByteArray): String? {
        if (dnsPayload.size < 18) return null
        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdCount != 1) return null

        val sb = StringBuilder()
        var pos = 12
        var guard = 0
        while (pos < dnsPayload.size && guard < 128) {
            guard++
            val len = dnsPayload[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) != 0) return null // pointer in query not expected
            pos++
            if (pos + len > dnsPayload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dnsPayload, pos, len, Charsets.US_ASCII).lowercase())
            pos += len
        }
        if (pos + 4 > dnsPayload.size || sb.isEmpty()) return null
        val qtype = ((dnsPayload[pos].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF)
        val qclass = ((dnsPayload[pos + 2].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 3].toInt() and 0xFF)
        return "$sb:$qtype:$qclass"
    }

    fun rewriteTxId(cachedResponse: ByteArray, queryPacket: ByteArray): ByteArray {
        val copy = cachedResponse.copyOf()
        if (copy.size >= 2 && queryPacket.size >= 2) {
            copy[0] = queryPacket[0]
            copy[1] = queryPacket[1]
        }
        return copy
    }

    fun buildServFailResponse(queryPacket: ByteArray): ByteArray? {
        if (queryPacket.size < 12) return null
        // Extract header + single question if cleanly parseable, else echo header
        var qEnd = 12
        val qdCount = ((queryPacket[4].toInt() and 0xFF) shl 8) or (queryPacket[5].toInt() and 0xFF)
        if (qdCount == 1) {
            var pos = 12
            while (pos < queryPacket.size) {
                val len = queryPacket[pos].toInt() and 0xFF
                pos++
                if (len == 0) break
                if ((len and 0xC0) != 0 || pos + len > queryPacket.size) {
                    pos = 12
                    break
                }
                pos += len
            }
            if (pos > 12 && pos + 4 <= queryPacket.size) {
                qEnd = pos + 4
            }
        }
        val resp = queryPacket.copyOf(qEnd)
        val rdBit = resp[2].toInt() and 0x01
        resp[2] = (0x80 or rdBit).toByte() // QR=1, Opcode=0, AA=0, TC=0, RD preserved
        resp[3] = 0x02.toByte() // RA=0, Z=0, RCODE=2 (SERVFAIL)
        if (qEnd == 12) {
            resp[4] = 0; resp[5] = 0 // QDCOUNT = 0
        }
        resp[6] = 0; resp[7] = 0 // ANCOUNT = 0
        resp[8] = 0; resp[9] = 0 // NSCOUNT = 0
        resp[10] = 0; resp[11] = 0 // ARCOUNT = 0
        return resp
    }
}
