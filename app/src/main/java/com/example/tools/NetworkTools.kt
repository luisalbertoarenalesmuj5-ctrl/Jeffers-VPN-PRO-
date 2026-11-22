package com.example.tools

import com.example.data.local.VpnProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class HostCheckMode(val label: String) {
    HEADER("Header"),
    SNI_INFO("SNI Info"),
    IP_ADDRESS("IP Address"),
    CDN_FINDER("CDN Finder")
}

enum class HttpCheckMethod(val label: String) {
    GET("GET"),
    HEAD("HEAD"),
    CONNECT("CONNECT"),
    POST("POST")
}

data class HostCheckResult(
    val success: Boolean,
    val title: String,
    val output: String
)

object HostCheckerEngine {

    suspend fun verify(
        rawTarget: String,
        method: HttpCheckMethod,
        mode: HostCheckMode,
        useProxy: Boolean,
        activeProxyProfile: VpnProfileEntity?
    ): HostCheckResult = withContext(Dispatchers.IO) {
        val cleaned = rawTarget.trim()
        if (cleaned.isEmpty()) {
            return@withContext HostCheckResult(
                success = false,
                title = "Objetivo vacío",
                output = "Ingresa un host, IP, URL o formato IP#dominio."
            )
        }

        // Parse IP#domain syntax or URL
        val (connectHost, sniOrHostHeader, explicitPort, isHttps) = parseTarget(cleaned)

        when (mode) {
            HostCheckMode.IP_ADDRESS -> checkIpAndPorts(connectHost, sniOrHostHeader)
            HostCheckMode.SNI_INFO -> checkTlsSni(connectHost, explicitPort ?: 443, sniOrHostHeader)
            HostCheckMode.CDN_FINDER -> checkCdn(connectHost, explicitPort ?: if (isHttps) 443 else 80, sniOrHostHeader)
            HostCheckMode.HEADER -> checkHttpHeaders(
                connectHost = connectHost,
                port = explicitPort ?: if (isHttps) 443 else 80,
                hostHeader = sniOrHostHeader,
                method = method,
                proxyProfile = if (useProxy) activeProxyProfile else null
            )
        }
    }

    private data class ParsedTarget(
        val connectHost: String,
        val hostHeader: String,
        val port: Int?,
        val isHttps: Boolean
    )

    private fun parseTarget(input: String): ParsedTarget {
        val isHttps = input.startsWith("https://", ignoreCase = true)
        val noScheme = input
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")

        return if (noScheme.contains("#")) {
            val parts = noScheme.split("#", limit = 2)
            val ipPart = parts[0].substringBefore(":")
            val portPart = parts[0].substringAfter(":", "").toIntOrNull()
            val domainPart = parts[1].ifBlank { ipPart }
            ParsedTarget(ipPart, domainPart, portPart, isHttps)
        } else {
            val hostPart = noScheme.substringBefore(":")
            val portPart = noScheme.substringAfter(":", "").toIntOrNull()
            ParsedTarget(hostPart, hostPart, portPart, isHttps)
        }
    }

