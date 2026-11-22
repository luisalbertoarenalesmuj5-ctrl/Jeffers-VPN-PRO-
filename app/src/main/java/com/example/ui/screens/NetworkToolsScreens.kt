package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackgroundEffect
import com.example.data.model.ChristmasSoundStyle
import com.example.data.model.ServerAnimationStyle
import com.example.data.model.ThemePreset
import com.example.tools.HostCheckMode
import com.example.tools.HostCheckResult
import com.example.tools.HotspotProxyManager
import com.example.tools.HttpCheckMethod
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianTerminal
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
fun HostCheckerScreen(
    isChecking: Boolean,
    result: HostCheckResult?,
    surfaceAlpha: Float,
    onVerify: (target: String, method: HttpCheckMethod, mode: HostCheckMode, useProxy: Boolean) -> Unit,
    onBack: () -> Unit,
    backgroundEffect: BackgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableStateOf("192.6.154.212:8080") }
    var method by remember { mutableStateOf(HttpCheckMethod.GET) }
    var mode by remember { mutableStateOf(HostCheckMode.HEADER) }
    var useProxy by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = ObsidianGlass.copy(alpha = surfaceAlpha.coerceIn(0.66f, 0.90f))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = backgroundEffect,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SubScreenTopBar(
                title = "Host Checker",
                subtitle = "HOST.CHECK // ${mode.label.uppercase()}",
                titleColor = ChampagneGoldBright,
                onBack = onBack
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor,
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(ChampagneGold.copy(alpha = 0.48f), accentColor.copy(alpha = 0.30f), OutlineSubtle))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, ChampagneGoldBright.copy(alpha = 0.78f), Color.Transparent)))
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("OBJETIVO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ChampagneGoldBright)

                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            placeholder = { Text("Dirección URL o host") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChampagneGoldBright,
                                unfocusedBorderColor = ChampagneGold.copy(alpha = 0.35f),
                                focusedContainerColor = Color(0xCC030B08),
                                unfocusedContainerColor = Color(0xCC030B08),
                                focusedTextColor = FrostIce,
                                unfocusedTextColor = FrostIce
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Acepta host, host:puerto, http(s)://... y la forma IP#dominio (conecta a la IP enviando ese SNI/Host).",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostIce.copy(alpha = 0.72f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xCC030B08),
                                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.34f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { methodExpanded = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Método", style = MaterialTheme.typography.labelSmall, color = ChampagneGold.copy(alpha = 0.78f))
                                        Text(method.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FrostIce)
                                    }
                                }
                                DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                                    HttpCheckMethod.entries.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item.label) },
                                            onClick = {
                                                method = item
                                                methodExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xCC030B08),
                                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.34f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { modeExpanded = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Modo", style = MaterialTheme.typography.labelSmall, color = ChampagneGold.copy(alpha = 0.78f))
                                        Text(mode.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FrostIce)
                                    }
                                }
                                DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                                    HostCheckMode.entries.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item.label) },
                                            onClick = {
                                                mode = item
                                                modeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { useProxy = !useProxy }
                        ) {
                            Checkbox(checked = useProxy, onCheckedChange = { useProxy = it })
                            Column {
                                Text("Proxy", style = MaterialTheme.typography.titleSmall, color = FrostIce)
                                Text(
                                    text = if (useProxy) "Probar usando perfil VPS activo" else "Prueba directa por la red del móvil",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FrostIce.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onVerify(target, method, mode, useProxy) },
                enabled = !isChecking && target.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = ObsidianVoid),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianVoid)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isChecking) "Verificando…" else "Verificar",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (result != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianTerminal,
                    border = BorderStroke(
                        1.dp,
                        if (result.success) accentColor.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (result.success) accentColor else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = result.output,
                            style = MonoTypography.terminal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                Text(
                    text = "Elige un modo y pulsa Verificar. «Header» muestra la respuesta HTTP cruda, «SNI Info» inspecciona el certificado TLS, «IP Address» resuelve y prueba puertos, y «CDN Finder» deduce el proveedor a partir de cabeceras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
    }
}

@Composable
fun HotspotScreen(
    isRunning: Boolean,
    activeClients: Int,
    surfaceAlpha: Float,
    onToggleHotspot: (Boolean) -> Unit,
    onBack: () -> Unit,
    backgroundEffect: BackgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val localIp = remember { HotspotProxyManager.getLocalWifiIpAddress() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = ObsidianGlass.copy(alpha = surfaceAlpha.coerceIn(0.66f, 0.90f))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = backgroundEffect,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SubScreenTopBar(
                title = "Hotspot VPN",
                subtitle = "NET.SHARE // PROXY:${HotspotProxyManager.PROXY_PORT}",
                titleColor = ChampagneGoldBright,
                onBack = onBack
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor,
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(ChampagneGold.copy(alpha = 0.50f), PulseAmber.copy(alpha = 0.35f), OutlineSubtle))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, ChampagneGoldBright.copy(alpha = 0.78f), Color.Transparent)))
                    )
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PulseAmber.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, PulseAmber.copy(alpha = 0.55f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.WifiTethering, contentDescription = null, tint = PulseAmber)
                                    }
                                }
                                Column {
                                    Text("Hotspot VPN", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = FrostIce)
                                    Text("Comparte tu túnel VPN por WiFi", style = MaterialTheme.typography.bodySmall, color = FrostIce.copy(alpha = 0.72f))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isRunning) accentColor.copy(alpha = 0.18f) else Color(0xCC030B08),
                                border = BorderStroke(0.8.dp, if (isRunning) accentColor.copy(alpha = 0.55f) else OutlineSubtle)
                            ) {
                                Text(
                                    text = if (isRunning) "• ACTIVO ($activeClients)" else "• INACTIVO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isRunning) accentColor else TextMuted,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isRunning) "Proxy HTTP escuchando en :${HotspotProxyManager.PROXY_PORT}" else "Proxy inactivo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = FrostIce
                                )
                                Text(
                                    text = "Actívalo para compartir red vía HTTP Proxy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChampagneGold
                                )
                            }
                            Switch(checked = isRunning, onCheckedChange = onToggleHotspot)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.42f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString("$localIp:${HotspotProxyManager.PROXY_PORT}"))
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• PUNTO DE ACCESO // ENDPOINT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ChampagneGoldBright)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SERVIDOR", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(localIp, style = MonoTypography.metric, color = FrostIce)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("PUERTO", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(HotspotProxyManager.PROXY_PORT.toString(), style = MonoTypography.metric, color = ChampagneGoldBright)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(14.dp))
                        Text("Toca para copiar IP:Puerto", style = MaterialTheme.typography.bodySmall, color = ChampagneGold.copy(alpha = 0.85f))
                    }
                }
            }

            Text("— PARA LAPTOP // PASOS POR SISTEMA", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ChampagneGoldBright)

            SetupGuideCard(
                index = "01",
                title = "Windows",
                accent = primaryColor,
                steps = listOf(
                    "Conecta al Hotspot de este teléfono",
                    "Ajustes → Red e Internet → Proxy",
                    "Activar «Usar un servidor proxy»",
                    "Dirección: $localIp   Puerto: ${HotspotProxyManager.PROXY_PORT}",
                    "Clic en Guardar"
                ),
                surfaceColor = surfaceColor
            )
            SetupGuideCard(
                index = "02",
                title = "macOS",
                accent = TextMuted,
                steps = listOf(
                    "Conecta al Hotspot de este teléfono",
                    "Preferencias → Red → WiFi → Avanzado",
                    "Pestaña Proxies → HTTP Proxy",
                    "Servidor: $localIp   Puerto: ${HotspotProxyManager.PROXY_PORT}",
                    "Aplicar y aceptar"
                ),
                surfaceColor = surfaceColor
            )
            SetupGuideCard(
                index = "03",
                title = "Linux / Terminal",
                accent = accentColor,
                steps = listOf(
                    "export http_proxy=http://$localIp:${HotspotProxyManager.PROXY_PORT}",
                    "export https_proxy=http://$localIp:${HotspotProxyManager.PROXY_PORT}",
                    "curl, apt y wget usarán el proxy automáticamente"
                ),
                surfaceColor = surfaceColor
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, OutlineSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.SettingsInputAntenna, contentDescription = null, tint = primaryColor)
                        Column {
                            Text("Configurar Hotspot del sistema", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                            Text("Abrir ajustes de red inalámbrica", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun SetupGuideCard(
    index: String,
    title: String,
    accent: Color,
    steps: List<String>,
    surfaceColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accent)
                Text(index, style = MonoTypography.terminal, color = ChampagneGold.copy(alpha = 0.72f))
            }
            steps.forEachIndexed { idx, step ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(0.6.dp, accent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MonoTypography.terminal,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(step, style = MaterialTheme.typography.bodySmall, color = FrostIce.copy(alpha = 0.80f))
                }
            }
        }
    }
}

