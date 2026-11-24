package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import com.example.data.model.CountryRegion
import com.example.data.model.PlanTier
import com.example.data.model.ServerStatus
import com.example.data.model.TunnelProtocol
import com.example.data.repository.VpnRepository
import com.example.ui.VpnDashboardUiState
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.vpn.BhttpPayloadBuilder
import com.example.vpn.VpsAnalysisReport

@Composable
fun AdminServersSection(
    uiState: VpnDashboardUiState,
    prefillHost: String?,
    prefillPort: Int?,
    prefillVpsName: String?,
    prefillPassword: String?,
    prefillSni: String?,
    onClearPrefill: () -> Unit,
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
    onAnalyzeServer: (VpnProfileEntity) -> Unit,
    onTestProfile: (VpnProfileEntity) -> Unit = {},
    onVerifyAllServers: () -> Unit = {},
    onSwitchProfileProtocol: (VpnProfileEntity, TunnelProtocol) -> Unit = { _, _ -> },
    onEnableHttpConnectAll: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = uiState.settings.cardAlpha)

    var editingProfileId by remember { mutableStateOf(0L) }
    var country by remember { mutableStateOf(CountryRegion.GENERAL) }
    var tier by remember { mutableStateOf(PlanTier.FREE) }
    var carrier by remember { mutableStateOf(CarrierType.TIGO) }
    var protocol by remember { mutableStateOf(TunnelProtocol.BHTTP) }
    var profileName by remember(prefillHost, prefillPort) {
        mutableStateOf(
            if (prefillHost != null) "${prefillVpsName ?: "VPSpremiumPRO"} • ${prefillPort ?: 8080}"
            else ""
        )
    }
    var vpsName by remember(prefillVpsName) { mutableStateOf(prefillVpsName ?: "VPSpremiumPRO") }
    var host by remember(prefillHost) { mutableStateOf(prefillHost ?: "192.6.154.212") }
    var portText by remember(prefillPort) { mutableStateOf((prefillPort ?: 8080).toString()) }
    var username by remember { mutableStateOf(VpnRepository.DEFAULT_SSH_USER) }
    var password by remember(prefillPassword) {
        mutableStateOf(prefillPassword ?: VpnRepository.DEFAULT_VPS_PASSWORD)
    }
    var sniHost by remember(prefillSni) { mutableStateOf(prefillSni ?: CarrierType.TIGO.defaultSniHost) }
    var bhttpPayload by remember { mutableStateOf(BhttpPayloadBuilder.defaultPayloadFor(CarrierType.TIGO)) }
    var selectImmediately by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf<String?>(null) }

    fun loadForEdit(profile: VpnProfileEntity) {
        editingProfileId = profile.id
        country = CountryRegion.fromName(profile.country)
        tier = PlanTier.fromName(profile.tier)
        carrier = CarrierType.fromName(profile.carrier)
        protocol = TunnelProtocol.fromName(profile.protocol)
        profileName = profile.profileName
        vpsName = profile.vpsName
        host = profile.host
        portText = profile.port.toString()
        username = profile.username
        password = profile.password
        sniHost = profile.sniHost
        bhttpPayload = profile.bhttpPayload
        selectImmediately = profile.isSelected
        savedBanner = null
    }

    fun resetForm() {
        editingProfileId = 0L
        country = CountryRegion.GENERAL
        tier = PlanTier.FREE
        carrier = CarrierType.TIGO
        protocol = TunnelProtocol.BHTTP
        profileName = ""
        vpsName = "VPSpremiumPRO"
        host = "192.6.154.212"
        portText = "8080"
        username = VpnRepository.DEFAULT_SSH_USER
        password = VpnRepository.DEFAULT_VPS_PASSWORD
        sniHost = CarrierType.TIGO.defaultSniHost
        bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(CarrierType.TIGO)
        selectImmediately = true
        onClearPrefill()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = primaryColor)
                        Text(
                            text = if (editingProfileId == 0L) "Crear Servidor en el Panel" else "Editar Servidor #$editingProfileId",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (editingProfileId != 0L || prefillHost != null) {
                        TextButton(onClick = ::resetForm) {
                            Text("Limpiar")
                        }
                    }
                }

                Text("País / Región", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountryRegion.entries.forEach { region ->
                        FilterChip(
                            selected = country == region,
                            onClick = { country = region },
                            label = { Text("${region.flagEmoji} ${region.displayName}") },
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }

                Text("Plan del Servidor", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanTier.entries.forEach { item ->
                        val tierColor = Color(item.colorHex)
                        FilterChip(
                            selected = tier == item,
                            onClick = { tier = item },
                            label = { Text(item.badgeLabel, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tierColor.copy(alpha = 0.22f),
                                selectedLabelColor = tierColor
                            ),
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }

                Text("Operadora y Puerto (${country.titleName})", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                val orderedCarriers = remember(country) {
                    (country.carriers + CarrierType.entries).distinct()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    orderedCarriers.forEach { item ->
                        FilterChip(
                            selected = carrier == item,
                            onClick = {
                                carrier = item
                                portText = item.defaultPort.toString()
                                sniHost = country.regionalSniFor(item)
                                if (editingProfileId == 0L) {
                                    bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(item)
                                }
                            },
                            label = { Text("${item.displayName} (${item.defaultPort})") },
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }

                Text("Protocolo", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TunnelProtocol.entries.forEach { item ->
                        FilterChip(
                            selected = protocol == item,
                            onClick = { protocol = item },
                            label = { Text(item.displayName) },
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Nombre del Servidor / Perfil") },
                    placeholder = { Text("${vpsName.ifBlank { "VPSpremiumPRO" }} • ${carrier.displayName} $portText") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_server_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = vpsName,
                        onValueChange = { vpsName = it },
                        label = { Text("Nombre VPS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                        label = { Text("Puerto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp).testTag("admin_server_port_input")
                    )
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP o Dominio del VPS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_server_host_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario VPS / SSH (ej: root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña VPS") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Mostrar u ocultar contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sniHost,
                    onValueChange = { sniHost = it },
                    label = { Text("Host SNI / Bug Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payload BHTTP", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    TextButton(onClick = { bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(carrier) }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restablecer")
                    }
                }

                OutlinedTextField(
                    value = bhttpPayload,
                    onValueChange = { bhttpPayload = it },
                    label = { Text("Plantilla BHTTP") },
                    minLines = 3,
                    maxLines = 5,
                    textStyle = MonoTypography.terminal,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activar como servidor principal al guardar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = selectImmediately,
                        onCheckedChange = { selectImmediately = it }
                    )
                }

                if (savedBanner != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = savedBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull()?.coerceIn(1, 65535) ?: carrier.defaultPort
                        val resolvedName = profileName.trim().ifBlank {
                            "${vpsName.ifBlank { "VPSpremiumPRO" }} • ${carrier.displayName} $parsedPort"
                        }
                        onSaveProfile(
                            editingProfileId,
                            resolvedName,
                            vpsName,
                            host,
                            parsedPort,
                            username,
                            password,
                            carrier,
                            protocol,
                            country,
                            tier,
                            sniHost,
                            bhttpPayload,
                            selectImmediately
                        )
                        savedBanner = if (editingProfileId == 0L) {
                            "Servidor '$resolvedName' creado en ${country.displayName} (${tier.badgeLabel})."
                        } else {
                            "Servidor '$resolvedName' actualizado."
                        }
                        resetForm()
                    },
                    enabled = host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("admin_save_server_button")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingProfileId == 0L) "Guardar Servidor en la App" else "Actualizar Servidor",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HttpConnectProxyDeployCard(
            uiState = uiState,
            primaryColor = primaryColor,
            accentColor = accentColor,
            surfaceColor = surfaceColor,
            onEnableHttpConnectAll = onEnableHttpConnectAll,
            onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SERVIDORES EN EL PANEL (${uiState.profiles.size})",
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor
            )
            OutlinedButton(
                onClick = onVerifyAllServers,
                enabled = !uiState.isVerifyingAllServers,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f)),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("admin_verify_all_servers_button")
            ) {
                if (uiState.isVerifyingAllServers) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = primaryColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Verificar todos los servidores",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isVerifyingAllServers) "Verificando…" else "Verificar Todos",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        uiState.profiles.forEach { profile ->
            val profileTier = PlanTier.fromName(profile.tier)
            val tierColor = Color(profileTier.colorHex)
            val region = CountryRegion.fromName(profile.country)
            val isSelected = profile.id == uiState.selectedProfile?.id
            val (srvStatus, srvVerification) = resolveEffectiveServerStatus(profile, uiState)
            val isCheckingThis = srvVerification?.isChecking == true || uiState.testingProfileId == profile.id

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = when {
                        srvStatus == ServerStatus.CONNECTED -> accentColor
                        isSelected -> primaryColor
                        srvStatus == ServerStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                        else -> OutlineSubtle
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${region.flagEmoji} ${profile.profileName}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${profile.username.ifBlank { "root" }}@${profile.host}:${profile.port} • ${profile.protocol} • ${profile.vpsName}",
                                style = MonoTypography.terminal,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ServerStatusBadge(
                                status = srvStatus,
                                isChecking = isCheckingThis,
                                accentColor = accentColor
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tierColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = profileTier.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    val statusDetail = when {
                        isCheckingThis -> "Probando IP, puerto, protocolo y credenciales…"
                        srvVerification != null -> srvVerification.detail
                        profile.lastLatencyMs != null -> "Última verificación: ${profile.lastLatencyMs} ms"
                        else -> "Sin verificar — toca Probar para validar conexión"
                    }
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            srvStatus == ServerStatus.ERROR -> MaterialTheme.colorScheme.error
                            srvStatus == ServerStatus.CONNECTED || srvVerification?.isReachable == true -> accentColor
                            else -> TextMuted
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSelectProfile(profile) },
                            border = BorderStroke(1.dp, if (isSelected) accentColor else OutlineSubtle),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (isSelected) accentColor else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSelected) "Elegido" else "Elegir", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { onTestProfile(profile) },
                            enabled = !isCheckingThis,
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            if (isCheckingThis) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = "Probar servidor",
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar", style = MaterialTheme.typography.labelSmall, color = accentColor)
                        }

                        OutlinedButton(
                            onClick = { onAnalyzeServer(profile) },
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Analytics, contentDescription = null, tint = primaryColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Analizar", style = MaterialTheme.typography.labelSmall, color = primaryColor)
                        }

                        IconButton(onClick = { loadForEdit(profile) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar servidor", tint = primaryColor)
                        }

                        if (uiState.profiles.size > 1) {
                            IconButton(onClick = { onDeleteProfile(profile) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar servidor", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (profile.protocol != TunnelProtocol.HTTP_CONNECT.name) {
                        OutlinedButton(
                            onClick = { onSwitchProfileProtocol(profile, TunnelProtocol.HTTP_CONNECT) },
                            enabled = !isCheckingThis,
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_switch_http_connect_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Http,
                                contentDescription = "Cambiar a HTTP CONNECT",
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cambiar este servidor a HTTP CONNECT",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HttpConnectProxyDeployCard(
    uiState: VpnDashboardUiState,
    primaryColor: Color,
    accentColor: Color,
    surfaceColor: Color,
    onEnableHttpConnectAll: () -> Unit,
    onCopyCommand: (String) -> Unit
) {
    val totalCount = uiState.profiles.size
    val httpConnectCount = uiState.profiles.count { it.protocol == TunnelProtocol.HTTP_CONNECT.name }
    var proxyFlavor by remember { mutableIntStateOf(0) } // 0 = SSH-Bridge (:8080/:80), 1 = Squid Full (:8080), 2 = Red VPS (NAT/Forward/FW)
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val selectedCommand = remember(proxyFlavor) {
        when (proxyFlavor) {
            0 -> VPS_HTTP_CONNECT_SSH_BRIDGE_CMD
            1 -> VPS_SQUID_CONNECT_CMD
            2 -> VPS_STUNNEL_443_CMD
            else -> VPS_NETWORK_REPAIR_CMD
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("http_connect_deploy_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Http, contentDescription = null, tint = accentColor)
                    Column {
                        Text(
                            text = "Proxy HTTP CONNECT en App y VPS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$httpConnectCount de $totalCount servidor(es) en modo HTTP CONNECT",
                            style = MonoTypography.terminal,
                            color = accentColor
                        )
                    }
                }
            }

            Text(
                text = "Activa HTTP CONNECT en tus perfiles guardados (sin borrar IPs ni contraseñas), o copia el comando de reparación de red VPS (IP forwarding, NAT MASQUERADE, firewall y DNS).",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Button(
                onClick = onEnableHttpConnectAll,
                enabled = totalCount > 0 && !uiState.isVerifyingAllServers,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = ObsidianVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_enable_http_connect_all_button")
            ) {
                Icon(Icons.Rounded.Http, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (httpConnectCount == totalCount && totalCount > 0) {
                        "Re-verificar HTTP CONNECT en los $totalCount VPS"
                    } else {
                        "Activar HTTP CONNECT en los $totalCount VPS"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = proxyFlavor == 0,
                    onClick = {
                        proxyFlavor = 0
                        copiedNotice = null
                    },
                    label = { Text("HTTP CONNECT→SSH (8080/80)") },
                    modifier = Modifier.height(44.dp)
                )
                FilterChip(
                    selected = proxyFlavor == 1,
                    onClick = {
                        proxyFlavor = 1
                        copiedNotice = null
                    },
                    label = { Text("Squid Completo (:8080)") },
                    modifier = Modifier.height(44.dp)
                )
                FilterChip(
                    selected = proxyFlavor == 2,
                    onClick = {
                        proxyFlavor = 2
                        copiedNotice = null
                    },
                    label = { Text("SSL/TLS Stunnel (:443)") },
                    modifier = Modifier.height(44.dp)
                )
                FilterChip(
                    selected = proxyFlavor == 3,
                    onClick = {
                        proxyFlavor = 3
                        copiedNotice = null
                    },
                    label = { Text("Reparar Red VPS (Forward/NAT/FW)") },
                    modifier = Modifier.height(44.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A1017),
                border = BorderStroke(1.dp, OutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedCommand,
                    style = MonoTypography.terminal.copy(fontSize = 10.5.sp),
                    color = Color(0xFFD6E4F0),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copiedNotice ?: "Pega el comando como root en SSH de cada VPS",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (copiedNotice != null) accentColor else TextMuted,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                OutlinedButton(
                    onClick = {
                        onCopyCommand(selectedCommand)
                        copiedNotice = "Comando copiado ✓"
                    },
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("copy_vps_proxy_script_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar script proxy VPS",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar comando", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                }
            }
        }
    }
}

private val VPS_HTTP_CONNECT_SSH_BRIDGE_CMD = """
sudo bash -c 'cat >/usr/local/bin/jeffers-proxy.py <<"PY"
import socket,threading
def pipe(a,b):
 try:
  while True:
   d=a.recv(8192)
   if not d:break
   b.sendall(d)
 except:pass
 finally:
  try:a.close()
  except:pass
  try:b.close()
  except:pass
def handle(c):
 try:
  req=b""
  while b"\r\n\r\n" not in req and len(req)<8192:
   ch=c.recv(1024)
   if not ch:break
   req+=ch
  text=req.decode("latin1","ignore")
  parts=text.split("\r\n",1)[0].split()
  if "upgrade: websocket" in text.lower():
   u=socket.create_connection(("127.0.0.1",22),10)
   c.sendall(b"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n")
   threading.Thread(target=pipe,args=(c,u),daemon=True).start()
   pipe(u,c);return
  if len(parts)>=2 and parts[0].upper()=="CONNECT":
   hp=parts[1].split(":")
   port=int(hp[1]) if len(hp)>1 else 22
   if port==22:
    u=socket.create_connection(("127.0.0.1",22),10)
    c.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
    threading.Thread(target=pipe,args=(c,u),daemon=True).start()
    pipe(u,c);return
  c.sendall(b"HTTP/1.1 403 Forbidden\r\n\r\n")
 except:pass
 try:c.close()
 except:pass
def serve(p):
 s=socket.socket();s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
 try:s.bind(("0.0.0.0",p));s.listen(128)
 except:return
 while True:
  c,_=s.accept()
  threading.Thread(target=handle,args=(c,),daemon=True).start()
t1=threading.Thread(target=serve,args=(8080,))
t2=threading.Thread(target=serve,args=(80,))
t1.start();t2.start();t1.join();t2.join()
PY
printf "[Unit]\nDescription=Jeffers HTTP CONNECT + WS Proxy\nAfter=network.target\n[Service]\nExecStart=/usr/bin/python3 /usr/local/bin/jeffers-proxy.py\nRestart=always\nRestartSec=2\n[Install]\nWantedBy=multi-user.target\n" >/etc/systemd/system/jeffers-proxy.service && systemctl daemon-reload && systemctl enable --now jeffers-proxy && systemctl restart jeffers-proxy && (ufw allow 8080/tcp 2>/dev/null; ufw allow 80/tcp 2>/dev/null; true) && systemctl status jeffers-proxy --no-pager'
""".trimIndent()

private val VPS_SQUID_CONNECT_CMD =
    "sudo bash -c 'apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y squid && printf \"http_port 8080\\nacl Safe_ports port 22 53 80 443\\nacl CONNECT method CONNECT\\nhttp_access allow CONNECT Safe_ports\\nhttp_access allow all\\n\" >/etc/squid/conf.d/jeffers-connect.conf && systemctl enable --now squid && systemctl restart squid && (ufw allow 8080/tcp 2>/dev/null; true) && systemctl status squid --no-pager'"

private val VPS_STUNNEL_443_CMD =
    "sudo bash -c 'apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y stunnel4 openssl && openssl req -new -x509 -days 3650 -nodes -out /etc/stunnel/stunnel.pem -keyout /etc/stunnel/stunnel.pem -subj \"/CN=jeffersvpn\" && chmod 600 /etc/stunnel/stunnel.pem && printf \"pid = /var/run/stunnel4/stunnel.pid\\ncert = /etc/stunnel/stunnel.pem\\n[ssh]\\naccept = 0.0.0.0:443\\nconnect = 127.0.0.1:22\\n\" >/etc/stunnel/stunnel.conf && sed -i \"s/ENABLED=0/ENABLED=1/\" /etc/default/stunnel4 2>/dev/null; systemctl enable --now stunnel4 && systemctl restart stunnel4 && (ufw allow 443/tcp 2>/dev/null; true) && systemctl status stunnel4 --no-pager'"

private val VPS_NETWORK_REPAIR_CMD =
    "sudo bash -c 'IFACE=\$(ip route show default | awk \"/default/ {print \\\$5; exit}\"); echo \"net.ipv4.ip_forward=1\" >/etc/sysctl.d/99-jeffers-vpn.conf && sysctl -w net.ipv4.ip_forward=1 && (iptables -t nat -C POSTROUTING -o \$IFACE -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -o \$IFACE -j MASQUERADE) && sed -i \"s/^#*AllowTcpForwarding.*/AllowTcpForwarding yes/; s/^#*PermitOpen.*/PermitOpen any/\" /etc/ssh/sshd_config && grep -q \"^AllowTcpForwarding\" /etc/ssh/sshd_config || echo \"AllowTcpForwarding yes\" >>/etc/ssh/sshd_config && systemctl reload ssh 2>/dev/null || systemctl reload sshd && (ufw allow 22/tcp; ufw allow 53/tcp; ufw allow 53/udp; ufw allow 80/tcp; ufw allow 443/tcp; ufw allow 8080/tcp) 2>/dev/null || true; grep -q nameserver /etc/resolv.conf || printf \"nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n\" >>/etc/resolv.conf; echo \"OK IFACE=\$IFACE FWD=\$(cat /proc/sys/net/ipv4/ip_forward)\"'"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminVpsAnalysisSection(
    uiState: VpnDashboardUiState,
    initialProfile: VpnProfileEntity?,
    isAnalyzing: Boolean,
    report: VpsAnalysisReport?,
    onRunAnalysis: (
        host: String,
        port: Int,
        vpsName: String,
        password: String,
        sniHost: String,
        bhttpPayload: String
    ) -> Unit,
    onCreateServerFromAnalysis: (
        host: String,
        port: Int,
        vpsName: String,
        password: String,
        sniHost: String
    ) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = uiState.settings.cardAlpha)

    val seed = initialProfile ?: uiState.selectedProfile
    var host by remember(seed?.id) { mutableStateOf(seed?.host ?: "192.6.154.212") }
    var portText by remember(seed?.id) { mutableStateOf((seed?.port ?: 8080).toString()) }
    var vpsName by remember(seed?.id) { mutableStateOf(seed?.vpsName ?: "VPSpremiumPRO") }
    var password by remember(seed?.id) { mutableStateOf(seed?.password ?: VpnRepository.DEFAULT_VPS_PASSWORD) }
    var sniHost by remember(seed?.id) { mutableStateOf(seed?.sniHost ?: CarrierType.TIGO.defaultSniHost) }
    var bhttpPayload by remember(seed?.id) {
        mutableStateOf(seed?.bhttpPayload ?: BhttpPayloadBuilder.defaultPayloadFor(CarrierType.TIGO))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    Icon(Icons.Rounded.Analytics, contentDescription = null, tint = primaryColor)
                    Text(
                        text = "Análisis Profundo de VPS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "Carga un servidor guardado o escribe cualquier IP/puerto para medir latencia TCP, jitter, pérdida, puertos abiertos, handshake BHTTP y TLS/SNI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                if (uiState.profiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.profiles.forEach { profile ->
                            FilterChip(
                                selected = host == profile.host && portText == profile.port.toString(),
                                onClick = {
                                    host = profile.host
                                    portText = profile.port.toString()
                                    vpsName = profile.vpsName
                                    password = profile.password
                                    sniHost = profile.sniHost
                                    bhttpPayload = profile.bhttpPayload
                                },
                                label = { Text("${profile.vpsName} :${profile.port}") },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP o Dominio VPS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("analyze_vps_host_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                        label = { Text("Puerto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(105.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = vpsName,
                        onValueChange = { vpsName = it },
                        label = { Text("Nombre VPS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sniHost,
                        onValueChange = { sniHost = it },
                        label = { Text("SNI / Bug Host") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 8080
                        onRunAnalysis(host, port, vpsName, password, sniHost, bhttpPayload)
                    },
                    enabled = !isAnalyzing && host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_vps_analysis_button")
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = ObsidianVoid,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Rounded.Speed, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzing) "Analizando puertos, BHTTP y TLS…" else "Ejecutar Análisis del VPS",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (report != null) {
            val gradeColor = Color(report.grade.colorHex)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = surfaceColor,
                border = BorderStroke(1.5.dp, gradeColor.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth().testTag("vps_analysis_report_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SALUD DEL VPS • ${report.host}:${report.targetPort}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = "${report.grade.label} (${report.score}/100)",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = gradeColor
                            )
                            Text(
                                text = "Stack: ${report.detectedServerStack}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = gradeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${report.score}%",
                                style = MonoTypography.metric,
                                color = gradeColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { report.score / 100f },
                        color = gradeColor,
                        trackColor = OutlineSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    if (report.resolvedIps.isNotEmpty() || report.reverseDns != null) {
                        Text(
                            text = buildString {
                                if (report.resolvedIps.isNotEmpty()) {
                                    append("IP(s): ${report.resolvedIps.joinToString(", ")}")
                                }
                                if (report.reverseDns != null) {
                                    if (isNotEmpty()) append("\n")
                                    append("rDNS: ${report.reverseDns}")
                                }
                            },
                            style = MonoTypography.terminal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 4-metric TCP stability row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalysisMetricTile("MÍNIMO", report.minLatencyMs?.let { "${it}ms" } ?: "--", primaryColor, Modifier.weight(1f))
                        AnalysisMetricTile("PROMEDIO", report.avgLatencyMs?.let { "${it}ms" } ?: "--", accentColor, Modifier.weight(1f))
                        AnalysisMetricTile("JITTER", report.jitterMs?.let { "${it}ms" } ?: "--", primaryColor, Modifier.weight(1f))
                        AnalysisMetricTile("PÉRDIDA", "${report.packetLossPercent}%", if (report.packetLossPercent == 0) accentColor else gradeColor, Modifier.weight(1f))
                    }

                    Text("ESCANEO DE PUERTOS VPN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        report.portProbes.forEach { probe ->
                            val chipColor = if (probe.isOpen) accentColor else TextMuted
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (probe.isOpen) accentColor.copy(alpha = 0.14f) else Color(0xFF101720),
                                border = BorderStroke(1.dp, if (probe.isOpen) accentColor.copy(alpha = 0.5f) else OutlineSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(chipColor)
                                    )
                                    Text(
                                        text = if (probe.isOpen) {
                                            "${probe.port} (${probe.serviceLabel}) • ${probe.latencyMs}ms"
                                        } else {
                                            "${probe.port} (${probe.serviceLabel}) • Cerrado"
                                        },
                                        style = MonoTypography.terminal.copy(fontSize = 11.sp),
                                        color = if (probe.isOpen) MaterialTheme.colorScheme.onBackground else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    Text("HANDSHAKE BHTTP & TLS/SNI", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianVoid,
                        border = BorderStroke(1.dp, OutlineSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "BHTTP: ${report.bhttpStatusLine ?: "Sin banner HTTP inmediato"}${report.bhttpTtfbMs?.let { " (TTFB ${it} ms)" } ?: ""}",
                                style = MonoTypography.terminal,
                                color = if (report.bhttpStatusLine != null) accentColor else TextMuted
                            )
                            if (report.tlsProtocol != null) {
                                Text(
                                    text = "TLS: ${report.tlsProtocol} • ${report.tlsCipher ?: ""}",
                                    style = MonoTypography.terminal,
                                    color = primaryColor
                                )
                            }
                            if (report.bhttpHeaders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                report.bhttpHeaders.forEach { header ->
                                    Text(
                                        text = header,
                                        style = MonoTypography.terminal.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (report.recommendations.isNotEmpty()) {
                        Text("DIAGNÓSTICO Y RECOMENDACIONES", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            report.recommendations.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onCreateServerFromAnalysis(
                                report.host,
                                report.targetPort,
                                vpsName,
                                password,
                                sniHost
                            )
                        },
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crear Servidor con estos Datos", color = primaryColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianVoid,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MonoTypography.terminal.copy(fontWeight = FontWeight.Bold), color = valueColor)
        }
    }
}