    private fun checkIpAndPorts(connectHost: String, displayHost: String): HostCheckResult {
        return try {
            val startNs = System.nanoTime()
            val addresses = InetAddress.getAllByName(connectHost)
            val dnsMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

            val sb = StringBuilder()
            sb.appendLine("Host: $displayHost")
            sb.appendLine("Resolución DNS (${dnsMs} ms):")
            addresses.forEach { addr ->
                sb.appendLine("  • ${addr.hostAddress} (${addr.canonicalHostName})")
            }
            sb.appendLine()
            sb.appendLine("Escaneo de puertos comunes:")
            listOf(80, 443, 8080, 8443).forEach { port ->
                val s = Socket()
                val status = try {
                    val t0 = System.nanoTime()
                    s.connect(InetSocketAddress(addresses.first().hostAddress, port), 2000)
                    val rtt = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
                    "ABIERTO ($rtt ms)"
                } catch (_: Exception) {
                    "CERRADO / FILTRADO"
                } finally {
                    try { s.close() } catch (_: Exception) {}
                }
                sb.appendLine("  • Puerto $port: $status")
            }
            HostCheckResult(true, "Resolución IP completada", sb.toString().trim())
        } catch (e: Exception) {
            HostCheckResult(
                false,
                "Error DNS",
                "No se pudo resolver $connectHost: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun checkTlsSni(connectHost: String, port: Int, sniHost: String): HostCheckResult {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        var sslSocket: SSLSocket? = null
        return try {
            val raw = Socket()
            raw.connect(InetSocketAddress(connectHost, port), 5000)
            sslSocket = factory.createSocket(raw, sniHost, port, true) as SSLSocket
            val params = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(sniHost))
            sslSocket.sslParameters = params
            sslSocket.soTimeout = 5000
            sslSocket.startHandshake()

            val session = sslSocket.session
            val cert = session.peerCertificates.firstOrNull() as? X509Certificate
            val sb = StringBuilder()
            sb.appendLine("Conexión TLS exitosa -> $connectHost:$port")
            sb.appendLine("SNI enviado: $sniHost")
            sb.appendLine("Protocolo: ${session.protocol}")
            sb.appendLine("Cipher Suite: ${session.cipherSuite}")
            if (cert != null) {
                sb.appendLine("Subject: ${cert.subjectX500Principal.name}")
                sb.appendLine("Emisor: ${cert.issuerX500Principal.name}")
                sb.appendLine("Válido hasta: ${cert.notAfter}")
            }
            HostCheckResult(true, "Certificado TLS / SNI válido", sb.toString().trim())
        } catch (e: Exception) {
            HostCheckResult(
                false,
                "Fallo TLS / SNI",
                "No se pudo completar handshake TLS con $connectHost:$port (SNI=$sniHost): ${e.localizedMessage}"
            )
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun checkHttpHeaders(
        connectHost: String,
        port: Int,
        hostHeader: String,
        method: HttpCheckMethod,
        proxyProfile: VpnProfileEntity?
    ): HostCheckResult {
        val targetHost = proxyProfile?.host ?: connectHost
        val targetPort = proxyProfile?.port ?: port
        val socket = Socket()
        return try {
            val t0 = System.nanoTime()
            socket.connect(InetSocketAddress(targetHost, targetPort), 5000)
            val rtt = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            socket.soTimeout = 4500

            val reqTarget = if (method == HttpCheckMethod.CONNECT) "$connectHost:$port" else "/"
            val rawReq = buildString {
                append("${method.label} $reqTarget HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                append("User-Agent: JeffersVPN-Checker/1.0\r\n")
                append("Connection: close\r\n\r\n")
            }

            val out = socket.getOutputStream()
            out.write(rawReq.toByteArray(Charsets.UTF_8))
            out.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val lines = mutableListOf<String>()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 28) {
                if (line!!.isEmpty()) break
                lines.add(line!!)
                count++
            }

            if (lines.isEmpty()) {
                HostCheckResult(
                    true,
                    "Socket abierto (${rtt} ms)",
                    "Conectado a $targetHost:$targetPort sin respuesta de cabecera HTTP inmediata."
                )
            } else {
                HostCheckResult(
                    true,
                    "${lines.first()} (${rtt} ms)",
                    lines.joinToString("\n")
                )
            }
        } catch (e: Exception) {
            HostCheckResult(
                false,
                "Error de conexión",
                "No se pudo conectar a $targetHost:$targetPort: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun checkCdn(connectHost: String, port: Int, hostHeader: String): HostCheckResult {
        val headerResult = checkHttpHeaders(connectHost, port, hostHeader, HttpCheckMethod.HEAD, null)
        if (!headerResult.success) return headerResult

        val lower = headerResult.output.lowercase()
        val provider = when {
            lower.contains("cloudflare") || lower.contains("cf-ray") -> "Cloudflare"
            lower.contains("cloudfront") || lower.contains("x-amz-cf") -> "Amazon CloudFront"
            lower.contains("akamai") || lower.contains("akamaighost") -> "Akamai"
            lower.contains("fastly") -> "Fastly"
            lower.contains("gws") || lower.contains("google") -> "Google Edge / GWS"
            lower.contains("nginx") || lower.contains("apache") -> "Directo / VPS (${if (lower.contains("nginx")) "Nginx" else "Apache"})"
            else -> "Directo o CDN no identificado"
        }

        return HostCheckResult(
            true,
            "CDN detectado: $provider",
            "Proveedor probable: $provider\n\n${headerResult.output}"
        )
    }
}

object HotspotProxyManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeClients = MutableStateFlow(0)
    val activeClients: StateFlow<Int> = _activeClients.asStateFlow()

    const val PROXY_PORT = 8080

    fun getLocalWifiIpAddress(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val candidate = interfaces
                .filter { !it.isLoopback && it.isUp }
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { addr ->
                    addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress
                }
            candidate?.hostAddress ?: "192.168.43.1"
        } catch (_: Exception) {
            "192.168.43.1"
        }
    }

    fun startProxy() {
        if (_isRunning.value) return
        serverJob = scope.launch {
            try {
                val ss = ServerSocket(PROXY_PORT)
                serverSocket = ss
                _isRunning.value = true
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    launch { handleClient(client) }
                }
            } catch (_: Exception) {
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopProxy() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _isRunning.value = false
        _activeClients.value = 0
    }

    private fun handleClient(client: Socket) {
        _activeClients.value += 1
        var upstream: Socket? = null
        try {
            client.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]

            // Drain headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            if (method.equals("CONNECT", ignoreCase = true)) {
                val host = target.substringBefore(":")
                val port = target.substringAfter(":", "443").toIntOrNull() ?: 443
                upstream = Socket(host, port)
                val clientOut = client.getOutputStream()
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()
                relayBidirectional(client, upstream)
            }
        } catch (_: Exception) {
        } finally {
            try { upstream?.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
            _activeClients.value = (_activeClients.value - 1).coerceAtLeast(0)
        }
    }

    private fun relayBidirectional(a: Socket, b: Socket) {
        val t1 = Thread {
            copyStream(a.getInputStream(), b.getOutputStream())
        }
        t1.start()
        copyStream(b.getInputStream(), a.getOutputStream())
        try { t1.join(500) } catch (_: Exception) {}
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }
}