@Composable
fun ThemesScreen(
    uiState: VpnDashboardUiState,
    onSelectPreset: (ThemePreset) -> Unit,
    onSelectBackground: (BackgroundEffect) -> Unit,
    onSelectServerAnimation: (ServerAnimationStyle) -> Unit,
    onUpdateAlphas: (Float, Float, Float) -> Unit,
    onResetAppearance: () -> Unit,
    onBack: () -> Unit,
    onToggleChristmasSounds: (Boolean) -> Unit = {},
    onToggleChristmasAmbient: (Boolean) -> Unit = {},
    onSelectChristmasSoundStyle: (ChristmasSoundStyle) -> Unit = {},
    onPreviewChristmasSound: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = ObsidianGlass.copy(alpha = settings.cardAlpha.coerceIn(0.66f, 0.90f))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = settings.backgroundEffect,
            primaryColor = primaryColor,
            accentColor = accentColor,
            modifier = Modifier.alpha(0.52f)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SubScreenTopBar(
                    title = "Temas",
                    subtitle = "UI.THEME // ENGINE",
                    titleColor = ChampagneGoldBright,
                    onBack = onBack,
                    trailing = {
                        OutlinedButton(
                            onClick = onResetAppearance,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restablecer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Text("— PRESETS", style = MaterialTheme.typography.labelSmall, color = ChampagneGoldBright)
                val presetRows = ThemePreset.entries.chunked(2)
                presetRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { preset ->
                            val selected = settings.themePreset == preset
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = surfaceColor,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) Color(preset.primaryHex) else if (preset.isChristmas) ChampagneGold.copy(alpha = 0.32f) else OutlineSubtle
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectPreset(preset) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.primaryHex))
                                    )
                                    Text(preset.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf(preset.primaryHex, preset.accentHex, preset.errorHex).forEach { hex ->
                                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(hex)))
                                        }
                                        if (preset.isChristmas) {
                                            Text("★ PRO", style = MonoTypography.terminal.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold), color = ChampagneGoldBright)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            Text("— FONDO", style = MaterialTheme.typography.labelSmall, color = primaryColor)
            BackgroundEffect.entries.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { effect ->
                        val selected = settings.backgroundEffect == effect
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) primaryColor.copy(alpha = 0.14f) else surfaceColor,
                            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) primaryColor else OutlineSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectBackground(effect) }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(effect.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(effect.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }

            Text("— ANIMACIÓN DE SERVIDORES", style = MaterialTheme.typography.labelSmall, color = primaryColor)
            ServerAnimationStyle.entries.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { anim ->
                        val selected = settings.serverAnimation == anim
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) accentColor.copy(alpha = 0.14f) else surfaceColor,
                            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accentColor else OutlineSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectServerAnimation(anim) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(anim.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }

            Text("— TRANSPARENCIA", style = MaterialTheme.typography.labelSmall, color = primaryColor)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, OutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlphaSliderRow(
                        label = "Paneles / tarjetas",
                        value = settings.cardAlpha,
                        primaryColor = primaryColor,
                        onValueChange = { onUpdateAlphas(it, settings.drawerAlpha, settings.buttonAlpha) }
                    )
                    AlphaSliderRow(
                        label = "Barra lateral",
                        value = settings.drawerAlpha,
                        primaryColor = primaryColor,
                        onValueChange = { onUpdateAlphas(settings.cardAlpha, it, settings.buttonAlpha) }
                    )
                    AlphaSliderRow(
                        label = "Botón de conexión",
                        value = settings.buttonAlpha,
                        primaryColor = primaryColor,
                        onValueChange = { onUpdateAlphas(settings.cardAlpha, settings.drawerAlpha, it) }
                    )
                }
            }

            Text("— SONIDO NAVIDEÑO PRO", style = MaterialTheme.typography.labelSmall, color = primaryColor)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = primaryColor)
                            Column {
                                Text("Efectos Navideños al Conectar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                                Text("Campanas y cascabeles al conectar/desconectar", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        Switch(
                            checked = settings.christmasSoundsEnabled,
                            onCheckedChange = onToggleChristmasSounds
                        )
                    }

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
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accentColor)
                            Column {
                                Text("Villancico Ambiental Pro", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                                Text("Carillón invernal continuo en celesta", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        Switch(
                            checked = settings.christmasAmbientEnabled,
                            onCheckedChange = onToggleChristmasAmbient
                        )
                    }

                    ChristmasSoundStyle.entries.chunked(2).forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowStyles.forEach { soundStyle ->
                                val selected = settings.christmasSoundStyle == soundStyle
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) primaryColor.copy(alpha = 0.16f) else Color.Transparent,
                                    border = BorderStroke(
                                        if (selected) 1.5.dp else 1.dp,
                                        if (selected) primaryColor else OutlineSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectChristmasSoundStyle(soundStyle) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(11.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = soundStyle.title,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) primaryColor else MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = soundStyle.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                            if (rowStyles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreviewChristmasSound("connect") },
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Conectar", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onPreviewChristmasSound("connected") },
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fanfarria", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreviewChristmasSound("carol") },
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Villancico", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onPreviewChristmasSound("bells") },
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cascabeles", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { onPreviewChristmasSound("popurri") },
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = primaryColor, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Popurrí 8 Villancicos (Frase Completa)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun AlphaSliderRow(
    label: String,
    value: Float,
    primaryColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("${(value * 100).roundToInt()}%", style = MonoTypography.terminal, color = primaryColor)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.35f..1.0f,
            colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
        )
    }
}
