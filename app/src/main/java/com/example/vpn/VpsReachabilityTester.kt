package com.example.vpn

import com.example.data.local.VpnProfileEntity
import com.example.data.model.LogLevel
import com.example.data.model.PingTestResult
import com.example.data.model.TunnelProtocol
import com.example.data.repository.VpnRepository
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

data class HttpProxyProbeOutcome(
    val supported: Boolean,
    val latencyMs: Int?,
    val statusLine: String?,
    val errorDetail: String?,
    val locationHeader: String? = null
)

data class TunnelProbeOutcome(
    val isReachable: Boolean,
    val latencyMs: Int?,
    val channelLabel: String?,
    val httpBanner: String?,
    val errorDetail: String?
)

object VpsReachabilityTester {

    fun resolveCredentialCandidates(
        profile: VpnProfileEntity,
        personalUsername: String = "",
        personalPassword: String = ""
    ): List<SshCredential> {
        val profileUser = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
        val profilePasswords = resolveCandidatePasswords(profile)
        val extraUser = personalUsername.trim()
        val extraPass = personalPassword.trim()

        return buildList {
            // 1. Always try the VPS profile's configured SSH credentials FIRST so app account logins never break VPS auth
            profilePasswords.forEach { pwd ->
                add(SshCredential(profileUser, pwd))
            }
            // 2. Append personal tunnel credentials as fallback candidates if configured
            if (extraUser.isNotEmpty() || extraPass.isNotEmpty()) {
                val resolvedExtraUser = extraUser.ifBlank { profileUser }
                if (extraPass.isNotEmpty()) {
                    add(SshCredential(resolvedExtraUser, extraPass))
                    if (resolvedExtraUser != profileUser) {
                        add(SshCredential(profileUser, extraPass))
                    }
                }
                if (extraUser.isNotEmpty() && extraUser != profileUser) {
                    profilePasswords.forEach { pwd ->
                        add(SshCredential(extraUser, pwd))
                    }
                }
            }
        }.distinct()
    }

    fun resolveCandidatePasswords(profile: VpnProfileEntity): List<String> {
        val cleanUser = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
        val cleanPass = profile.password.trim()
        val isSeededDefaultToken = profile.host.trim() == VpnRepository.DEFAULT_VPS_HOST &&
            cleanPass == VpnRepository.DEFAULT_VPS_PASSWORD &&
            cleanUser == VpnRepository.DEFAULT_SSH_USER
        return buildList {
            if (isSeededDefaultToken) {
                add(VpnRepository.DEFAULT_SSH_PASSWORD)
            }
            if (cleanPass.isNotBlank()) {
                add(cleanPass)
            }
        }.distinct()
    }

    fun validateProfileConfig(
        profile: VpnProfileEntity,
        personalUsername: String = "",
        personalPassword: String = ""
    ): String? {
        val host = profile.host.trim()
        if (host.isBlank()) return "IP o dominio del VPS vacío"
        if (host.contains(' ') || host.contains("://")) return "Formato de IP/dominio inválido ($host)"
        if (profile.port !in 1..65535) return "Puerto inválido (${profile.port})"
        val protocol = TunnelProtocol.fromName(profile.protocol)
        if ((protocol == TunnelProtocol.SSH_DIRECT || profile.port == 22) &&
            resolveCredentialCandidates(profile, personalUsername, personalPassword).isEmpty()
        ) {
            return "Falta contraseña SSH para ${profile.username.ifBlank { "root" }}@$host:${profile.port}"
        }
        return null
    }

    suspend fun testProfile(
        profile: VpnProfileEntity,
        timeoutMs: Int = 5500,
        protectSocket: (Socket) -> Boolean = { true },
        personalUsername: String = "",
        personalPassword: String = ""
    ): PingTestResult = withContext(Dispatchers.IO) {
        val configError = validateProfileConfig(profile, personalUsername, personalPassword)
        if (configError != null) {
            return@withContext PingTestResult(
                profileId = profile.id,
                isReachable = false,
                latencyMs = null,
                httpBanner = null,
                errorDetail = configError,
                verifiedChannel = null
            )
        }

        val outcome = verifyProfileEndToEnd(
            profile = profile,
            timeoutMs = timeoutMs,
            protectSocket = protectSocket,
            personalUsername = personalUsername,
            personalPassword = personalPassword
        )
        PingTestResult(
            profileId = profile.id,
            isReachable = outcome.isReachable,
            latencyMs = outcome.latencyMs,
            httpBanner = outcome.httpBanner,
            errorDetail = outcome.errorDetail,
            verifiedChannel = outcome.channelLabel
        )
    }

