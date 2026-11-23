package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.VpnProfileEntity
import com.example.data.model.AppSettingsState
import com.example.data.model.DiagnosticCheckStatus
import com.example.data.model.LogLevel
import com.example.data.model.NetworkDiagnosticCheck
import com.example.data.model.NetworkDiagnosticReport
import com.example.data.model.TunnelDiagnosticsState
import com.example.data.repository.VpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object NetworkDiagnosticEngine {

    suspend fun runDiagnostics(
        context: Context,
        profile: VpnProfileEntity?,
        settings: AppSettingsState,
        logEmitter: (String, String, LogLevel) -> Unit = { tag, msg, level ->
            VpnStateHolder.appendLog(tag, msg, level)
        }
    ): NetworkDiagnosticReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<NetworkDiagnosticCheck>()
        val targetHost = profile?.host?.trim().orEmpty()
        val targetPort = profile?.port ?: 8080

        logEmitter(
            "DIAG",
            "Iniciando diagnóstico completo (${targetHost.ifBlank { "sin VPS" }}:$targetPort)…",
            LogLevel.INFO
        )

        // 1. Active Network & TUN Interface
        val ifaceCheck = inspectNetworkInterfaces(context, VpnStateHolder.diagnostics.value)
        checks += ifaceCheck
        logCheck("INTERFAZ", ifaceCheck, logEmitter)

        // 2. DNS UDP & System Resolver
        val dnsCheck = probeDnsResolution(settings.dnsProvider.primary, settings.dnsProvider.secondary)
        checks += dnsCheck
        logCheck("DIAG-DNS", dnsCheck, logEmitter)

        // 3. VPS TCP & BHTTP Handshake
        val bhttpCheck = probeVpsBhttp(profile)
        checks += bhttpCheck
        logCheck("DIAG-BHTTP", bhttpCheck, logEmitter)

        // 4. HTTP CONNECT Tunnel Capability on VPS Port
        val proxyCheck = probeHttpConnect(profile)
        checks += proxyCheck
        logCheck("DIAG-PROXY", proxyCheck, logEmitter)

        // 5. SSH :22 Auth + Real direct-tcpip HTTP 204
        val sshCheck = probeSshDirectTcpIp(profile, settings, logEmitter)
        checks += sshCheck
        logCheck("DIAG-SSH", sshCheck, logEmitter)

        // 6. Real End-to-End HTTP 204 Probe
        val httpCheck = probeHttp204("connectivitycheck.gstatic.com", 80)
        checks += httpCheck
        logCheck("DIAG-HTTP", httpCheck, logEmitter)

        val (overall, summary, recommendation) = summarizeDiagnosticChecks(
            checks = checks,
            tunnelDiagnostics = VpnStateHolder.diagnostics.value,
            host = targetHost,
            port = targetPort
        )
        logEmitter(
            "DIAG",
            "Diagnóstico finalizado: $summary",
            when (overall) {
                DiagnosticCheckStatus.PASS -> LogLevel.SUCCESS
                DiagnosticCheckStatus.WARN -> LogLevel.WARNING
                DiagnosticCheckStatus.FAIL -> LogLevel.ERROR
            }
        )

        NetworkDiagnosticReport(
            checkedAtMillis = System.currentTimeMillis(),
            summary = summary,
            recommendedAction = recommendation,
            overallStatus = overall,
            checks = checks
        )
    }

    private fun logCheck(
        tag: String,
        check: NetworkDiagnosticCheck,
        logEmitter: (String, String, LogLevel) -> Unit
    ) {
        val level = when (check.status) {
            DiagnosticCheckStatus.PASS -> LogLevel.SUCCESS
            DiagnosticCheckStatus.WARN -> LogLevel.WARNING
            DiagnosticCheckStatus.FAIL -> LogLevel.ERROR
        }
        val rttSuffix = check.latencyMs?.let { " (${it}ms)" }.orEmpty()
        logEmitter(tag, "${check.title}$rttSuffix: ${check.detail}", level)
    }

    private fun inspectNetworkInterfaces(
        context: Context,
        diag: TunnelDiagnosticsState
    ): NetworkDiagnosticCheck {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            val links = if (network != null) cm.getLinkProperties(network) else null

            if (network == null || caps == null) {
                return NetworkDiagnosticCheck(
                    id = "network_iface",
                    title = "Red Activa e Interfaz TUN",
                    detail = "Sin red activa detectada por Android ConnectivityManager.",
                    status = DiagnosticCheckStatus.FAIL
                )
            }

            val transports = buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Datos Móviles")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            }.ifEmpty { listOf("Otra") }

            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val dnsServers = links?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ").orEmpty()
            val addrs = links?.linkAddresses?.map { it.toString() }?.take(2)?.joinToString(", ").orEmpty()

            val vpnErrors = diag.dnsFailCount + diag.tcpFailCount + diag.sshFailCount
            val status = when {
                !hasInternet -> DiagnosticCheckStatus.FAIL
                vpnErrors > 0 -> DiagnosticCheckStatus.WARN
                else -> DiagnosticCheckStatus.PASS
            }

            val detail = buildString {
                append("Transporte: ${transports.joinToString("+")}")
                append(" • Internet=${if (hasInternet) "Sí" else "No"}")
                append(" • Validada=${if (isValidated) "Sí" else "Pendiente"}")
                if (addrs.isNotBlank()) append(" • IP=$addrs")
                if (dnsServers.isNotBlank()) append(" • DNS=$dnsServers")
                if (diag.tunnelMode != "Inactivo") append(" • Modo=${diag.tunnelMode}")
                if (vpnErrors > 0) append(" • Errores en sesión: DNS=${diag.dnsFailCount}, TCP=${diag.tcpFailCount}, SSH=${diag.sshFailCount}")
            }
            NetworkDiagnosticCheck(
                id = "network_iface",
                title = "Red Activa e Interfaz TUN",
                detail = detail,
                status = status
            )
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "network_iface",
                title = "Red Activa e Interfaz TUN",
                detail = "Error leyendo estado de red: ${e.localizedMessage ?: e.javaClass.simpleName}",
                status = DiagnosticCheckStatus.WARN
            )
        }
    }

    private fun probeDnsResolution(primaryDns: String, secondaryDns: String): NetworkDiagnosticCheck {
        val query = buildDnsAQueryPacket("google.com")
        val servers = listOf(primaryDns, secondaryDns, "8.8.8.8").filter { it.isNotBlank() }.distinct()
        var lastErr = "Sin respuesta"

        for (dnsIp in servers) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 1500
                val startNs = System.nanoTime()
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName(dnsIp), 53))
                val resp = ByteArray(1024)
                val dp = DatagramPacket(resp, resp.size)
                socket.receive(dp)
                val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                if (dp.length >= 12) {
                    val answers = ((resp[6].toInt() and 0xFF) shl 8) or (resp[7].toInt() and 0xFF)
                    return NetworkDiagnosticCheck(
                        id = "dns_udp",
                        title = "Resolución DNS UDP ($dnsIp:53)",
                        detail = "google.com resuelto vía UDP :53 con $answers registro(s) en ${ms} ms.",
                        status = DiagnosticCheckStatus.PASS,
                        latencyMs = ms
                    )
                }
            } catch (e: Exception) {
                lastErr = "$dnsIp -> ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        // Compare with OS resolver
        return try {
            val startNs = System.nanoTime()
            val resolved = InetAddress.getAllByName("google.com").firstOrNull()?.hostAddress
            val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            NetworkDiagnosticCheck(
                id = "dns_udp",
                title = "Resolución DNS",
                detail = "UDP :53 falló ($lastErr), pero sistema resolvió google.com -> $resolved (${ms}ms). Posible bloqueo UDP de operadora.",
                status = DiagnosticCheckStatus.WARN,
                latencyMs = ms
            )
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "dns_udp",
                title = "Resolución DNS",
                detail = "Fallo total DNS UDP ($lastErr) y sistema (${e.localizedMessage ?: e.javaClass.simpleName}).",
                status = DiagnosticCheckStatus.FAIL
            )
        }
    }

    private fun probeVpsBhttp(profile: VpnProfileEntity?): NetworkDiagnosticCheck {
        if (profile == null || profile.host.isBlank()) {
            return NetworkDiagnosticCheck(
                id = "vps_bhttp",
                title = "Puerto VPS & Handshake BHTTP",
                detail = "No hay ningún perfil VPS seleccionado.",
                status = DiagnosticCheckStatus.WARN
            )
        }
        if (profile.port == 22) {
            return NetworkDiagnosticCheck(
                id = "vps_bhttp",
                title = "Puerto VPS (${profile.host}:22)",
                detail = "Perfil configurado en modo SSH Directo (:22); omitiendo handshake BHTTP.",
                status = DiagnosticCheckStatus.PASS
            )
        }

        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(profile.host, profile.port), 3000)
            val connectMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            socket.soTimeout = 2000

            val payload = BhttpPayloadBuilder.compile(profile).toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var readErr: String? = null
            val statusLine = try {
                reader.readLine()
            } catch (e: Exception) {
                readErr = e.localizedMessage ?: e.javaClass.simpleName
                null
            }
            val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            if (statusLine != null) {
                NetworkDiagnosticCheck(
                    id = "vps_bhttp",
                    title = "VPS BHTTP (${profile.host}:${profile.port})",
                    detail = "TCP ${connectMs}ms • Respuesta servidor: '$statusLine'",
                    status = DiagnosticCheckStatus.PASS,
                    latencyMs = totalMs
                )
            } else {
                NetworkDiagnosticCheck(
                    id = "vps_bhttp",
                    title = "VPS BHTTP (${profile.host}:${profile.port})",
                    detail = "Puerto TCP abierto en ${connectMs}ms, pero sin cabecera HTTP BHTTP (${readErr ?: "EOF"}). Si el VPS no reenvía datos aquí, requiere SSH :22 o proxy.",
                    status = DiagnosticCheckStatus.WARN,
                    latencyMs = totalMs
                )
            }
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "vps_bhttp",
                title = "VPS BHTTP (${profile.host}:${profile.port})",
                detail = "No conecta a ${profile.host}:${profile.port}: ${e.localizedMessage ?: e.javaClass.simpleName}",
                status = DiagnosticCheckStatus.FAIL
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun probeHttpConnect(profile: VpnProfileEntity?): NetworkDiagnosticCheck {
        if (profile == null || profile.host.isBlank()) {
            return NetworkDiagnosticCheck(
                id = "proxy_connect",
                title = "Proxy HTTP CONNECT",
                detail = "No hay perfil seleccionado para verificar proxy HTTP CONNECT.",
                status = DiagnosticCheckStatus.WARN
            )
        }
        if (profile.port == 22) {
            return NetworkDiagnosticCheck(
                id = "proxy_connect",
                title = "Proxy HTTP CONNECT",
                detail = "Puerto 22 usa SSH direct-tcpip directamente.",
                status = DiagnosticCheckStatus.PASS
            )
        }

        val externalProbe = VpsReachabilityTester.probeHttpConnectProxy(
            host = profile.host,
            port = profile.port,
            username = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER },
            password = profile.password.trim(),
            protectSocket = { true },
            timeoutMs = 3500,
            sniHost = profile.sniHost.trim()
        )
        if (externalProbe.supported) {
            return NetworkDiagnosticCheck(
                id = "proxy_connect",
                title = "Proxy HTTP CONNECT (:${profile.port})",
                detail = "Puerto ${profile.port} acepta HTTP CONNECT completo hacia Internet (${externalProbe.statusLine ?: "200 OK"}) en ${externalProbe.latencyMs ?: 1} ms.",
                status = DiagnosticCheckStatus.PASS,
                latencyMs = externalProbe.latencyMs
            )
        }

        // Also test CONNECT 127.0.0.1:22 (SSH-only HTTP CONNECT proxy pattern used by Squid/Python scripts)
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(profile.host.trim(), profile.port), 2500)
            socket.soTimeout = 1800

            val req = VpsReachabilityTester.buildHttpConnectRequest(
                dstHost = "127.0.0.1",
                dstPort = 22,
                username = profile.username.trim(),
                password = profile.password.trim(),
                sniHost = profile.sniHost.trim(),
                includeAuthHeaders = false
            )
            socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()

            val statusLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
            val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            val code = VpsReachabilityTester.parseHttpStatusCode(statusLine)
            when {
                code in 200..299 -> NetworkDiagnosticCheck(
                    id = "proxy_connect",
                    title = "Proxy HTTP CONNECT→SSH (:${profile.port})",
                    detail = "Puerto ${profile.port} acepta HTTP CONNECT hacia SSH 127.0.0.1:22 ($statusLine) en ${ms} ms.",
                    status = DiagnosticCheckStatus.PASS,
                    latencyMs = ms
                )
                statusLine != null -> NetworkDiagnosticCheck(
                    id = "proxy_connect",
                    title = "Proxy HTTP CONNECT (:${profile.port})",
                    detail = "Puerto ${profile.port} rechazó HTTP CONNECT con '$statusLine' (${ms}ms). Instala o activa el proxy HTTP CONNECT en el VPS.",
                    status = DiagnosticCheckStatus.WARN,
                    latencyMs = ms
                )
                else -> NetworkDiagnosticCheck(
                    id = "proxy_connect",
                    title = "Proxy HTTP CONNECT (:${profile.port})",
                    detail = "Puerto ${profile.port} cerró conexión (EOF) sin aceptar HTTP CONNECT (${ms}ms). Instala el proxy HTTP CONNECT en tu VPS.",
                    status = DiagnosticCheckStatus.WARN,
                    latencyMs = ms
                )
            }
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "proxy_connect",
                title = "Proxy HTTP CONNECT (:${profile.port})",
                detail = externalProbe.errorDetail ?: "Error probando CONNECT en ${profile.host}:${profile.port}: ${e.localizedMessage ?: e.javaClass.simpleName}",
                status = DiagnosticCheckStatus.WARN
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun probeSshDirectTcpIp(
        profile: VpnProfileEntity?,
        settings: AppSettingsState,
        logEmitter: (String, String, LogLevel) -> Unit
    ): NetworkDiagnosticCheck {
        if (profile == null || profile.host.isBlank()) {
            return NetworkDiagnosticCheck(
                id = "ssh_tunnel",
                title = "Túnel SSH (:22) + Salida Internet",
                detail = "Sin perfil VPS para probar SSH.",
                status = DiagnosticCheckStatus.WARN
            )
        }

        val bannerSocket = Socket()
        val bannerText: String?
        val bannerMs: Int
        try {
            val startNs = System.nanoTime()
            bannerSocket.connect(InetSocketAddress(profile.host, 22), 2500)
            bannerSocket.soTimeout = 1800
            bannerText = BufferedReader(InputStreamReader(bannerSocket.getInputStream())).readLine()
            bannerMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            try { bannerSocket.close() } catch (_: Exception) {}
            return NetworkDiagnosticCheck(
                id = "ssh_tunnel",
                title = "Túnel SSH (${profile.host}:22)",
                detail = "Puerto 22 inaccesible: ${e.localizedMessage ?: e.javaClass.simpleName}",
                status = DiagnosticCheckStatus.FAIL
            )
        } finally {
            try { bannerSocket.close() } catch (_: Exception) {}
        }

        val effectiveUser = settings.personalTunnelUsername.trim().ifBlank {
            profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
        }
        val personalPass = settings.personalTunnelPassword.trim()
        val cleanProfilePass = profile.password.trim()
        val candidatePasswords = buildList {
            if (personalPass.isNotBlank()) add(personalPass)
            if (profile.host.trim() == VpnRepository.DEFAULT_VPS_HOST &&
                effectiveUser == VpnRepository.DEFAULT_SSH_USER
            ) {
                add(VpnRepository.DEFAULT_SSH_PASSWORD)
            }
            if (cleanProfilePass.isNotBlank()) add(cleanProfilePass)
        }.distinct()

        val probeSsh = SshTunnelClient(
            protectSocket = { true },
            host = profile.host,
            port = 22,
            username = effectiveUser,
            candidatePasswords = candidatePasswords,
            onLog = logEmitter
        )

        return try {
            val connectResult = probeSsh.connect(4500)
            if (connectResult.isFailure) {
                val err = connectResult.exceptionOrNull()?.localizedMessage ?: "Error autenticación SSH"
                return NetworkDiagnosticCheck(
                    id = "ssh_tunnel",
                    title = "Túnel SSH (${profile.host}:22)",
                    detail = "Banner '${bannerText ?: "SSH"}' (${bannerMs}ms), pero falló login '$effectiveUser': $err",
                    status = DiagnosticCheckStatus.FAIL,
                    latencyMs = bannerMs
                )
            }

            val sshMs = connectResult.getOrDefault(bannerMs)
            val startChannelNs = System.nanoTime()
            val channel = probeSsh.openDirectTcpIp("connectivitycheck.gstatic.com", 80, 3500)
            if (channel == null) {
                val detail = probeSsh.lastErrorMessage ?: "canal direct-tcpip rechazado por el servidor SSH"
                return NetworkDiagnosticCheck(
                    id = "ssh_tunnel",
                    title = "Túnel SSH (${profile.host}:22)",
                    detail = "SSH autenticado ($effectiveUser), pero el VPS bloqueó AllowTcpForwarding / direct-tcpip ($detail).",
                    status = DiagnosticCheckStatus.FAIL,
                    latencyMs = sshMs
                )
            }

            try {
                channel.outputStream.write(
                    "GET /generate_204 HTTP/1.0\r\nHost: connectivitycheck.gstatic.com\r\n\r\n".toByteArray(Charsets.UTF_8)
                )
                channel.outputStream.flush()
                val httpLine = BufferedReader(InputStreamReader(channel.inputStream)).readLine()
                val totalMs = ((System.nanoTime() - startChannelNs) / 1_000_000L).toInt().coerceAtLeast(1)
                if (httpLine != null && (httpLine.contains("204") || httpLine.contains("200"))) {
                    NetworkDiagnosticCheck(
                        id = "ssh_tunnel",
                        title = "Túnel SSH (${profile.host}:22)",
                        detail = "Auth OK + canal direct-tcpip funcional -> '$httpLine' en ${totalMs} ms.",
                        status = DiagnosticCheckStatus.PASS,
                        latencyMs = sshMs
                    )
                } else {
                    NetworkDiagnosticCheck(
                        id = "ssh_tunnel",
                        title = "Túnel SSH (${profile.host}:22)",
                        detail = "Canal direct-tcpip abierto, respuesta inesperada: ${httpLine ?: "EOF sin respuesta HTTP"}",
                        status = DiagnosticCheckStatus.WARN,
                        latencyMs = totalMs
                    )
                }
            } finally {
                channel.close()
            }
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "ssh_tunnel",
                title = "Túnel SSH (${profile.host}:22)",
                detail = "Excepción probando canal SSH: ${e.localizedMessage ?: e.javaClass.simpleName}",
                status = DiagnosticCheckStatus.FAIL
            )
        } finally {
            probeSsh.disconnect()
        }
    }

    private fun probeHttp204(host: String, port: Int): NetworkDiagnosticCheck {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.soTimeout = 2500
            socket.getOutputStream().write(
                "GET /generate_204 HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
            )
            socket.getOutputStream().flush()
            val statusLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
            val ms = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            if (statusLine != null && (statusLine.contains("204") || statusLine.contains("200") || statusLine.contains("30"))) {
                NetworkDiagnosticCheck(
                    id = "direct_http",
                    title = "Salida Internet HTTP 204",
                    detail = "$host respondió '$statusLine' en ${ms} ms.",
                    status = DiagnosticCheckStatus.PASS,
                    latencyMs = ms
                )
            } else {
                NetworkDiagnosticCheck(
                    id = "direct_http",
                    title = "Salida Internet HTTP 204",
                    detail = "$host devolvió respuesta inesperada: ${statusLine ?: "EOF"}",
                    status = DiagnosticCheckStatus.WARN,
                    latencyMs = ms
                )
            }
        } catch (e: Exception) {
            NetworkDiagnosticCheck(
                id = "direct_http",
                title = "Salida Internet HTTP 204",
                detail = "Sin salida HTTP a $host:$port (${e.localizedMessage ?: e.javaClass.simpleName}).",
                status = DiagnosticCheckStatus.FAIL
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    internal fun buildDnsAQueryPacket(domain: String, txId: Int = 0x4A46): ByteArray {
        val labels = domain.trim('.').split('.').filter { it.isNotEmpty() }
        val qnameLen = labels.sumOf { 1 + it.length } + 1
        val pkt = ByteArray(12 + qnameLen + 4)
        pkt[0] = ((txId ushr 8) and 0xFF).toByte()
        pkt[1] = (txId and 0xFF).toByte()
        pkt[2] = 0x01 // standard query, recursion desired
        pkt[3] = 0x00
        pkt[4] = 0x00
        pkt[5] = 0x01 // QDCOUNT = 1

        var pos = 12
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            pkt[pos++] = bytes.size.toByte()
            System.arraycopy(bytes, 0, pkt, pos, bytes.size)
            pos += bytes.size
        }
        pkt[pos++] = 0 // root label
        pkt[pos++] = 0x00
        pkt[pos++] = 0x01 // TYPE A
        pkt[pos++] = 0x00
        pkt[pos] = 0x01 // CLASS IN
        return pkt
    }

    internal fun summarizeDiagnosticChecks(
        checks: List<NetworkDiagnosticCheck>,
        tunnelDiagnostics: TunnelDiagnosticsState,
        host: String,
        port: Int
    ): Triple<DiagnosticCheckStatus, String, String> {
        val byId = checks.associateBy { it.id }
        val dns = byId["dns_udp"]
        val bhttp = byId["vps_bhttp"]
        val proxy = byId["proxy_connect"]
        val ssh = byId["ssh_tunnel"]
        val http = byId["direct_http"]

        val failCount = checks.count { it.status == DiagnosticCheckStatus.FAIL }
        val warnCount = checks.count { it.status == DiagnosticCheckStatus.WARN }

        return when {
            http?.status == DiagnosticCheckStatus.FAIL && dns?.status == DiagnosticCheckStatus.FAIL -> Triple(
                DiagnosticCheckStatus.FAIL,
                "Sin resolución DNS ni salida HTTP 204 en la ruta actual.",
                "Revisa si la operadora tiene datos activos o si el túnel está bloqueando UDP :53; revisa la pestaña LOG VPN para ver el error exacto en [DNS] y [TCP]."
            )

            ssh?.status == DiagnosticCheckStatus.FAIL && proxy?.status != DiagnosticCheckStatus.PASS -> Triple(
                DiagnosticCheckStatus.FAIL,
                "Puerto $port sin proxy HTTP CONNECT y respaldo SSH ($host:22) con error.",
                "El servidor SSH rechazó login o direct-tcpip, y el puerto $port no acepta HTTP CONNECT. Verifica usuario/clave o protocolo SSH :22 en tu perfil."
            )

            ssh?.status == DiagnosticCheckStatus.PASS && http?.status == DiagnosticCheckStatus.PASS -> Triple(
                if (warnCount > 0) DiagnosticCheckStatus.WARN else DiagnosticCheckStatus.PASS,
                "Túnel SSH y salida HTTP 204 operativos.",
                if (proxy?.status == DiagnosticCheckStatus.WARN) {
                    "Nota: el puerto $port no funciona como proxy HTTP CONNECT; mantén activo el respaldo SSH :22 o usa el perfil SSH Directo (:22)."
                } else {
                    "Conectividad validada correctamente. Si una app falla, revisa los errores en vivo en la pestaña LOG VPN."
                }
            )

            bhttp?.status == DiagnosticCheckStatus.FAIL -> Triple(
                DiagnosticCheckStatus.FAIL,
                "El VPS $host:$port no responde a la conexión TCP.",
                "Comprueba que la IP/puerto del VPS sean correctos o cambia de puerto/compañía en Servidores."
            )

            tunnelDiagnostics.dnsFailCount > 0 || tunnelDiagnostics.tcpFailCount > 0 -> Triple(
                DiagnosticCheckStatus.WARN,
                "Se detectaron errores recientes en el relay del túnel (DNS=${tunnelDiagnostics.dnsFailCount}, TCP=${tunnelDiagnostics.tcpFailCount}).",
                "Abre LOG VPN y filtra por 'Errores' para inspeccionar el último destino rechazado."
            )

            failCount > 0 -> Triple(
                DiagnosticCheckStatus.FAIL,
                "Diagnóstico detectó $failCount error(es) crítico(s) de conectividad.",
                "Revisa los bloques en rojo abajo y los mensajes detallados en LOG VPN."
            )

            warnCount > 0 -> Triple(
                DiagnosticCheckStatus.WARN,
                "Conectividad parcial con $warnCount advertencia(s).",
                "Revisa los detalles de puerto BHTTP/CONNECT y compara con el modo mostrado en Monitor 10 Min."
            )

            else -> Triple(
                DiagnosticCheckStatus.PASS,
                "Todas las pruebas de red y túnel pasaron correctamente.",
                "El enlace DNS, puerto VPS y salida HTTP 204 responden dentro de parámetros normales."
            )
        }
    }
}
