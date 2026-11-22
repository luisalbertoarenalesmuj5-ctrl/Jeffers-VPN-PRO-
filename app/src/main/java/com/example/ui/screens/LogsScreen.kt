package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.local.ConnectionSessionEntity
import com.example.data.model.LogLevel
import com.example.data.model.VpnLogEntry
import com.example.ui.Formatters
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianTerminal
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PulseAmber

@Composable
fun LogsScreen(
    uiState: VpnDashboardUiState,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

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
                    title = "Diagnóstico",
                    subtitle = "LOG.ENGINE // BHTTP",
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
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copiar consola",
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
                            } else if (uiState.sessions.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearHistory,
                                    modifier = Modifier.size(48.dp)
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

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text("Consola BHTTP")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text("Historial (${uiState.sessions.size})")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                LiveConsoleView(logs = uiState.logs, modifier = Modifier.weight(1f))
            } else {
                SessionHistoryList(sessions = uiState.sessions, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
        }
    }
}

@Composable
private fun LiveConsoleView(
    logs: List<VpnLogEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        color = ObsidianTerminal.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs, key = { it.id }) { entry ->
                val color = when (entry.level) {
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    LogLevel.SUCCESS -> CyberEmerald
                    LogLevel.WARNING -> PulseAmber
                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.timestamp,
                        style = MonoTypography.terminal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "[${entry.tag}]",
                        style = MonoTypography.terminal,
                        color = ElectricCyan
                    )
                    Text(
                        text = entry.message,
                        style = MonoTypography.terminal,
                        color = color,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryList(
    sessions: List<ConnectionSessionEntity>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGlass),
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.25f)),
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sin sesiones registradas todavía",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cuando te conectes a VPSpremiumPRO aparecerá aquí el consumo y duración.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = modifier.fillMaxSize()
        ) {
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
