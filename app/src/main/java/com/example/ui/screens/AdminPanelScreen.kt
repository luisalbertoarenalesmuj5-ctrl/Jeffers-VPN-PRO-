package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.LicenseRecordEntity
import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import com.example.data.model.CountryRegion
import com.example.data.model.PlanTier
import com.example.data.model.TunnelProtocol
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.vpn.LicenseCodeEngine
import com.example.vpn.VpsAnalysisReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    uiState: VpnDashboardUiState,
    lastGeneratedLicense: LicenseRecordEntity?,
    isAnalyzingVps: Boolean,
    vpsAnalysisReport: VpsAnalysisReport?,
    isCloudAdmin: Boolean = false,
    authenticatedEmail: String? = null,
    adminFeedback: Pair<Boolean, String>? = null,
    onGenerateCode: (tier: PlanTier, days: Int, note: String) -> Unit,
    onActivateById: (tier: PlanTier, days: Int, targetId: String, note: String) -> Unit,
    onCreatePersonalClient: (tier: PlanTier, days: Int, clientUsername: String, clientPassword: String, note: String) -> Unit,
    onExtendLicense: (LicenseRecordEntity, Int) -> Unit,
    onToggleRevoke: (LicenseRecordEntity) -> Unit,
    onDeleteLicense: (Long) -> Unit,
    onChangeAdminPassword: (String) -> Unit,
    onSaveProfile: (
        existingId: Long,
        profileName: String,
        vpsName: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        carrier: CarrierType,
        protocol: TunnelProtocol,
        country: CountryRegion,
        tier: PlanTier,
        sniHost: String,
        bhttpPayload: String,
        selectImmediately: Boolean
    ) -> Unit,
    onSelectProfile: (VpnProfileEntity) -> Unit,
    onDeleteProfile: (VpnProfileEntity) -> Unit,
    onRunVpsAnalysis: (
        host: String,
        port: Int,
        vpsName: String,
        password: String,
        sniHost: String,
        bhttpPayload: String
    ) -> Unit,
    onSyncCloudLicenses: () -> Unit = {},
    onClearAdminFeedback: () -> Unit = {},
    isLinkingGoogle: Boolean = false,
    onLinkGoogleClick: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = ObsidianGlass.copy(alpha = (uiState.settings.cardAlpha * 0.90f).coerceIn(0.68f, 0.92f))
    val clipboard = LocalClipboardManager.current

    var adminSection by remember { mutableIntStateOf(0) } // 0 = Licencias, 1 = Servidores, 2 = Análisis VPS
    var analysisSeedProfile by remember { mutableStateOf<VpnProfileEntity?>(null) }
    var serverPrefillHost by remember { mutableStateOf<String?>(null) }
    var serverPrefillPort by remember { mutableStateOf<Int?>(null) }
    var serverPrefillVps by remember { mutableStateOf<String?>(null) }
    var serverPrefillPass by remember { mutableStateOf<String?>(null) }
    var serverPrefillSni by remember { mutableStateOf<String?>(null) }

    var modeTab by remember { mutableIntStateOf(0) } // 0 = Por Código, 1 = Por ID, 2 = Por Usuario
    var selectedTier by remember { mutableStateOf(PlanTier.PREMIUM) }
    var durationDaysText by remember { mutableStateOf("30") }
    var targetDeviceId by remember { mutableStateOf("") }
    var clientUsernameInput by remember { mutableStateOf("") }
    var clientPasswordInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val parsedDays = durationDaysText.toIntOrNull()?.coerceIn(1, 3650) ?: 30
    val previewExpiryMillis = remember(parsedDays) {
        LicenseCodeEngine.computeExpirationForDays(parsedDays)
    }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = uiState.settings.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.alpha(0.42f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SubScreenTopBar(
                    title = "Panel Admin",
                    subtitle = "ADMIN // LICENCIAS, SERVIDORES & VPS",
                    onBack = onBack,
                    trailing = {
                        OutlinedButton(
                            onClick = { showChangePasswordDialog = true },
                            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                        Icon(Icons.Rounded.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clave", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            // Top Admin 3-Section Switcher: Licencias | Servidores | Análisis VPS
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = adminSection == 0,
                    onClick = { adminSection = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_tab_licenses")
                ) {
                    Text("Licencias", maxLines = 1)
                }
                SegmentedButton(
                    selected = adminSection == 1,
                    onClick = { adminSection = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_tab_servers")
                ) {
                    Text("Servidores", maxLines = 1)
                }
                SegmentedButton(
                    selected = adminSection == 2,
                    onClick = { adminSection = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_tab_analysis")
                ) {
                    Text("Análisis VPS", maxLines = 1)
                }
            }

            if (adminSection == 1) {
                AdminServersSection(
                    uiState = uiState,
                    prefillHost = serverPrefillHost,
                    prefillPort = serverPrefillPort,
                    prefillVpsName = serverPrefillVps,
                    prefillPassword = serverPrefillPass,
                    prefillSni = serverPrefillSni,
                    onClearPrefill = {
                        serverPrefillHost = null
                        serverPrefillPort = null
                        serverPrefillVps = null
                        serverPrefillPass = null
                        serverPrefillSni = null
                    },
                    onSaveProfile = onSaveProfile,
                    onSelectProfile = onSelectProfile,
                    onDeleteProfile = onDeleteProfile,
                    onAnalyzeServer = { profile ->
                        analysisSeedProfile = profile
                        adminSection = 2
                        onRunVpsAnalysis(
                            profile.host,
                            profile.port,
                            profile.vpsName,
                            profile.password,
                            profile.sniHost,
                            profile.bhttpPayload
                        )
                    }
                )
            } else if (adminSection == 2) {
                AdminVpsAnalysisSection(
                    uiState = uiState,
                    initialProfile = analysisSeedProfile,
                    isAnalyzing = isAnalyzingVps,
                    report = vpsAnalysisReport,
                    onRunAnalysis = onRunVpsAnalysis,
                    onCreateServerFromAnalysis = { h, p, vps, pass, sni ->
                        serverPrefillHost = h
                        serverPrefillPort = p
                        serverPrefillVps = vps
                        serverPrefillPass = pass
                        serverPrefillSni = sni
                        adminSection = 1
                    }
                )
            } else {

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isCloudAdmin) Color(0x2210B981) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isCloudAdmin) Color(0x8810B981) else MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isCloudAdmin) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = if (isCloudAdmin) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isCloudAdmin) "Sincronización Cloud Admin ACTIVA" else "Cuenta sin permisos Cloud Admin",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isCloudAdmin) Color(0xFF34D399) else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isCloudAdmin) {
                                    "${authenticatedEmail ?: "Admin Google"} • Los planes por ID llegan al instante."
                                } else {
                                    "Sesión actual: ${authenticatedEmail ?: "sin cuenta"}. Usa la cuenta Google Admin para enviar planes en la nube."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isCloudAdmin) {
                        IconButton(
                            onClick = onSyncCloudLicenses,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sincronizar licencias",
                                tint = Color(0xFF34D399)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onLinkGoogleClick,
                            enabled = !isLinkingGoogle,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = if (isLinkingGoogle) "..." else "Vincular",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (adminFeedback != null) {
                val (ok, feedbackText) = adminFeedback
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (ok) Color(0x2210B981) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (ok) Color(0xAA10B981) else MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClearAdminFeedback)
                ) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (ok) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Mode switcher: Por Código vs Por ID vs Por Usuario
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = modeTab == 0,
                    onClick = { modeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_license_mode_code")
                ) {
                    Text("Código", maxLines = 1)
                }
                SegmentedButton(
                    selected = modeTab == 1,
                    onClick = { modeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_license_mode_id")
                ) {
                    Text("Por ID", maxLines = 1)
                }
                SegmentedButton(
                    selected = modeTab == 2,
                    onClick = { modeTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("admin_license_mode_user")
                ) {
                    Text("Usuario", maxLines = 1)
                }
            }

            // Generator / Activator Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, OutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = primaryColor)
                        Text(
                            text = when (modeTab) {
                                0 -> "Generar Código de Plan"
                                1 -> "Activar Plan por ID"
                                else -> "Crear Cliente Personal (Usuario)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text("Tipo de Plan", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlanTier.entries.forEach { tier ->
                            val tierColor = Color(tier.colorHex)
                            FilterChip(
                                selected = selectedTier == tier,
                                onClick = { selectedTier = tier },
                                label = { Text(tier.badgeLabel, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tierColor.copy(alpha = 0.22f),
                                    selectedLabelColor = tierColor
                                ),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("admin_plan_${tier.name.lowercase()}")
                            )
                        }
                    }

                    Text("Vencimiento y Fecha", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 7, 15, 30, 90, 365).forEach { days ->
                            FilterChip(
                                selected = parsedDays == days,
                                onClick = { durationDaysText = days.toString() },
                                label = { Text("${days}d") },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = durationDaysText,
                            onValueChange = { input -> durationDaysText = input.filter { it.isDigit() }.take(4) },
                            label = { Text("Días") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(105.dp)
                        )
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            border = BorderStroke(1.dp, OutlineSubtle),
                            modifier = Modifier.weight(1f).height(54.dp)
                        ) {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Vence: ${dateFormat.format(Date(previewExpiryMillis))}",
                                style = MonoTypography.terminal
                            )
                        }
                    }

                    if (modeTab == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = targetDeviceId,
                                onValueChange = { targetDeviceId = it },
                                label = { Text("ID del Usuario / Dispositivo") },
                                placeholder = { Text("Ej: ${uiState.settings.deviceId}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("admin_target_id_input")
                            )
                            OutlinedButton(
                                onClick = { targetDeviceId = uiState.settings.deviceId },
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("Mi ID", color = primaryColor)
                            }
                        }
                    } else if (modeTab == 2) {
                        OutlinedTextField(
                            value = clientUsernameInput,
                            onValueChange = { clientUsernameInput = it },
                            label = { Text("Usuario del cliente personal") },
                            placeholder = { Text("Ej: juan_vip o cliente_tigo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_client_username_input")
                        )
                        OutlinedTextField(
                            value = clientPasswordInput,
                            onValueChange = { clientPasswordInput = it },
                            label = { Text("Clave del cliente (opcional)") },
                            placeholder = { Text("Déjalo vacío para acceso directo por usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_client_password_input")
                        )
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota / Cliente (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isActionEnabled = when (modeTab) {
                        1 -> targetDeviceId.isNotBlank()
                        2 -> clientUsernameInput.trim().length >= 3
                        else -> true
                    }

                    Button(
                        onClick = {
                            when (modeTab) {
                                0 -> onGenerateCode(selectedTier, parsedDays, note)
                                1 -> onActivateById(selectedTier, parsedDays, targetDeviceId, note)
                                else -> {
                                    onCreatePersonalClient(
                                        selectedTier,
                                        parsedDays,
                                        clientUsernameInput,
                                        clientPasswordInput,
                                        note
                                    )
                                    clientUsernameInput = ""
                                    clientPasswordInput = ""
                                    note = ""
                                }
                            }
                        },
                        enabled = isActionEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(selectedTier.colorHex),
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_execute_button")
                    ) {
                        Icon(
                            imageVector = when (modeTab) {
                                0 -> Icons.Rounded.QrCode2
                                1 -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.Person
                            },
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (modeTab) {
                                0 -> "Generar Código ${selectedTier.badgeLabel}"
                                1 -> "Activar ${selectedTier.badgeLabel} por ID"
                                else -> "Crear Usuario Personal ${selectedTier.badgeLabel}"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Highlight recently generated code
            if (lastGeneratedLicense != null) {
                val tierColor = Color(PlanTier.fromName(lastGeneratedLicense.tier).colorHex)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tierColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, tierColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!lastGeneratedLicense.clientUsername.isNullOrBlank()) {
                                    "Cliente personal: ${lastGeneratedLicense.clientUsername} (${lastGeneratedLicense.tier})"
                                } else {
                                    "Último código generado (${lastGeneratedLicense.tier})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = tierColor
                            )
                            Text(
                                text = lastGeneratedLicense.code,
                                style = MonoTypography.address,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Vence: ${dateFormat.format(Date(lastGeneratedLicense.expiresAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(lastGeneratedLicense.code)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar código", tint = tierColor)
                        }
                    }
                }
            }

            Text(
                text = "CÓDIGOS Y ACTIVACIONES (${uiState.licenses.size})",
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor
            )

            if (uiState.licenses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No has generado códigos ni activado IDs todavía. Elige un plan (FREE, PRO, VIP, PREMIUM, SUPER PRO) y vencimiento arriba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                uiState.licenses.forEach { license ->
                    LicenseRecordCard(
                        license = license,
                        dateFormat = dateFormat,
                        surfaceColor = surfaceColor,
                        onCopy = { clipboard.setText(AnnotatedString(license.code)) },
                        onExtend = { onExtendLicense(license, 30) },
                        onToggleRevoke = { onToggleRevoke(license) },
                        onDelete = { onDeleteLicense(license.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            } // end Licencias tab
        }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = previewExpiryMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis
                    if (picked != null) {
                        val diffDays = ((picked - System.currentTimeMillis()) / 86_400_000L).toInt() + 1
                        durationDaysText = diffDays.coerceIn(1, 3650).toString()
                    }
                    showDatePicker = false
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showChangePasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        val isStrong = LicenseCodeEngine.isUltraStrongAdminPassword(newPass)
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Cambiar contraseña Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nivel ultra: mínimo 12 caracteres combinando mayúsculas, minúsculas, números y símbolos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isStrong || newPass.isEmpty()) TextMuted else MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Nueva contraseña ultra") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Confirmar contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isStrong && newPass == confirmPass,
                    onClick = {
                        onChangeAdminPassword(newPass)
                        showChangePasswordDialog = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun LicenseRecordCard(
    license: LicenseRecordEntity,
    dateFormat: SimpleDateFormat,
    surfaceColor: Color,
    onCopy: () -> Unit,
    onExtend: () -> Unit,
    onToggleRevoke: () -> Unit,
    onDelete: () -> Unit
) {
    val tier = PlanTier.fromName(license.tier)
    val tierColor = Color(tier.colorHex)
    val now = System.currentTimeMillis()
    val isExpired = license.expiresAt < now
    val statusLabel = when {
        license.isRevoked -> "REVOCADO"
        isExpired -> "VENCIDO"
        else -> "ACTIVO"
    }
    val statusColor = when {
        license.isRevoked -> MaterialTheme.colorScheme.error
        isExpired -> TextMuted
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = tierColor.copy(alpha = 0.2f)) {
                        Text(
                            text = tier.badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tierColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.14f)) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = "Vence: ${dateFormat.format(Date(license.expiresAt))}",
                    style = MonoTypography.terminal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = license.code,
                        style = MonoTypography.address,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!license.clientUsername.isNullOrBlank()) {
                        Text(
                            text = "Usuario personal: ${license.clientUsername}" +
                                if (!license.clientPassword.isNullOrBlank()) " (con clave)" else " (acceso directo)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tierColor
                        )
                    }
                    Text(
                        text = buildString {
                            append(if (license.boundDeviceId != null) "ID: ${license.boundDeviceId}" else "ID: Libre (cualquier dispositivo)")
                            if (license.note.isNotBlank()) append(" • ${license.note}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar", tint = tierColor, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExtend,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+30d", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onToggleRevoke,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (license.isRevoked) "Reactivar" else "Revocar", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
