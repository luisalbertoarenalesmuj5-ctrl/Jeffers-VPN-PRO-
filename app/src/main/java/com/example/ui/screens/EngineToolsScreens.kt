package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CountryRegion
import com.example.data.model.DnsProvider
import com.example.data.model.PlanTier
import com.example.data.model.VpnConnectionStatus
import com.example.data.model.VpnPerformanceMode
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.components.LuxuryChristmasHat
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CranberryRed
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianGlassElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VpnModeScreen(
    uiState: VpnDashboardUiState,
    onSelectMode: (VpnPerformanceMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = uiState.settings.performanceMode
    var expandedMode by remember { mutableStateOf(activeMode) }
    val surfaceColor = ObsidianGlass.copy(alpha = uiState.settings.cardAlpha.coerceIn(0.66f, 0.90f))
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = uiState.settings.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = accentColor,
            modifier = Modifier.alpha(0.50f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                SubScreenTopBar(
                    title = "Modo VPN",
                    subtitle = "ENGINE.TUNING // BHTTP",
                    titleColor = ChampagneGoldBright,
                    onBack = onBack
                )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Activo al reconectar — si estás conectado, reconecta para aplicar",
                style = MaterialTheme.typography.bodySmall,
                color = FrostIce.copy(alpha = 0.74f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            VpnPerformanceMode.entries.forEach { mode ->
                val isSelected = mode == activeMode
                val isExpanded = mode == expandedMode
                val barColor = when (mode) {
                    VpnPerformanceMode.BALANCED -> primaryColor
                    VpnPerformanceMode.GAMING -> CyberEmerald
                    VpnPerformanceMode.POWER_SAVER -> PulseAmber
                    VpnPerformanceMode.MAX_PERFORMANCE -> MaterialTheme.colorScheme.error
                }
                val icon = when (mode) {
                    VpnPerformanceMode.BALANCED -> Icons.Rounded.Balance
                    VpnPerformanceMode.GAMING -> Icons.Rounded.SportsEsports
                    VpnPerformanceMode.POWER_SAVER -> Icons.Rounded.BatteryChargingFull
                    VpnPerformanceMode.MAX_PERFORMANCE -> Icons.Rounded.RocketLaunch
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        brush = if (isSelected) {
                            Brush.horizontalGradient(listOf(ChampagneGoldBright, barColor, ChampagneGold))
                        } else {
                            Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.35f), ChampagneGold.copy(alpha = 0.28f), OutlineSubtle))
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            expandedMode = mode
                            onSelectMode(mode)
                        }
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            if (isSelected) ChampagneGoldBright else barColor.copy(alpha = 0.65f),
                                            barColor.copy(alpha = 0.65f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(if (isExpanded) 96.dp else 36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Brush.verticalGradient(listOf(ChampagneGoldBright, barColor)))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = barColor.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, barColor.copy(alpha = 0.48f)),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = null, tint = barColor, modifier = Modifier.size(19.dp))
                                            }
                                        }
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = FrostIce,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = barColor.copy(alpha = 0.18f),
                                                border = BorderStroke(0.8.dp, barColor.copy(alpha = 0.60f))
                                            ) {
                                                Text(
                                                    text = "✦ ACTIVO",
                                                    style = MonoTypography.terminal.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                                    color = barColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = ChampagneGoldBright
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = mode.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = FrostIce.copy(alpha = 0.84f)
                                        )
                                        mode.bullets.forEach { bullet ->
                                            Text(
                                                text = "• $bullet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FrostIce.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.34f)),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(
                    text = "El modo elegido queda guardado y se aplica cada vez que conectas. Máximo rendimiento usa MTU 1500 y keep-alive inmediato; Ahorro de energía reduce radio y batería sin cortar el túnel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostIce.copy(alpha = 0.78f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        }
    }
}

@Composable
fun SettingsScreen(
    uiState: VpnDashboardUiState,
    onSaveSettings: (
        showSpeedPanel: Boolean,
        dnsProvider: DnsProvider,
        fullTunnel: Boolean,
        autoReconnect: Boolean,
        mtu: Int,
        smartAlerts: Boolean
    ) -> Unit,
    onVerifyAdminPassword: (String) -> Boolean,
    onSavePersonalTunnelCredentials: (username: String, password: String) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenLiveMatches: () -> Unit,
    onBack: () -> Unit,
    onToggleChristmasSounds: (Boolean) -> Unit = {},
    onToggleChristmasAmbient: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val initial = uiState.settings
    var showSpeedPanel by remember(initial) { mutableStateOf(initial.showSpeedPanel) }
    var dnsProvider by remember(initial) { mutableStateOf(initial.dnsProvider) }
    var fullTunnel by remember(initial) { mutableStateOf(initial.fullTunnel) }
    var autoReconnect by remember(initial) { mutableStateOf(initial.autoReconnect) }
    var mtu by remember(initial) { mutableIntStateOf(initial.mtu) }
    var smartAlerts by remember(initial) { mutableStateOf(initial.smartAlerts) }

    var showPersonalClientDialog by remember { mutableStateOf(false) }
    var personalUserDraft by remember(initial.personalTunnelUsername) { mutableStateOf(initial.personalTunnelUsername) }
    var personalPassDraft by remember(initial.personalTunnelPassword) { mutableStateOf(initial.personalTunnelPassword) }
    var showPersonalPassText by remember { mutableStateOf(false) }

    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var adminAuthError by remember { mutableStateOf(false) }
    var showAdminPasswordText by remember { mutableStateOf(false) }
    var failedAdminAttempts by remember { mutableIntStateOf(0) }
    var adminLockRemainingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(adminLockRemainingSeconds) {
        if (adminLockRemainingSeconds > 0) {
            delay(1000L)
            adminLockRemainingSeconds -= 1
            if (adminLockRemainingSeconds == 0) {
                failedAdminAttempts = 0
                adminAuthError = false
            }
        }
    }

    val surfaceColor = ObsidianGlass.copy(alpha = initial.cardAlpha.coerceIn(0.66f, 0.90f))
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = initial.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = accentColor,
            modifier = Modifier.alpha(0.48f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubScreenTopBar(
                    title = "Configuración",
                    subtitle = "NETWORK // ENGINE",
                    titleColor = ChampagneGoldBright,
                    onBack = onBack
                )

            Text(
                text = "Ajustes de red y motor — se aplican al reconectar",
                style = MaterialTheme.typography.bodySmall,
                color = FrostIce.copy(alpha = 0.74f)
            )

            SettingSwitchCard(
                icon = Icons.Rounded.Monitor,
                iconTint = primaryColor,
                title = "PANTALLA",
                subtitle = if (showSpeedPanel) "Panel de velocidad visible" else "Panel de velocidad oculto",
                checked = showSpeedPanel,
                onCheckedChange = { showSpeedPanel = it },
                surfaceColor = surfaceColor
            )

            SettingCycleCard(
                icon = Icons.Rounded.Dns,
                iconTint = primaryColor,
                title = "DNS",
                subtitle = "${dnsProvider.label} (${dnsProvider.primary})",
                surfaceColor = surfaceColor,
                onClick = {
                    val values = DnsProvider.entries
                    dnsProvider = values[(dnsProvider.ordinal + 1) % values.size]
                }
            )

            SettingSwitchCard(
                icon = Icons.Rounded.Link,
                iconTint = accentColor,
                title = "CONEXIÓN",
                subtitle = if (fullTunnel) "Túnel completo (0.0.0.0/0)" else "Túnel estándar",
                checked = fullTunnel,
                onCheckedChange = { fullTunnel = it },
                surfaceColor = surfaceColor
            )

            SettingSwitchCard(
                icon = Icons.Rounded.Refresh,
                iconTint = PulseAmber,
                title = "RECONEXIÓN",
                subtitle = if (autoReconnect) "Activada ante caídas" else "Desactivada",
                checked = autoReconnect,
                onCheckedChange = { autoReconnect = it },
                surfaceColor = surfaceColor
            )

            SettingCycleCard(
                icon = Icons.Rounded.Settings,
                iconTint = Color(0xFFA855F7),
                title = "AVANZADO",
                subtitle = "MTU $mtu bytes (toca para alternar 1360 / 1400 / 1500)",
                surfaceColor = surfaceColor,
                onClick = {
                    mtu = when (mtu) {
                        1400 -> 1500
                        1500 -> 1360
                        else -> 1400
                    }
                }
            )

            SettingSwitchCard(
                icon = Icons.Rounded.NotificationsActive,
                iconTint = primaryColor,
                title = "ALERTAS INTELIGENTES",
                subtitle = if (smartAlerts) "Activadas" else "Desactivadas",
                checked = smartAlerts,
                onCheckedChange = { smartAlerts = it },
                surfaceColor = surfaceColor
            )

            SettingSwitchCard(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                iconTint = primaryColor,
                title = "SONIDO NAVIDEÑO PRO",
                subtitle = if (initial.christmasSoundsEnabled) "Celesta y cascabeles al conectar (${initial.christmasSoundStyle.title})" else "Silenciado",
                checked = initial.christmasSoundsEnabled,
                onCheckedChange = onToggleChristmasSounds,
                surfaceColor = surfaceColor
            )

            SettingSwitchCard(
                icon = Icons.Rounded.MusicNote,
                iconTint = accentColor,
                title = "VILLANCICO AMBIENTAL",
                subtitle = if (initial.christmasAmbientEnabled) "Música invernal activa de fondo" else "Desactivado",
                checked = initial.christmasAmbientEnabled,
                onCheckedChange = onToggleChristmasAmbient,
                surfaceColor = surfaceColor
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        personalUserDraft = initial.personalTunnelUsername
                        personalPassDraft = initial.personalTunnelPassword
                        showPersonalClientDialog = true
                    }
                    .testTag("open_personal_client_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USUARIO CLIENTE PERSONAL",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (initial.personalTunnelUsername.isNotBlank()) {
                                "Activo: ${initial.personalTunnelUsername}"
                            } else {
                                "Escribir usuario propio de túnel (clientes personales)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Escribir usuario de cliente personal",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenLiveMatches)
                    .testTag("open_live_matches_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CANAL EN VIVO • FÚTBOL",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Partidos y transmisiones deportivas en directo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Abrir canal de partidos en vivo",
                        tint = CyberEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, PulseAmber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        adminPasswordInput = ""
                        adminAuthError = false
                        showAdminAuthDialog = true
                    }
                    .testTag("open_admin_panel_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PulseAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = PulseAmber, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PANEL ADMIN",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Códigos, activación por ID, planes y vencimientos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Acceso protegido por contraseña",
                        tint = PulseAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    onSaveSettings(showSpeedPanel, dnsProvider, fullTunnel, autoReconnect, mtu, smartAlerts)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(primaryColor, accentColor))
                    )
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, tint = ObsidianVoid)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GUARDAR CONFIGURACIÓN",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = ObsidianVoid
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }

    val settingsDialogContainer = Color(0xFF091B14)
    val settingsFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ChampagneGoldBright,
        unfocusedBorderColor = ChampagneGold.copy(alpha = 0.35f),
        focusedLabelColor = ChampagneGoldBright,
        unfocusedLabelColor = FrostIce.copy(alpha = 0.72f),
        focusedTextColor = FrostIce,
        unfocusedTextColor = FrostIce,
        cursorColor = ChampagneGoldBright
    )

    if (showAdminAuthDialog) {
        val isLocked = adminLockRemainingSeconds > 0
        AlertDialog(
            onDismissRequest = { showAdminAuthDialog = false },
            containerColor = settingsDialogContainer,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = PulseAmber)
                    Text("Acceso Panel Admin", color = ChampagneGoldBright, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa la contraseña ultra de administrador para gestionar códigos, IDs, usuarios y servidores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostIce.copy(alpha = 0.78f)
                    )
                    OutlinedTextField(
                        value = adminPasswordInput,
                        onValueChange = {
                            adminPasswordInput = it
                            adminAuthError = false
                        },
                        enabled = !isLocked,
                        label = { Text("Contraseña Admin") },
                        singleLine = true,
                        isError = adminAuthError || isLocked,
                        colors = settingsFieldColors,
                        visualTransformation = if (showAdminPasswordText) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showAdminPasswordText = !showAdminPasswordText }) {
                                Icon(
                                    imageVector = if (showAdminPasswordText) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Mostrar u ocultar contraseña",
                                    tint = ChampagneGoldBright
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                    )
                    if (isLocked) {
                        Text(
                            text = "Demasiados intentos fallidos. Espera ${adminLockRemainingSeconds}s para reintentar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (adminAuthError) {
                        Text(
                            text = "Contraseña incorrecta (${5 - failedAdminAttempts} intento(s) antes de bloqueo temporal).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isLocked && adminPasswordInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGoldBright,
                        contentColor = ObsidianVoid
                    ),
                    onClick = {
                        if (onVerifyAdminPassword(adminPasswordInput)) {
                            showAdminAuthDialog = false
                            adminPasswordInput = ""
                            failedAdminAttempts = 0
                            adminAuthError = false
                            onOpenAdminPanel()
                        } else {
                            adminAuthError = true
                            failedAdminAttempts += 1
                            if (failedAdminAttempts >= 5) {
                                adminLockRemainingSeconds = 30
                            }
                        }
                    },
                    modifier = Modifier.testTag("admin_unlock_button")
                ) {
                    Text("Desbloquear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminAuthDialog = false }) {
                    Text("Cancelar", color = FrostIce.copy(alpha = 0.85f))
                }
            }
        )
    }

    if (showPersonalClientDialog) {
        AlertDialog(
            onDismissRequest = { showPersonalClientDialog = false },
            containerColor = settingsDialogContainer,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = ChampagneGoldBright)
                    Text("Usuario Cliente Personal", color = ChampagneGoldBright, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Escribe tu usuario asignado para conectar el túnel como cliente personal. Déjalo vacío si deseas usar el usuario por defecto del servidor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostIce.copy(alpha = 0.78f)
                    )
                    OutlinedTextField(
                        value = personalUserDraft,
                        onValueChange = { personalUserDraft = it },
                        label = { Text("Usuario personal") },
                        placeholder = { Text("Ej: cliente_vip01") },
                        singleLine = true,
                        colors = settingsFieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("settings_personal_user_input")
                    )
                    OutlinedTextField(
                        value = personalPassDraft,
                        onValueChange = { personalPassDraft = it },
                        label = { Text("Contraseña (opcional)") },
                        singleLine = true,
                        colors = settingsFieldColors,
                        visualTransformation = if (showPersonalPassText) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPersonalPassText = !showPersonalPassText }) {
                                Icon(
                                    imageVector = if (showPersonalPassText) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Mostrar u ocultar contraseña",
                                    tint = ChampagneGoldBright
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_personal_pass_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePersonalTunnelCredentials(personalUserDraft, personalPassDraft)
                        showPersonalClientDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGoldBright,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("settings_personal_save_button")
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (initial.personalTunnelUsername.isNotBlank() || personalUserDraft.isNotBlank()) {
                        TextButton(onClick = {
                            personalUserDraft = ""
                            personalPassDraft = ""
                            onSavePersonalTunnelCredentials("", "")
                            showPersonalClientDialog = false
                        }) {
                            Text("Limpiar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showPersonalClientDialog = false }) {
                        Text("Cancelar", color = FrostIce.copy(alpha = 0.85f))
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    surfaceColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(iconTint.copy(alpha = 0.42f), ChampagneGold.copy(alpha = 0.30f), OutlineSubtle)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, ChampagneGold.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = iconTint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.42f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FrostIce)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FrostIce.copy(alpha = 0.72f))
                    }
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun SettingCycleCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(iconTint.copy(alpha = 0.42f), ChampagneGold.copy(alpha = 0.30f), OutlineSubtle)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, ChampagneGold.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconTint.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.42f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FrostIce)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FrostIce.copy(alpha = 0.72f))
                }
                Icon(
                    imageVector = Icons.Rounded.FlashOn,
                    contentDescription = "Cambiar",
                    tint = ChampagneGoldBright,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    uiState: VpnDashboardUiState,
    activationMessage: Pair<Boolean, String>?,
    onUpdateAlias: (String) -> Unit,
    onRedeemCode: (String) -> Unit,
    onRedeemPersonalUsername: (username: String, secret: String?) -> Unit,
    onSyncById: () -> Unit,
    onClearActivationMessage: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    authenticatedEmail: String? = null,
    isLinkingGoogle: Boolean = false,
    googleLinkError: String? = null,
    onLinkGoogleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val subscription = settings.activeSubscription
    val activeTier = subscription.effectiveTier()
    val tierColor = Color(activeTier.colorHex)
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val isChristmasTheme = settings.themePreset.isChristmas
    val surfaceColor = if (isChristmasTheme) {
        Color(0xBA06140F)
    } else {
        ObsidianGlass.copy(alpha = settings.cardAlpha.coerceIn(0.66f, 0.90f))
    }
    val dialogContainerColor = Color(0xFF091B14)
    val clipboard = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var showId by remember { mutableStateOf(true) }
    var idCopied by remember { mutableStateOf(false) }
    var editingAlias by remember { mutableStateOf(false) }
    var aliasDraft by remember(settings.userAlias) { mutableStateOf(settings.userAlias) }
    var showActivationDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var activationUsePersonalUser by remember { mutableStateOf(false) }
    var personalUserInput by remember { mutableStateOf(settings.personalTunnelUsername) }
    var personalSecretInput by remember { mutableStateOf("") }

    LaunchedEffect(idCopied) {
        if (idCopied) {
            delay(1800)
            idCopied = false
        }
    }

    val vpnStatus = uiState.telemetry.status
    val vpnStatusColor = when (vpnStatus) {
        VpnConnectionStatus.CONNECTED -> CyberEmerald
        VpnConnectionStatus.CONNECTING -> PulseAmber
        VpnConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        VpnConnectionStatus.DISCONNECTED -> ChampagneGold
    }
    val vpnStatusLabel = when (vpnStatus) {
        VpnConnectionStatus.CONNECTED -> "Online"
        VpnConnectionStatus.CONNECTING -> "Cifrando"
        VpnConnectionStatus.ERROR -> "Error"
        VpnConnectionStatus.DISCONNECTED -> "Listo"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = settings.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = accentColor,
            modifier = Modifier.alpha(0.55f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SubScreenTopBar(
                    title = "Mi Perfil",
                    subtitle = "CREDENCIAL VIP // LICENCIA",
                    onBack = onBack,
                    titleColor = ChampagneGoldBright
                )

                // 1. Luxury VIP Identity Hero Card
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = surfaceColor,
                    border = BorderStroke(
                        1.1.dp,
                        Brush.linearGradient(
                            listOf(
                                ChampagneGoldBright.copy(alpha = 0.72f),
                                tierColor.copy(alpha = 0.55f),
                                CranberryRed.copy(alpha = 0.42f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ChampagneGold.copy(alpha = 0.16f),
                                            tierColor.copy(alpha = 0.09f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.5f, 76.dp.toPx()),
                                        radius = size.maxDimension * 0.55f
                                    ),
                                    center = Offset(size.width * 0.5f, 76.dp.toPx()),
                                    radius = size.maxDimension * 0.55f
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                ChampagneGoldBright.copy(alpha = 0.95f),
                                                tierColor.copy(alpha = 0.85f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CranberryRed.copy(alpha = 0.24f),
                                        border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.65f))
                                    ) {
                                        Text(
                                            text = "✦ PERFIL OFICIAL",
                                            style = MonoTypography.terminal.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.7.sp
                                            ),
                                            color = ChampagneGoldBright,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tierColor.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.68f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.WorkspacePremium,
                                                contentDescription = null,
                                                tint = tierColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = activeTier.badgeLabel,
                                                style = MonoTypography.terminal.copy(
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = tierColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Avatar with outer aura ring, verified dot, and signature Santa Hat
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.dp,
                                                ChampagneGold.copy(alpha = 0.34f),
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(98.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(Color(0xFF14382A), ObsidianGlassElevated, Color(0xFF040D09))
                                                )
                                            )
                                            .border(
                                                3.dp,
                                                Brush.sweepGradient(
                                                    listOf(
                                                        ChampagneGoldBright,
                                                        tierColor,
                                                        CranberryRed,
                                                        ChampagneGoldBright
                                                    )
                                                ),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = settings.userAlias.firstOrNull()?.uppercase() ?: "J",
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = ChampagneGoldBright
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = ObsidianGlassElevated,
                                        border = BorderStroke(1.5.dp, vpnStatusColor),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-4).dp, y = (-4).dp)
                                            .size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = null,
                                                tint = vpnStatusColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    LuxuryChristmasHat(
                                        baseTiltDegrees = -18f,
                                        animated = true,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-8).dp, y = (-26).dp)
                                            .size(66.dp)
                                            .testTag("profile_avatar_christmas_hat")
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${settings.userCountry.flagEmoji} ${settings.userAlias}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = FrostIce,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { editingAlias = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Editar alias",
                                            tint = ChampagneGoldBright,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0x88071913),
                                    border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.28f))
                                ) {
                                    Text(
                                        text = if (!authenticatedEmail.isNullOrBlank()) {
                                            "Región oficial: ${settings.userCountry.titleName} • $authenticatedEmail"
                                        } else {
                                            "Región oficial: ${settings.userCountry.titleName}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FrostIce.copy(alpha = 0.84f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Device ID Credential Capsule
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xCC030B08),
                                    border = BorderStroke(
                                        1.dp,
                                        if (idCopied) CyberEmerald.copy(alpha = 0.72f)
                                        else ChampagneGold.copy(alpha = 0.34f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(7.dp),
                                                color = ChampagneGold.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "ID",
                                                    style = MonoTypography.terminal.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = ChampagneGoldBright,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = if (showId) settings.deviceId else "••••••••••••••••",
                                                style = MonoTypography.address.copy(
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = FrostIce,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { showId = !showId },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showId) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Mostrar u ocultar ID",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(settings.deviceId))
                                                    idCopied = true
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (idCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                    contentDescription = "Copiar ID",
                                                    tint = if (idCopied) CyberEmerald else ChampagneGoldBright,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Account KPI Bento Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileKpiTile(
                        label = "ESTADO VPN",
                        value = vpnStatusLabel,
                        accent = vpnStatusColor,
                        surfaceColor = surfaceColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ProfileKpiTile(
                        label = "NODOS VIP",
                        value = "${uiState.profiles.size}",
                        accent = ChampagneGoldBright,
                        surfaceColor = surfaceColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ProfileKpiTile(
                        label = "CUOTA PAÍS",
                        value = "${activeTier.companyQuotaPerCountry} CÍAS",
                        accent = tierColor,
                        surfaceColor = surfaceColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // 3. VIP Digital Membership Pass Card
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SUSCRIPCIÓN Y PLAN ACTIVO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ChampagneGold.copy(alpha = 0.82f),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    tierColor.copy(alpha = 0.78f),
                                    ChampagneGoldBright.copy(alpha = 0.65f),
                                    tierColor.copy(alpha = 0.45f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(0xD90A2018),
                                        Color(0xD406130E),
                                        Color(0xD91A1508)
                                    )
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                tierColor.copy(alpha = 0.95f),
                                                ChampagneGoldBright.copy(alpha = 0.90f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = tierColor.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, tierColor.copy(alpha = 0.62f)),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = tierColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = activeTier.displayName,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = FrostIce,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = activeTier.featuresSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FrostIce.copy(alpha = 0.74f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tierColor.copy(alpha = 0.20f),
                                        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.65f))
                                    ) {
                                        Text(
                                            text = activeTier.badgeLabel,
                                            style = MonoTypography.terminal.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = tierColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                if (settings.personalTunnelUsername.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CyberEmerald.copy(alpha = 0.14f),
                                        border = BorderStroke(0.8.dp, CyberEmerald.copy(alpha = 0.50f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = CyberEmerald,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Cliente personal activo: ${settings.personalTunnelUsername}",
                                                style = MonoTypography.terminal.copy(
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = CyberEmerald,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Sleek gradient membership validity bar
                                val progressFrac = subscription.progressFraction().coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x66020806))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(if (progressFrac <= 0f) 0.06f else progressFrac)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(tierColor, ChampagneGoldBright)
                                                    )
                                                )
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (subscription.expiresAt > 0L) {
                                                "Vence: ${dateFormat.format(Date(subscription.expiresAt))}"
                                            } else {
                                                "Vencimiento: Sin límite"
                                            },
                                            style = MonoTypography.terminal,
                                            color = FrostIce.copy(alpha = 0.78f)
                                        )
                                        Text(
                                            text = if (subscription.expiresAt > 0L) {
                                                "${subscription.remainingDays()} día(s) restantes"
                                            } else {
                                                PlanTier.FREE.badgeLabel
                                            },
                                            style = MonoTypography.terminal.copy(fontWeight = FontWeight.Bold),
                                            color = tierColor
                                        )
                                    }
                                }

                                // 2x2 VIP Benefits Matrix
                                val footballUnlocked = activeTier.includesLiveFootball
                                val channelsUnlocked = activeTier.includesChannels
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ProfilePerkChip(
                                            icon = Icons.Rounded.SportsSoccer,
                                            title = if (footballUnlocked) "Fútbol VIP: ACTIVO" else "Fútbol: PREMIUM+",
                                            unlocked = footballUnlocked,
                                            activeTint = Color(PlanTier.PREMIUM.colorHex),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfilePerkChip(
                                            icon = Icons.Rounded.LiveTv,
                                            title = if (channelsUnlocked) "Canales TV: ACTIVO" else "Canales: PRO+",
                                            unlocked = channelsUnlocked,
                                            activeTint = Color(PlanTier.PRO.colorHex),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ProfilePerkChip(
                                            icon = Icons.Rounded.Public,
                                            title = "${activeTier.companyQuotaPerCountry} Cías por País",
                                            unlocked = true,
                                            activeTint = ChampagneGoldBright,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfilePerkChip(
                                            icon = Icons.Rounded.Bolt,
                                            title = "Motor ${uiState.selectedProfile?.protocol ?: "BHTTP"}",
                                            unlocked = true,
                                            activeTint = accentColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // 5-Tier VIP Roadmap Strip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PlanTier.entries.forEach { tier ->
                                        val isCurrent = tier == activeTier
                                        val unlocked = activeTier.unlocksServerTier(tier)
                                        val chipColor = Color(tier.colorHex)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurrent) chipColor.copy(alpha = 0.24f) else Color(0x66030B08),
                                            border = BorderStroke(
                                                width = if (isCurrent) 1.3.dp else 0.8.dp,
                                                color = if (isCurrent) ChampagneGoldBright
                                                else if (unlocked) chipColor.copy(alpha = 0.45f)
                                                else OutlineSubtle
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onClearActivationMessage()
                                                    showActivationDialog = true
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = if (tier == PlanTier.SUPER_PRO) "S.PRO" else tier.badgeLabel,
                                                    style = MonoTypography.terminal.copy(
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (unlocked) chipColor else TextMuted,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${tier.companyQuotaPerCountry} cías",
                                                    style = MonoTypography.terminal.copy(fontSize = 7.5.sp),
                                                    color = if (isCurrent) ChampagneGoldBright else FrostIce.copy(alpha = 0.65f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        onClearActivationMessage()
                                        showActivationDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tierColor,
                                        contentColor = ObsidianVoid
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("open_activate_plan_button")
                                ) {
                                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Activar Plan, Usuario o ID", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. Dedicated VPS Node Card
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "NODO VPS CONFIGURADO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ChampagneGold.copy(alpha = 0.82f),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = surfaceColor,
                        border = BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.52f),
                                    ChampagneGold.copy(alpha = 0.42f),
                                    OutlineSubtle
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                accentColor.copy(alpha = 0.85f),
                                                ChampagneGoldBright.copy(alpha = 0.80f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            Brush.verticalGradient(listOf(ChampagneGoldBright, accentColor))
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shield,
                                                contentDescription = null,
                                                tint = ChampagneGoldBright,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = uiState.selectedProfile?.vpsName ?: "VPSpremiumPRO",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = FrostIce
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentColor.copy(alpha = 0.16f),
                                            border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.55f))
                                        ) {
                                            Text(
                                                text = "ACTIVO",
                                                style = MonoTypography.terminal.copy(
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${uiState.profiles.size} nodo(s) listos • Protocolo BHTTP (Tigo :8080 / Claro :80)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FrostIce.copy(alpha = 0.78f)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x88030A07),
                                            border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "IP: ${uiState.selectedProfile?.host ?: "192.6.154.212"}",
                                                style = MonoTypography.terminal.copy(fontWeight = FontWeight.SemiBold),
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        val selectedCountry = CountryRegion.fromName(
                                            uiState.selectedProfile?.country ?: settings.userCountry.name
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x88030A07),
                                            border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.30f))
                                        ) {
                                            Text(
                                                text = "${selectedCountry.flagEmoji} ${selectedCountry.displayName}",
                                                style = MonoTypography.terminal,
                                                color = ChampagneGoldBright,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4.5. Google Cloud Account Linking (for real-time Plan por ID)
                val googleLinked = !authenticatedEmail.isNullOrBlank()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceColor,
                    border = BorderStroke(
                        1.dp,
                        if (googleLinked) CyberEmerald.copy(alpha = 0.62f) else ChampagneGold.copy(alpha = 0.40f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (googleLinked) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = if (googleLinked) CyberEmerald else ChampagneGoldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (googleLinked) "Google Vinculado (Planes en la nube)" else "Vincular Google (Planes por ID)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (googleLinked) CyberEmerald else ChampagneGoldBright
                                )
                                Text(
                                    text = if (googleLinked) {
                                        authenticatedEmail.orEmpty()
                                    } else {
                                        "Vincula tu cuenta de Google para recibir planes activados por ID al instante."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FrostIce.copy(alpha = 0.78f)
                                )
                            }
                        }

                        if (!googleLinkError.isNullOrBlank()) {
                            Text(
                                text = googleLinkError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedButton(
                            onClick = onLinkGoogleClick,
                            enabled = !isLinkingGoogle,
                            border = BorderStroke(
                                1.dp,
                                if (googleLinked) CyberEmerald.copy(alpha = 0.65f) else ChampagneGoldBright.copy(alpha = 0.75f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_link_google_button")
                        ) {
                            if (isLinkingGoogle) {
                                CircularProgressIndicator(
                                    color = ChampagneGoldBright,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vinculando con Google…", color = ChampagneGoldBright)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = if (googleLinked) CyberEmerald else ChampagneGoldBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (googleLinked) "Cambiar cuenta Google vinculada" else "Vincular cuenta Google",
                                    color = if (googleLinked) CyberEmerald else ChampagneGoldBright,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5. Sign out / switch account button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showSignOutDialog = true }
                        .testTag("sign_out_account_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cambiar cuenta / Cerrar sesión",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val luxuryFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ChampagneGoldBright,
        unfocusedBorderColor = ChampagneGold.copy(alpha = 0.35f),
        focusedLabelColor = ChampagneGoldBright,
        unfocusedLabelColor = FrostIce.copy(alpha = 0.72f),
        focusedTextColor = FrostIce,
        unfocusedTextColor = FrostIce,
        cursorColor = ChampagneGoldBright
    )

    if (editingAlias) {
        AlertDialog(
            onDismissRequest = { editingAlias = false },
            containerColor = dialogContainerColor,
            title = {
                Text(
                    "Editar nombre de perfil",
                    color = ChampagneGoldBright,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = aliasDraft,
                    onValueChange = { aliasDraft = it },
                    singleLine = true,
                    label = { Text("Alias") },
                    colors = luxuryFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAlias(aliasDraft)
                        editingAlias = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGoldBright,
                        contentColor = ObsidianVoid
                    )
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editingAlias = false }) {
                    Text("Cancelar", color = FrostIce.copy(alpha = 0.85f))
                }
            }
        )
    }

    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            containerColor = dialogContainerColor,
            title = {
                Text(
                    "Activar Suscripción",
                    color = ChampagneGoldBright,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isPersonal = activationUsePersonalUser
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (!isPersonal) ChampagneGold.copy(alpha = 0.18f) else ObsidianVoid,
                            border = BorderStroke(1.dp, if (!isPersonal) ChampagneGoldBright else OutlineSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onClearActivationMessage()
                                    activationUsePersonalUser = false
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Código / ID",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (!isPersonal) ChampagneGoldBright else TextMuted
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPersonal) ChampagneGold.copy(alpha = 0.18f) else ObsidianVoid,
                            border = BorderStroke(1.dp, if (isPersonal) ChampagneGoldBright else OutlineSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onClearActivationMessage()
                                    activationUsePersonalUser = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Usuario Personal",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isPersonal) ChampagneGoldBright else TextMuted
                                )
                            }
                        }
                    }

                    if (!activationUsePersonalUser) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xCC030B08),
                            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Tu ID para activación directa", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(settings.deviceId, style = MonoTypography.address, color = ChampagneGoldBright)
                                }
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(settings.deviceId)) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar ID", tint = ChampagneGoldBright)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it },
                            label = { Text("Código de Activación") },
                            placeholder = { Text("JFF-PRM-20261231-ALL-XXXX") },
                            singleLine = true,
                            colors = luxuryFieldColors,
                            modifier = Modifier.fillMaxWidth().testTag("redeem_code_input")
                        )

                        OutlinedButton(
                            onClick = onSyncById,
                            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.65f)),
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("sync_by_id_button")
                        ) {
                            Text("Sincronizar plan asignado a mi ID", color = ChampagneGoldBright)
                        }

                        if (authenticatedEmail.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = onLinkGoogleClick,
                                enabled = !isLinkingGoogle,
                                border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = CyberEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isLinkingGoogle) "Vinculando…" else "Vincular Google para sincronizar",
                                    color = CyberEmerald
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = personalUserInput,
                            onValueChange = { personalUserInput = it },
                            label = { Text("Usuario de cliente personal") },
                            placeholder = { Text("Ej: juan_vip") },
                            singleLine = true,
                            colors = luxuryFieldColors,
                            modifier = Modifier.fillMaxWidth().testTag("profile_personal_user_input")
                        )

                        OutlinedTextField(
                            value = personalSecretInput,
                            onValueChange = { personalSecretInput = it },
                            label = { Text("Clave / Contraseña (si aplica)") },
                            placeholder = { Text("Opcional si es acceso directo") },
                            singleLine = true,
                            colors = luxuryFieldColors,
                            modifier = Modifier.fillMaxWidth().testTag("profile_personal_secret_input")
                        )
                    }

                    if (activationMessage != null) {
                        val (ok, msg) = activationMessage
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) accentColor else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (!activationUsePersonalUser) {
                    Button(
                        onClick = { onRedeemCode(codeInput) },
                        enabled = codeInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneGoldBright,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("redeem_code_button")
                    ) {
                        Text("Activar Código", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            onRedeemPersonalUsername(personalUserInput, personalSecretInput.ifBlank { null })
                        },
                        enabled = personalUserInput.trim().length >= 3,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneGoldBright,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("redeem_personal_user_button")
                    ) {
                        Text("Activar Usuario", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("Cerrar", color = FrostIce.copy(alpha = 0.85f))
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = dialogContainerColor,
            title = {
                Text(
                    "Cambiar cuenta",
                    color = FrostIce,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Quieres volver a la pantalla de Iniciar sesión / Crear cuenta? Tus servidores y Device ID se conservarán.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostIce.copy(alpha = 0.80f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Cerrar sesión", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar", color = FrostIce.copy(alpha = 0.85f))
                }
            }
        )
    }
}

@Composable
private fun ProfileKpiTile(
    label: String,
    value: String,
    accent: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = label,
                    style = MonoTypography.terminal.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = TextMuted,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfilePerkChip(
    icon: ImageVector,
    title: String,
    unlocked: Boolean,
    activeTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (unlocked) activeTint.copy(alpha = 0.14f) else OutlineSubtle.copy(alpha = 0.25f),
        border = BorderStroke(
            0.9.dp,
            if (unlocked) activeTint.copy(alpha = 0.56f) else OutlineSubtle
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (unlocked) icon else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (unlocked) activeTint else TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                style = MonoTypography.terminal.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (unlocked) activeTint else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    trailing: @Composable (() -> Unit)? = null
) {
    val accentColor = MaterialTheme.colorScheme.secondary
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianGlass,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(ChampagneGold.copy(alpha = 0.48f), accentColor.copy(alpha = 0.28f), ChampagneGold.copy(alpha = 0.36f))
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, ChampagneGoldBright.copy(alpha = 0.85f), accentColor.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianGlassElevated,
                        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.42f)),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = ChampagneGoldBright
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MonoTypography.terminal,
                                color = ChampagneGold.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                trailing?.invoke()
            }
        }
    }
}
