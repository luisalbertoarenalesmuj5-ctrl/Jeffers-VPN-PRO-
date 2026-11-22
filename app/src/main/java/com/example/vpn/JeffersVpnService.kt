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
    private var activeRelay: TunPacketRelay? = null
    private var sessionStartedAt: Long = 0L
    private var currentProfile: VpnProfileEntity? = null

    @Volatile
    private var userInitiatedStop: Boolean = false

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
            buildNotification("Iniciando túnel BHTTP…", isConnected = false),
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
        val carrier = CarrierType.fromName(profile.carrier)
        val protocol = TunnelProtocol.fromName(profile.protocol)

        VpnStateHolder.updateTelemetry {
            it.copy(
                status = VpnConnectionStatus.CONNECTING,
                activeProfileId = profile.id,
                activeProfileName = profile.vpsName,
                serverHost = profile.host,
                serverPort = profile.port,
                carrier = carrier,
                protocol = protocol,
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                durationSeconds = 0L,
                pingMs = null,
                statusMessage = "Conectando (${engineSettings.performanceMode.title}) a ${profile.vpsName}…"
            )
        }

        VpnStateHolder.appendLog(
            "VPS",
            "Iniciando ${profile.vpsName} • ${carrier.displayName}:${profile.port} • ${protocol.badge} • ${engineSettings.performanceMode.title}",
            LogLevel.INFO
        )

        val socket = Socket()
        controlSocket = socket
        val pingMs: Int
        try {
            protect(socket)
            socket.tcpNoDelay = true
            socket.keepAlive = true
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(profile.host, profile.port), 6000)
            pingMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            repository.updateLatency(profile.id, pingMs)
            VpnStateHolder.appendLog(
                "TCP",
                "Conexión TCP establecida con ${profile.host}:${profile.port} (${pingMs} ms)",
                LogLevel.SUCCESS
            )
        } catch (e: Exception) {
            val errorDetail = e.localizedMessage ?: e.javaClass.simpleName
            VpnStateHolder.appendLog(
                "VPS",
                "Sin respuesta de ${profile.host}:${profile.port} — $errorDetail",
                LogLevel.ERROR
            )
            if (!engineSettings.autoReconnect) {
                stopTunnel(
                    status = VpnConnectionStatus.ERROR,
                    message = "Sin respuesta de ${profile.host}:${profile.port} ($errorDetail)"
                )
            }
            return false
        }

        val isSshMode = protocol == TunnelProtocol.SSH_DIRECT || profile.port == 22
        var initialRxBytes = 0L
        var initialTxBytes = 0L
        try {
            socket.soTimeout = 4000
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            if (isSshMode) {
                val sshBanner = try {
                    reader.readLine()
                } catch (_: Exception) {
                    null
                }
                if (sshBanner != null) {
                    initialRxBytes += sshBanner.toByteArray(Charsets.UTF_8).size
                    VpnStateHolder.appendLog("SSH", "Servidor SSH: $sshBanner", LogLevel.SUCCESS)
                }
                val clientHello = "${BhttpPayloadBuilder.DEFAULT_SSH_CLIENT_ID}\r\n".toByteArray(Charsets.UTF_8)
                out.write(clientHello)
                out.flush()
                initialTxBytes += clientHello.size.toLong()
                VpnStateHolder.appendLog(
                    "SSH",
                    "Identificación SSH enviada • Usuario=${profile.username} • Puerto=${profile.port}",
                    LogLevel.INFO
                )
            } else {
                val compiledPayload = BhttpPayloadBuilder.compile(profile)
                val payloadBytes = compiledPayload.toByteArray(Charsets.UTF_8)
                initialTxBytes = payloadBytes.size.toLong()
                out.write(payloadBytes)
                out.flush()
                VpnStateHolder.appendLog(
                    "BHTTP",
                    "Handshake BHTTP enviado ($initialTxBytes B) • SNI=${profile.sniHost}",
                    LogLevel.INFO
                )

                val statusLine = try {
                    reader.readLine()
                } catch (_: Exception) {
                    null
                }
                if (statusLine != null) {
                    initialRxBytes += statusLine.toByteArray(Charsets.UTF_8).size
                    VpnStateHolder.appendLog("BHTTP", "Respuesta servidor: $statusLine", LogLevel.SUCCESS)
                } else {
                    VpnStateHolder.appendLog(
                        "BHTTP",
                        "Socket abierto sin banner inmediato; manteniendo túnel activo",
                        LogLevel.WARNING
                    )
                }
            }
        } catch (e: Exception) {
            VpnStateHolder.appendLog(
                if (isSshMode) "SSH" else "BHTTP",
                "Error en intercambio inicial: ${e.localizedMessage ?: e.javaClass.simpleName}",
                LogLevel.ERROR
            )
            if (!engineSettings.autoReconnect) {
                stopTunnel(
                    status = VpnConnectionStatus.ERROR,
                    message = "Fallo en handshake (${e.localizedMessage ?: "Error de red"})"
                )
            }
            try { socket.close() } catch (_: Exception) {}
            return false
        }

        // Close the initial handshake socket cleanly; TunPacketRelay manages live protected sockets
        // without using TCP Urgent OOB probes that trigger 15-second proxy resets.
        try { socket.close() } catch (_: Exception) {}
        controlSocket = null

        val activeDnsPrimary = engineSettings.dnsProvider.primary
        val activeDnsSecondary = engineSettings.dnsProvider.secondary
        val activeMtu = engineSettings.mtu.coerceIn(1200, 1500)

        val tunFd = try {
            val builder = Builder()
                .setSession("Jeffers VPN - ${profile.vpsName}")
                .setMtu(activeMtu)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(activeDnsPrimary)
                .addDnsServer(activeDnsSecondary)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            builder.establish()
        } catch (e: Exception) {
            VpnStateHolder.appendLog("TUN", "Error creando interfaz TUN: ${e.localizedMessage}", LogLevel.ERROR)
            stopTunnel(VpnConnectionStatus.ERROR, "Error creando interfaz VPN local")
            return false
        }

        if (tunFd == null) {
            VpnStateHolder.appendLog("TUN", "El sistema canceló la interfaz TUN", LogLevel.ERROR)
            stopTunnel(VpnConnectionStatus.ERROR, "Permiso VPN no disponible")
            return false
        }

        tunInterface = tunFd
        sessionStartedAt = System.currentTimeMillis()
        VpnStateHolder.appendLog(
            "TUN",
            "Túnel 10.8.0.2/32 MTU $activeMtu activo • DNS $activeDnsPrimary / $activeDnsSecondary",
            LogLevel.SUCCESS
        )

        updateNotification(
            "Conectado • ${profile.vpsName} (${carrier.displayName} :${profile.port})",
            isConnected = true
        )

        VpnStateHolder.updateTelemetry {
            it.copy(
                status = VpnConnectionStatus.CONNECTED,
                pingMs = pingMs,
                rxBytes = initialRxBytes,
                txBytes = initialTxBytes,
                statusMessage = "Túnel BHTTP activo • ${engineSettings.performanceMode.title}"
            )
        }

        val relay = TunPacketRelay(
            vpnService = this@JeffersVpnService,
            tunFd = tunFd,
            dnsPrimaryIp = activeDnsPrimary,
            dnsSecondaryIp = activeDnsSecondary,
            vpsHost = profile.host,
            vpsPort = profile.port,
            vpsPassword = profile.password,
            scope = serviceScope,
            mtu = activeMtu
        )
        activeRelay = relay

        val relayJob = serviceScope.launch(Dispatchers.IO) {
            relay.runLoop()
        }

        var prevRx = initialRxBytes
        var prevTx = initialTxBytes

        try {
            while (serviceScope.isActive && relayJob.isActive && !userInitiatedStop) {
                delay(1000L)
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
            }
        } finally {
            relay.closeAllSessions()
            relayJob.cancel()
            activeRelay = null
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
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null

        VpnStateHolder.updateTelemetry {
            it.copy(
                status = status,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                statusMessage = message
            )
        }
    }

    private fun stopTunnel(status: VpnConnectionStatus, message: String?) {
        activeRelay?.closeAllSessions()
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
