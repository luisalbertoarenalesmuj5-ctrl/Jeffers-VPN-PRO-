package com.example.vpn

import com.example.BuildConfig
import com.example.data.model.LogLevel
import com.example.data.model.ServerStatus
import com.example.data.model.ServerVerificationState
import com.example.data.model.TunnelDiagnosticsState
import com.example.data.model.TunnelMonitorSample
import com.example.data.model.VpnConnectionStatus
import com.example.data.model.VpnLogEntry
import com.example.data.model.VpnTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object VpnStateHolder {

    private val logIdCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _telemetry = MutableStateFlow(VpnTelemetry())
    val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    private val _diagnostics = MutableStateFlow(TunnelDiagnosticsState())
    val diagnostics: StateFlow<TunnelDiagnosticsState> = _diagnostics.asStateFlow()

    private val _monitorSamples = MutableStateFlow<List<TunnelMonitorSample>>(emptyList())
    val monitorSamples: StateFlow<List<TunnelMonitorSample>> = _monitorSamples.asStateFlow()

    private val _serverStatuses = MutableStateFlow<Map<Long, ServerVerificationState>>(emptyMap())
    val serverStatuses: StateFlow<Map<Long, ServerVerificationState>> = _serverStatuses.asStateFlow()

    private val _logs = MutableStateFlow<List<VpnLogEntry>>(
        listOf(
            VpnLogEntry(
                id = logIdCounter.getAndIncrement(),
                timestamp = timeFormat.format(Date()),
                tag = "CORE",
                message = "Jeffers VPN v${BuildConfig.VERSION_NAME} (HTTP CONNECT + SSH) listo • VPSpremiumPRO (192.6.154.212)",
                level = LogLevel.INFO
            )
        )
    )
    val logs: StateFlow<List<VpnLogEntry>> = _logs.asStateFlow()

    fun updateTelemetry(transform: (VpnTelemetry) -> VpnTelemetry) {
        _telemetry.update(transform)
    }

    fun updateDiagnostics(transform: (TunnelDiagnosticsState) -> TunnelDiagnosticsState) {
        _diagnostics.update(transform)
    }

    fun updateServerStatus(
        profileId: Long,
        transform: (ServerVerificationState) -> ServerVerificationState
    ) {
        if (profileId <= 0L) return
        _serverStatuses.update { current ->
            val prev = current[profileId] ?: ServerVerificationState(profileId = profileId)
            current + (profileId to transform(prev))
        }
    }

    fun markServerChecking(profileId: Long) {
        updateServerStatus(profileId) { prev ->
            prev.copy(
                isChecking = true,
                detail = "Verificando IP, puerto, credenciales y protocolo…"
            )
        }
    }

    fun markServerVerificationResult(
        profileId: Long,
        isConnectedTunnel: Boolean,
        isReachable: Boolean,
        latencyMs: Int?,
        channelLabel: String?,
        detail: String
    ) {
        updateServerStatus(profileId) { prev ->
            prev.copy(
                status = when {
                    isConnectedTunnel -> ServerStatus.CONNECTED
                    isReachable -> ServerStatus.DISCONNECTED
                    else -> ServerStatus.ERROR
                },
                isChecking = false,
                isReachable = isReachable,
                latencyMs = latencyMs,
                activeChannelLabel = channelLabel,
                detail = detail,
                checkedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun markConnectedServer(profileId: Long, latencyMs: Int?, channelLabel: String) {
        _serverStatuses.update { current ->
            val updated = current.mapValues { (id, state) ->
                if (id != profileId && state.status == ServerStatus.CONNECTED) {
                    state.copy(
                        status = if (state.isReachable == false) ServerStatus.ERROR else ServerStatus.DISCONNECTED
                    )
                } else {
                    state
                }
            }.toMutableMap()
            val prev = updated[profileId] ?: ServerVerificationState(profileId = profileId)
            updated[profileId] = prev.copy(
                status = ServerStatus.CONNECTED,
                isChecking = false,
                isReachable = true,
                latencyMs = latencyMs ?: prev.latencyMs,
                activeChannelLabel = channelLabel,
                detail = "Túnel verificado • $channelLabel${(latencyMs ?: prev.latencyMs)?.let { " (${it} ms)" }.orEmpty()}",
                checkedAtMillis = System.currentTimeMillis()
            )
            updated
        }
    }

    fun markServerDisconnected(profileId: Long?) {
        _serverStatuses.update { current ->
            current.mapValues { (id, state) ->
                if ((profileId == null || id == profileId) && state.status == ServerStatus.CONNECTED) {
                    state.copy(
                        status = if (state.isReachable == false) ServerStatus.ERROR else ServerStatus.DISCONNECTED,
                        isChecking = false,
                        detail = if (state.isReachable == true) {
                            val ch = state.activeChannelLabel?.let { " • $it" }.orEmpty()
                            val rtt = state.latencyMs?.let { " • ${it} ms" }.orEmpty()
                            "VPS activo$ch$rtt"
                        } else {
                            "Desconectado"
                        }
                    )
                } else {
                    state
                }
            }
        }
    }

    fun markServerError(profileId: Long?, detail: String) {
        if (profileId == null || profileId <= 0L) return
        updateServerStatus(profileId) { prev ->
            prev.copy(
                status = ServerStatus.ERROR,
                isChecking = false,
                isReachable = false,
                latencyMs = null,
                detail = detail,
                checkedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun resetDiagnostics() {
        _diagnostics.value = TunnelDiagnosticsState()
        _monitorSamples.value = emptyList()
    }

    fun recordMonitorSample(
        rxBytesPerSec: Long,
        txBytesPerSec: Long,
        pingMs: Int?
    ) {
        val now = System.currentTimeMillis()
        val diag = _diagnostics.value
        val sample = TunnelMonitorSample(
            timestampMillis = now,
            rxBytesPerSec = rxBytesPerSec,
            txBytesPerSec = txBytesPerSec,
            pingMs = pingMs,
            dnsOkCount = diag.dnsOkCount,
            tcpOkCount = diag.tcpOkCount,
            errorCount = diag.dnsFailCount + diag.tcpFailCount + diag.sshFailCount
        )
        val cutoff = now - 600_000L
        _monitorSamples.update { current ->
            (current + sample).filter { it.timestampMillis >= cutoff }.takeLast(300)
        }
    }

    fun setStatus(status: VpnConnectionStatus, message: String? = null) {
        _telemetry.update {
            it.copy(
                status = status,
                statusMessage = message
            )
        }
    }

    fun appendLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = VpnLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        _logs.update { current ->
            (current + entry).takeLast(400)
        }
    }

    fun clearLogs() {
        _logs.value = listOf(
            VpnLogEntry(
                id = logIdCounter.getAndIncrement(),
                timestamp = timeFormat.format(Date()),
                tag = "CORE",
                message = "Consola limpiada. Esperando actividad del túnel.",
                level = LogLevel.INFO
            )
        )
    }
}