    private fun verifyProfileEndToEnd(
        profile: VpnProfileEntity,
        timeoutMs: Int,
        protectSocket: (Socket) -> Boolean,
        personalUsername: String = "",
        personalPassword: String = ""
    ): TunnelProbeOutcome {
        val host = profile.host.trim()
        val port = profile.port
        val protocol = TunnelProtocol.fromName(profile.protocol)
        val user = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
        val passwords = resolveCandidatePasswords(profile)
        val credentials = resolveCredentialCandidates(profile, personalUsername, personalPassword)

        // 1. Direct SSH mode (or port 22)
        if (protocol == TunnelProtocol.SSH_DIRECT || port == 22) {
            val ssh = SshTunnelClient(
                protectSocket = protectSocket,
                host = host,
                port = port,
                username = user,
                candidatePasswords = passwords,
                onLog = { _, _, _ -> },
                candidateCredentials = credentials
            )
            return try {
                val conn = ssh.connect(timeoutMs)
                if (conn.isFailure) {
                    val reason = conn.exceptionOrNull()?.localizedMessage ?: "Error SSH"
                    return TunnelProbeOutcome(
                        isReachable = false,
                        latencyMs = null,
                        channelLabel = null,
                        httpBanner = null,
                        errorDetail = "SSH $user@$host:$port falló: $reason"
                    )
                }
                val fwd = ssh.verifyForwarding(timeoutMs.coerceAtMost(4500))
                if (fwd.isFailure) {
                    val reason = fwd.exceptionOrNull()?.localizedMessage ?: "direct-tcpip rechazado"
                    return TunnelProbeOutcome(
                        isReachable = false,
                        latencyMs = null,
                        channelLabel = null,
                        httpBanner = null,
                        errorDetail = "SSH autenticado pero sin salida/reenvío TCP: $reason"
                    )
                }
                val rtt = conn.getOrNull() ?: fwd.getOrNull() ?: 1
                TunnelProbeOutcome(
                    isReachable = true,
                    latencyMs = rtt,
                    channelLabel = "SSH :$port",
                    httpBanner = "SSH-2.0 autenticado y salida real verificada (${rtt} ms)",
                    errorDetail = null
                )
            } finally {
                ssh.disconnect()
            }
        }

        // 2. Explicit HTTP CONNECT proxy mode (supports both open HTTP CONNECT & SSH-over-HTTP-CONNECT)
        if (protocol == TunnelProtocol.HTTP_CONNECT) {
            val proxyProbe = probeHttpConnectProxy(
                host = host,
                port = port,
                username = user,
                password = profile.password.trim(),
                protectSocket = protectSocket,
                timeoutMs = timeoutMs,
                sniHost = profile.sniHost.trim()
            )
            if (proxyProbe.supported) {
                return TunnelProbeOutcome(
                    isReachable = true,
                    latencyMs = proxyProbe.latencyMs,
                    channelLabel = "HTTP CONNECT :$port",
                    httpBanner = "${proxyProbe.statusLine ?: "HTTP 200"} • Proxy verificado",
                    errorDetail = null
                )
            }

            if (credentials.isNotEmpty()) {
                val sshOverProxy = trySshOverHttpConnect(
                    profile = profile,
                    user = user,
                    passwords = passwords,
                    credentials = credentials,
                    protectSocket = protectSocket,
                    timeoutMs = timeoutMs
                )
                if (sshOverProxy != null) {
                    return sshOverProxy
                }
            }

            return TunnelProbeOutcome(
                isReachable = false,
                latencyMs = null,
                channelLabel = null,
                httpBanner = proxyProbe.statusLine,
                errorDetail = proxyProbe.errorDetail ?: "Puerto $port no responde como proxy HTTP CONNECT"
            )
        }

        // 2b. SSL/TLS + SNI mode (port 443 -> stunnel/TLS proxy to SSH)
        if (protocol == TunnelProtocol.SSL_TLS) {
            if (credentials.isEmpty()) {
                return TunnelProbeOutcome(
                    isReachable = false,
                    latencyMs = null,
                    channelLabel = null,
                    httpBanner = null,
                    errorDetail = "Falta contraseña SSH para túnel SSL/TLS en $host:$port"
                )
            }
            return trySshOverSsl(
                profile = profile,
                user = user,
                passwords = passwords,
                credentials = credentials,
                protectSocket = protectSocket,
                timeoutMs = timeoutMs
            )
        }

        // 3. BHTTP / BHTTP_WS: test payload upgrade -> SSH, or HTTP CONNECT on port, or SSH :22
        val bhttpResult = probeBhttpHandshake(profile, protectSocket, timeoutMs)
        if (!bhttpResult.tcpConnected) {
            return TunnelProbeOutcome(
                isReachable = false,
                latencyMs = null,
                channelLabel = null,
                httpBanner = null,
                errorDetail = bhttpResult.errorDetail ?: "No se pudo conectar TCP a $host:$port"
            )
        }

        // 3a. If server returned 101 Switching Protocols or 200 Connection established, try SSH over upgraded stream
        if (bhttpResult.canUpgradeToSsh && credentials.isNotEmpty()) {
            val upgradedSsh = trySshOverBhttpUpgrade(
                profile = profile,
                user = user,
                passwords = passwords,
                credentials = credentials,
                protectSocket = protectSocket,
                timeoutMs = timeoutMs
            )
            if (upgradedSsh != null) {
                return upgradedSsh
            }
        }

        // 3b. Check if host:port accepts standard HTTP CONNECT forwarding (or SSH-over-HTTP-CONNECT)
        val proxyProbe = probeHttpConnectProxy(
            host = host,
            port = port,
            username = user,
            password = profile.password.trim(),
            protectSocket = protectSocket,
            timeoutMs = timeoutMs,
            sniHost = profile.sniHost.trim()
        )
        if (proxyProbe.supported) {
            return TunnelProbeOutcome(
                isReachable = true,
                latencyMs = proxyProbe.latencyMs ?: bhttpResult.latencyMs,
                channelLabel = "HTTP CONNECT :$port",
                httpBanner = "${proxyProbe.statusLine ?: "HTTP 200"} • Túnel proxy activo",
                errorDetail = null
            )
        }

        if (credentials.isNotEmpty()) {
            val sshOverProxy = trySshOverHttpConnect(
                profile = profile,
                user = user,
                passwords = passwords,
                credentials = credentials,
                protectSocket = protectSocket,
                timeoutMs = timeoutMs
            )
            if (sshOverProxy != null) {
                return sshOverProxy
            }
        }

        // 3c. Check fallback SSH on port 22 with full forwarding verification
        if (credentials.isNotEmpty()) {
            val ssh22 = SshTunnelClient(
                protectSocket = protectSocket,
                host = host,
                port = 22,
                username = user,
                candidatePasswords = passwords,
                onLog = { _, _, _ -> },
                candidateCredentials = credentials
            )
            try {
                val conn = ssh22.connect(timeoutMs.coerceAtMost(4500))
                if (conn.isSuccess && ssh22.verifyForwarding(3500).isSuccess) {
                    val rtt = conn.getOrNull() ?: bhttpResult.latencyMs ?: 1
                    val bhttpTag = bhttpResult.statusLine ?: "sin proxy en :$port"
                    return TunnelProbeOutcome(
                        isReachable = true,
                        latencyMs = rtt,
                        channelLabel = "SSH :22 (respaldo)",
                        httpBanner = "Respaldo SSH :22 verificado ($bhttpTag)",
                        errorDetail = null
                    )
                }
                val sshErr = conn.exceptionOrNull()?.localizedMessage ?: ssh22.lastErrorMessage ?: "Sin acceso SSH :22"
                val portBanner = bhttpResult.statusLine ?: bhttpResult.errorDetail ?: "sin respuesta proxy"
                return TunnelProbeOutcome(
                    isReachable = false,
                    latencyMs = null,
                    channelLabel = null,
                    httpBanner = bhttpResult.statusLine,
                    errorDetail = "Puerto $port respondió '$portBanner' (no es túnel) y SSH :22 falló ($sshErr)"
                )
            } finally {
                ssh22.disconnect()
            }
        }

        val portBanner = bhttpResult.statusLine ?: bhttpResult.errorDetail ?: "sin túnel HTTP/WS"
        return TunnelProbeOutcome(
            isReachable = false,
            latencyMs = null,
            channelLabel = null,
            httpBanner = bhttpResult.statusLine,
            errorDetail = "Puerto $port respondió '$portBanner' (no actúa como proxy ni WebSocket SSH)"
        )
    }

