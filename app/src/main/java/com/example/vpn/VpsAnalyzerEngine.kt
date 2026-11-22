package com.example.vpn

import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs

enum class VpsHealthGrade(val label: String, val colorHex: Long) {
    OPTIMAL("ÓPTIMO", 0xFF39D353),
    STABLE("ESTABLE", 0xFF00D4FF),
    UNSTABLE("INESTABLE", 0xFFF59E0B),
    OFFLINE("SIN RESPUESTA", 0xFFFF5252)
}

data class VpsPortProbe(
    val port: Int,
    val serviceLabel: String,
    val isOpen: Boolean,
    val latencyMs: Int?
)

data class VpsAnalysisReport(
    val host: String,
    val targetPort: Int,
    val resolvedIps: List<String>,
    val reverseDns: String?,
    val tcpSamplesMs: List<Int>,
    val minLatencyMs: Int?,
    val avgLatencyMs: Int?,
    val maxLatencyMs: Int?,
    val jitterMs: Int?,
    val packetLossPercent: Int,
    val portProbes: List<VpsPortProbe>,
    val bhttpStatusLine: String?,
    val bhttpTtfbMs: Int?,
    val bhttpHeaders: List<String>,
    val detectedServerStack: String,
    val tlsProtocol: String?,
    val tlsCipher: String?,
    val score: Int,
    val grade: VpsHealthGrade,
    val recommendations: List<String>,
    val analyzedAtMillis: Long = System.currentTimeMillis()
)

object VpsAnalyzerEngine {

    private val COMMON_VPN_PORTS = listOf(
        80 to "Claro / HTTP",
        8080 to "Tigo / BHTTP",
        443 to "SSL / TLS",
        8443 to "SSL Alt",
        3128 to "Squid Proxy",
        22 to "OpenSSH"
    )

