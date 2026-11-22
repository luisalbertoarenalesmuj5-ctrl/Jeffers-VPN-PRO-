package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectionSessionEntity
import com.example.data.model.DiagnosticCheckStatus
import com.example.data.model.LogLevel
import com.example.data.model.NetworkDiagnosticReport
import com.example.data.model.TunnelDiagnosticsState
import com.example.data.model.TunnelMonitorSample
import com.example.data.model.VpnLogEntry
import com.example.ui.Formatters
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianGlassElevated
import com.example.ui.theme.ObsidianTerminal
import com.example.ui.theme.PulseAmber

enum class LogFilterCategory(val label: String) {
    ALL("Todo"),
    ERRORS("Errores"),
    SSH_BHTTP("SSH / BHTTP"),
    DNS_TCP("DNS / TCP")
}

@Composable
fun DiagnosticTabStrip(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "LOG VPN", "diag_tab_log"),
        Triple(1, "DIAGNÓSTICO RED", "diag_tab_network"),
        Triple(2, "MONITOR 10 MIN", "diag_tab_monitor")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (index, title, tag) ->
            val selected = selectedTab == index
            Surface(
                onClick = { onSelectTab(index) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) ElectricCyan.copy(alpha = 0.14f) else ObsidianGlass,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) ElectricCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(tag)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun LiveConsoleTab(
    logs: List<VpnLogEntry>,
    diagnostics: TunnelDiagnosticsState,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(LogFilterCategory.ALL) }
    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            LogFilterCategory.ALL -> logs
            LogFilterCategory.ERRORS -> logs.filter {
                it.level == LogLevel.ERROR || it.level == LogLevel.WARNING
            }
            LogFilterCategory.SSH_BHTTP -> logs.filter {
                it.tag in setOf("SSH", "SSH-CORE", "BHTTP", "PROXY", "PROBE", "VPS", "DIAG", "DIAG-BHTTP", "DIAG-PROXY", "DIAG-SSH")
            }
            LogFilterCategory.DNS_TCP -> logs.filter {
                it.tag in setOf("DNS", "TCP", "UDP", "TUN", "RELAY", "DIAG-DNS", "DIAG-HTTP", "INTERFAZ")
            }
        }
    }
    val errorCount = remember(logs) {
        logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.WARNING }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Live relay diagnostics strip
        Surface(
            color = ObsidianGlass,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = diagnostics.tunnelMode,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (diagnostics.tunnelMode == "Inactivo") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            ElectricCyan
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TCP ${diagnostics.activeTcpSessions} • UDP ${diagnostics.activeUdpSessions}",
                        style = MonoTypography.terminal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DNS: ${diagnostics.dnsOkCount} ✓ / ${diagnostics.dnsFailCount} ✗",
                        style = MonoTypography.terminal,
                        color = if (diagnostics.dnsFailCount > 0) MaterialTheme.colorScheme.error else CyberEmerald
                    )
                    Text(
                        text = "TCP: ${diagnostics.tcpOkCount} ✓ / ${diagnostics.tcpFailCount} ✗",
                        style = MonoTypography.terminal,
                        color = if (diagnostics.tcpFailCount > 0) MaterialTheme.colorScheme.error else CyberEmerald
                    )
                    Text(
                        text = "SSH Err: ${diagnostics.sshFailCount}",
                        style = MonoTypography.terminal,
                        color = if (diagnostics.sshFailCount > 0) PulseAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val latestFault = diagnostics.lastSshDetail
                    ?: diagnostics.lastDnsDetail?.takeIf { diagnostics.dnsFailCount > 0 }
                    ?: diagnostics.lastTcpDetail?.takeIf { diagnostics.tcpFailCount > 0 }
                if (!latestFault.isNullOrBlank()) {
                    Text(
                        text = "Último detalle: $latestFault",
                        style = MonoTypography.terminal,
                        color = PulseAmber,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Log category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogFilterCategory.entries.forEach { category ->
                val countLabel = when (category) {
                    LogFilterCategory.ALL -> "${category.label} (${logs.size})"
                    LogFilterCategory.ERRORS -> "${category.label} ($errorCount)"
                    else -> category.label
                }
                val selected = selectedFilter == category
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = category },
                    label = {
                        Text(
                            text = countLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (category == LogFilterCategory.ERRORS && errorCount > 0) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                        } else {
                            ElectricCyan.copy(alpha = 0.18f)
                        },
                        selectedLabelColor = if (category == LogFilterCategory.ERRORS && errorCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            ElectricCyan
                        }
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("log_filter_${category.name.lowercase()}")
                )
            }
        }

        // Terminal log stream
        Surface(
            color = ObsidianTerminal.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.24f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin eventos registrados en el filtro '${selectedFilter.label}'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val levelColor = when (entry.level) {
                            LogLevel.INFO -> ElectricCyan
                            LogLevel.SUCCESS -> CyberEmerald
                            LogLevel.WARNING -> PulseAmber
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                        }
                        val glyph = when (entry.level) {
                            LogLevel.INFO -> "›"
                            LogLevel.SUCCESS -> "✓"
                            LogLevel.WARNING -> "!"
                            LogLevel.ERROR -> "✗"
                        }
                        Surface(
                            color = ObsidianCard.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (entry.level == LogLevel.ERROR) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = glyph,
                                    style = MonoTypography.terminal.copy(fontWeight = FontWeight.Bold),
                                    color = levelColor
                                )
                                Text(
                                    text = "[${entry.timestamp}]",
                                    style = MonoTypography.terminal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${entry.tag}] ${entry.message}",
                                        style = MonoTypography.terminal,
                                        color = levelColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkDiagnosticsTab(
    report: NetworkDiagnosticReport?,
    isRunning: Boolean,
    serverHost: String,
    serverPort: Int,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.28f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Diagnóstico de red",
                        tint = ElectricCyan,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "Diagnóstico de red",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Detecta errores reales en DNS, handshake BHTTP, HTTP CONNECT, túnel SSH (:22) y salida HTTP 204 hacia $serverHost:$serverPort.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onRunDiagnostics,
                        enabled = !isRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberEmerald.copy(alpha = 0.16f),
                            contentColor = CyberEmerald,
                            disabledContainerColor = ObsidianGlassElevated,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRunning) MaterialTheme.colorScheme.outline else CyberEmerald
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("run_network_diagnostic_button")
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = CyberEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("ANALIZANDO RED Y TÚNEL…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EJECUTAR DIAGNÓSTICO")
                        }
                    }
                }
            }
        }

        if (report != null) {
            item {
                val summaryColor = when (report.overallStatus) {
                    DiagnosticCheckStatus.PASS -> CyberEmerald
                    DiagnosticCheckStatus.WARN -> PulseAmber
                    DiagnosticCheckStatus.FAIL -> MaterialTheme.colorScheme.error
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianGlassElevated),
                    border = BorderStroke(1.2.dp, summaryColor.copy(alpha = 0.7f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RESUMEN DE FALLA / ESTADO",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = summaryColor
                            )
                            Text(
                                text = Formatters.formatDateTime(report.checkedAtMillis),
                                style = MonoTypography.terminal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = report.summary,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = report.recommendedAction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val exportText = buildString {
                                    appendLine("=== Jeffers VPN PRO - Diagnóstico ===")
                                    appendLine("Resumen: ${report.summary}")
                                    appendLine("Acción: ${report.recommendedAction}")
                                    report.checks.forEach { c ->
                                        val rtt = c.latencyMs?.let { " (${it}ms)" }.orEmpty()
                                        appendLine("[${c.status}] ${c.title}$rtt: ${c.detail}")
                                    }
                                }
                                clipboard.setText(AnnotatedString(exportText))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("copy_diagnostic_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copiar reporte completo")
                        }
                    }
                }
            }

            items(report.checks, key = { it.id }) { check ->
                val checkColor = when (check.status) {
                    DiagnosticCheckStatus.PASS -> CyberEmerald
                    DiagnosticCheckStatus.WARN -> PulseAmber
                    DiagnosticCheckStatus.FAIL -> MaterialTheme.colorScheme.error
                }
                val checkIcon = when (check.status) {
                    DiagnosticCheckStatus.PASS -> Icons.Rounded.CheckCircle
                    DiagnosticCheckStatus.WARN -> Icons.Rounded.WarningAmber
                    DiagnosticCheckStatus.FAIL -> Icons.Rounded.ErrorOutline
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
                    border = BorderStroke(1.dp, checkColor.copy(alpha = 0.42f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = checkIcon,
                            contentDescription = check.status.name,
                            tint = checkColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = check.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                check.latencyMs?.let { ms ->
                                    Text(
                                        text = "${ms} ms",
                                        style = MonoTypography.terminal,
                                        color = checkColor
                                    )
                                }
                            }
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorTenMinTab(
    samples: List<TunnelMonitorSample>,
    diagnostics: TunnelDiagnosticsState,
    sessions: List<ConnectionSessionEntity>,
    modifier: Modifier = Modifier
) {
    val peakRx = remember(samples) { samples.maxOfOrNull { it.rxBytesPerSec } ?: 0L }
    val peakTx = remember(samples) { samples.maxOfOrNull { it.txBytesPerSec } ?: 0L }
    val latest = samples.lastOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Text(
                                text = "Monitor en vivo (últimos 10 min)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "${samples.size} muestras",
                            style = MonoTypography.terminal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (samples.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Conecta el VPN para graficar velocidad RX/TX, DNS y errores en tiempo real.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val rxColor = CyberEmerald
                        val txColor = ElectricCyan
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val maxVal = maxOf(peakRx, peakTx, 1024L).toFloat()

                            for (step in 1..3) {
                                val y = h * (step / 4f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val rxPath = Path()
                            val txPath = Path()
                            val lastIdx = (samples.size - 1).coerceAtLeast(1)
                            samples.forEachIndexed { idx, s ->
                                val x = (idx.toFloat() / lastIdx.toFloat()) * w
                                val rxY = h - ((s.rxBytesPerSec.toFloat() / maxVal) * (h * 0.88f)) - 4f
                                val txY = h - ((s.txBytesPerSec.toFloat() / maxVal) * (h * 0.88f)) - 4f
                                if (idx == 0) {
                                    rxPath.moveTo(x, rxY)
                                    txPath.moveTo(x, txY)
                                } else {
                                    rxPath.lineTo(x, rxY)
                                    txPath.lineTo(x, txY)
                                }
                            }
                            drawPath(
                                path = txPath,
                                color = txColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            drawPath(
                                path = rxPath,
                                brush = Brush.horizontalGradient(listOf(rxColor.copy(alpha = 0.85f), rxColor)),
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "↓ Descarga pico: ${Formatters.formatSpeed(peakRx)}",
                            style = MonoTypography.terminal,
                            color = CyberEmerald
                        )
                        Text(
                            text = "↑ Subida pico: ${Formatters.formatSpeed(peakTx)}",
                            style = MonoTypography.terminal,
                            color = ElectricCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IPv6 filtrados: ${diagnostics.ipv6RejectedCount} • RST: ${diagnostics.orphanRstCount}",
                            style = MonoTypography.terminal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val errs = latest?.errorCount ?: (diagnostics.dnsFailCount + diagnostics.tcpFailCount + diagnostics.sshFailCount)
                        Text(
                            text = "Errores sesión: $errs",
                            style = MonoTypography.terminal,
                            color = if (errs > 0) MaterialTheme.colorScheme.error else CyberEmerald
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NetworkCheck,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Historial de Sesiones (${sessions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin sesiones registradas todavía",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cuando te conectes a VPSpremiumPRO aparecerá aquí el consumo y duración.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.profileName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = Formatters.formatDateTime(session.startedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${session.host}:${session.port} • ${session.protocol}",
                                style = MonoTypography.terminal,
                                color = ElectricCyan
                            )
                            Text(
                                text = "${Formatters.formatDuration(session.durationSeconds)} • ↓${Formatters.formatBytes(session.rxBytes)} ↑${Formatters.formatBytes(session.txBytes)}",
                                style = MonoTypography.terminal,
                                color = CyberEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}
