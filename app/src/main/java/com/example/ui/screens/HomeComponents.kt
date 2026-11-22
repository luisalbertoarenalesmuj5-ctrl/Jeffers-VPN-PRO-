package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PlanTier
import com.example.data.model.ServerAnimationStyle
import com.example.data.model.VpnConnectionStatus
import com.example.ui.Formatters
import com.example.ui.components.ServerCardAnimationStrip
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CranberryRed
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianGlassElevated
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun HomeTopBar(
    status: VpnConnectionStatus,
    statusColor: Color,
    primaryColor: Color,
    accentColor: Color,
    activeTier: PlanTier,
    isChristmasTheme: Boolean,
    onOpenDrawer: () -> Unit
) {
    val beaconTransition = rememberInfiniteTransition(label = "statusBeacon")
    val beaconAlpha by beaconTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )
    val tierColor = Color(activeTier.colorHex)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isChristmasTheme) Color(0xA605120D) else Color.Transparent,
        border = if (isChristmasTheme) {
            BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        ChampagneGold.copy(alpha = 0.52f),
                        accentColor.copy(alpha = 0.30f),
                        ChampagneGold.copy(alpha = 0.42f)
                    )
                )
            )
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 980.dp)
    ) {
        Column {
            if (isChristmasTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    ChampagneGoldBright.copy(alpha = 0.90f),
                                    accentColor.copy(alpha = 0.70f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isChristmasTheme) 10.dp else 0.dp,
                        vertical = if (isChristmasTheme) 7.dp else 0.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isChristmasTheme) ObsidianGlassElevated else MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        border = BorderStroke(
                            1.dp,
                            if (isChristmasTheme) ChampagneGold.copy(alpha = 0.45f) else OutlineSubtle
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenDrawer)
                            .testTag("drawer_menu_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Abrir menú lateral",
                                tint = if (isChristmasTheme) ChampagneGoldBright else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Image(
                        painter = painterResource(id = R.drawable.jeffers_vpn_icon),
                        contentDescription = "Jeffers VPN PRO",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.5.dp,
                                if (isChristmasTheme) Brush.linearGradient(listOf(ChampagneGoldBright, accentColor))
                                else Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.65f), primaryColor.copy(alpha = 0.65f))),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("home_brand_icon")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "JEFFERS VPN PRO",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                lineHeight = 15.sp
                            ),
                            color = if (isChristmasTheme) FrostIce else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isChristmasTheme) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CranberryRed.copy(alpha = 0.28f),
                                    border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.72f))
                                ) {
                                    Text(
                                        text = "✦ NAVIDAD PRO",
                                        style = MonoTypography.terminal.copy(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = ChampagneGoldBright,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "ULTRA EDITION",
                                    style = MonoTypography.terminal.copy(fontSize = 9.sp),
                                    color = primaryColor
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tierColor.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, tierColor.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = activeTier.badgeLabel,
                                    style = MonoTypography.terminal.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isChristmasTheme) ObsidianGlassElevated else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        if (status == VpnConnectionStatus.CONNECTED || status == VpnConnectionStatus.CONNECTING) {
                            statusColor.copy(alpha = 0.78f)
                        } else if (isChristmasTheme) {
                            ChampagneGold.copy(alpha = 0.36f)
                        } else {
                            OutlineSubtle
                        }
                    ),
                    modifier = Modifier.testTag("status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(if (status == VpnConnectionStatus.DISCONNECTED) 0.55f else beaconAlpha)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Icon(
                            imageVector = Icons.Rounded.SignalCellularAlt,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when (status) {
                                VpnConnectionStatus.DISCONNECTED -> "Listo"
                                VpnConnectionStatus.CONNECTING -> "Tunel…"
                                VpnConnectionStatus.CONNECTED -> "Online"
                                VpnConnectionStatus.ERROR -> "Error"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (status == VpnConnectionStatus.CONNECTED) statusColor else FrostIce.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusAndIpBlock(
    status: VpnConnectionStatus,
    ipAddress: String,
    showIp: Boolean,
    statusMessage: String?,
    carrierBadge: String,
    protocolBadge: String,
    accentColor: Color,
    errorColor: Color,
    isChristmasTheme: Boolean,
    onToggleIp: () -> Unit
) {
    val stateChipText = when (status) {
        VpnConnectionStatus.DISCONNECTED -> "DESCONECTADO"
        VpnConnectionStatus.CONNECTING -> "CONECTANDO"
        VpnConnectionStatus.CONNECTED -> "TÚNEL SEGURO"
        VpnConnectionStatus.ERROR -> "ERROR"
    }
    val stateColor = when (status) {
        VpnConnectionStatus.CONNECTED -> accentColor
        VpnConnectionStatus.ERROR -> errorColor
        VpnConnectionStatus.CONNECTING -> PulseAmber
        VpnConnectionStatus.DISCONNECTED -> if (isChristmasTheme) ChampagneGold else TextMuted
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isChristmasTheme) Color(0xAD06130E) else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            if (isChristmasTheme) {
                Brush.horizontalGradient(
                    listOf(
                        ChampagneGold.copy(alpha = 0.36f),
                        stateColor.copy(alpha = 0.52f),
                        ChampagneGold.copy(alpha = 0.36f)
                    )
                )
            } else {
                Brush.linearGradient(listOf(OutlineSubtle, OutlineSubtle))
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (isChristmasTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, ChampagneGoldBright.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = stateColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, stateColor.copy(alpha = 0.50f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = stateColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stateChipText,
                                    style = MonoTypography.terminal.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = stateColor
                                )
                            }
                        }

                        Text(
                            text = if (showIp) ipAddress else "•••.•••.•••.•••",
                            style = MonoTypography.address.copy(
                                letterSpacing = 1.0.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = FrostIce,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x6606100C),
                            border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "$carrierBadge • $protocolBadge",
                                style = MonoTypography.terminal.copy(fontSize = 9.sp),
                                color = FrostIce.copy(alpha = 0.80f),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleIp,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_ip_visibility")
                        ) {
                            Icon(
                                imageVector = if (showIp) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (showIp) "Ocultar IP" else "Mostrar IP",
                                tint = if (isChristmasTheme) ChampagneGold.copy(alpha = 0.85f) else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == VpnConnectionStatus.ERROR) errorColor
                        else FrostIce.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpeedTelemetryBentoSection(
    surfaceColor: Color,
    primaryColor: Color,
    accentColor: Color,
    isChristmasTheme: Boolean,
    isConnected: Boolean,
    rxSpeed: Long,
    txSpeed: Long,
    rxTotal: Long,
    txTotal: Long,
    durationSeconds: Long,
    pingMs: Int?,
    carrierBadge: String,
    showValues: Boolean,
    expanded: Boolean,
    isTesting: Boolean,
    onToggleShow: () -> Unit,
    onToggleExpand: () -> Unit,
    onTestPing: () -> Unit
) {
    val waveTransition = rememberInfiniteTransition(label = "bentoSpeedWave")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnected) 1800 else 4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bentoWavePhase"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isChristmasTheme) Color(0xAD06130E) else surfaceColor,
        border = BorderStroke(
            1.dp,
            if (isChristmasTheme) {
                Brush.linearGradient(
                    listOf(
                        ChampagneGold.copy(alpha = 0.45f),
                        accentColor.copy(alpha = 0.35f),
                        ChampagneGold.copy(alpha = 0.30f)
                    )
                )
            } else {
                Brush.linearGradient(listOf(OutlineSubtle, OutlineSubtle))
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (isChristmasTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.85f),
                                    ChampagneGoldBright.copy(alpha = 0.90f),
                                    accentColor.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Unified Dual-Pane Telemetry Console with hairline center divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                TelemetryBentoPane(
                    title = "DESCARGA",
                    speedText = if (showValues) Formatters.formatSpeed(rxSpeed) else "••• KB/s",
                    totalText = "↓ ${Formatters.formatBytes(rxTotal)}",
                    accentTint = primaryColor,
                    icon = Icons.Rounded.ArrowDownward,
                    isConnected = isConnected,
                    wavePhase = wavePhase,
                    phaseShift = 0f,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    if (isChristmasTheme) ChampagneGold.copy(alpha = 0.38f) else OutlineSubtle,
                                    Color.Transparent
                                )
                            )
                        )
                )

                TelemetryBentoPane(
                    title = "SUBIDA",
                    speedText = if (showValues) Formatters.formatSpeed(txSpeed) else "••• KB/s",
                    totalText = "↑ ${Formatters.formatBytes(txTotal)}",
                    accentTint = accentColor,
                    icon = Icons.Rounded.ArrowUpward,
                    isConnected = isConnected,
                    wavePhase = wavePhase,
                    phaseShift = 1.6f,
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (isChristmasTheme) ChampagneGold.copy(alpha = 0.28f) else OutlineSubtle,
                                Color.Transparent
                            )
                        )
                    )
            )

            // Integrated Diagnostics & Ping Footer inside same unified glass card
            Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = if (isChristmasTheme) ChampagneGold else primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = buildString {
                                append(pingMs?.let { "${it} ms • Latencia óptima" } ?: "Diagnóstico TCP • $carrierBadge")
                                if (isConnected) {
                                    append(" • ${Formatters.formatDuration(durationSeconds)}")
                                }
                            },
                            style = MonoTypography.terminal.copy(fontSize = 10.5.sp),
                            color = if (pingMs != null) accentColor else FrostIce.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleShow,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (showValues) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = "Alternar visibilidad de velocidad",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Expandir estadísticas",
                                tint = if (isChristmasTheme) ChampagneGoldBright else primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 10.dp, top = 4.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sesión: ${Formatters.formatDuration(durationSeconds)}",
                                style = MonoTypography.terminal,
                                color = FrostIce.copy(alpha = 0.80f)
                            )
                            Text(
                                text = "Ping: ${pingMs?.let { "${it}ms" } ?: "--ms"}",
                                style = MonoTypography.terminal,
                                color = accentColor
                            )
                        }
                        OutlinedButton(
                            onClick = onTestPing,
                            enabled = !isTesting,
                            border = BorderStroke(
                                1.dp,
                                if (isChristmasTheme) ChampagneGold.copy(alpha = 0.55f) else OutlineSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_vps_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTesting) "Probando TCP/BHTTP…" else "Probar Latencia VPS")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBentoPane(
    title: String,
    speedText: String,
    totalText: String,
    accentTint: Color,
    icon: ImageVector,
    isConnected: Boolean,
    wavePhase: Float,
    phaseShift: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .align(Alignment.BottomCenter)
        ) {
            val baseLine = size.height * 0.78f
            val amp = if (isConnected) size.height * 0.16f else size.height * 0.06f
            val strokePath = Path()
            val fillPath = Path()
            val steps = 28
            for (i in 0..steps) {
                val frac = i / steps.toFloat()
                val x = size.width * frac
                val y = baseLine + sin(frac * 2f * PI.toFloat() * 1.5f + wavePhase + phaseShift) * amp
                if (i == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(0f, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentTint.copy(alpha = if (isConnected) 0.18f else 0.08f), Color.Transparent),
                    startY = size.height * 0.45f,
                    endY = size.height
                )
            )
            drawPath(
                path = strokePath,
                color = accentTint.copy(alpha = if (isConnected) 0.50f else 0.22f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentTint.copy(alpha = 0.18f),
                    modifier = Modifier.size(19.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentTint,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp
                    ),
                    color = accentTint
                )
            }

            Text(
                text = speedText,
                style = MonoTypography.metric.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = FrostIce,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = totalText,
                style = MonoTypography.terminal.copy(fontSize = 10.sp),
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun OnlineServersCard(
    surfaceColor: Color,
    primaryColor: Color,
    accentColor: Color,
    isChristmasTheme: Boolean,
    serverCount: Int,
    countryFlag: String,
    activeServerTitle: String,
    protocolBadge: String,
    pingMs: Int?,
    activeTier: PlanTier,
    animationStyle: ServerAnimationStyle,
    onClick: () -> Unit
) {
    val tierColor = Color(activeTier.colorHex)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isChristmasTheme) Color(0xAD06130E) else surfaceColor,
        border = BorderStroke(
            1.dp,
            if (isChristmasTheme) {
                Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.44f), ChampagneGold.copy(alpha = 0.48f), OutlineSubtle)
                )
            } else {
                Brush.linearGradient(listOf(OutlineSubtle, OutlineSubtle))
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("online_servers_card")
    ) {
        Column {
            if (isChristmasTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, accentColor.copy(alpha = 0.80f), ChampagneGoldBright.copy(alpha = 0.88f), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NODOS VIP",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = FrostIce
                        )
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = serverCount.toString(),
                                style = MonoTypography.terminal.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(5.dp, 8.dp, 11.dp, 14.dp).forEach { barH ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(barH)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(accentColor.copy(alpha = 0.90f))
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xCC06120D),
                            border = BorderStroke(1.2.dp, tierColor),
                            modifier = Modifier.testTag("home_plan_tier_badge")
                        ) {
                            Text(
                                text = activeTier.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Ver servidores por país",
                            tint = ChampagneGoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ObsidianGlassElevated,
                        border = BorderStroke(
                            1.4.dp,
                            if (isChristmasTheme) {
                                Brush.linearGradient(listOf(ChampagneGoldBright, accentColor))
                            } else {
                                Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.6f), primaryColor.copy(alpha = 0.6f)))
                            }
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = countryFlag, fontSize = 18.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeServerTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = FrostIce,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$protocolBadge • ${pingMs?.let { "${it} ms óptimos" } ?: "Toca para cambiar servidor"}",
                            style = MonoTypography.terminal.copy(fontSize = 10.5.sp),
                            color = ChampagneGold.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ServerCardAnimationStrip(
                    style = animationStyle,
                    accentColor = accentColor,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
internal fun ChristmasGreetingBanner(
    primaryColor: Color,
    accentColor: Color,
    protocolBadge: String,
    activeCountryLabel: String,
    ambientEnabled: Boolean,
    soundStyleTitle: String,
    onBellBurst: () -> Unit,
    onToggleAmbient: () -> Unit,
    onCycleSoundStyle: () -> Unit
) {
    val eqTransition = rememberInfiniteTransition(label = "christmasEq")
    val eqPhase by eqTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    ChampagneGold.copy(alpha = 0.62f),
                    CranberryRed.copy(alpha = 0.42f),
                    accentColor.copy(alpha = 0.45f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xC2240A12),
                        Color(0xBC081C15),
                        Color(0xBC1B1508)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("christmas_greeting_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBellBurst)
                        .testTag("christmas_bell_burst_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        ChampagneGold.copy(alpha = 0.36f),
                                        CranberryRed.copy(alpha = 0.24f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.dp, ChampagneGoldBright.copy(alpha = 0.70f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val outerR = size.minDimension * 0.48f
                            val innerR = outerR * 0.25f
                            val starPath = Path().apply {
                                moveTo(cx, cy - outerR)
                                lineTo(cx + innerR, cy - innerR)
                                lineTo(cx + outerR, cy)
                                lineTo(cx + innerR, cy + innerR)
                                lineTo(cx, cy + outerR)
                                lineTo(cx - innerR, cy + innerR)
                                lineTo(cx - outerR, cy)
                                lineTo(cx - innerR, cy - innerR)
                                close()
                            }
                            drawPath(path = starPath, color = ChampagneGoldBright)
                            drawCircle(color = FrostIce, radius = innerR * 0.55f, center = Offset(cx, cy))
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Audio Navideño Pro",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGoldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (ambientEnabled) {
                            Canvas(modifier = Modifier.width(26.dp).height(12.dp)) {
                                val barW = 2.4.dp.toPx()
                                val gap = 1.6.dp.toPx()
                                for (b in 0 until 7) {
                                    val hFrac = 0.28f + 0.70f * ((sin(eqPhase + b * 0.95f) + 1f) / 2f)
                                    val barH = size.height * hFrac
                                    val bx = b * (barW + gap)
                                    drawLine(
                                        color = when (b % 3) {
                                            0 -> ChampagneGoldBright
                                            1 -> accentColor
                                            else -> CranberryRed
                                        },
                                        start = Offset(bx + barW / 2f, size.height),
                                        end = Offset(bx + barW / 2f, size.height - barH),
                                        strokeWidth = barW,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (ambientEnabled) soundStyleTitle else "$activeCountryLabel • $protocolBadge",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostIce.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampagneGold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.50f)),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCycleSoundStyle)
                        .testTag("christmas_carol_next_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Cambiar villancico navideño",
                            tint = ChampagneGoldBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (ambientEnabled) ChampagneGold.copy(alpha = 0.24f) else primaryColor.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (ambientEnabled) ChampagneGoldBright.copy(alpha = 0.85f) else primaryColor.copy(alpha = 0.36f)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleAmbient)
                        .testTag("christmas_ambient_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (ambientEnabled) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                            contentDescription = if (ambientEnabled) "Silenciar villancico" else "Activar villancico navideño",
                            tint = if (ambientEnabled) ChampagneGoldBright else FrostIce,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (ambientEnabled) "ON" else "OFF",
                            style = MonoTypography.terminal.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (ambientEnabled) ChampagneGoldBright else FrostIce,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConnectionSuccessBottomPill(
    status: VpnConnectionStatus,
    pingMs: Int?,
    primaryColor: Color,
    accentColor: Color,
    isChristmasTheme: Boolean
) {
    val isConnected = status == VpnConnectionStatus.CONNECTED
    val pillBorder = when {
        isConnected && isChristmasTheme -> Brush.horizontalGradient(listOf(accentColor, ChampagneGoldBright))
        isConnected -> Brush.horizontalGradient(listOf(accentColor, accentColor))
        else -> Brush.horizontalGradient(listOf(PulseAmber, primaryColor))
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xEB07140F),
        border = BorderStroke(1.2.dp, pillBorder),
        modifier = Modifier.testTag("home_connection_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.jeffers_vpn_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            if (isConnected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = if (isConnected) {
                    val pingTag = pingMs?.let { " • ${it}ms" } ?: ""
                    "Conexión exitosa$pingTag"
                } else {
                    "⚡ Conectando túnel cifrado…"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isConnected) accentColor else PulseAmber
            )
        }
    }
}