    suspend fun analyze(
        rawHost: String,
        targetPort: Int,
        vpsName: String,
        password: String,
        sniHost: String,
        bhttpPayload: String
    ): VpsAnalysisReport = withContext(Dispatchers.IO) {
        val host = rawHost.trim()
        val safePort = targetPort.coerceIn(1, 65535)

        // 1. DNS Resolution & Reverse DNS
        val resolvedAddresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (_: Exception) {
            emptyList()
        }
        val resolvedIps = resolvedAddresses.mapNotNull { it.hostAddress }.distinct()
        val primaryIp = resolvedIps.firstOrNull() ?: host
        val reverseDns = resolvedAddresses.firstOrNull()?.canonicalHostName?.takeIf { it != primaryIp }

        // 2. Multi-probe TCP RTT stability test (3 attempts)
        val samples = mutableListOf<Int>()
        repeat(3) {
            probeTcpLatency(primaryIp, safePort, timeoutMs = 2500)?.let { samples.add(it) }
        }
        val packetLoss = (((3 - samples.size) * 100) / 3).coerceIn(0, 100)
        val minMs = samples.minOrNull()
        val maxMs = samples.maxOrNull()
        val avgMs = if (samples.isNotEmpty()) samples.average().toInt() else null
        val jitterMs = if (samples.size >= 2) {
            samples.zipWithNext { a, b -> abs(a - b) }.average().toInt()
        } else if (samples.size == 1) 0 else null

        // 3. Common VPN Ports Scan
        val portsToScan = buildList {
            addAll(COMMON_VPN_PORTS)
            if (none { it.first == safePort }) {
                add(safePort to "Puerto VPS")
            }
        }
        val portProbes = portsToScan.map { (p, label) ->
            val rtt = probeTcpLatency(primaryIp, p, timeoutMs = 1500)
            VpsPortProbe(
                port = p,
                serviceLabel = label,
                isOpen = rtt != null,
                latencyMs = rtt
            )
        }

        // 4. BHTTP Handshake & Server Header Detection
        val syntheticProfile = VpnProfileEntity(
            profileName = vpsName.ifBlank { "VPSpremiumPRO" },
            vpsName = vpsName.ifBlank { "VPSpremiumPRO" },
            host = primaryIp,
            port = safePort,
            password = password,
            carrier = if (safePort == 80) CarrierType.CLARO.name else CarrierType.TIGO.name,
            protocol = "BHTTP",
            sniHost = sniHost.ifBlank { "m.tigo.com" },
            bhttpPayload = bhttpPayload.ifBlank { BhttpPayloadBuilder.defaultPayloadFor(CarrierType.TIGO) }
        )
        val bhttpProbe = probeBhttpHandshake(primaryIp, safePort, syntheticProfile)

        // 5. Optional TLS / SNI inspection if port 443/8443 is open or targetPort is TLS
        val tlsPort = when {
            safePort == 443 || safePort == 8443 -> safePort
            portProbes.any { it.port == 443 && it.isOpen } -> 443
            portProbes.any { it.port == 8443 && it.isOpen } -> 8443
            else -> null
        }
        val (tlsProtocol, tlsCipher) = if (tlsPort != null && sniHost.isNotBlank()) {
            probeTlsHandshake(primaryIp, tlsPort, sniHost.trim())
        } else {
            null to null
        }

        val detectedStack = detectServerStack(bhttpProbe.headers, portProbes)
        val score = computeHealthScore(
            avgLatencyMs = avgMs,
            jitterMs = jitterMs,
            packetLossPercent = packetLoss,
            openPortsCount = portProbes.count { it.isOpen },
            hasBhttpBanner = bhttpProbe.statusLine != null,
            hasTls = tlsProtocol != null
        )
        val grade = when {
            samples.isEmpty() && portProbes.none { it.isOpen } -> VpsHealthGrade.OFFLINE
            score >= 80 -> VpsHealthGrade.OPTIMAL
            score >= 55 -> VpsHealthGrade.STABLE
            else -> VpsHealthGrade.UNSTABLE
        }
        val recommendations = buildRecommendations(
            targetPort = safePort,
            samples = samples,
            packetLoss = packetLoss,
            avgMs = avgMs,
            portProbes = portProbes,
            bhttpStatusLine = bhttpProbe.statusLine
        )

        VpsAnalysisReport(
            host = host,
            targetPort = safePort,
            resolvedIps = resolvedIps,
            reverseDns = reverseDns,
            tcpSamplesMs = samples,
            minLatencyMs = minMs,
            avgLatencyMs = avgMs,
            maxLatencyMs = maxMs,
            jitterMs = jitterMs,
            packetLossPercent = packetLoss,
            portProbes = portProbes,
            bhttpStatusLine = bhttpProbe.statusLine,
            bhttpTtfbMs = bhttpProbe.ttfbMs,
            bhttpHeaders = bhttpProbe.headers,
            detectedServerStack = detectedStack,
            tlsProtocol = tlsProtocol,
            tlsCipher = tlsCipher,
            score = score,
            grade = grade,
            recommendations = recommendations
        )
    }

    fun computeHealthScore(
        avgLatencyMs: Int?,
        jitterMs: Int?,
        packetLossPercent: Int,
        openPortsCount: Int,
        hasBhttpBanner: Boolean,
        hasTls: Boolean
    ): Int {
        if (avgLatencyMs == null && openPortsCount == 0) return 0
        var score = 100
        score -= (packetLossPercent * 0.6f).toInt()
        if (avgLatencyMs == null) {
            score -= 45
        } else when {
            avgLatencyMs > 400 -> score -= 30
            avgLatencyMs > 220 -> score -= 18
            avgLatencyMs > 120 -> score -= 8
        }
        if (jitterMs != null && jitterMs > 50) {
            score -= 10
        }
        if (hasBhttpBanner) score += 5
        if (hasTls) score += 5
        if (openPortsCount == 0) score -= 25
        return score.coerceIn(0, 100)
    }