    private data class BhttpProbeResult(
        val tcpConnected: Boolean,
        val latencyMs: Int?,
        val statusLine: String?,
        val canUpgradeToSsh: Boolean,
        val errorDetail: String?
    )

    private fun probeBhttpHandshake(
        profile: VpnProfileEntity,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): BhttpProbeResult {
        val socket = Socket()
        return try {
            bindSocketIfNeeded(socket)
            if (!protectSocket(socket)) {
                return BhttpProbeResult(false, null, null, false, "No se pudo proteger socket de prueba")
            }
            socket.tcpNoDelay = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(profile.host.trim(), profile.port), timeoutMs)
            val connectMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            socket.soTimeout = timeoutMs.coerceAtMost(2500)

            val payload = BhttpPayloadBuilder.compile(profile).toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()

            val headers = readHttpHeadersExact(socket.getInputStream())
            val statusLine = headers?.lineSequence()?.firstOrNull()?.trim()
            val statusCode = parseHttpStatusCode(statusLine)
            val lower = headers.orEmpty().lowercase()
            val canUpgrade = statusCode == 101 ||
                (statusCode in 200..299 && !lower.contains("content-type: text/html"))

            BhttpProbeResult(
                tcpConnected = true,
                latencyMs = connectMs,
                statusLine = statusLine,
                canUpgradeToSsh = canUpgrade,
                errorDetail = if (statusLine == null) "EOF sin respuesta HTTP/WS" else null
            )
        } catch (e: Exception) {
            BhttpProbeResult(
                tcpConnected = false,
                latencyMs = null,
                statusLine = null,
                canUpgradeToSsh = false,
                errorDetail = e.localizedMessage ?: e.javaClass.simpleName
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun trySshOverBhttpUpgrade(
        profile: VpnProfileEntity,
        user: String,
        passwords: List<String>,
        credentials: List<SshCredential>,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): TunnelProbeOutcome? {
        val factory = createBhttpUpgradeSocketFactory(
            profile = profile,
            protectSocket = protectSocket,
            timeoutMs = timeoutMs,
            onLog = { _, _, _ -> }
        )
        val ssh = SshTunnelClient(
            protectSocket = protectSocket,
            host = profile.host.trim(),
            port = profile.port,
            username = user,
            candidatePasswords = passwords,
            customSocketFactory = factory,
            onLog = { _, _, _ -> },
            candidateCredentials = credentials
        )
        return try {
            val conn = ssh.connect(timeoutMs)
            if (conn.isSuccess && ssh.verifyForwarding(3500).isSuccess) {
                val rtt = conn.getOrNull() ?: 1
                TunnelProbeOutcome(
                    isReachable = true,
                    latencyMs = rtt,
                    channelLabel = "BHTTP/WS→SSH :${profile.port}",
                    httpBanner = "Upgrade HTTP/WS→SSH verificado (${rtt} ms)",
                    errorDetail = null
                )
            } else {
                null
            }
        } finally {
            ssh.disconnect()
        }
    }

    private fun trySshOverHttpConnect(
        profile: VpnProfileEntity,
        user: String,
        passwords: List<String>,
        credentials: List<SshCredential>,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): TunnelProbeOutcome? {
        val factory = createHttpConnectSshSocketFactory(
            profile = profile,
            protectSocket = protectSocket,
            timeoutMs = timeoutMs,
            onLog = { _, _, _ -> }
        )
        val ssh = SshTunnelClient(
            protectSocket = protectSocket,
            host = profile.host.trim(),
            port = profile.port,
            username = user,
            candidatePasswords = passwords,
            customSocketFactory = factory,
            onLog = { _, _, _ -> },
            candidateCredentials = credentials
        )
        return try {
            val conn = ssh.connect(timeoutMs)
            if (conn.isSuccess && ssh.verifyForwarding(3500).isSuccess) {
                val rtt = conn.getOrNull() ?: 1
                TunnelProbeOutcome(
                    isReachable = true,
                    latencyMs = rtt,
                    channelLabel = "HTTP CONNECT→SSH :${profile.port}",
                    httpBanner = "HTTP CONNECT→SSH verificado (${rtt} ms)",
                    errorDetail = null
                )
            } else {
                null
            }
        } finally {
            ssh.disconnect()
        }
    }

    private fun trySshOverSsl(
        profile: VpnProfileEntity,
        user: String,
        passwords: List<String>,
        credentials: List<SshCredential>,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int
    ): TunnelProbeOutcome {
        val factory = createSslSshSocketFactory(
            profile = profile,
            protectSocket = protectSocket,
            timeoutMs = timeoutMs,
            onLog = { _, _, _ -> }
        )
        val ssh = SshTunnelClient(
            protectSocket = protectSocket,
            host = profile.host.trim(),
            port = profile.port,
            username = user,
            candidatePasswords = passwords,
            customSocketFactory = factory,
            onLog = { _, _, _ -> },
            candidateCredentials = credentials
        )
        return try {
            val conn = ssh.connect(timeoutMs)
            if (conn.isFailure) {
                val err = conn.exceptionOrNull()?.localizedMessage ?: "Error SSL/TLS→SSH"
                return TunnelProbeOutcome(
                    isReachable = false,
                    latencyMs = null,
                    channelLabel = null,
                    httpBanner = null,
                    errorDetail = "SSL/TLS :${profile.port} falló: $err"
                )
            }
            val fwd = ssh.verifyForwarding(3500)
            if (fwd.isFailure) {
                val err = fwd.exceptionOrNull()?.localizedMessage ?: "direct-tcpip rechazado"
                return TunnelProbeOutcome(
                    isReachable = false,
                    latencyMs = null,
                    channelLabel = null,
                    httpBanner = null,
                    errorDetail = "SSL/TLS→SSH autenticado pero sin salida TCP: $err"
                )
            }
            val rtt = conn.getOrNull() ?: fwd.getOrDefault(1)
            TunnelProbeOutcome(
                isReachable = true,
                latencyMs = rtt,
                channelLabel = "SSL/TLS :${profile.port}→SSH",
                httpBanner = "TLS + SNI (${profile.sniHost.ifBlank { profile.host }}) verificado (${rtt} ms)",
                errorDetail = null
            )
        } finally {
            ssh.disconnect()
        }
    }

    @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun createStunnelCompatibleSslFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        })
        return javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAll, java.security.SecureRandom())
        }.socketFactory
    }

    fun createSslSshSocketFactory(
        profile: VpnProfileEntity,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int,
        onLog: (String, String, LogLevel) -> Unit
    ): SocketFactory = object : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val targetHost = profile.host.trim()
            val targetPort = profile.port
            val sni = profile.sniHost.trim().ifBlank { targetHost }

            for (allowSelfSigned in listOf(false, true)) {
                val raw = Socket()
                var sslSocket: javax.net.ssl.SSLSocket? = null
                try {
                    bindSocketIfNeeded(raw)
                    if (!protectSocket(raw)) {
                        try { raw.close() } catch (_: Exception) {}
                        throw IOException("No se pudo proteger socket SSL/TLS")
                    }
                    raw.tcpNoDelay = true
                    raw.keepAlive = true
                    raw.connect(InetSocketAddress(targetHost, targetPort), timeoutMs)
                    raw.soTimeout = timeoutMs

                    val sslFactory = if (allowSelfSigned) {
                        createStunnelCompatibleSslFactory()
                    } else {
                        javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                    }
                    sslSocket = sslFactory.createSocket(raw, sni, targetPort, true) as javax.net.ssl.SSLSocket
                    if (sni.isNotBlank() && !isIpAddressLiteral(sni)) {
                        val params = sslSocket.sslParameters
                        params.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
                        sslSocket.sslParameters = params
                    }
                    sslSocket.startHandshake()
                    sslSocket.soTimeout = 30_000
                    onLog("SSL", "Handshake TLS completado en $targetHost:$targetPort (SNI=$sni)", LogLevel.INFO)
                    return sslSocket
                } catch (e: javax.net.ssl.SSLHandshakeException) {
                    try { sslSocket?.close() } catch (_: Exception) {}
                    try { raw.close() } catch (_: Exception) {}
                    if (allowSelfSigned) {
                        throw IOException("TLS/SNI falló en $targetHost:$targetPort ($sni): ${e.localizedMessage ?: e.javaClass.simpleName}", e)
                    }
                } catch (e: Exception) {
                    try { sslSocket?.close() } catch (_: Exception) {}
                    try { raw.close() } catch (_: Exception) {}
                    throw IOException("TLS/SNI falló en $targetHost:$targetPort ($sni): ${e.localizedMessage ?: e.javaClass.simpleName}", e)
                }
            }
            throw IOException("TLS/SNI no disponible en $targetHost:$targetPort")
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }

    private fun isIpAddressLiteral(value: String): Boolean {
        if (value.contains(':')) return true
        val parts = value.split('.')
        return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    fun createBhttpUpgradeSocketFactory(
        profile: VpnProfileEntity,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int,
        onLog: (String, String, LogLevel) -> Unit
    ): SocketFactory = object : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val socket = Socket()
            bindSocketIfNeeded(socket)
            if (!protectSocket(socket)) {
                try { socket.close() } catch (_: Exception) {}
                throw IOException("No se pudo proteger socket BHTTP/WebSocket")
            }
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(profile.host.trim(), profile.port), timeoutMs)
            socket.soTimeout = timeoutMs

            val payload = BhttpPayloadBuilder.compile(profile).toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()

            val headers = readHttpHeadersExact(socket.getInputStream())
            val statusLine = headers?.lineSequence()?.firstOrNull()?.trim()
            val code = parseHttpStatusCode(statusLine)
            if (code != 101 && code !in 200..299) {
                try { socket.close() } catch (_: Exception) {}
                throw IOException("Upgrade BHTTP/WS rechazado: ${statusLine ?: "EOF"}")
            }
            socket.soTimeout = 30_000
            onLog("BHTTP-WS", "Canal SSH sobre upgrade '$statusLine' establecido", LogLevel.INFO)
            return socket
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }

    fun createHttpConnectSshSocketFactory(
        profile: VpnProfileEntity,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int,
        onLog: (String, String, LogLevel) -> Unit
    ): SocketFactory = object : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val proxyHost = profile.host.trim()
            val proxyPort = profile.port
            val username = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
            val password = profile.password.trim()
            val sniHost = profile.sniHost.trim()
            val sshTargets = listOf("127.0.0.1", "localhost", proxyHost).distinct()

            var lastErr: String? = null
            for (sshTarget in sshTargets) {
                val authModes = if (password.isNotBlank()) listOf(true, false) else listOf(false)
                for (sendAuthHeaders in authModes) {
                    val socket = Socket()
                    try {
                        bindSocketIfNeeded(socket)
                        if (!protectSocket(socket)) {
                            try { socket.close() } catch (_: Exception) {}
                            throw IOException("No se pudo proteger socket HTTP CONNECT")
                        }
                        socket.tcpNoDelay = true
                        socket.keepAlive = true
                        socket.connect(InetSocketAddress(proxyHost, proxyPort), timeoutMs)
                        socket.soTimeout = timeoutMs

                        val req = buildHttpConnectRequest(
                            dstHost = sshTarget,
                            dstPort = 22,
                            username = username,
                            password = password,
                            sniHost = sniHost,
                            includeAuthHeaders = sendAuthHeaders
                        )
                        socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
                        socket.getOutputStream().flush()

                        val headers = readHttpHeadersExact(socket.getInputStream())
                        val statusLine = headers?.lineSequence()?.firstOrNull()?.trim()
                        val code = parseHttpStatusCode(statusLine)
                        if (code in 200..299) {
                            socket.soTimeout = 30_000
                            onLog(
                                "PROXY",
                                "Túnel HTTP CONNECT $sshTarget:22 listo ($statusLine)",
                                LogLevel.INFO
                            )
                            return socket
                        }
                        lastErr = statusLine ?: "EOF"
                        try { socket.close() } catch (_: Exception) {}
                        // Only retry without auth headers if proxy responded 400 (strict header parser) or 403
                        if (code !in setOf(400, 403)) break
                    } catch (e: IOException) {
                        try { socket.close() } catch (_: Exception) {}
                        lastErr = e.localizedMessage ?: e.javaClass.simpleName
                        throw e
                    } catch (e: Exception) {
                        try { socket.close() } catch (_: Exception) {}
                        lastErr = e.localizedMessage ?: e.javaClass.simpleName
                    }
                }
            }
            throw IOException("HTTP CONNECT hacia SSH :22 rechazado en $proxyHost:$proxyPort ($lastErr)")
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }

    internal fun buildHttpConnectRequest(
        dstHost: String,
        dstPort: Int,
        username: String = "",
        password: String = "",
        sniHost: String = "",
        includeAuthHeaders: Boolean = true
    ): String = buildString {
        append("CONNECT $dstHost:$dstPort HTTP/1.1\r\n")
        append("Host: $dstHost:$dstPort\r\n")
        if (sniHost.isNotBlank()) {
            append("X-Online-Host: $sniHost\r\n")
        }
        if (includeAuthHeaders && (username.isNotBlank() || password.isNotBlank())) {
            val creds = "${username.ifBlank { "root" }}:$password"
            append("Proxy-Authorization: Basic ${encodeBasicAuth(creds)}\r\n")
        }
        if (includeAuthHeaders && password.isNotBlank()) {
            append("X-VPS-Auth: $password\r\n")
        }
        append("Proxy-Connection: Keep-Alive\r\n\r\n")
    }

    fun probeHttpConnectProxy(
        host: String,
        port: Int,
        username: String,
        password: String,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 4500,
        sniHost: String = ""
    ): HttpProxyProbeOutcome {
        val headerModes = if (password.isNotBlank() || username.isNotBlank()) {
            listOf(true, false)
        } else {
            listOf(false)
        }
        var lastOutcome: HttpProxyProbeOutcome? = null

        for (includeAuthHeaders in headerModes) {
            val socket = Socket()
            val outcome = try {
                bindSocketIfNeeded(socket)
                if (!protectSocket(socket)) {
                    return HttpProxyProbeOutcome(false, null, null, "No se pudo proteger el socket proxy")
                }
                socket.tcpNoDelay = true
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
                socket.soTimeout = timeoutMs

                val req = buildHttpConnectRequest(
                    dstHost = "connectivitycheck.gstatic.com",
                    dstPort = 80,
                    username = username,
                    password = password,
                    sniHost = sniHost,
                    includeAuthHeaders = includeAuthHeaders
                )
                socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()

                val headers = readHttpHeadersExact(socket.getInputStream())
                val connectRtt = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                val statusLine = headers?.lineSequence()?.firstOrNull()?.trim()
                val statusCode = parseHttpStatusCode(statusLine)
                val location = extractHeaderValue(headers, "Location")

                if (statusCode !in 200..299) {
                    val locSuffix = location?.let { " → Location: $it" }.orEmpty()
                    val msg = when {
                        statusCode == 407 -> "Proxy exige autenticación válida ($statusLine)"
                        statusCode in 300..399 -> "Puerto $port redirigió '$statusLine'$locSuffix (no es proxy CONNECT)"
                        statusLine != null -> "Puerto $port respondió '$statusLine' (no es proxy CONNECT)"
                        else -> "Puerto $port cerró conexión sin cabecera HTTP"
                    }
                    HttpProxyProbeOutcome(false, connectRtt, statusLine, msg, locationHeader = location)
                } else {
                    // Send real HTTP 204 request inside the CONNECT tunnel to verify upstream internet works
                    socket.getOutputStream().write(
                        "GET /generate_204 HTTP/1.1\r\nHost: connectivitycheck.gstatic.com\r\nConnection: close\r\n\r\n"
                            .toByteArray(Charsets.UTF_8)
                    )
                    socket.getOutputStream().flush()
                    val innerHeaders = readHttpHeadersExact(socket.getInputStream())
                    val innerStatus = innerHeaders?.lineSequence()?.firstOrNull()?.trim()
                    val innerCode = parseHttpStatusCode(innerStatus)
                    val totalRtt = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

                    if (innerCode in 200..399) {
                        HttpProxyProbeOutcome(true, totalRtt, statusLine, null)
                    } else {
                        HttpProxyProbeOutcome(
                            false,
                            totalRtt,
                            statusLine,
                            "CONNECT aceptó ($statusLine) pero el tráfico hacia Internet falló (${innerStatus ?: "sin respuesta"})"
                        )
                    }
                }
            } catch (e: Exception) {
                HttpProxyProbeOutcome(
                    false,
                    null,
                    null,
                    e.localizedMessage ?: e.javaClass.simpleName
                )
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }

            if (outcome.supported) return outcome
            lastOutcome = outcome
            val code = parseHttpStatusCode(outcome.statusLine)
            // Retry without auth headers only if strict proxy rejected extra headers with 400 or 403
            if (code !in setOf(400, 403) && outcome.statusLine?.contains("200") != true) break
        }

        // Fallback: many Squid/CONNECT proxies restrict CONNECT to SSL port 443 only (denying :80 with 403).
        val lastCode = parseHttpStatusCode(lastOutcome?.statusLine)
        if (lastCode == 403 || lastOutcome?.statusLine?.startsWith("HTTP/1.1 200") == true || lastOutcome?.statusLine?.startsWith("HTTP/1.0 200") == true) {
            val httpsProbe = probeHttpConnectHttpsDoH(
                host = host,
                port = port,
                username = username,
                password = password,
                protectSocket = protectSocket,
                timeoutMs = timeoutMs,
                sniHost = sniHost
            )
            if (httpsProbe.supported) return httpsProbe
        }

        return lastOutcome ?: HttpProxyProbeOutcome(false, null, null, "Puerto $port no responde como proxy HTTP CONNECT")
    }

    private fun probeHttpConnectHttpsDoH(
        host: String,
        port: Int,
        username: String,
        password: String,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int,
        sniHost: String
    ): HttpProxyProbeOutcome {
        val headerModes = if (password.isNotBlank() || username.isNotBlank()) listOf(true, false) else listOf(false)
        for (includeAuthHeaders in headerModes) {
            val socket = Socket()
            try {
                bindSocketIfNeeded(socket)
                if (!protectSocket(socket)) {
                    return HttpProxyProbeOutcome(false, null, null, "No se pudo proteger el socket proxy")
                }
                socket.tcpNoDelay = true
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
                socket.soTimeout = timeoutMs

                val req = buildHttpConnectRequest(
                    dstHost = "1.1.1.1",
                    dstPort = 443,
                    username = username,
                    password = password,
                    sniHost = sniHost,
                    includeAuthHeaders = includeAuthHeaders
                )
                socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()

                val headers = readHttpHeadersExact(socket.getInputStream())
                val statusLine = headers?.lineSequence()?.firstOrNull()?.trim()
                val code = parseHttpStatusCode(statusLine)
                if (code in 200..299) {
                    val dohResp = DnsSslEngineHelper.executeDoHOverStreams(
                        input = socket.getInputStream(),
                        output = socket.getOutputStream(),
                        sniHost = "1.1.1.1",
                        dnsQuery = NetworkDiagnosticEngine.buildDnsAQueryPacket("google.com"),
                        timeoutMs = timeoutMs,
                        onTimeoutClose = { try { socket.close() } catch (_: Exception) {} }
                    )
                    if (dohResp != null && dohResp.size >= 12) {
                        val totalRtt = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                        return HttpProxyProbeOutcome(true, totalRtt, statusLine, null)
                    }
                    break
                } else if (code !in setOf(400, 403)) {
                    break
                }
            } catch (_: Exception) {
                break
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
        return HttpProxyProbeOutcome(false, null, null, null)
    }

    fun bindSocketIfNeeded(socket: Socket) {
        try {
            if (!socket.isBound) {
                socket.bind(InetSocketAddress(0))
            }
        } catch (_: Exception) {
        }
    }

    fun readHttpHeadersExact(input: InputStream): String? {
        val bytes = ByteArray(4096)
        var count = 0
        while (count < bytes.size) {
            val b = try {
                input.read()
            } catch (_: Exception) {
                return if (count > 0) String(bytes, 0, count, Charsets.UTF_8) else null
            }
            if (b < 0) return if (count > 0) String(bytes, 0, count, Charsets.UTF_8) else null
            bytes[count++] = b.toByte()
            if (count >= 4 &&
                bytes[count - 4] == '\r'.code.toByte() &&
                bytes[count - 3] == '\n'.code.toByte() &&
                bytes[count - 2] == '\r'.code.toByte() &&
                bytes[count - 1] == '\n'.code.toByte()
            ) {
                break
            }
            if (count >= 2 &&
                bytes[count - 2] == '\n'.code.toByte() &&
                bytes[count - 1] == '\n'.code.toByte()
            ) {
                break
            }
        }
        return if (count > 0) String(bytes, 0, count, Charsets.UTF_8) else null
    }

    fun extractHeaderValue(headers: String?, headerName: String): String? {
        if (headers.isNullOrBlank()) return null
        val prefix = "$headerName:"
        return headers.lineSequence()
            .firstOrNull { it.trimStart().startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseHttpStatusCode(statusLine: String?): Int {
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
}
