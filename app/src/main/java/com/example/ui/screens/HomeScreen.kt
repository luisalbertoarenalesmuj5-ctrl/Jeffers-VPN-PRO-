package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VpnProfileEntity
import com.example.data.model.BackgroundEffect
import com.example.data.model.CountryRegion
import com.example.data.model.HomeLayoutStyle
import com.example.data.model.VpnConnectionStatus
import com.example.ui.Formatters
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CranberryRed
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianGlassElevated
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.TextMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    uiState: VpnDashboardUiState,
    onOpenDrawer: () -> Unit,
    onToggleConnect: () -> Unit,
    onOpenCountries: () -> Unit,
    onTestActiveProfile: (VpnProfileEntity) -> Unit,
    onToggleChristmasAmbient: () -> Unit = {},
    onCycleChristmasSound: () -> Unit = {},
    onBellBurst: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val telemetry = uiState.telemetry
    val activeProfile = uiState.selectedProfile
    val effectiveTier = settings.activeSubscription.effectiveTier()
    val isChristmasTheme = settings.themePreset.isChristmas ||
        settings.backgroundEffect == BackgroundEffect.CHRISTMAS_SNOW
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val glassSurfaceColor = if (isChristmasTheme) {
        ObsidianGlass.copy(alpha = (settings.cardAlpha * 0.74f).coerceIn(0.56f, 0.78f))
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = settings.cardAlpha)
    }

    var showIp by remember { mutableStateOf(true) }
    var showSpeedValues by remember { mutableStateOf(true) }
    var expandStats by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when (telemetry.status) {
            VpnConnectionStatus.CONNECTED -> accentColor
            VpnConnectionStatus.CONNECTING -> PulseAmber
            VpnConnectionStatus.ERROR -> errorColor
            VpnConnectionStatus.DISCONNECTED -> TextMuted
        },
        label = "homeStatusColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = settings.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = accentColor
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeTopBar(
                    status = telemetry.status,
                    statusColor = statusColor,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    activeTier = effectiveTier,
                    isChristmasTheme = isChristmasTheme,
                    onOpenDrawer = onOpenDrawer
                )

                Spacer(modifier = Modifier.height(if (isWide) 18.dp else 22.dp))

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 980.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PowerControlWidget(
                                layoutStyle = settings.homeLayoutStyle,
                                status = telemetry.status,
                                buttonAlpha = settings.buttonAlpha,
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                errorColor = errorColor,
                                isChristmasTheme = isChristmasTheme,
                                durationSeconds = telemetry.durationSeconds,
                                protocolBadge = activeProfile?.protocol ?: "BHTTP",
                                profileName = activeProfile?.profileName ?: "VPSpremiumPRO",
                                onClick = onToggleConnect
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            StatusAndIpBlock(
                                status = telemetry.status,
                                ipAddress = activeProfile?.host ?: telemetry.serverHost,
                                showIp = showIp,
                                statusMessage = telemetry.statusMessage,
                                carrierBadge = "${telemetry.carrier.displayName} ${telemetry.serverPort}",
                                protocolBadge = activeProfile?.protocol ?: "BHTTP",
                                accentColor = accentColor,
                                errorColor = errorColor,
                                isChristmasTheme = isChristmasTheme,
                                onToggleIp = { showIp = !showIp }
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (settings.showSpeedPanel) {
                                SpeedTelemetryBentoSection(
                                    surfaceColor = glassSurfaceColor,
                                    primaryColor = primaryColor,
                                    accentColor = accentColor,
                                    isChristmasTheme = isChristmasTheme,
                                    isConnected = telemetry.status == VpnConnectionStatus.CONNECTED,
                                    rxSpeed = telemetry.rxBytesPerSec,
                                    txSpeed = telemetry.txBytesPerSec,
                                    rxTotal = telemetry.rxBytes,
                                    txTotal = telemetry.txBytes,
                                    durationSeconds = telemetry.durationSeconds,
                                    pingMs = telemetry.pingMs ?: activeProfile?.lastLatencyMs,
                                    carrierBadge = "${telemetry.carrier.displayName} ${telemetry.serverPort}",
                                    showValues = showSpeedValues,
                                    expanded = expandStats,
                                    isTesting = uiState.isTestingPing,
                                    onToggleShow = { showSpeedValues = !showSpeedValues },
                                    onToggleExpand = { expandStats = !expandStats },
                                    onTestPing = { activeProfile?.let(onTestActiveProfile) }
                                )
                            }

                            OnlineServersCard(
                                surfaceColor = glassSurfaceColor,
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                isChristmasTheme = isChristmasTheme,
                                serverCount = uiState.profiles.size,
                                countryFlag = CountryRegion.fromName(
                                    activeProfile?.country ?: "GUATEMALA"
                                ).flagEmoji,
                                activeServerTitle = activeProfile?.profileName ?: "Seleccionar servidor",
                                protocolBadge = activeProfile?.protocol ?: "BHTTP",
                                pingMs = telemetry.pingMs ?: activeProfile?.lastLatencyMs,
                                activeTier = effectiveTier,
                                animationStyle = settings.serverAnimation,
                                onClick = onOpenCountries
                            )

                            if (isChristmasTheme) {
                                ChristmasGreetingBanner(
                                    primaryColor = primaryColor,
                                    accentColor = accentColor,
                                    protocolBadge = activeProfile?.protocol ?: "BHTTP",
                                    activeCountryLabel = activeProfile?.country ?: "GLOBAL",
                                    ambientEnabled = settings.christmasAmbientEnabled,
                                    soundStyleTitle = settings.christmasSoundStyle.title,
                                    onBellBurst = onBellBurst,
                                    onToggleAmbient = onToggleChristmasAmbient,
                                    onCycleSoundStyle = onCycleChristmasSound
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(if (isChristmasTheme) 18.dp else 4.dp))

                        PowerControlWidget(
                            layoutStyle = settings.homeLayoutStyle,
                            status = telemetry.status,
                            buttonAlpha = settings.buttonAlpha,
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            errorColor = errorColor,
                            isChristmasTheme = isChristmasTheme,
                            durationSeconds = telemetry.durationSeconds,
                            protocolBadge = activeProfile?.protocol ?: "BHTTP",
                            profileName = activeProfile?.profileName ?: "VPSpremiumPRO",
                            onClick = onToggleConnect
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        StatusAndIpBlock(
                            status = telemetry.status,
                            ipAddress = activeProfile?.host ?: telemetry.serverHost,
                            showIp = showIp,
                            statusMessage = telemetry.statusMessage,
                            carrierBadge = "${telemetry.carrier.displayName} ${telemetry.serverPort}",
                            protocolBadge = activeProfile?.protocol ?: "BHTTP",
                            accentColor = accentColor,
                            errorColor = errorColor,
                            isChristmasTheme = isChristmasTheme,
                            onToggleIp = { showIp = !showIp }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (settings.showSpeedPanel) {
                            SpeedTelemetryBentoSection(
                                surfaceColor = glassSurfaceColor,
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                isChristmasTheme = isChristmasTheme,
                                isConnected = telemetry.status == VpnConnectionStatus.CONNECTED,
                                rxSpeed = telemetry.rxBytesPerSec,
                                txSpeed = telemetry.txBytesPerSec,
                                rxTotal = telemetry.rxBytes,
                                txTotal = telemetry.txBytes,
                                durationSeconds = telemetry.durationSeconds,
                                pingMs = telemetry.pingMs ?: activeProfile?.lastLatencyMs,
                                carrierBadge = "${telemetry.carrier.displayName} ${telemetry.serverPort}",
                                showValues = showSpeedValues,
                                expanded = expandStats,
                                isTesting = uiState.isTestingPing,
                                onToggleShow = { showSpeedValues = !showSpeedValues },
                                onToggleExpand = { expandStats = !expandStats },
                                onTestPing = { activeProfile?.let(onTestActiveProfile) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OnlineServersCard(
                            surfaceColor = glassSurfaceColor,
                            primaryColor = primaryColor,
                            accentColor = accentColor,
                            isChristmasTheme = isChristmasTheme,
                            serverCount = uiState.profiles.size,
                            countryFlag = CountryRegion.fromName(
                                activeProfile?.country ?: "GUATEMALA"
                            ).flagEmoji,
                            activeServerTitle = activeProfile?.profileName ?: "Seleccionar servidor",
                            protocolBadge = activeProfile?.protocol ?: "BHTTP",
                            pingMs = telemetry.pingMs ?: activeProfile?.lastLatencyMs,
                            activeTier = effectiveTier,
                            animationStyle = settings.serverAnimation,
                            onClick = onOpenCountries
                        )

                        if (isChristmasTheme) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ChristmasGreetingBanner(
                                primaryColor = primaryColor,
                                accentColor = accentColor,
                                protocolBadge = activeProfile?.protocol ?: "BHTTP",
                                activeCountryLabel = activeProfile?.country ?: "GLOBAL",
                                ambientEnabled = settings.christmasAmbientEnabled,
                                soundStyleTitle = settings.christmasSoundStyle.title,
                                onBellBurst = onBellBurst,
                                onToggleAmbient = onToggleChristmasAmbient,
                                onCycleSoundStyle = onCycleChristmasSound
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedVisibility(
                    visible = telemetry.status == VpnConnectionStatus.CONNECTED ||
                        telemetry.status == VpnConnectionStatus.CONNECTING
                ) {
                    ConnectionSuccessBottomPill(
                        status = telemetry.status,
                        pingMs = telemetry.pingMs ?: activeProfile?.lastLatencyMs,
                        primaryColor = primaryColor,
                        accentColor = accentColor,
                        isChristmasTheme = isChristmasTheme
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun PowerControlWidget(
    layoutStyle: HomeLayoutStyle,
    status: VpnConnectionStatus,
    buttonAlpha: Float,
    primaryColor: Color,
    accentColor: Color,
    errorColor: Color,
    isChristmasTheme: Boolean,
    durationSeconds: Long,
    protocolBadge: String,
    profileName: String,
    onClick: () -> Unit
) {
    val ringColor by animateColorAsState(
        targetValue = when (status) {
            VpnConnectionStatus.DISCONNECTED -> primaryColor
            VpnConnectionStatus.CONNECTING -> PulseAmber
            VpnConnectionStatus.CONNECTED -> accentColor
            VpnConnectionStatus.ERROR -> errorColor
        },
        label = "ringColor"
    )

    val label = when (status) {
        VpnConnectionStatus.DISCONNECTED -> "CONECTAR"
        VpnConnectionStatus.CONNECTING -> "CIFRANDO"
        VpnConnectionStatus.CONNECTED -> "CONECTADO"
        VpnConnectionStatus.ERROR -> "REINTENTAR"
    }

    when (layoutStyle) {
        HomeLayoutStyle.SPHERE -> {
            val infiniteTransition = rememberInfiniteTransition(label = "chronometerAnim")
            val sweepRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = when (status) {
                            VpnConnectionStatus.CONNECTING -> 1050
                            VpnConnectionStatus.CONNECTED -> 2800
                            else -> 7200
                        },
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sweepRotation"
            )
            val sonarWave by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = if (status == VpnConnectionStatus.CONNECTING) 1300 else 2300,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sonarWave"
            )
            val scale by animateFloatAsState(
                targetValue = if (status == VpnConnectionStatus.CONNECTED) 1.025f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "sphereScale"
            )

            Box(
                modifier = Modifier
                    .size(204.dp)
                    .scale(scale)
                    .alpha(buttonAlpha),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outerRadius = size.minDimension / 2f - 6.dp.toPx()
                    val octRadius = outerRadius - 10.dp.toPx()
                    val innerDialRadius = octRadius - 11.dp.toPx()
                    val centerPt = Offset(size.width / 2f, size.height / 2f)

                    if (status == VpnConnectionStatus.CONNECTED || status == VpnConnectionStatus.CONNECTING) {
                        val minWaveR = 68.dp.toPx()
                        val waveR = minWaveR + (outerRadius - minWaveR) * sonarWave
                        val waveAlpha = (1f - sonarWave) * 0.48f
                        drawCircle(
                            color = ringColor.copy(alpha = waveAlpha),
                            radius = waveR,
                            center = centerPt,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    if (isChristmasTheme) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ringColor.copy(alpha = if (status == VpnConnectionStatus.CONNECTED) 0.34f else 0.18f),
                                    ChampagneGold.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            ),
                            radius = outerRadius
                        )

                        for (t in 0 until 60) {
                            val angleRad = (t * 6.0 - 90.0) * PI / 180.0
                            val isCardinal = t % 15 == 0
                            val isHour = t % 5 == 0
                            val tickLen = when {
                                isCardinal -> 7.dp.toPx()
                                isHour -> 4.5.dp.toPx()
                                else -> 2.4.dp.toPx()
                            }
                            val rEnd = outerRadius - 1.dp.toPx()
                            val rStart = rEnd - tickLen
                            drawLine(
                                color = when {
                                    isCardinal -> ChampagneGoldBright
                                    isHour -> ChampagneGold.copy(alpha = 0.70f)
                                    else -> FrostIce.copy(alpha = 0.24f)
                                },
                                start = Offset(
                                    centerPt.x + (cos(angleRad) * rStart).toFloat(),
                                    centerPt.y + (sin(angleRad) * rStart).toFloat()
                                ),
                                end = Offset(
                                    centerPt.x + (cos(angleRad) * rEnd).toFloat(),
                                    centerPt.y + (sin(angleRad) * rEnd).toFloat()
                                ),
                                strokeWidth = when {
                                    isCardinal -> 2.2.dp.toPx()
                                    isHour -> 1.4.dp.toPx()
                                    else -> 0.8.dp.toPx()
                                },
                                cap = StrokeCap.Round
                            )
                        }

                        val octAngle = if (status == VpnConnectionStatus.DISCONNECTED) 22.5f else 22.5f + sweepRotation * 0.12f
                        val octPath = buildPolygonPath(centerPt, octRadius, sides = 8, rotationDeg = octAngle)
                        drawPath(
                            path = octPath,
                            brush = Brush.linearGradient(
                                listOf(ChampagneGold.copy(alpha = 0.36f), ringColor.copy(alpha = 0.42f), CranberryRed.copy(alpha = 0.24f))
                            ),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = octPath,
                            brush = Brush.linearGradient(
                                listOf(ChampagneGoldBright, ringColor, ChampagneGold)
                            ),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        drawArc(
                            color = ChampagneGold.copy(alpha = 0.62f),
                            startAngle = -sweepRotation * 0.65f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(centerPt.x - innerDialRadius, centerPt.y - innerDialRadius),
                            size = Size(innerDialRadius * 2f, innerDialRadius * 2f),
                            style = Stroke(
                                width = 1.1.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f), 0f)
                            )
                        )

                        for (c in 0 until 12) {
                            val cRad = (c * 30.0 - 90.0) * PI / 180.0
                            val nx = centerPt.x + (cos(cRad) * (outerRadius - 9.dp.toPx())).toFloat()
                            val ny = centerPt.y + (sin(cRad) * (outerRadius - 9.dp.toPx())).toFloat()
                            val gemColor = when (c % 3) {
                                0 -> ChampagneGoldBright
                                1 -> CranberryRed
                                else -> accentColor
                            }
                            val gemR = if (c == 0) 7.5.dp.toPx() else if (c % 3 == 0) 4.6.dp.toPx() else 3.2.dp.toPx()
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(FrostIce, gemColor.copy(alpha = 0.85f), Color.Transparent),
                                    center = Offset(nx, ny),
                                    radius = gemR
                                ),
                                radius = gemR,
                                center = Offset(nx, ny)
                            )
                            if (c == 0) {
                                val armL = 6.dp.toPx()
                                val armS = 1.8.dp.toPx()
                                val northStar = Path().apply {
                                    moveTo(nx, ny - armL)
                                    lineTo(nx + armS, ny - armS)
                                    lineTo(nx + armL, ny)
                                    lineTo(nx + armS, ny + armS)
                                    lineTo(nx, ny + armL)
                                    lineTo(nx - armS, ny + armS)
                                    lineTo(nx - armL, ny)
                                    lineTo(nx - armS, ny - armS)
                                    close()
                                }
                                drawPath(path = northStar, color = ChampagneGoldBright)
                                drawCircle(color = FrostIce, radius = 1.5.dp.toPx(), center = Offset(nx, ny))
                            }
                        }
                    } else {
                        drawCircle(
                            color = ringColor.copy(alpha = 0.16f),
                            radius = outerRadius,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = ringColor.copy(alpha = 0.22f),
                            radius = outerRadius - 14.dp.toPx(),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    drawArc(
                        brush = Brush.sweepGradient(
                            if (isChristmasTheme) {
                                listOf(
                                    Color.Transparent,
                                    ChampagneGoldBright.copy(alpha = 0.95f),
                                    ringColor.copy(alpha = 0.95f),
                                    FrostIce.copy(alpha = 0.80f),
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    Color.Transparent,
                                    ringColor.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            }
                        ),
                        startAngle = sweepRotation,
                        sweepAngle = if (status == VpnConnectionStatus.DISCONNECTED) 125f else 205f,
                        useCenter = false,
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            if (isChristmasTheme) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (status == VpnConnectionStatus.CONNECTED) Color(0x880B3324) else Color(0x6E092119),
                                        Color(0x8A05120D),
                                        Color(0xAA030806)
                                    )
                                )
                            } else {
                                Brush.radialGradient(listOf(Color(0xFF080D15), Color(0xFF04070B)))
                            }
                        )
                        .border(
                            width = if (isChristmasTheme) 1.5.dp else 1.dp,
                            brush = if (isChristmasTheme) {
                                Brush.linearGradient(
                                    listOf(ChampagneGoldBright.copy(alpha = 0.90f), ringColor.copy(alpha = 0.68f), CranberryRed.copy(alpha = 0.45f))
                                )
                            } else {
                                Brush.linearGradient(listOf(ringColor.copy(alpha = 0.5f), ringColor.copy(alpha = 0.5f)))
                            },
                            shape = CircleShape
                        )
                        .clickable(onClick = onClick)
                        .testTag("vpn_connect_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChristmasTheme) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.minDimension * 0.36f
                            val filigreeColor = ChampagneGold.copy(alpha = 0.14f)
                            for (a in 0 until 6) {
                                val rad = (a * 60.0) * PI / 180.0
                                val ex = c.x + (cos(rad) * r).toFloat()
                                val ey = c.y + (sin(rad) * r).toFloat()
                                drawLine(filigreeColor, c, Offset(ex, ey), strokeWidth = 1.dp.toPx())
                                val bx = c.x + (cos(rad) * (r * 0.62f)).toFloat()
                                val by = c.y + (sin(rad) * (r * 0.62f)).toFloat()
                                val bl = r * 0.25f
                                drawLine(
                                    filigreeColor,
                                    Offset(bx, by),
                                    Offset(bx + (cos(rad + 0.72) * bl).toFloat(), by + (sin(rad + 0.72) * bl).toFloat()),
                                    strokeWidth = 0.9.dp.toPx()
                                )
                                drawLine(
                                    filigreeColor,
                                    Offset(bx, by),
                                    Offset(bx + (cos(rad - 0.72) * bl).toFloat(), by + (sin(rad - 0.72) * bl).toFloat()),
                                    strokeWidth = 0.9.dp.toPx()
                                )
                            }
                            drawArc(
                                brush = Brush.verticalGradient(
                                    listOf(FrostIce.copy(alpha = 0.40f), Color.Transparent)
                                ),
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(7.dp.toPx(), 7.dp.toPx()),
                                size = Size(size.width - 14.dp.toPx(), size.height - 14.dp.toPx()),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (status == VpnConnectionStatus.CONNECTED) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Rounded.PowerSettingsNew
                            },
                            contentDescription = label,
                            tint = when {
                                status == VpnConnectionStatus.CONNECTED -> accentColor
                                status == VpnConnectionStatus.ERROR -> errorColor
                                isChristmasTheme -> ChampagneGoldBright
                                else -> primaryColor
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.0.sp
                            ),
                            color = if (status == VpnConnectionStatus.CONNECTED) accentColor else FrostIce,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x8003070C),
                            border = BorderStroke(
                                0.8.dp,
                                if (status == VpnConnectionStatus.CONNECTED) accentColor.copy(alpha = 0.55f)
                                else ChampagneGold.copy(alpha = 0.42f)
                            )
                        ) {
                            Text(
                                text = when (status) {
                                    VpnConnectionStatus.CONNECTED -> Formatters.formatDuration(durationSeconds)
                                    VpnConnectionStatus.CONNECTING -> "CIFRANDO…"
                                    VpnConnectionStatus.ERROR -> "REINTENTAR"
                                    VpnConnectionStatus.DISCONNECTED -> "$protocolBadge • TLS"
                                },
                                style = MonoTypography.terminal.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = if (status == VpnConnectionStatus.CONNECTED) accentColor else ChampagneGoldBright,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        HomeLayoutStyle.CENTER_BUTTON -> {
            Surface(
                shape = CircleShape,
                color = if (isChristmasTheme) ObsidianGlass else ringColor.copy(alpha = 0.14f * buttonAlpha),
                border = BorderStroke(
                    width = 2.dp,
                    brush = if (isChristmasTheme) {
                        Brush.linearGradient(listOf(ChampagneGoldBright, ringColor, ChampagneGold))
                    } else {
                        Brush.linearGradient(listOf(ringColor, ringColor))
                    }
                ),
                modifier = Modifier
                    .size(168.dp)
                    .alpha(buttonAlpha)
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .testTag("vpn_connect_button")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (status == VpnConnectionStatus.CONNECTED) Icons.Rounded.Shield else Icons.Rounded.PowerSettingsNew,
                        contentDescription = label,
                        tint = if (isChristmasTheme && status == VpnConnectionStatus.DISCONNECTED) ChampagneGoldBright else ringColor,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = FrostIce
                    )
                }
            }
        }

        HomeLayoutStyle.CLASSIC -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isChristmasTheme) ObsidianGlassElevated else ringColor.copy(alpha = buttonAlpha),
                border = BorderStroke(
                    width = 1.4.dp,
                    brush = if (isChristmasTheme) {
                        Brush.horizontalGradient(listOf(ChampagneGoldBright, ringColor, ChampagneGold))
                    } else {
                        Brush.linearGradient(listOf(ringColor, ringColor))
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .alpha(buttonAlpha)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onClick)
                    .testTag("vpn_connect_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (status == VpnConnectionStatus.CONNECTED) Icons.Rounded.Shield else Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isChristmasTheme) ChampagneGoldBright else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$label • $profileName",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isChristmasTheme) FrostIce else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun buildPolygonPath(
    center: Offset,
    radius: Float,
    sides: Int,
    rotationDeg: Float
): Path {
    val path = Path()
    val step = 360.0 / sides
    for (i in 0 until sides) {
        val angleRad = (i * step + rotationDeg - 90.0) * PI / 180.0
        val px = center.x + (cos(angleRad) * radius).toFloat()
        val py = center.y + (sin(angleRad) * radius).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}