    private fun probeTcpLatency(host: String, port: Int, timeoutMs: Int): Int? {
        val socket = Socket()
        return try {
            val t0 = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            null
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private data class BhttpProbeOutput(
        val statusLine: String?,
        val ttfbMs: Int?,
        val headers: List<String>
    )

    private fun probeBhttpHandshake(
        host: String,
        port: Int,
        profile: VpnProfileEntity
    ): BhttpProbeOutput {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), 3500)
            socket.soTimeout = 2500
            val payload = BhttpPayloadBuilder.compile(profile)
            val t0 = System.nanoTime()
            socket.getOutputStream().write(payload.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val lines = mutableListOf<String>()
            var firstLine: String? = null
            var ttfb: Int? = null
            var count = 0
            while (count < 16) {
                val line = try {
                    reader.readLine()
                } catch (_: Exception) {
                    null
                } ?: break
                if (firstLine == null) {
                    firstLine = line
                    ttfb = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                }
                if (line.isEmpty()) break
                lines.add(line)
                count++
            }
            BhttpProbeOutput(statusLine = firstLine, ttfbMs = ttfb, headers = lines)
        } catch (_: Exception) {
            BhttpProbeOutput(statusLine = null, ttfbMs = null, headers = emptyList())
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun probeTlsHandshake(host: String, port: Int, sniHost: String): Pair<String?, String?> {
        var sslSocket: SSLSocket? = null
        return try {
            val raw = Socket()
            raw.connect(InetSocketAddress(host, port), 3000)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = factory.createSocket(raw, sniHost, port, true) as SSLSocket
            val params = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(sniHost))
            sslSocket.sslParameters = params
            sslSocket.soTimeout = 3000
            sslSocket.startHandshake()
            sslSocket.session.protocol to sslSocket.session.cipherSuite
        } catch (_: Exception) {
            null to null
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun detectServerStack(headers: List<String>, probes: List<VpsPortProbe>): String {
        val joined = headers.joinToString("\n").lowercase()
        return when {
            joined.contains("cloudflare") || joined.contains("cf-ray") -> "Cloudflare CDN"
            joined.contains("squid") -> "Squid HTTP Proxy"
            joined.contains("nginx") -> "Nginx Reverse Proxy"
            joined.contains("apache") -> "Apache HTTP Server"
            joined.contains("haproxy") -> "HAProxy"
            probes.any { it.port == 8080 && it.isOpen } && probes.any { it.port == 80 && it.isOpen } ->
                "VPS Dual-Port (80 + 8080)"
            probes.any { it.port == 22 && it.isOpen } -> "Linux VPS (SSH activo)"
            probes.any { it.isOpen } -> "VPS TCP Activo"
            else -> "Sin banner identificado"
        }
    }

    private fun buildRecommendations(
        targetPort: Int,
        samples: List<Int>,
        packetLoss: Int,
        avgMs: Int?,
        portProbes: List<VpsPortProbe>,
        bhttpStatusLine: String?
    ): List<String> = buildList {
        if (samples.isEmpty()) {
            add("El puerto $targetPort no respondió. Verifica el firewall o prueba otro puerto abierto.")
            val alt = portProbes.firstOrNull { it.isOpen }
            if (alt != null) {
                add("Puerto alternativo disponible detectado: ${alt.port} (${alt.serviceLabel}).")
            }
        } else {
            if (packetLoss > 0) {
                add("Pérdida parcial de paquetes ($packetLoss%). Activa Reconexión automática en Configuración.")
            }
            if (avgMs != null && avgMs <= 120) {
                add("Latencia excelente (${avgMs} ms). Recomendado usar Modo Juego o Máximo rendimiento.")
            } else if (avgMs != null && avgMs > 220) {
                add("Latencia elevada (${avgMs} ms). Prueba ajustar MTU a 1360 para mayor estabilidad.")
            }
            if (bhttpStatusLine == null) {
                add("Puerto TCP abierto sin respuesta HTTP inmediata; puede requerir Payload BHTTP personalizado o WebSocket.")
            } else {
                add("Handshake BHTTP respondió: $bhttpStatusLine")
            }
        }
    }
}
