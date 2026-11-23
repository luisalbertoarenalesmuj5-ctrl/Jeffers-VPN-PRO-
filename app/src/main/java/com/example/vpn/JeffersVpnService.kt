package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionSessionEntity
import com.example.data.local.SettingsStore
import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import com.example.data.model.LogLevel
import com.example.data.model.TunnelProtocol
import com.example.data.model.VpnConnectionStatus
import com.example.data.repository.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class JeffersVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private var controlSocket: Socket? = null
    private var activeSshClient: SshTunnelClient? = null
    private var activeRelay: TunPacketRelay? = null
    private var sessionStartedAt: Long = 0L
    private var currentProfile: VpnProfileEntity? = null

    @Volatile
    private var userInitiatedStop: Boolean = false

    @Volatile
    private var selfAppExcluded: Boolean = false

    private var loggedBypassFallbackProtect: Boolean = false

    private fun protectSocketSafely(socket: Socket): Boolean {
        VpsReachabilityTester.bindSocketIfNeeded(socket)
        if (protect(socket)) {
            return true
        }
        val tunActive = tunInterface != null
        if (!tunActive || selfAppExcluded) {
            if (!loggedBypassFallbackProtect) {
                loggedBypassFallbackProtect = true
                VpnStateHolder.appendLog(
                    "SOCK",
                    if (!tunActive) {
                        "Socket pre-TUN enlazado directamente sobre red física"
                    } else {
                        "App excluida del TUN (addDisallowedApplication); socket protegido por UID"
                    },
                    LogLevel.INFO
                )
            }
            return true
        }
        return false
    }

    private fun protectDatagramSafely(socket: java.net.DatagramSocket): Boolean {
        try {
            if (!socket.isBound) {
                socket.bind(null)
            }
        } catch (_: Exception) {
        }
        if (protect(socket)) {
            return true
        }
        return tunInterface == null || selfAppExcluded
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            userInitiatedStop = true
            VpnStateHolder.appendLog("VPN", "Desconexión solicitada por el usuario", LogLevel.INFO)
            stopTunnel(status = VpnConnectionStatus.DISCONNECTED, message = null)
            return START_NOT_STICKY
        }

        userInitiatedStop = false
        val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
        createNotificationChannel()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Verificando servidor VPS…", isConnected = false),
            fgsType
        )

        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            runWithAutoReconnect(profileId)
        }
        return START_STICKY
    }

    private suspend fun runWithAutoReconnect(requestedProfileId: Long) {
        val settingsStore = SettingsStore(applicationContext)
        val maxAttempts = if (settingsStore.settings.value.autoReconnect) 5 else 1
        var attempt = 1

        while (attempt <= maxAttempts && !userInitiatedStop && serviceScope.isActive) {
            val result = startTunnelAttempt(requestedProfileId)
            if (userInitiatedStop || !settingsStore.settings.value.autoReconnect || result) {
                break
            }
            if (attempt < maxAttempts) {
                val delayMs = (attempt * 3000L).coerceAtMost(12000L)
                VpnStateHolder.appendLog(
                    "RECONEXIÓN",
                    "Reintento $attempt/$maxAttempts en ${delayMs / 1000}s…",
                    LogLevel.WARNING
                )
                updateNotification("Reconectando ($attempt/$maxAttempts)…", isConnected = false)
                delay(delayMs)
            }
            attempt++
        }
    }

    private suspend fun startTunnelAttempt(requestedProfileId: Long): Boolean {
        val repository = VpnRepository(AppDatabase.getInstance(applicationContext).vpnDao())
        repository.ensureDefaultProfilesSeeded()

        val baseProfile = if (requestedProfileId > 0L) {
            repository.getProfileById(requestedProfileId) ?: repository.getSelectedProfile()
        } else {
            repository.getSelectedProfile()
        }

        if (baseProfile == null) {
            VpnStateHolder.appendLog("CORE", "No se encontró ningún perfil VPS activo", LogLevel.ERROR)
            stopTunnel(VpnConnectionStatus.ERROR, "No hay perfil VPS seleccionado")
            return false
        }

        val engineSettings = SettingsStore(applicationContext).settings.value
        val personalUser = engineSettings.personalTunnelUsername.trim()
        val personalPass = engineSettings.personalTunnelPassword.trim()
        val profile = if (personalUser.isNotEmpty()) {
            VpnStateHolder.appendLog(
                "CUENTA",
                "Usando usuario personal de túnel: $personalUser",
                LogLevel.INFO
            )
            baseProfile.copy(
                username = personalUser,
                password = personalPass.ifEmpty { baseProfile.password }
            )
        } else {
            baseProfile
        }

        currentProfile = profile
        selfAppExcluded = false
        loggedBypassFallbackProtect = false
        val carrier = CarrierType.fromName(profile.carrier)
        val protocol = TunnelProtocol.fromName(profile.protocol)
        val cleanHost = profile.host.trim()
        val sshUser = profile.username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
        val sshCandidatePasswords = VpsReachabilityTester.resolveCandidatePasswords(profile)

        VpnStateHolder.resetDiagnostics()
        VpnStateHolder.markServerChecking(profile.id)
        VpnStateHolder.updateTelemetry {
            it.copy(
                status = VpnConnectionStatus.CONNECTING,
                activeProfileId = profile.id,
                activeProfileName = profile.vpsName,
                serverHost = cleanHost,
                serverPort = profile.port,
                carrier = carrier,
                protocol = protocol,
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                durationSeconds = 0L,
                pingMs = null,
                statusMessage = "Verificando ${profile.vpsName} ($cleanHost:${profile.port} • ${protocol.badge})…"
            )
        }

        fun failAttempt(userFacingError: String, logDetail: String = userFacingError): Boolean {
            VpnStateHolder.appendLog("VPS", logDetail, LogLevel.ERROR)
            VpnStateHolder.markServerError(profile.id, userFacingError)
            VpnStateHolder.setStatus(VpnConnectionStatus.ERROR, userFacingError)
            if (!engineSettings.autoReconnect) {
                stopTunnel(
                    status = VpnConnectionStatus.ERROR,
                    message = userFacingError
                )
            }
            return false
        }

        val configValidationErr = VpsReachabilityTester.validateProfileConfig(profile)
        if (configValidationErr != null) {
            return failAttempt(configValidationErr)
        }

        VpnStateHolder.appendLog(
            "VPS",
            "Iniciando ${profile.vpsName} • $cleanHost:${profile.port} • ${carrier.displayName} • ${protocol.badge} • ${engineSettings.performanceMode.title}",
            LogLevel.INFO
        )

        var pingMs = 1
        var initialRxBytes = 0L
        var initialTxBytes = 0L
        var establishedSshClient: SshTunnelClient? = null
        var verifiedProxyConnect = false
        var verifiedChannelLabel = protocol.badge

        when {
            protocol == TunnelProtocol.SSH_DIRECT || profile.port == 22 -> {
                VpnStateHolder.appendLog(
                    "SSH",
                    "Conectando sesión SSH ($sshUser@$cleanHost:${profile.port})…",
                    LogLevel.INFO
                )
                val sshClient = SshTunnelClient(
                    protectSocket = ::protectSocketSafely,
                    host = cleanHost,
                    port = profile.port,
                    username = sshUser,
                    candidatePasswords = sshCandidatePasswords
                )
                val connectResult = sshClient.connect(6000)
                if (connectResult.isFailure) {
                    sshClient.disconnect()
                    val err = connectResult.exceptionOrNull()
                    val errorDetail = err?.localizedMessage ?: err?.javaClass?.simpleName ?: "Error SSH"
                    return failAttempt(
                        userFacingError = "Fallo SSH en $cleanHost:${profile.port}: $errorDetail",
                        logDetail = "Error autenticando SSH en $cleanHost:${profile.port}: $errorDetail"
                    )
                }
                val fwdResult = sshClient.verifyForwarding(4500)
                if (fwdResult.isFailure) {
                    val fwdErr = fwdResult.exceptionOrNull()?.localizedMessage ?: "Sin reenvío TCP"
                    sshClient.disconnect()
                    return failAttempt(
                        userFacingError = "SSH autenticado pero sin salida a Internet: $fwdErr",
                        logDetail = "SSH conectado en $cleanHost:${profile.port} pero direct-tcpip rechazado: $fwdErr"
                    )
                }
                pingMs = connectResult.getOrNull() ?: fwdResult.getOrDefault(1)
                establishedSshClient = sshClient
                activeSshClient = sshClient
                verifiedChannelLabel = "SSH :${profile.port}"
            }

            protocol == TunnelProtocol.HTTP_CONNECT -> {
                VpnStateHolder.appendLog(
                    "PROXY",
                    "Verificando proxy HTTP CONNECT en $cleanHost:${profile.port}…",
                    LogLevel.INFO
                )
                val proxyCheck = VpsReachabilityTester.probeHttpConnectProxy(
                    host = cleanHost,
                    port = profile.port,
                    username = sshUser,
                    password = profile.password.trim(),
                    protectSocket = ::protectSocketSafely,
                    timeoutMs = 5000,
                    sniHost = profile.sniHost.trim()
                )
                if (proxyCheck.supported) {
                    pingMs = proxyCheck.latencyMs ?: 1
                    verifiedProxyConnect = true
                    verifiedChannelLabel = "HTTP CONNECT :${profile.port}"
                    VpnStateHolder.appendLog(
                        "PROXY",
                        "Proxy HTTP CONNECT verificado ✓ (${proxyCheck.statusLine ?: "200 OK"} • ${pingMs} ms)",
                        LogLevel.SUCCESS
                    )
                } else if (sshCandidatePasswords.isNotEmpty()) {
                    VpnStateHolder.appendLog(
                        "PROXY",
                        "Proxy directo no disponible (${proxyCheck.errorDetail ?: proxyCheck.statusLine ?: "sin 200"}); probando HTTP CONNECT→SSH (127.0.0.1:22)…",
                        LogLevel.INFO
                    )
                    val connectFactory = VpsReachabilityTester.createHttpConnectSshSocketFactory(
                        profile = profile,
                        protectSocket = ::protectSocketSafely,
                        timeoutMs = 5500,
                        onLog = VpnStateHolder::appendLog
                    )
                    val sshViaProxy = SshTunnelClient(
                        protectSocket = ::protectSocketSafely,
                        host = cleanHost,
                        port = profile.port,
                        username = sshUser,
                        candidatePasswords = sshCandidatePasswords,
                        customSocketFactory = connectFactory
                    )
                    val conn = sshViaProxy.connect(6000)
                    val fwd = if (conn.isSuccess) sshViaProxy.verifyForwarding(4500) else Result.failure(
                        conn.exceptionOrNull() ?: java.io.IOException("Error SSH sobre HTTP CONNECT")
                    )
                    if (conn.isSuccess && fwd.isSuccess) {
                        pingMs = conn.getOrNull() ?: fwd.getOrDefault(1)
                        establishedSshClient = sshViaProxy
                        activeSshClient = sshViaProxy
                        verifiedChannelLabel = "HTTP CONNECT→SSH :${profile.port}"
                        VpnStateHolder.appendLog(
                            "PROXY",
                            "Túnel HTTP CONNECT→SSH verificado ✓ (${pingMs} ms)",
                            LogLevel.SUCCESS
                        )
                    } else {
                        sshViaProxy.disconnect()
                        val sshErr = fwd.exceptionOrNull()?.localizedMessage ?: sshViaProxy.lastErrorMessage
                        val reason = proxyCheck.errorDetail ?: "Puerto ${profile.port} no actúa como proxy HTTP CONNECT"
                        return failAttempt(
                            userFacingError = if (sshErr != null) "$reason • $sshErr" else reason,
                            logDetail = "HTTP CONNECT y HTTP CONNECT→SSH fallaron en $cleanHost:${profile.port}: $reason | $sshErr"
                        )
                    }
                } else {
                    val reason = proxyCheck.errorDetail ?: "Puerto ${profile.port} no actúa como proxy HTTP CONNECT"
                    return failAttempt(
                        userFacingError = reason,
                        logDetail = "HTTP CONNECT falló en $cleanHost:${profile.port}: $reason"
                    )
                }
            }

            else -> {
                // BHTTP or BHTTP_WS: test handshake & upgrade, then HTTP CONNECT proxy, then verified SSH :22
                var bhttpStatusLine: String? = null
                var bhttpHeaders: String? = null
                var bhttpTcpOk = false
                var bhttpTcpErr: String? = null

                val probeSocket = Socket()
                controlSocket = probeSocket
                try {
                    if (!protectSocketSafely(probeSocket)) {
                        throw java.io.IOException("No se pudo proteger socket de handshake")
                    }
                    probeSocket.tcpNoDelay = true
                    probeSocket.keepAlive = true
                    val startNs = System.nanoTime()
                    probeSocket.connect(InetSocketAddress(cleanHost, profile.port), 5500)
                    pingMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                    bhttpTcpOk = true
                    VpnStateHolder.appendLog(
                        "TCP",
                        "Conexión TCP establecida con $cleanHost:${profile.port} (${pingMs} ms)",
                        LogLevel.SUCCESS
                    )

                    probeSocket.soTimeout = 2500
                    val compiledPayload = BhttpPayloadBuilder.compile(profile)
                    val payloadBytes = compiledPayload.toByteArray(Charsets.UTF_8)
                    initialTxBytes = payloadBytes.size.toLong()
                    probeSocket.getOutputStream().write(payloadBytes)
                    probeSocket.getOutputStream().flush()
                    VpnStateHolder.appendLog(
                        "BHTTP",
                        "Handshake ${protocol.badge} enviado a $cleanHost:${profile.port} ($initialTxBytes B) • SNI=${profile.sniHost}",
                        LogLevel.INFO
                    )

                    bhttpHeaders = VpsReachabilityTester.readHttpHeadersExact(probeSocket.getInputStream())
                    bhttpStatusLine = bhttpHeaders?.lineSequence()?.firstOrNull()?.trim()
                    if (bhttpStatusLine != null) {
                        initialRxBytes += bhttpHeaders!!.toByteArray(Charsets.UTF_8).size
                        val code = VpsReachabilityTester.parseHttpStatusCode(bhttpStatusLine)
                        VpnStateHolder.appendLog(
                            "BHTTP",
                            "Respuesta servidor: $bhttpStatusLine",
                            if (code == 101 || code in 200..299) LogLevel.SUCCESS else LogLevel.WARNING
                        )
                    } else {
                        VpnStateHolder.appendLog(
                            "BHTTP",
                            "Puerto ${profile.port} sin cabecera HTTP inmediata (EOF/timeout)",
                            LogLevel.WARNING
                        )
                    }
                } catch (e: Exception) {
                    bhttpTcpErr = e.localizedMessage ?: e.javaClass.simpleName
                    VpnStateHolder.appendLog(
                        "TCP",
                        "Sin conexión TCP a $cleanHost:${profile.port}: $bhttpTcpErr",
                        LogLevel.WARNING
                    )
                } finally {
                    try { probeSocket.close() } catch (_: Exception) {}
                    controlSocket = null
                }

                // Step A: If server accepted 101 Switching Protocols or non-HTML 200, try SSH over upgraded stream
                val statusCode = VpsReachabilityTester.parseHttpStatusCode(bhttpStatusLine)
                val headersLower = bhttpHeaders.orEmpty().lowercase()
                val supportsUpgrade = statusCode == 101 ||
                    (statusCode in 200..299 && !headersLower.contains("content-type: text/html"))

                if (bhttpTcpOk && supportsUpgrade && sshCandidatePasswords.isNotEmpty()) {
                    val upgradeFactory = VpsReachabilityTester.createBhttpUpgradeSocketFactory(
                        profile = profile,
                        protectSocket = ::protectSocketSafely,
                        timeoutMs = 5500,
                        onLog = VpnStateHolder::appendLog
                    )
                    val upgradeSsh = SshTunnelClient(
                        protectSocket = ::protectSocketSafely,
                        host = cleanHost,
                        port = profile.port,
                        username = sshUser,
                        candidatePasswords = sshCandidatePasswords,
                        customSocketFactory = upgradeFactory
                    )
                    val conn = upgradeSsh.connect(5500)
                    if (conn.isSuccess && upgradeSsh.verifyForwarding(4000).isSuccess) {
                        establishedSshClient = upgradeSsh
                        activeSshClient = upgradeSsh
                        verifiedChannelLabel = "${protocol.badge}→SSH :${profile.port}"
                    } else {
                        upgradeSsh.disconnect()
                    }
                }

                // Step B: Check if port accepts standard HTTP CONNECT + internet egress OR SSH-over-HTTP-CONNECT
                if (establishedSshClient == null && bhttpTcpOk) {
                    val proxyCheck = VpsReachabilityTester.probeHttpConnectProxy(
                        host = cleanHost,
                        port = profile.port,
                        username = sshUser,
                        password = profile.password.trim(),
                        protectSocket = ::protectSocketSafely,
                        timeoutMs = 4500,
                        sniHost = profile.sniHost.trim()
                    )
                    if (proxyCheck.supported) {
                        verifiedProxyConnect = true
                        pingMs = proxyCheck.latencyMs ?: pingMs
                        verifiedChannelLabel = "HTTP CONNECT :${profile.port}"
                        VpnStateHolder.appendLog(
                            "PROXY",
                            "Proxy HTTP CONNECT verificado en :${profile.port} ✓ (${pingMs} ms)",
                            LogLevel.SUCCESS
                        )
                    } else if (sshCandidatePasswords.isNotEmpty()) {
                        val connectFactory = VpsReachabilityTester.createHttpConnectSshSocketFactory(
                            profile = profile,
                            protectSocket = ::protectSocketSafely,
                            timeoutMs = 5000,
                            onLog = VpnStateHolder::appendLog
                        )
                        val sshViaProxy = SshTunnelClient(
                            protectSocket = ::protectSocketSafely,
                            host = cleanHost,
                            port = profile.port,
                            username = sshUser,
                            candidatePasswords = sshCandidatePasswords,
                            customSocketFactory = connectFactory
                        )
                        val connProxy = sshViaProxy.connect(5000)
                        if (connProxy.isSuccess && sshViaProxy.verifyForwarding(4000).isSuccess) {
                            pingMs = connProxy.getOrNull() ?: pingMs
                            establishedSshClient = sshViaProxy
                            activeSshClient = sshViaProxy
                            verifiedChannelLabel = "HTTP CONNECT→SSH :${profile.port}"
                            VpnStateHolder.appendLog(
                                "PROXY",
                                "Túnel HTTP CONNECT→SSH en :${profile.port} verificado ✓ (${pingMs} ms)",
                                LogLevel.SUCCESS
                            )
                        } else {
                            sshViaProxy.disconnect()
                            VpnStateHolder.appendLog(
                                "PROXY",
                                "Puerto ${profile.port} no es proxy HTTP CONNECT (${proxyCheck.errorDetail ?: bhttpStatusLine ?: "sin túnel"})",
                                LogLevel.WARNING
                            )
                        }
                    } else {
                        VpnStateHolder.appendLog(
                            "PROXY",
                            "Puerto ${profile.port} no es proxy HTTP CONNECT (${proxyCheck.errorDetail ?: bhttpStatusLine ?: "sin túnel"})",
                            LogLevel.WARNING
                        )
                    }
                }

                // Step C: Try real SSH tunnel on port 22 with full direct-tcpip forwarding verification
                var sshFallbackErr: String? = null
                if (establishedSshClient == null && !verifiedProxyConnect) {
                    VpnStateHolder.appendLog(
                        "SSH",
                        "Probando túnel SSH verificado en $cleanHost:22 ($sshUser)…",
                        LogLevel.INFO
                    )
                    val ssh22 = SshTunnelClient(
                        protectSocket = ::protectSocketSafely,
                        host = cleanHost,
                        port = 22,
                        username = sshUser,
                        candidatePasswords = sshCandidatePasswords
                    )
                    val conn22 = ssh22.connect(5500)
                    if (conn22.isSuccess) {
                        val fwd22 = ssh22.verifyForwarding(4000)
                        if (fwd22.isSuccess) {
                            pingMs = conn22.getOrNull() ?: pingMs
                            establishedSshClient = ssh22
                            activeSshClient = ssh22
                            verifiedChannelLabel = "SSH :22 (respaldo)"
                        } else {
                            sshFallbackErr = fwd22.exceptionOrNull()?.localizedMessage ?: "direct-tcpip rechazado"
                            ssh22.disconnect()
                        }
                    } else {
                        sshFallbackErr = conn22.exceptionOrNull()?.localizedMessage ?: "Sin respuesta en :22"
                        ssh22.disconnect()
                    }
                }

                // Step D: Strict guarantee — NEVER declare CONNECTED without a real verified tunnel!
                if (establishedSshClient == null && !verifiedProxyConnect) {
                    val portReason = when {
                        !bhttpTcpOk -> "Puerto ${profile.port} inaccesible (${bhttpTcpErr ?: "sin conexión"})"
                        bhttpStatusLine != null -> "Puerto ${profile.port} respondió '$bhttpStatusLine' (no es túnel proxy/WebSocket)"
                        else -> "Puerto ${profile.port} no responde como túnel HTTP/WebSocket"
                    }
                    val sshReason = sshFallbackErr ?: "Sin canal SSH"
                    return failAttempt(
                        userFacingError = "$portReason • SSH :22 falló: $sshReason",
                        logDetail = "CONEXIÓN RECHAZADA (sin túnel real): $portReason | SSH :22: $sshReason"
                    )
                }
            }
        }

        // Update persisted latency now that end-to-end tunnel passed verification
        repository.updateLatency(profile.id, pingMs)

        val activeDnsPrimary = engineSettings.dnsProvider.primary
        val activeDnsSecondary = engineSettings.dnsProvider.secondary
        val activeMtu = engineSettings.mtu.coerceIn(1200, 1500)

        val tunFd = try {
            val builder = Builder()
                .setSession("Jeffers VPN - ${profile.vpsName}")
                .setMtu(activeMtu)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:10:8::2", 128)
                .addRoute("::", 0)
                .addDnsServer(activeDnsPrimary)
                .addDnsServer(activeDnsSecondary)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            try {
                builder.addDisallowedApplication(packageName)
                selfAppExcluded = true
            } catch (_: Exception) {
                selfAppExcluded = false
            }

            builder.establish()
        } catch (e: Exception) {
            establishedSshClient?.disconnect()
            activeSshClient = null
            return failAttempt(
                userFacingError = "Error creando interfaz TUN: ${e.localizedMessage}",
                logDetail = "Error creando interfaz TUN: ${e.localizedMessage}"
            )
        }

        if (tunFd == null) {
            establishedSshClient?.disconnect()
            activeSshClient = null
            return failAttempt(
                userFacingError = "Permiso VPN no disponible o cancelado por Android",
                logDetail = "El sistema canceló la interfaz TUN"
            )
        }

        tunInterface = tunFd
        sessionStartedAt = System.currentTimeMillis()
        VpnStateHolder.appendLog(
            "TUN",
            "TUN 10.8.0.2/32 + fd00:10:8::2/128 MTU $activeMtu activo • Canal=$verifiedChannelLabel",
            LogLevel.SUCCESS
        )

        updateNotification(
            "Conectado • ${profile.vpsName} ($verifiedChannelLabel)",
            isConnected = true
        )

        VpnStateHolder.markConnectedServer(
            profileId = profile.id,
            latencyMs = pingMs,
            channelLabel = verifiedChannelLabel
        )
        VpnStateHolder.updateTelemetry {
            it.copy(
                status = VpnConnectionStatus.CONNECTED,
                pingMs = pingMs,
                rxBytes = initialRxBytes,
                txBytes = initialTxBytes,
                statusMessage = "Conectado ($verifiedChannelLabel) • ${engineSettings.performanceMode.title}"
            )
        }

        val relay = TunPacketRelay(
            protectSocket = ::protectSocketSafely,
            protectDatagramSocket = ::protectDatagramSafely,
            tunInputProvider = { java.io.FileInputStream(tunFd.fileDescriptor) },
            tunOutputProvider = { java.io.FileOutputStream(tunFd.fileDescriptor) },
            dnsPrimaryIp = activeDnsPrimary,
            dnsSecondaryIp = activeDnsSecondary,
            vpsHost = cleanHost,
            vpsPort = profile.port,
            vpsUsername = sshUser,
            vpsPassword = profile.password,
            sshCandidatePasswords = sshCandidatePasswords,
            initialSshClient = establishedSshClient,
            scope = serviceScope,
            mtu = activeMtu,
            initialProxyVerified = verifiedProxyConnect,
            allowDirectFallback = false,
            proxySniHost = profile.sniHost.trim(),
            sshClientFactory = { targetPort ->
                if (protocol == TunnelProtocol.HTTP_CONNECT && targetPort == profile.port) {
                    SshTunnelClient(
                        protectSocket = ::protectSocketSafely,
                        host = cleanHost,
                        port = targetPort,
                        username = sshUser,
                        candidatePasswords = sshCandidatePasswords,
                        customSocketFactory = VpsReachabilityTester.createHttpConnectSshSocketFactory(
                            profile = profile,
                            protectSocket = ::protectSocketSafely,
                            timeoutMs = 5500,
                            onLog = VpnStateHolder::appendLog
                        )
                    )
                } else {
                    SshTunnelClient(
                        protectSocket = ::protectSocketSafely,
                        host = cleanHost,
                        port = targetPort,
                        username = sshUser,
                        candidatePasswords = sshCandidatePasswords
                    )
                }
            }
        )
        activeRelay = relay

        val relayJob = serviceScope.launch(Dispatchers.IO) {
            relay.runLoop()
        }

        var prevRx = initialRxBytes
        var prevTx = initialTxBytes
        var tickCount = 0

        try {
            while (serviceScope.isActive && relayJob.isActive && !userInitiatedStop) {
                delay(1000L)
                tickCount++
                val now = System.currentTimeMillis()
                val currentRx = initialRxBytes + relay.rxBytes.get()
                val currentTx = initialTxBytes + relay.txBytes.get()
                val rxSpeed = (currentRx - prevRx).coerceAtLeast(0L)
                val txSpeed = (currentTx - prevTx).coerceAtLeast(0L)
                prevRx = currentRx
                prevTx = currentTx
                val elapsedSec = ((now - sessionStartedAt) / 1000L).coerceAtLeast(0L)

                VpnStateHolder.updateTelemetry {
                    it.copy(
                        rxBytes = currentRx,
                        txBytes = currentTx,
                        rxBytesPerSec = rxSpeed,
                        txBytesPerSec = txSpeed,
                        durationSeconds = elapsedSec
                    )
                }
                if (tickCount == 1 || tickCount % 2 == 0) {
                    VpnStateHolder.recordMonitorSample(
                        rxBytesPerSec = rxSpeed,
                        txBytesPerSec = txSpeed,
                        pingMs = VpnStateHolder.telemetry.value.pingMs
                    )
                }
            }
        } finally {
            relay.closeAllSessions()
            relayJob.cancel()
            activeRelay = null
            activeSshClient?.disconnect()
            activeSshClient = null
            if (!userInitiatedStop && sessionStartedAt > 0L) {
                recordAndResetTun(VpnConnectionStatus.DISCONNECTED, "Túnel finalizado")
            }
        }
        return userInitiatedStop
    }

    private fun recordAndResetTun(status: VpnConnectionStatus, message: String?) {
        val snapshot = VpnStateHolder.telemetry.value
        val profile = currentProfile

        if (sessionStartedAt > 0L && profile != null) {
            val duration = ((System.currentTimeMillis() - sessionStartedAt) / 1000L).coerceAtLeast(0L)
            serviceScope.launch(Dispatchers.IO) {
                val repo = VpnRepository(AppDatabase.getInstance(applicationContext).vpnDao())
                repo.recordSession(
                    ConnectionSessionEntity(
                        profileName = profile.profileName,
                        vpsName = profile.vpsName,
                        host = profile.host,
                        port = profile.port,
                        carrier = profile.carrier,
                        protocol = profile.protocol,
                        durationSeconds = duration,
                        rxBytes = snapshot.rxBytes,
                        txBytes = snapshot.txBytes,
                        status = if (status == VpnConnectionStatus.ERROR) "ERROR" else "COMPLETADA",
                        startedAt = sessionStartedAt
                    )
                )
            }
        }
        sessionStartedAt = 0L

        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        activeSshClient?.disconnect()
        activeSshClient = null
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        selfAppExcluded = false

        if (status == VpnConnectionStatus.ERROR) {
            VpnStateHolder.markServerError(profile?.id, message ?: "Error de conexión")
        } else {
            VpnStateHolder.markServerDisconnected(profile?.id)
        }

        VpnStateHolder.updateTelemetry {
            it.copy(
                status = status,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                statusMessage = message
            )
        }
        VpnStateHolder.updateDiagnostics {
            it.copy(
                tunnelMode = "Inactivo",
                activeTcpSessions = 0,
                activeUdpSessions = 0
            )
        }
    }

    private fun stopTunnel(status: VpnConnectionStatus, message: String?) {
        activeRelay?.closeAllSessions()
        activeSshClient?.disconnect()
        activeSshClient = null
        recordAndResetTun(status, message)
        connectionJob?.cancel()
        connectionJob = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onRevoke() {
        userInitiatedStop = true
        VpnStateHolder.appendLog("TUN", "Permiso VPN revocado por el sistema", LogLevel.WARNING)
        stopTunnel(VpnConnectionStatus.DISCONNECTED, "Sesión VPN revocada por el sistema")
        super.onRevoke()
    }

    override fun onDestroy() {
        activeRelay?.closeAllSessions()
        activeSshClient?.disconnect()
        activeSshClient = null
        try {
            controlSocket?.close()
            tunInterface?.close()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String, isConnected: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText, isConnected))
    }

    private fun buildNotification(contentText: String, isConnected: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JeffersVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (isConnected) getString(R.string.vpn_notification_title) else "Jeffers VPN"
            )
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Desconectar", disconnectIntent)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val CHANNEL_ID = "jeffers_vpn_tunnel"
        private const val NOTIFICATION_ID = 4101
    }
}
