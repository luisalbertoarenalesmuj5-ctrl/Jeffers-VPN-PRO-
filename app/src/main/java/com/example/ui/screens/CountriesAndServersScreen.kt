package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import com.example.data.model.CountryRegion
import com.example.data.model.PlanTier
import com.example.data.model.TunnelProtocol
import com.example.data.model.VpnConnectionStatus
import com.example.ui.VpnDashboardUiState
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianGlassElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PulseAmber

@Composable
fun CountriesListScreen(
    uiState: VpnDashboardUiState,
    onBack: () -> Unit,
    onOpenCountry: (CountryRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    val countsByCountry = remember(uiState.profiles) {
        CountryRegion.entries.associateWith { region ->
            uiState.profiles.count { CountryRegion.fromName(it.country) == region }
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
            modifier = Modifier.alpha(0.55f)
        )
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                ) {
                    SubScreenTopBar(
                        title = "Servidores por País",
                        subtitle = "${uiState.profiles.size} nodo(s) VIP • Toca un país para ver compañías",
                        onBack = onBack
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(CountryRegion.entries, key = { it.name }) { region ->
                            val count = countsByCountry[region] ?: 0
                            CountryRowCard(
                                region = region,
                                serverCount = count,
                                surfaceColor = ObsidianGlass.copy(alpha = uiState.settings.cardAlpha.coerceIn(0.66f, 0.90f)),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                onClick = { onOpenCountry(region) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRowCard(
    region: CountryRegion,
    serverCount: Int,
    surfaceColor: Color,
    primaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val carrierPreview = remember(region) {
        region.carriers.joinToString(" • ") { it.displayName }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (serverCount > 0) ChampagneGold.copy(alpha = 0.45f) else OutlineSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("country_row_${region.name.lowercase()}")
    ) {
        Column {
            if (serverCount > 0) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ObsidianGlassElevated,
                        border = BorderStroke(
                            1.2.dp,
                            if (serverCount > 0) Brush.linearGradient(listOf(ChampagneGoldBright, accentColor))
                            else Brush.linearGradient(listOf(ChampagneGold.copy(alpha = 0.45f), OutlineSubtle))
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = region.flagEmoji,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = region.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = primaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = carrierPreview,
                            style = MonoTypography.terminal.copy(fontSize = 10.5.sp),
                            color = FrostIce.copy(alpha = 0.72f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (serverCount > 0) accentColor.copy(alpha = 0.16f) else Color(0x99121B22)
                    ) {
                        Text(
                            text = if (serverCount > 0) "4 Free • 5×Plan • $serverCount" else "4 Free • 5×Plan",
                            style = MonoTypography.terminal.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (serverCount > 0) accentColor else FrostIce.copy(alpha = 0.72f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Abrir ${region.displayName}",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CountryServersScreen(
    country: CountryRegion,
    uiState: VpnDashboardUiState,
    onBack: () -> Unit,
    onSelectProfile: (VpnProfileEntity) -> Unit,
    onSelectCarrier: (CarrierType, PlanTier) -> Unit = { _, _ -> },
    onTestCarrier: (CarrierType, PlanTier) -> Unit = { _, _ -> },
    onTestProfile: (VpnProfileEntity) -> Unit,
    onOpenActivatePlan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTier = remember(uiState.settings.activeSubscription) {
        uiState.settings.activeSubscription.effectiveTier()
    }
    val countryProfiles = remember(uiState.profiles, country) {
        uiState.profiles.filter { CountryRegion.fromName(it.country) == country }
    }

    var selectedPlanFilter by remember(country) { mutableStateOf<PlanTier?>(null) }
    var selectedCarrierFilter by remember(country) { mutableStateOf<CarrierType?>(null) }
    var lockedPlanPrompt by remember { mutableStateOf<PlanTier?>(null) }

    val visiblePlans = remember(selectedPlanFilter) {
        if (selectedPlanFilter == null) PlanTier.entries else listOfNotNull(selectedPlanFilter)
    }

    val availableCarriers = remember(country, countryProfiles, visiblePlans) {
        val fromPlans = visiblePlans.flatMap { country.carriersForPlan(it) }
        val fromSaved = countryProfiles.map { CarrierType.fromName(it.carrier) }
        (fromPlans + fromSaved).distinct()
    }

    val isLockedByConnection = uiState.telemetry.status == VpnConnectionStatus.CONNECTED ||
        uiState.telemetry.status == VpnConnectionStatus.CONNECTING

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
            modifier = Modifier.alpha(0.55f)
        )
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                ) {
                    SubScreenTopBar(
                        title = "${country.flagEmoji} ${country.displayName}",
                        subtitle = "${com.example.data.repository.VpnRepository.DEFAULT_VPS_NAME} (${com.example.data.repository.VpnRepository.DEFAULT_VPS_HOST}) • Free: 4 • 5 por plan",
                        onBack = onBack
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Plan filter bar (FREE = 4 companies, PRO / VIP / PREMIUM / SUPER PRO = 5 companies each)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedPlanFilter == null,
                            onClick = { selectedPlanFilter = null },
                            label = {
                                Text(
                                    text = "Todos los Planes",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChampagneGold.copy(alpha = 0.22f),
                                selectedLabelColor = ChampagneGoldBright
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("plan_filter_all")
                        )
                        PlanTier.entries.forEach { tier ->
                            val tierColor = Color(tier.colorHex)
                            val unlocked = activeTier.unlocksServerTier(tier)
                            FilterChip(
                                selected = selectedPlanFilter == tier,
                                onClick = {
                                    selectedPlanFilter = if (selectedPlanFilter == tier) null else tier
                                },
                                leadingIcon = {
                                    if (unlocked) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(tierColor)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Plan bloqueado",
                                            tint = tierColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${tier.badgeLabel} (${tier.companyQuotaPerCountry})",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tierColor.copy(alpha = 0.22f),
                                    selectedLabelColor = tierColor
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("plan_filter_${tier.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Carrier company filter strip inside the selected country
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedCarrierFilter == null,
                            onClick = { selectedCarrierFilter = null },
                            label = {
                                Text(
                                    text = "Todas (${availableCarriers.size})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ChampagneGold.copy(alpha = 0.22f),
                                selectedLabelColor = ChampagneGoldBright
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("carrier_filter_all")
                        )
                        availableCarriers.forEach { carrier ->
                            val carrierColor = Color(carrier.accentColorHex)
                            FilterChip(
                                selected = selectedCarrierFilter == carrier,
                                onClick = {
                                    selectedCarrierFilter = if (selectedCarrierFilter == carrier) null else carrier
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(carrierColor)
                                    )
                                },
                                label = {
                                    Text(
                                        text = carrier.displayName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = carrierColor.copy(alpha = 0.22f),
                                    selectedLabelColor = carrierColor
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("carrier_filter_${carrier.name.lowercase()}")
                            )
                        }
                    }

                    if (isLockedByConnection) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PulseAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, PulseAmber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = PulseAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Desconecta el túnel actual para cambiar de compañía o servidor.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PulseAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        visiblePlans.forEach { planTier ->
                            val planUnlocked = activeTier.unlocksServerTier(planTier)
                            val planCarriers = country.carriersForPlan(planTier)
                            val planSavedProfiles = countryProfiles.filter {
                                PlanTier.fromName(it.tier) == planTier
                            }
                            val matchedProfileIds = mutableSetOf<Long>()

                            item(key = "plan_header_${country.name}_${planTier.name}") {
                                PlanSectionHeader(
                                    planTier = planTier,
                                    activeTier = activeTier,
                                    companyCount = planCarriers.size,
                                    isUnlocked = planUnlocked
                                )
                            }

                            planCarriers.forEachIndexed { slotIndex, carrier ->
                                if (selectedCarrierFilter == null || selectedCarrierFilter == carrier) {
                                    val matchingSaved = planSavedProfiles.firstOrNull { profile ->
                                        profile.id !in matchedProfileIds &&
                                            CarrierType.fromName(profile.carrier) == carrier
                                    }
                                    if (matchingSaved != null) {
                                        matchedProfileIds += matchingSaved.id
                                        item(key = "saved_${planTier.name}_${slotIndex}_${matchingSaved.id}") {
                                            ServerProfileCard(
                                                profile = matchingSaved,
                                                isSelected = matchingSaved.id == uiState.selectedProfile?.id,
                                                isTestingThis = uiState.testingProfileId == matchingSaved.id,
                                                isConnectionLocked = isLockedByConnection,
                                                isPlanUnlocked = planUnlocked,
                                                surfaceColor = if (matchingSaved.id == uiState.selectedProfile?.id) {
                                                    ObsidianGlassElevated.copy(alpha = 0.92f)
                                                } else {
                                                    ObsidianGlass.copy(alpha = uiState.settings.cardAlpha.coerceIn(0.66f, 0.90f))
                                                },
                                                primaryColor = primaryColor,
                                                accentColor = accentColor,
                                                onSelect = {
                                                    if (planUnlocked) {
                                                        onSelectProfile(matchingSaved)
                                                        onBack()
                                                    } else {
                                                        lockedPlanPrompt = planTier
                                                    }
                                                },
                                                onTest = { onTestProfile(matchingSaved) }
                                            )
                                        }
                                    } else {
                                        item(key = "quick_${country.name}_${planTier.name}_${slotIndex}_${carrier.name}") {
                                            CarrierQuickNodeCard(
                                                country = country,
                                                carrier = carrier,
                                                planTier = planTier,
                                                slotNumber = slotIndex + 1,
                                                isConnectionLocked = isLockedByConnection,
                                                isPlanUnlocked = planUnlocked,
                                                isTesting = uiState.isTestingPing,
                                                surfaceColor = ObsidianGlass.copy(alpha = uiState.settings.cardAlpha.coerceIn(0.66f, 0.90f)),
                                                onActivate = {
                                                    if (planUnlocked) {
                                                        onSelectCarrier(carrier, planTier)
                                                        onBack()
                                                    } else {
                                                        lockedPlanPrompt = planTier
                                                    }
                                                },
                                                onTest = { onTestCarrier(carrier, planTier) }
                                            )
                                        }
                                    }
                                }
                            }

                            val extraSaved = planSavedProfiles.filter { profile ->
                                profile.id !in matchedProfileIds &&
                                    (selectedCarrierFilter == null || CarrierType.fromName(profile.carrier) == selectedCarrierFilter)
                            }
                            if (extraSaved.isNotEmpty()) {
                                items(
                                    items = extraSaved,
                                    key = { "extra_${planTier.name}_${it.id}" }
                                ) { profile ->
                                    ServerProfileCard(
                                        profile = profile,
                                        isSelected = profile.id == uiState.selectedProfile?.id,
                                        isTestingThis = uiState.testingProfileId == profile.id,
                                        isConnectionLocked = isLockedByConnection,
                                        isPlanUnlocked = planUnlocked,
                                        surfaceColor = if (profile.id == uiState.selectedProfile?.id) {
                                            ObsidianGlassElevated.copy(alpha = 0.92f)
                                        } else {
                                            ObsidianGlass.copy(alpha = uiState.settings.cardAlpha.coerceIn(0.66f, 0.90f))
                                        },
                                        primaryColor = primaryColor,
                                        accentColor = accentColor,
                                        onSelect = {
                                            if (planUnlocked) {
                                                onSelectProfile(profile)
                                                onBack()
                                            } else {
                                                lockedPlanPrompt = planTier
                                            }
                                        },
                                        onTest = { onTestProfile(profile) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingPlan = lockedPlanPrompt
    if (pendingPlan != null) {
        val pendingColor = Color(pendingPlan.colorHex)
        AlertDialog(
            onDismissRequest = { lockedPlanPrompt = null },
            containerColor = ObsidianGlassElevated,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = pendingColor
                )
            },
            title = {
                Text(
                    text = "Requiere ${pendingPlan.displayName}",
                    color = pendingColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "El Plan FREE incluye 4 compañías gratuitas por país. Para usar las 5 compañías del ${pendingPlan.displayName}, activa ${pendingPlan.badgeLabel} o SUPER PRO en Mi Perfil.",
                    color = FrostIce.copy(alpha = 0.88f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        lockedPlanPrompt = null
                        onOpenActivatePlan()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pendingColor,
                        contentColor = ObsidianVoid
                    )
                ) {
                    Text("Activar Plan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { lockedPlanPrompt = null }) {
                    Text("Cerrar", color = FrostIce)
                }
            }
        )
    }
}

@Composable
private fun PlanSectionHeader(
    planTier: PlanTier,
    activeTier: PlanTier,
    companyCount: Int,
    isUnlocked: Boolean
) {
    val tierColor = Color(planTier.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tierColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, tierColor.copy(alpha = 0.60f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = tierColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = planTier.badgeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp
                        ),
                        color = tierColor
                    )
                }
            }
            Text(
                text = if (planTier == PlanTier.FREE) {
                    "$companyCount compañías gratuitas"
                } else {
                    "$companyCount compañías • ${planTier.displayName}"
                },
                style = MonoTypography.terminal.copy(fontSize = 11.sp),
                color = FrostIce.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isUnlocked) CyberEmerald.copy(alpha = 0.15f) else PulseAmber.copy(alpha = 0.14f)
        ) {
            Text(
                text = if (isUnlocked) "ACTIVO (${activeTier.badgeLabel})" else "REQUIERE ${planTier.badgeLabel}",
                style = MonoTypography.terminal.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = if (isUnlocked) CyberEmerald else PulseAmber,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun CarrierQuickNodeCard(
    country: CountryRegion,
    carrier: CarrierType,
    planTier: PlanTier,
    slotNumber: Int,
    isConnectionLocked: Boolean,
    isPlanUnlocked: Boolean,
    isTesting: Boolean,
    surfaceColor: Color,
    onActivate: () -> Unit,
    onTest: () -> Unit
) {
    val carrierColor = Color(carrier.accentColorHex)
    val tierColor = Color(planTier.colorHex)
    val sniHost = remember(country, carrier) { country.regionalSniFor(carrier) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    carrierColor.copy(alpha = if (isPlanUnlocked) 0.55f else 0.28f),
                    tierColor.copy(alpha = if (isPlanUnlocked) 0.45f else 0.25f),
                    OutlineSubtle
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !isConnectionLocked, onClick = onActivate)
            .testTag("carrier_quick_card_${planTier.name.lowercase()}_${slotNumber}_${carrier.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = carrierColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.2.dp, carrierColor.copy(alpha = 0.55f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlanUnlocked) Icons.Rounded.CellTower else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = carrierColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$slotNumber. ${carrier.displayName} :${carrier.defaultPort}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FrostIce,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tierColor.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, tierColor.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = planTier.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${com.example.data.repository.VpnRepository.DEFAULT_VPS_HOST}:${carrier.defaultPort} • BHTTP",
                            style = MonoTypography.terminal.copy(fontSize = 11.sp),
                            color = ChampagneGold.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPlanUnlocked) carrierColor.copy(alpha = 0.18f) else tierColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (isPlanUnlocked) carrierColor.copy(alpha = 0.60f) else tierColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlanUnlocked) Icons.Rounded.Bolt else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isPlanUnlocked) carrierColor else tierColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isPlanUnlocked) "Usar" else planTier.badgeLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isPlanUnlocked) carrierColor else tierColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SNI: $sniHost",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostIce.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.42f)),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = ChampagneGoldBright)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = "Probar ${carrier.displayName}",
                            tint = ChampagneGoldBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ping", style = MaterialTheme.typography.labelMedium, color = FrostIce)
                }
            }
        }
    }
}

@Composable
private fun ServerProfileCard(
    profile: VpnProfileEntity,
    isSelected: Boolean,
    isTestingThis: Boolean,
    isConnectionLocked: Boolean,
    isPlanUnlocked: Boolean,
    surfaceColor: Color,
    primaryColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onTest: () -> Unit
) {
    val carrier = CarrierType.fromName(profile.carrier)
    val protocol = TunnelProtocol.fromName(profile.protocol)
    val profileTier = PlanTier.fromName(profile.tier)
    val tierColor = Color(profileTier.colorHex)
    val carrierColor = Color(carrier.accentColorHex)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            brush = if (isSelected) {
                Brush.horizontalGradient(listOf(ChampagneGoldBright, accentColor, ChampagneGold))
            } else {
                Brush.horizontalGradient(
                    listOf(
                        carrierColor.copy(alpha = if (isPlanUnlocked) 0.55f else 0.28f),
                        tierColor.copy(alpha = if (isPlanUnlocked) 0.45f else 0.25f),
                        OutlineSubtle
                    )
                )
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !isConnectionLocked, onClick = onSelect)
            .testTag("server_card_${profile.id}")
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
                                if (isSelected) ChampagneGoldBright else carrierColor.copy(alpha = 0.72f),
                                tierColor.copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isSelected) accentColor else carrierColor).copy(alpha = 0.16f),
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) ChampagneGoldBright else carrierColor.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        !isPlanUnlocked -> Icons.Rounded.Lock
                                        isSelected -> Icons.Rounded.CheckCircle
                                        else -> Icons.Rounded.RadioButtonUnchecked
                                    },
                                    contentDescription = if (isSelected) "Activo" else "Seleccionar",
                                    tint = when {
                                        !isPlanUnlocked -> tierColor
                                        isSelected -> accentColor
                                        else -> carrierColor
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = profile.profileName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FrostIce,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (
                                    protocol == TunnelProtocol.SSH_DIRECT ||
                                    profile.port == 22 ||
                                    CountryRegion.fromName(profile.country) == CountryRegion.GUATEMALA
                                ) {
                                    "${profile.username}@${profile.host}:${profile.port} • ${protocol.badge}"
                                } else {
                                    "${profile.host}:${profile.port} • ${protocol.badge}"
                                },
                                style = MonoTypography.terminal.copy(fontSize = 11.5.sp),
                                color = ChampagneGold.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tierColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, tierColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = profileTier.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = carrierColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.9.dp, carrierColor.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(carrierColor)
                                )
                                Text(
                                    text = "${carrier.displayName} :${profile.port}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = carrierColor
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (profile.lastLatencyMs != null) {
                            "Latencia: ${profile.lastLatencyMs} ms"
                        } else {
                            "SNI: ${profile.sniHost}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.lastLatencyMs != null) accentColor else FrostIce.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTestingThis,
                        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.42f)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isTestingThis) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = ChampagneGoldBright)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = "Ping",
                                tint = ChampagneGoldBright,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ping", style = MaterialTheme.typography.labelMedium, color = FrostIce)
                    }
                }
            }
        }
    }
}
