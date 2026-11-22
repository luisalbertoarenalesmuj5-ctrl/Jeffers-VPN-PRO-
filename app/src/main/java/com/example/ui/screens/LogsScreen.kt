package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnConnectionStatus
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ElectricCyan

@Composable
fun LogsScreen(
    uiState: VpnDashboardUiState,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    onRunDiagnostics: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

    val dynamicSubtitle = remember(uiState.telemetry.status, uiState.diagnostics.tunnelMode, uiState.isRunningDiagnostic) {
        when {
            uiState.isRunningDiagnostic -> "Analizando red…"
            uiState.telemetry.status == VpnConnectionStatus.CONNECTED -> uiState.diagnostics.tunnelMode
            uiState.telemetry.status == VpnConnectionStatus.CONNECTING -> "Conectando…"
            uiState.telemetry.status == VpnConnectionStatus.ERROR -> "Error detectado"
            else -> "Listo"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = uiState.settings.backgroundEffect,
            primaryColor = MaterialTheme.colorScheme.primary,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.alpha(0.45f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                if (onBack != null) {
                    SubScreenTopBar(
                        title = "DIAGNÓSTICO",
                        subtitle = dynamicSubtitle,
                        onBack = onBack,
                        trailing = {
                            Row {
                                if (selectedTab == 0) {
                                    IconButton(
                                        onClick = {
                                            val text = uiState.logs.joinToString("\n") {
                                                "[${it.timestamp}] [${it.tag}] ${it.message}"
                                            }
                                            clipboard.setText(AnnotatedString(text))
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("copy_logs_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copiar logs",
                                            tint = ElectricCyan
                                        )
                                    }
                                    IconButton(
                                        onClick = onClearLogs,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("clear_logs_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CleaningServices,
                                            contentDescription = "Limpiar consola",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (selectedTab == 2 && uiState.sessions.isNotEmpty()) {
                                    IconButton(
                                        onClick = onClearHistory,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("clear_history_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CleaningServices,
                                            contentDescription = "Limpiar historial",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                DiagnosticTabStrip(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> LiveConsoleTab(
                        logs = uiState.logs,
                        diagnostics = uiState.diagnostics,
                        modifier = Modifier.weight(1f)
                    )
                    1 -> NetworkDiagnosticsTab(
                        report = uiState.diagnosticReport,
                        isRunning = uiState.isRunningDiagnostic,
                        serverHost = uiState.selectedProfile?.host ?: uiState.telemetry.serverHost,
                        serverPort = uiState.selectedProfile?.port ?: uiState.telemetry.serverPort,
                        onRunDiagnostics = onRunDiagnostics,
                        modifier = Modifier.weight(1f)
                    )
                    else -> MonitorTenMinTab(
                        samples = uiState.monitorSamples,
                        diagnostics = uiState.diagnostics,
                        sessions = uiState.sessions,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
