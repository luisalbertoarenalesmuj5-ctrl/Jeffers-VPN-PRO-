package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChristmasSoundEngine
import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionSessionEntity
import com.example.data.local.LicenseRecordEntity
import com.example.data.local.SettingsStore
import com.example.data.local.VpnProfileEntity
import com.example.data.model.ActiveSubscription
import com.example.data.model.AppSettingsState
import com.example.data.model.BackgroundEffect
import com.example.data.model.CarrierType
import com.example.data.model.ChristmasSoundStyle
import com.example.data.model.CountryRegion
import com.example.data.model.DnsProvider
import com.example.data.model.HomeLayoutStyle
import com.example.data.model.LogLevel
import com.example.data.model.PingTestResult
import com.example.data.model.PlanTier
import com.example.data.model.ServerAnimationStyle
import com.example.data.model.ThemePreset
import com.example.data.model.TunnelProtocol
import com.example.data.model.VpnConnectionStatus
import com.example.data.model.VpnLogEntry
import com.example.data.model.VpnPerformanceMode
import com.example.data.model.VpnTelemetry
import com.example.data.remote.CloudDeviceLicense
import com.example.data.remote.CloudLicenseRepository
import com.example.data.repository.ActivationOutcome
import com.example.data.repository.VpnRepository
import com.example.tools.HostCheckMode
import com.example.tools.HostCheckResult
import com.example.tools.HostCheckerEngine
import com.example.tools.HotspotProxyManager
import com.example.tools.HttpCheckMethod
import com.example.vpn.BhttpPayloadBuilder
import com.example.vpn.JeffersVpnService
import com.example.vpn.LicenseCodeEngine
import com.example.vpn.LicenseValidationResult
import com.example.vpn.VpnStateHolder
import com.example.vpn.VpsAnalysisReport
import com.example.vpn.VpsAnalyzerEngine
import com.example.vpn.VpsReachabilityTester
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class VpnDashboardUiState(
    val profiles: List<VpnProfileEntity> = emptyList(),
    val selectedProfile: VpnProfileEntity? = null,
    val sessions: List<ConnectionSessionEntity> = emptyList(),
    val licenses: List<LicenseRecordEntity> = emptyList(),
    val telemetry: VpnTelemetry = VpnTelemetry(),
    val logs: List<VpnLogEntry> = emptyList(),
    val settings: AppSettingsState = AppSettingsState(),
    val isTestingPing: Boolean = false,
    val testingProfileId: Long? = null,
    val lastPingResult: PingTestResult? = null
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnRepository(AppDatabase.getInstance(application).vpnDao())
    private val settingsStore = SettingsStore(application)
    private val cloudRepo = CloudLicenseRepository(application)

    private val _isTestingPing = MutableStateFlow(false)
    private val _testingProfileId = MutableStateFlow<Long?>(null)
    private val _lastPingResult = MutableStateFlow<PingTestResult?>(null)

    private val _isCheckingHost = MutableStateFlow(false)
    val isCheckingHost: StateFlow<Boolean> = _isCheckingHost.asStateFlow()

    private val _hostCheckResult = MutableStateFlow<HostCheckResult?>(null)
    val hostCheckResult: StateFlow<HostCheckResult?> = _hostCheckResult.asStateFlow()

    private val _lastGeneratedLicense = MutableStateFlow<LicenseRecordEntity?>(null)
    val lastGeneratedLicense: StateFlow<LicenseRecordEntity?> = _lastGeneratedLicense.asStateFlow()

    private val _activationMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val activationMessage: StateFlow<Pair<Boolean, String>?> = _activationMessage.asStateFlow()

    private val _adminFeedback = MutableStateFlow<Pair<Boolean, String>?>(null)
    val adminFeedback: StateFlow<Pair<Boolean, String>?> = _adminFeedback.asStateFlow()

    private val _isAnalyzingVps = MutableStateFlow(false)
    val isAnalyzingVps: StateFlow<Boolean> = _isAnalyzingVps.asStateFlow()

    private val _vpsAnalysisReport = MutableStateFlow<VpsAnalysisReport?>(null)
    val vpsAnalysisReport: StateFlow<VpsAnalysisReport?> = _vpsAnalysisReport.asStateFlow()

    private val _authFeedback = MutableStateFlow<Pair<Boolean, String>?>(null)
    val authFeedback: StateFlow<Pair<Boolean, String>?> = _authFeedback.asStateFlow()

    private val _authenticatedUid = MutableStateFlow<String?>(null)
    private val _authenticatedEmail = MutableStateFlow<String?>(null)
    val authenticatedEmail: StateFlow<String?> = _authenticatedEmail.asStateFlow()

    private val _isCloudAdmin = MutableStateFlow(false)
    val isCloudAdmin: StateFlow<Boolean> = _isCloudAdmin.asStateFlow()

    private var welcomeChimePlayed = false

    val initialAccountRegistered: Boolean
        get() = settingsStore.settings.value.isAccountRegistered

    val hotspotRunning: StateFlow<Boolean> = HotspotProxyManager.isRunning
    val hotspotClients: StateFlow<Int> = HotspotProxyManager.activeClients

    val uiState: StateFlow<VpnDashboardUiState> = combine(
        repository.profiles,
        repository.selectedProfile,
        repository.sessions,
        repository.licenses,
        VpnStateHolder.telemetry
    ) { profiles, selected, sessions, licenses, telemetry ->
        VpnDashboardUiState(
            profiles = profiles,
            selectedProfile = selected ?: profiles.firstOrNull(),
            sessions = sessions,
            licenses = licenses,
            telemetry = telemetry
        )
    }.combine(VpnStateHolder.logs) { state, logs ->
        state.copy(logs = logs)
    }.combine(settingsStore.settings) { state, settings ->
        state.copy(settings = settings)
    }.combine(_isTestingPing) { state, testing ->
        state.copy(isTestingPing = testing)
    }.combine(_testingProfileId) { state, profileId ->
        state.copy(testingProfileId = profileId)
    }.combine(_lastPingResult) { state, pingResult ->
        state.copy(lastPingResult = pingResult)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = VpnDashboardUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultProfilesSeeded()
            val active = repository.getSelectedProfile()
            if (active != null && VpnStateHolder.telemetry.value.status == VpnConnectionStatus.DISCONNECTED) {
                syncTelemetryWithProfile(active)
            }
            checkDeviceLicenseBinding()
        }

        viewModelScope.launch {
            var previousStatus = VpnStateHolder.telemetry.value.status
            VpnStateHolder.telemetry.collect { telemetry ->
                val currentStatus = telemetry.status
                if (currentStatus != previousStatus) {
                    val currentSettings = settingsStore.settings.value
                    if (currentSettings.christmasSoundsEnabled) {
                        val style = currentSettings.christmasSoundStyle
                        when (currentStatus) {
                            VpnConnectionStatus.CONNECTING -> ChristmasSoundEngine.playConnectStart(style)
                            VpnConnectionStatus.CONNECTED -> ChristmasSoundEngine.playConnectedFanfare(style)
                            VpnConnectionStatus.DISCONNECTED -> {
                                if (previousStatus == VpnConnectionStatus.CONNECTED ||
                                    previousStatus == VpnConnectionStatus.CONNECTING
                                ) {
                                    ChristmasSoundEngine.playDisconnectedChime(style)
                                }
                            }
                            VpnConnectionStatus.ERROR -> ChristmasSoundEngine.playErrorBell(style)
                        }
                    }
                    previousStatus = currentStatus
                }
            }
        }

        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                ChristmasSoundEngine.updateAmbientPreference(
                    enabled = settings.christmasAmbientEnabled,
                    style = settings.christmasSoundStyle
                )
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            combine(
                _authenticatedUid,
                settingsStore.settings.map { it.deviceId }.distinctUntilChanged()
            ) { uid, deviceId -> uid to deviceId }
                .flatMapLatest { (uid, deviceId) ->
                    if (uid.isNullOrBlank() || deviceId.isBlank()) {
                        flowOf(null to null)
                    } else {
                        cloudRepo.observeDeviceLicense(deviceId).map { license -> uid to license }
                    }
                }
                .collect { (uid, cloudLicense) ->
                    if (uid != null && cloudLicense != null) {
                        applyCloudDeviceLicense(uid, cloudLicense)
                    }
                }
        }
    }

    fun onAuthenticatedUserChanged(uid: String, email: String?, displayName: String?) {
        _authenticatedUid.value = uid
        _authenticatedEmail.value = email
        val isAdminEmail = email?.equals(CloudLicenseRepository.PRIMARY_ADMIN_EMAIL, ignoreCase = true) == true
        _isCloudAdmin.value = isAdminEmail
        viewModelScope.launch {
            if (isAdminEmail) {
                cloudRepo.ensureAdminRecordIfEligible()
                syncAdminLicensesFromCloud()
            }
            val current = settingsStore.settings.value
            if (!current.isAccountRegistered &&
                current.userAlias == "jeffers_user" &&
                !displayName.isNullOrBlank()
            ) {
                val cleanName = displayName.trim().take(30)
                if (cleanName.isNotEmpty()) {
                    settingsStore.update { it.copy(userAlias = cleanName) }
                }
            }
            pushCurrentUserProfileToCloud()
        }
    }

    fun onSignedOut() {
        _authenticatedUid.value = null
        _authenticatedEmail.value = null
        _isCloudAdmin.value = false
    }

    fun clearAdminFeedback() {
        _adminFeedback.value = null
    }

    fun syncAdminLicensesFromCloud() {
        viewModelScope.launch {
            val result = cloudRepo.fetchAllAdminLicenses()
            result.onSuccess { list ->
                list.forEach { cloudRecord ->
                    repository.upsertLicenseFromCloud(cloudRecord)
                }
            }
        }
    }

    private suspend fun pushCurrentUserProfileToCloud() {
        val uid = _authenticatedUid.value ?: return
        val s = settingsStore.settings.value
        val sub = s.activeSubscription
        cloudRepo.syncUserProfile(
            uid = uid,
            email = _authenticatedEmail.value,
            displayName = s.userAlias,
            userCountry = s.userCountry.name,
            deviceId = s.deviceId,
            activeTier = sub.effectiveTier(),
            activationCode = sub.activationCode,
            expiresAtMillis = sub.expiresAt
        )
    }

    private suspend fun applyCloudDeviceLicense(uid: String, cloud: CloudDeviceLicense) {
        val now = System.currentTimeMillis()
        val currentSettings = settingsStore.settings.value
        val tier = PlanTier.fromName(cloud.tier)
        val localEntity = LicenseRecordEntity(
            code = cloud.code,
            boundDeviceId = cloud.deviceId,
            tier = tier.name,
            durationDays = cloud.durationDays,
            createdAt = cloud.createdAt?.toDate()?.time ?: now,
            expiresAt = cloud.expiresAtMillis,
            activatedAt = cloud.activatedAtMillis.takeIf { it > 0L } ?: now,
            activatedByDeviceId = currentSettings.deviceId,
            isRevoked = cloud.revoked,
            note = cloud.note
        )
        repository.upsertLicenseFromCloud(localEntity)

        if (cloud.revoked || cloud.expiresAtMillis < now) {
            if (currentSettings.activeSubscription.activationCode == cloud.code) {
                settingsStore.update {
                    it.copy(activeSubscription = ActiveSubscription(tier = PlanTier.FREE))
                }
                pushCurrentUserProfileToCloud()
                VpnStateHolder.appendLog(
                    "PLAN",
                    if (cloud.revoked) "Suscripción revocada desde la nube; restaurado a Plan FREE"
                    else "Suscripción vencida; restaurado a Plan FREE",
                    LogLevel.WARNING
                )
            }
            return
        }

        val currentSub = currentSettings.activeSubscription
        val alreadyMatched = currentSub.activationCode == cloud.code &&
            currentSub.tier == tier &&
            currentSub.expiresAt == cloud.expiresAtMillis

        if (!alreadyMatched) {
            val activatedAt = cloud.activatedAtMillis.takeIf { it > 0L } ?: now
            settingsStore.update {
                it.copy(
                    activeSubscription = ActiveSubscription(
                        tier = tier,
                        activationCode = cloud.code,
                        activatedAt = activatedAt,
                        expiresAt = cloud.expiresAtMillis
                    )
                )
            }
            _activationMessage.value = true to "Plan ${tier.badgeLabel} recibido automáticamente para tu ID ${currentSettings.deviceId}."
            VpnStateHolder.appendLog(
                "PLAN",
                "Plan ${tier.badgeLabel} activado en tiempo real para ID ${currentSettings.deviceId}",
                LogLevel.SUCCESS
            )
            pushCurrentUserProfileToCloud()
        }

        if (cloud.claimedByUid.isNullOrBlank()) {
            cloudRepo.claimDeviceLicense(cloud.deviceId, uid)
        }
    }

    private suspend fun checkDeviceLicenseBinding() {
        val currentId = settingsStore.settings.value.deviceId
        val activeForDevice = repository.findValidDeviceActivation(currentId)
        if (activeForDevice != null) {
            val tier = PlanTier.fromName(activeForDevice.tier)
            settingsStore.update {
                it.copy(
                    activeSubscription = ActiveSubscription(
                        tier = tier,
                        activationCode = activeForDevice.code,
                        activatedAt = activeForDevice.activatedAt ?: activeForDevice.createdAt,
                        expiresAt = activeForDevice.expiresAt
                    )
                )
            }
        } else if (settingsStore.settings.value.activeSubscription.isExpired()) {
            settingsStore.update {
                it.copy(activeSubscription = ActiveSubscription(tier = PlanTier.FREE))
            }
            VpnStateHolder.appendLog("PLAN", "Suscripción vencida; restaurado a Plan Gratuito", LogLevel.WARNING)
        }
    }

    fun verifyAdminPassword(input: String): Boolean {
        return LicenseCodeEngine.verifyAdminPassword(input, settingsStore.settings.value.adminPasswordHash)
    }

    fun changeAdminPassword(newPassword: String) {
        val hash = LicenseCodeEngine.hashAdminPassword(newPassword)
        settingsStore.update { it.copy(adminPasswordHash = hash) }
        VpnStateHolder.appendLog("ADMIN", "Contraseña de administrador actualizada", LogLevel.SUCCESS)
    }

    fun generatePlanCode(tier: PlanTier, days: Int, note: String) {
        viewModelScope.launch {
            val record = repository.createLicenseRecord(
                tier = tier,
                durationDays = days,
                targetDeviceId = null,
                note = note
            )
            _lastGeneratedLicense.value = record
            val cloudResult = cloudRepo.publishLicenseCode(record)
            _adminFeedback.value = if (cloudResult.isSuccess) {
                true to "Código ${tier.badgeLabel} guardado y sincronizado en la nube."
            } else {
                false to "Código generado localmente, pero no se pudo subir a la nube: ${cloudResult.exceptionOrNull()?.message ?: "sin permisos Admin"}."
            }
            VpnStateHolder.appendLog(
                "ADMIN",
                "Código ${tier.badgeLabel} generado (${days}d): ${record.code}",
                LogLevel.SUCCESS
            )
        }
    }

    fun activatePlanById(tier: PlanTier, days: Int, targetDeviceId: String, note: String) {
        val cleanId = targetDeviceId.trim()
        if (cleanId.isBlank()) return
        viewModelScope.launch {
            val record = repository.createLicenseRecord(
                tier = tier,
                durationDays = days,
                targetDeviceId = cleanId,
                note = note.ifBlank { "Activado por ID" }
            )
            _lastGeneratedLicense.value = record
            val cloudResult = cloudRepo.publishDeviceLicense(record)
            _adminFeedback.value = if (cloudResult.isSuccess) {
                true to "Plan ${tier.badgeLabel} enviado en la nube al ID $cleanId (${days}d). El usuario lo recibirá automáticamente."
            } else {
                false to "No se pudo enviar en la nube al ID $cleanId: inicia sesión con la cuenta Google Admin (${CloudLicenseRepository.PRIMARY_ADMIN_EMAIL})."
            }
            if (cleanId.equals(settingsStore.settings.value.deviceId, ignoreCase = true)) {
                settingsStore.update {
                    it.copy(
                        activeSubscription = ActiveSubscription(
                            tier = tier,
                            activationCode = record.code,
                            activatedAt = record.activatedAt ?: record.createdAt,
                            expiresAt = record.expiresAt
                        )
                    )
                }
                pushCurrentUserProfileToCloud()
            }
            VpnStateHolder.appendLog(
                "ADMIN",
                if (cloudResult.isSuccess) {
                    "Plan ${tier.badgeLabel} enviado en la nube para ID $cleanId (${days}d)"
                } else {
                    "Plan ${tier.badgeLabel} guardado solo local para ID $cleanId (error nube)"
                },
                if (cloudResult.isSuccess) LogLevel.SUCCESS else LogLevel.WARNING
            )
        }
    }

    fun createPersonalClientLicense(
        tier: PlanTier,
        days: Int,
        clientUsername: String,
        clientPassword: String,
        note: String
    ) {
        val cleanUser = clientUsername.trim()
        if (cleanUser.isEmpty()) return
        viewModelScope.launch {
            val record = repository.createLicenseRecord(
                tier = tier,
                durationDays = days,
                targetDeviceId = null,
                note = note.ifBlank { "Cliente personal" },
                clientUsername = cleanUser,
                clientPassword = clientPassword.trim().ifBlank { null }
            )
            _lastGeneratedLicense.value = record
            val cloudResult = cloudRepo.publishPersonalClient(record)
            _adminFeedback.value = if (cloudResult.isSuccess) {
                true to "Usuario personal '$cleanUser' (${tier.badgeLabel}) publicado en la nube."
            } else {
                false to "Usuario '$cleanUser' guardado localmente, pero falló publicación en la nube."
            }
            VpnStateHolder.appendLog(
                "ADMIN",
                "Usuario personal '$cleanUser' (${tier.badgeLabel}, ${days}d) creado • ${record.code}",
                LogLevel.SUCCESS
            )
        }
    }

    fun extendLicense(license: LicenseRecordEntity, extraDays: Int) {
        viewModelScope.launch {
            val (newExpiry, newDays) = repository.extendLicense(license, extraDays)
            cloudRepo.extendLicenseInCloud(license, newExpiry, newDays)
            checkDeviceLicenseBinding()
            _adminFeedback.value = true to "Licencia extendida +${extraDays}d en la nube."
        }
    }

    fun toggleLicenseRevoke(license: LicenseRecordEntity) {
        viewModelScope.launch {
            val newRevoked = !license.isRevoked
            repository.setLicenseRevoked(license.id, newRevoked)
            cloudRepo.setLicenseRevokedInCloud(license, newRevoked)
            if (newRevoked && license.code == settingsStore.settings.value.activeSubscription.activationCode) {
                settingsStore.update { it.copy(activeSubscription = ActiveSubscription(tier = PlanTier.FREE)) }
            }
            _adminFeedback.value = true to if (newRevoked) "Licencia revocada en la nube." else "Licencia reactivada en la nube."
        }
    }

    fun deleteLicense(id: Long) {
        viewModelScope.launch {
            val license = repository.getLicenseById(id)
            repository.deleteLicense(id)
            if (license != null) {
                cloudRepo.deleteLicenseInCloud(license)
            }
        }
    }

    fun redeemActivationCode(code: String) {
        viewModelScope.launch {
            val deviceId = settingsStore.settings.value.deviceId
            val cleanCode = code.trim().uppercase(Locale.US)

            val cloudLookup = if (cloudRepo.isSignedIn()) {
                cloudRepo.fetchLicenseCodeOnce(cleanCode).getOrNull()
            } else null
            if (cloudLookup != null) {
                val now = System.currentTimeMillis()
                if (cloudLookup.revoked) {
                    _activationMessage.value = false to "Este código fue revocado por el administrador."
                    return@launch
                }
                if (cloudLookup.expiresAtMillis < now) {
                    _activationMessage.value = false to "Este código ya venció."
                    return@launch
                }
                if (!cloudLookup.boundDeviceId.isNullOrBlank() &&
                    !cloudLookup.boundDeviceId.equals(deviceId, ignoreCase = true)
                ) {
                    _activationMessage.value = false to "Código asignado al ID ${cloudLookup.boundDeviceId}."
                    return@launch
                }
                val tier = PlanTier.fromName(cloudLookup.tier)
                val activatedAt = cloudLookup.activatedAtMillis ?: now
                repository.upsertLicenseFromCloud(
                    LicenseRecordEntity(
                        code = cloudLookup.code,
                        boundDeviceId = cloudLookup.boundDeviceId,
                        tier = tier.name,
                        durationDays = cloudLookup.durationDays,
                        expiresAt = cloudLookup.expiresAtMillis,
                        activatedAt = activatedAt,
                        activatedByDeviceId = deviceId,
                        isRevoked = false,
                        note = cloudLookup.note
                    )
                )
                _authenticatedUid.value?.let { uid ->
                    cloudRepo.claimLicenseCode(cloudLookup.code, deviceId, uid)
                }
                settingsStore.update {
                    it.copy(
                        activeSubscription = ActiveSubscription(
                            tier = tier,
                            activationCode = cloudLookup.code,
                            activatedAt = activatedAt,
                            expiresAt = cloudLookup.expiresAtMillis
                        )
                    )
                }
                pushCurrentUserProfileToCloud()
                _activationMessage.value = true to "Plan ${tier.badgeLabel} activado correctamente."
                VpnStateHolder.appendLog("PLAN", "Activado ${tier.badgeLabel} (${cloudLookup.code})", LogLevel.SUCCESS)
                return@launch
            }

            when (val outcome = repository.redeemCode(cleanCode, deviceId)) {
                is ActivationOutcome.Success -> {
                    settingsStore.update {
                        it.copy(
                            activeSubscription = ActiveSubscription(
                                tier = outcome.tier,
                                activationCode = outcome.code,
                                activatedAt = outcome.activatedAt,
                                expiresAt = outcome.expiresAt
                            )
                        )
                    }
                    pushCurrentUserProfileToCloud()
                    _activationMessage.value = true to "Plan ${outcome.tier.badgeLabel} activado correctamente."
                    VpnStateHolder.appendLog("PLAN", "Activado ${outcome.tier.badgeLabel} (${outcome.code})", LogLevel.SUCCESS)
                }
                is ActivationOutcome.Failure -> {
                    _activationMessage.value = false to outcome.reason
                }
            }
        }
    }

    fun refreshActivationByDeviceId() {
        viewModelScope.launch {
            val deviceId = settingsStore.settings.value.deviceId
            val signedIn = cloudRepo.isSignedIn()
            val cloudResult = if (signedIn) cloudRepo.fetchDeviceLicenseOnce(deviceId) else null
            val cloud = cloudResult?.getOrNull()
            if (cloud != null) {
                val now = System.currentTimeMillis()
                if (cloud.revoked) {
                    _activationMessage.value = false to "El plan asignado al ID $deviceId fue revocado por el administrador."
                    return@launch
                }
                if (cloud.expiresAtMillis < now) {
                    _activationMessage.value = false to "El plan asignado al ID $deviceId ya venció."
                    return@launch
                }
                val uid = _authenticatedUid.value
                if (uid != null) {
                    applyCloudDeviceLicense(uid, cloud)
                } else {
                    val tier = PlanTier.fromName(cloud.tier)
                    settingsStore.update {
                        it.copy(
                            activeSubscription = ActiveSubscription(
                                tier = tier,
                                activationCode = cloud.code,
                                activatedAt = cloud.activatedAtMillis.takeIf { ts -> ts > 0L } ?: now,
                                expiresAt = cloud.expiresAtMillis
                            )
                        )
                    }
                }
                val tier = PlanTier.fromName(cloud.tier)
                _activationMessage.value = true to "Plan ${tier.badgeLabel} sincronizado desde la nube para ID $deviceId."
                return@launch
            }

            val active = repository.findValidDeviceActivation(deviceId)
            if (active != null) {
                val tier = PlanTier.fromName(active.tier)
                settingsStore.update {
                    it.copy(
                        activeSubscription = ActiveSubscription(
                            tier = tier,
                            activationCode = active.code,
                            activatedAt = active.activatedAt ?: active.createdAt,
                            expiresAt = active.expiresAt
                        )
                    )
                }
                pushCurrentUserProfileToCloud()
                _activationMessage.value = true to "Plan ${tier.badgeLabel} sincronizado para ID $deviceId."
            } else if (!signedIn) {
                _activationMessage.value = false to "Vincula tu cuenta de Google en Perfil para sincronizar planes desde la nube (ID $deviceId)."
            } else {
                val errHint = cloudResult?.exceptionOrNull()?.let { " (sin conexión con la nube)" } ?: ""
                _activationMessage.value = false to "No hay activación pendiente asignada al ID $deviceId$errHint."
            }
        }
    }

    fun clearActivationMessage() {
        _activationMessage.value = null
    }

    fun redeemPersonalUsername(username: String, secret: String?) {
        viewModelScope.launch {
            val deviceId = settingsStore.settings.value.deviceId
            when (val outcome = activatePersonalClientWithCloudFallback(username, secret, deviceId)) {
                is ActivationOutcome.Success -> {
                    val cleanAlias = outcome.clientUsername ?: username.trim()
                    settingsStore.update {
                        it.copy(
                            userAlias = cleanAlias,
                            personalTunnelUsername = cleanAlias,
                            personalTunnelPassword = secret?.trim()?.takeIf { s -> s.isNotEmpty() }
                                ?: it.personalTunnelPassword,
                            activeSubscription = ActiveSubscription(
                                tier = outcome.tier,
                                activationCode = outcome.code,
                                activatedAt = outcome.activatedAt,
                                expiresAt = outcome.expiresAt
                            )
                        )
                    }
                    pushCurrentUserProfileToCloud()
                    _activationMessage.value = true to "Usuario '$cleanAlias' activado (${outcome.tier.badgeLabel})."
                    VpnStateHolder.appendLog("PLAN", "Cliente personal '$cleanAlias' activado (${outcome.tier.badgeLabel})", LogLevel.SUCCESS)
                }
                is ActivationOutcome.Failure -> {
                    _activationMessage.value = false to outcome.reason
                }
            }
        }
    }

    private suspend fun activatePersonalClientWithCloudFallback(
        rawUsername: String,
        rawSecret: String?,
        currentDeviceId: String
    ): ActivationOutcome {
        val cleanUser = rawUsername.trim()
        if (cleanUser.isEmpty()) {
            return ActivationOutcome.Failure("Escribe el usuario del cliente personal.")
        }
        val cloud = if (cloudRepo.isSignedIn()) {
            cloudRepo.fetchPersonalClientOnce(cleanUser).getOrNull()
        } else null
        if (cloud != null) {
            if (cloud.revoked) {
                return ActivationOutcome.Failure("Este usuario fue revocado por el administrador.")
            }
            val now = System.currentTimeMillis()
            if (cloud.expiresAtMillis < now) {
                return ActivationOutcome.Failure("La suscripción de '$cleanUser' ya venció.")
            }
            val providedSecret = rawSecret?.trim().orEmpty()
            if (cloud.hasPassword) {
                val expectedHash = cloud.passwordHash
                val enteredHash = CloudLicenseRepository.hashPersonalClientPassword(cloud.usernameKey, providedSecret)
                if (enteredHash != expectedHash && !providedSecret.equals(cloud.code, ignoreCase = true)) {
                    return ActivationOutcome.Failure("Clave o código incorrecto para '$cleanUser'.")
                }
            }
            if (!cloud.boundDeviceId.isNullOrBlank() &&
                !cloud.boundDeviceId.equals(currentDeviceId, ignoreCase = true)
            ) {
                return ActivationOutcome.Failure("Usuario '$cleanUser' vinculado al ID ${cloud.boundDeviceId}.")
            }
            val tier = PlanTier.fromName(cloud.tier)
            val activatedAt = cloud.activatedAtMillis ?: now
            repository.upsertLicenseFromCloud(
                LicenseRecordEntity(
                    code = cloud.code,
                    boundDeviceId = cloud.boundDeviceId,
                    clientUsername = cloud.displayUsername,
                    clientPassword = providedSecret.takeIf { it.isNotEmpty() },
                    tier = tier.name,
                    durationDays = cloud.durationDays,
                    expiresAt = cloud.expiresAtMillis,
                    activatedAt = activatedAt,
                    activatedByDeviceId = currentDeviceId,
                    isRevoked = false,
                    note = cloud.note
                )
            )
            _authenticatedUid.value?.let { uid ->
                cloudRepo.claimPersonalClient(cloud.usernameKey, currentDeviceId, uid)
            }
            return ActivationOutcome.Success(
                tier = tier,
                code = cloud.code,
                activatedAt = activatedAt,
                expiresAt = cloud.expiresAtMillis,
                clientUsername = cloud.displayUsername
            )
        }
        return repository.activateByClientUsername(cleanUser, rawSecret, currentDeviceId)
    }

    fun selectProfile(profile: VpnProfileEntity) {
        viewModelScope.launch {
            val activeTier = settingsStore.settings.value.activeSubscription.effectiveTier()
            val requiredTier = PlanTier.fromName(profile.tier)
            if (!activeTier.unlocksServerTier(requiredTier)) {
                VpnStateHolder.appendLog(
                    "PLAN",
                    "Servidor '${profile.profileName}' requiere ${requiredTier.displayName} (plan actual: ${activeTier.badgeLabel}).",
                    LogLevel.WARNING
                )
                return@launch
            }
            val currentStatus = VpnStateHolder.telemetry.value.status
            repository.selectProfile(profile.id)
            if (currentStatus == VpnConnectionStatus.DISCONNECTED || currentStatus == VpnConnectionStatus.ERROR) {
                syncTelemetryWithProfile(profile)
            }
            val currentSettings = settingsStore.settings.value
            if (currentSettings.christmasSoundsEnabled) {
                ChristmasSoundEngine.playUiSparkle(currentSettings.christmasSoundStyle)
            }
            VpnStateHolder.appendLog(
                "PERFIL",
                "Servidor activo: ${profile.profileName} (${profile.host}:${profile.port})",
                LogLevel.INFO
            )
        }
    }

    fun selectOrProvisionCarrierProfile(
        country: CountryRegion,
        carrier: CarrierType,
        planTier: PlanTier = PlanTier.FREE
    ) {
        viewModelScope.launch {
            val activeTier = settingsStore.settings.value.activeSubscription.effectiveTier()
            if (!activeTier.unlocksServerTier(planTier)) {
                VpnStateHolder.appendLog(
                    "PLAN",
                    "${country.titleName} • ${carrier.displayName} (${planTier.badgeLabel}) requiere ${planTier.displayName}.",
                    LogLevel.WARNING
                )
                return@launch
            }
            val currentProfiles = uiState.value.profiles
            val existing = currentProfiles.firstOrNull {
                CountryRegion.fromName(it.country) == country &&
                    CarrierType.fromName(it.carrier) == carrier &&
                    PlanTier.fromName(it.tier) == planTier
            }
            if (existing != null) {
                selectProfile(existing)
                return@launch
            }
            val dns = settingsStore.settings.value.dnsProvider
            val label = if (planTier == PlanTier.FREE) {
                "${country.titleName} • ${carrier.displayName} ${carrier.defaultPort}"
            } else {
                "${country.titleName} • ${carrier.displayName} ${planTier.badgeLabel} ${carrier.defaultPort}"
            }
            val entity = VpnProfileEntity(
                profileName = label,
                vpsName = VpnRepository.DEFAULT_VPS_NAME,
                host = VpnRepository.DEFAULT_VPS_HOST,
                port = carrier.defaultPort,
                username = VpnRepository.DEFAULT_SSH_USER,
                password = VpnRepository.DEFAULT_VPS_PASSWORD,
                carrier = carrier.name,
                protocol = TunnelProtocol.BHTTP.name,
                country = country.name,
                tier = planTier.name,
                sniHost = country.regionalSniFor(carrier),
                bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(carrier),
                dnsPrimary = dns.primary,
                dnsSecondary = dns.secondary,
                isSelected = true,
                isPreset = false
            )
            val savedId = repository.saveProfile(entity)
            val persisted = entity.copy(id = savedId)
            val currentStatus = VpnStateHolder.telemetry.value.status
            if (currentStatus == VpnConnectionStatus.DISCONNECTED || currentStatus == VpnConnectionStatus.ERROR) {
                syncTelemetryWithProfile(persisted)
            }
            val currentSettings = settingsStore.settings.value
            if (currentSettings.christmasSoundsEnabled) {
                ChristmasSoundEngine.playUiSparkle(currentSettings.christmasSoundStyle)
            }
            VpnStateHolder.appendLog(
                "PERFIL",
                "Compañía activada (${country.titleName} • ${carrier.displayName} [${planTier.badgeLabel}]) en ${VpnRepository.DEFAULT_VPS_HOST}:${persisted.port}",
                LogLevel.SUCCESS
            )
        }
    }

    fun testCountryCarrierProfile(
        country: CountryRegion,
        carrier: CarrierType,
        planTier: PlanTier = PlanTier.FREE
    ) {
        val existing = uiState.value.profiles.firstOrNull {
            CountryRegion.fromName(it.country) == country &&
                CarrierType.fromName(it.carrier) == carrier &&
                PlanTier.fromName(it.tier) == planTier
        }
        if (existing != null) {
            testProfileLatency(existing)
            return
        }
        if (_isTestingPing.value) return
        viewModelScope.launch {
            _isTestingPing.value = true
            val dns = settingsStore.settings.value.dnsProvider
            val label = if (planTier == PlanTier.FREE) {
                "${country.titleName} • ${carrier.displayName} ${carrier.defaultPort}"
            } else {
                "${country.titleName} • ${carrier.displayName} ${planTier.badgeLabel} ${carrier.defaultPort}"
            }
            val probeProfile = VpnProfileEntity(
                profileName = label,
                vpsName = VpnRepository.DEFAULT_VPS_NAME,
                host = VpnRepository.DEFAULT_VPS_HOST,
                port = carrier.defaultPort,
                username = VpnRepository.DEFAULT_SSH_USER,
                password = VpnRepository.DEFAULT_VPS_PASSWORD,
                carrier = carrier.name,
                protocol = TunnelProtocol.BHTTP.name,
                country = country.name,
                tier = planTier.name,
                sniHost = country.regionalSniFor(carrier),
                bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(carrier),
                dnsPrimary = dns.primary,
                dnsSecondary = dns.secondary
            )
            VpnStateHolder.appendLog(
                "TEST",
                "Probando ${country.titleName} • ${carrier.displayName} (${planTier.badgeLabel}) hacia ${probeProfile.host}:${probeProfile.port}…",
                LogLevel.INFO
            )
            val result = VpsReachabilityTester.testProfile(probeProfile)
            _lastPingResult.value = result
            if (result.isReachable) {
                val s = settingsStore.settings.value
                if (s.christmasSoundsEnabled) {
                    ChristmasSoundEngine.playTestSuccessBell(s.christmasSoundStyle)
                }
                VpnStateHolder.appendLog(
                    "TEST",
                    "OK ${carrier.displayName} (${probeProfile.host}:${probeProfile.port}) en ${result.latencyMs} ms",
                    LogLevel.SUCCESS
                )
            } else {
                VpnStateHolder.appendLog(
                    "TEST",
                    "Sin respuesta en ${probeProfile.host}:${probeProfile.port}: ${result.errorDetail}",
                    LogLevel.ERROR
                )
            }
            _isTestingPing.value = false
        }
    }

    fun startVpnConnection() {
        val profile = uiState.value.selectedProfile ?: return
        syncTelemetryWithProfile(profile)
        val context = getApplication<Application>()
        val intent = Intent(context, JeffersVpnService::class.java).apply {
            action = JeffersVpnService.ACTION_CONNECT
            putExtra(JeffersVpnService.EXTRA_PROFILE_ID, profile.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpnConnection() {
        val context = getApplication<Application>()
        val intent = Intent(context, JeffersVpnService::class.java).apply {
            action = JeffersVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun testProfileLatency(profile: VpnProfileEntity) {
        if (_isTestingPing.value) return
        viewModelScope.launch {
            _isTestingPing.value = true
            _testingProfileId.value = profile.id
            VpnStateHolder.appendLog(
                "TEST",
                "Probando TCP + BHTTP hacia ${profile.host}:${profile.port}…",
                LogLevel.INFO
            )
            val result = VpsReachabilityTester.testProfile(profile)
            _lastPingResult.value = result
            if (result.isReachable) {
                repository.updateLatency(profile.id, result.latencyMs)
                val s = settingsStore.settings.value
                if (s.christmasSoundsEnabled) {
                    ChristmasSoundEngine.playTestSuccessBell(s.christmasSoundStyle)
                }
                VpnStateHolder.appendLog(
                    "TEST",
                    "OK ${profile.host}:${profile.port} en ${result.latencyMs} ms • ${result.httpBanner}",
                    LogLevel.SUCCESS
                )
            } else {
                repository.updateLatency(profile.id, null)
                VpnStateHolder.appendLog(
                    "TEST",
                    "Sin respuesta en ${profile.host}:${profile.port}: ${result.errorDetail}",
                    LogLevel.ERROR
                )
            }
            _isTestingPing.value = false
            _testingProfileId.value = null
        }
    }

    fun saveProfile(
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
    ) {
        viewModelScope.launch {
            val cleanHost = host.trim()
            val cleanVps = vpsName.trim().ifBlank { "VPSpremiumPRO" }
            val cleanName = profileName.trim().ifBlank { "$cleanVps • ${carrier.displayName} $port" }
            val cleanSni = sniHost.trim().ifBlank { carrier.defaultSniHost }
            val cleanUser = username.trim().ifBlank { VpnRepository.DEFAULT_SSH_USER }
            val cleanPayload = bhttpPayload.trim().ifBlank {
                if (protocol == TunnelProtocol.SSH_DIRECT || port == 22) {
                    BhttpPayloadBuilder.DEFAULT_SSH_CLIENT_ID
                } else {
                    BhttpPayloadBuilder.defaultPayloadFor(carrier)
                }
            }

            val existing = if (existingId > 0L) repository.getProfileById(existingId) else null
            val dns = settingsStore.settings.value.dnsProvider
            val entity = VpnProfileEntity(
                id = existingId,
                profileName = cleanName,
                vpsName = cleanVps,
                host = cleanHost,
                port = port,
                username = cleanUser,
                password = password.trim(),
                carrier = carrier.name,
                protocol = protocol.name,
                country = country.name,
                tier = tier.name,
                sniHost = cleanSni,
                bhttpPayload = cleanPayload,
                dnsPrimary = dns.primary,
                dnsSecondary = dns.secondary,
                lastLatencyMs = existing?.lastLatencyMs,
                isSelected = selectImmediately || (existing?.isSelected == true),
                isPreset = existing?.isPreset ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            val savedId = repository.saveProfile(entity)
            if (entity.isSelected) {
                syncTelemetryWithProfile(entity.copy(id = savedId))
            }
            VpnStateHolder.appendLog(
                "PERFIL",
                "Perfil guardado (${tier.badgeLabel}) en ${country.displayName}: $cleanName ($cleanHost:$port)",
                LogLevel.SUCCESS
            )
        }
    }

    fun deleteProfile(profile: VpnProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            VpnStateHolder.appendLog(
                "PERFIL",
                "Perfil eliminado: ${profile.profileName}",
                LogLevel.WARNING
            )
        }
    }

    fun runHostCheck(
        target: String,
        method: HttpCheckMethod,
        mode: HostCheckMode,
        useProxy: Boolean
    ) {
        if (_isCheckingHost.value) return
        viewModelScope.launch {
            _isCheckingHost.value = true
            _hostCheckResult.value = HostCheckerEngine.verify(
                rawTarget = target,
                method = method,
                mode = mode,
                useProxy = useProxy,
                activeProxyProfile = uiState.value.selectedProfile
            )
            _isCheckingHost.value = false
        }
    }

    fun analyzeVps(
        host: String,
        port: Int,
        vpsName: String,
        password: String,
        sniHost: String,
        bhttpPayload: String
    ) {
        if (_isAnalyzingVps.value) return
        viewModelScope.launch {
            _isAnalyzingVps.value = true
            VpnStateHolder.appendLog("VPS", "Iniciando análisis completo de $host:$port…", LogLevel.INFO)
            val report = VpsAnalyzerEngine.analyze(
                rawHost = host,
                targetPort = port,
                vpsName = vpsName,
                password = password,
                sniHost = sniHost,
                bhttpPayload = bhttpPayload
            )
            _vpsAnalysisReport.value = report
            _isAnalyzingVps.value = false
            VpnStateHolder.appendLog(
                "VPS",
                "Análisis ${report.host}:${report.targetPort} -> ${report.grade.label} (${report.score}/100, avg=${report.avgLatencyMs ?: "--"}ms)",
                if (report.score >= 55) LogLevel.SUCCESS else LogLevel.WARNING
            )
        }
    }

    fun toggleHotspotProxy(enable: Boolean) {
        if (enable) {
            HotspotProxyManager.startProxy()
            VpnStateHolder.appendLog(
                "HOTSPOT",
                "Proxy Wi-Fi activo en ${HotspotProxyManager.getLocalWifiIpAddress()}:${HotspotProxyManager.PROXY_PORT}",
                LogLevel.SUCCESS
            )
        } else {
            HotspotProxyManager.stopProxy()
            VpnStateHolder.appendLog("HOTSPOT", "Proxy Wi-Fi detenido", LogLevel.INFO)
        }
    }

    fun setPerformanceMode(mode: VpnPerformanceMode) {
        settingsStore.update { it.copy(performanceMode = mode, mtu = mode.mtu) }
        VpnStateHolder.appendLog("MODO", "Modo VPN cambiado a ${mode.title} (MTU ${mode.mtu})", LogLevel.INFO)
    }

    fun saveEngineSettings(
        showSpeedPanel: Boolean,
        dnsProvider: DnsProvider,
        fullTunnel: Boolean,
        autoReconnect: Boolean,
        mtu: Int,
        smartAlerts: Boolean
    ) {
        settingsStore.update {
            it.copy(
                showSpeedPanel = showSpeedPanel,
                dnsProvider = dnsProvider,
                fullTunnel = fullTunnel,
                autoReconnect = autoReconnect,
                mtu = mtu.coerceIn(1200, 1500),
                smartAlerts = smartAlerts
            )
        }
        VpnStateHolder.appendLog(
            "CONFIG",
            "Configuración guardada • ${dnsProvider.label} • MTU $mtu",
            LogLevel.SUCCESS
        )
    }

    fun updateUserAlias(alias: String) {
        val clean = alias.trim().ifBlank { "jeffers_user" }
        settingsStore.update { it.copy(userAlias = clean) }
        viewModelScope.launch { pushCurrentUserProfileToCloud() }
    }

    fun updateUserCountry(country: CountryRegion) {
        settingsStore.update { it.copy(userCountry = country) }
        viewModelScope.launch { pushCurrentUserProfileToCloud() }
    }

    fun clearAuthFeedback() {
        _authFeedback.value = null
    }

    fun registerNewAccount(
        alias: String,
        country: CountryRegion,
        activationCode: String?,
        onSuccess: () -> Unit
    ) {
        val cleanAlias = alias.trim()
        val hasMinLen = cleanAlias.length >= 6
        val hasDigit = cleanAlias.any { it.isDigit() }
        if (!hasMinLen || !hasDigit) {
            _authFeedback.value = false to "El nombre debe tener mínimo 6 caracteres e incluir al menos un número."
            return
        }

        viewModelScope.launch {
            val currentDeviceId = settingsStore.settings.value.deviceId
            val trimmedCode = activationCode?.trim().orEmpty()
            var resolvedSubscription = settingsStore.settings.value.activeSubscription

            if (trimmedCode.isNotEmpty()) {
                val cloudCode = cloudRepo.fetchLicenseCodeOnce(trimmedCode).getOrNull()
                if (cloudCode != null) {
                    val now = System.currentTimeMillis()
                    if (cloudCode.revoked) {
                        _authFeedback.value = false to "Este código fue revocado por el administrador."
                        return@launch
                    }
                    if (cloudCode.expiresAtMillis < now) {
                        _authFeedback.value = false to "Este código ya venció."
                        return@launch
                    }
                    if (!cloudCode.boundDeviceId.isNullOrBlank() &&
                        !cloudCode.boundDeviceId.equals(currentDeviceId, ignoreCase = true)
                    ) {
                        _authFeedback.value = false to "Código asignado al ID ${cloudCode.boundDeviceId}."
                        return@launch
                    }
                    val tier = PlanTier.fromName(cloudCode.tier)
                    val activatedAt = cloudCode.activatedAtMillis ?: now
                    _authenticatedUid.value?.let { uid ->
                        cloudRepo.claimLicenseCode(cloudCode.code, currentDeviceId, uid)
                    }
                    resolvedSubscription = ActiveSubscription(
                        tier = tier,
                        activationCode = cloudCode.code,
                        activatedAt = activatedAt,
                        expiresAt = cloudCode.expiresAtMillis
                    )
                } else {
                    when (val outcome = repository.redeemCode(trimmedCode, currentDeviceId)) {
                        is ActivationOutcome.Failure -> {
                            _authFeedback.value = false to outcome.reason
                            return@launch
                        }
                        is ActivationOutcome.Success -> {
                            resolvedSubscription = ActiveSubscription(
                                tier = outcome.tier,
                                activationCode = outcome.code,
                                activatedAt = outcome.activatedAt,
                                expiresAt = outcome.expiresAt
                            )
                        }
                    }
                }
            } else {
                val cloudDevice = cloudRepo.fetchDeviceLicenseOnce(currentDeviceId).getOrNull()
                if (cloudDevice != null && !cloudDevice.revoked && cloudDevice.expiresAtMillis >= System.currentTimeMillis()) {
                    val tier = PlanTier.fromName(cloudDevice.tier)
                    val activatedAt = cloudDevice.activatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                    resolvedSubscription = ActiveSubscription(
                        tier = tier,
                        activationCode = cloudDevice.code,
                        activatedAt = activatedAt,
                        expiresAt = cloudDevice.expiresAtMillis
                    )
                } else {
                    val activeForDevice = repository.findValidDeviceActivation(currentDeviceId)
                    if (activeForDevice != null) {
                        resolvedSubscription = ActiveSubscription(
                            tier = PlanTier.fromName(activeForDevice.tier),
                            activationCode = activeForDevice.code,
                            activatedAt = activeForDevice.activatedAt ?: activeForDevice.createdAt,
                            expiresAt = activeForDevice.expiresAt
                        )
                    }
                }
            }

            settingsStore.update {
                it.copy(
                    isAccountRegistered = true,
                    termsAccepted = true,
                    userCountry = country,
                    userAlias = cleanAlias,
                    activeSubscription = resolvedSubscription
                )
            }
            pushCurrentUserProfileToCloud()
            _authFeedback.value = null
            VpnStateHolder.appendLog(
                "CUENTA",
                "Cuenta registrada: $cleanAlias (${country.titleName}) • Plan ${resolvedSubscription.tier.badgeLabel}",
                LogLevel.SUCCESS
            )
            onSuccess()
        }
    }

    fun signInWithPreviousDevice(
        previousDeviceId: String,
        alias: String?,
        country: CountryRegion,
        activationCode: String?,
        onSuccess: () -> Unit
    ) {
        val cleanId = previousDeviceId.trim()
        if (cleanId.length < 6) {
            _authFeedback.value = false to "Ingresa un Device ID válido (mínimo 6 caracteres)."
            return
        }

        viewModelScope.launch {
            val trimmedCode = activationCode?.trim().orEmpty()
            var resolvedSubscription = ActiveSubscription(tier = PlanTier.FREE)

            val cloudDevice = cloudRepo.fetchDeviceLicenseOnce(cleanId).getOrNull()
            if (cloudDevice != null && !cloudDevice.revoked && cloudDevice.expiresAtMillis >= System.currentTimeMillis()) {
                val tier = PlanTier.fromName(cloudDevice.tier)
                resolvedSubscription = ActiveSubscription(
                    tier = tier,
                    activationCode = cloudDevice.code,
                    activatedAt = cloudDevice.activatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    expiresAt = cloudDevice.expiresAtMillis
                )
            } else {
                val existingForDevice = repository.findValidDeviceActivation(cleanId)
                if (existingForDevice != null) {
                    resolvedSubscription = ActiveSubscription(
                        tier = PlanTier.fromName(existingForDevice.tier),
                        activationCode = existingForDevice.code,
                        activatedAt = existingForDevice.activatedAt ?: existingForDevice.createdAt,
                        expiresAt = existingForDevice.expiresAt
                    )
                }
            }

            if (trimmedCode.isNotEmpty()) {
                val cloudCode = cloudRepo.fetchLicenseCodeOnce(trimmedCode).getOrNull()
                if (cloudCode != null) {
                    val now = System.currentTimeMillis()
                    if (cloudCode.revoked) {
                        _authFeedback.value = false to "Este código fue revocado por el administrador."
                        return@launch
                    }
                    if (cloudCode.expiresAtMillis < now) {
                        _authFeedback.value = false to "Este código ya venció."
                        return@launch
                    }
                    if (!cloudCode.boundDeviceId.isNullOrBlank() &&
                        !cloudCode.boundDeviceId.equals(cleanId, ignoreCase = true)
                    ) {
                        _authFeedback.value = false to "Código asignado al ID ${cloudCode.boundDeviceId}."
                        return@launch
                    }
                    val tier = PlanTier.fromName(cloudCode.tier)
                    val activatedAt = cloudCode.activatedAtMillis ?: now
                    _authenticatedUid.value?.let { uid ->
                        cloudRepo.claimLicenseCode(cloudCode.code, cleanId, uid)
                    }
                    resolvedSubscription = ActiveSubscription(
                        tier = tier,
                        activationCode = cloudCode.code,
                        activatedAt = activatedAt,
                        expiresAt = cloudCode.expiresAtMillis
                    )
                } else {
                    when (val outcome = repository.redeemCode(trimmedCode, cleanId)) {
                        is ActivationOutcome.Failure -> {
                            _authFeedback.value = false to outcome.reason
                            return@launch
                        }
                        is ActivationOutcome.Success -> {
                            resolvedSubscription = ActiveSubscription(
                                tier = outcome.tier,
                                activationCode = outcome.code,
                                activatedAt = outcome.activatedAt,
                                expiresAt = outcome.expiresAt
                            )
                        }
                    }
                }
            }

            val cleanAlias = alias?.trim()?.takeIf { it.isNotEmpty() }
            settingsStore.update { current ->
                current.copy(
                    isAccountRegistered = true,
                    termsAccepted = true,
                    deviceId = cleanId,
                    userCountry = country,
                    userAlias = cleanAlias ?: current.userAlias,
                    activeSubscription = resolvedSubscription
                )
            }
            pushCurrentUserProfileToCloud()
            _authFeedback.value = null
            VpnStateHolder.appendLog(
                "CUENTA",
                "Dispositivo vinculado ($cleanId) • Plan ${resolvedSubscription.tier.badgeLabel}",
                LogLevel.SUCCESS
            )
            onSuccess()
        }
    }

    fun signOutAccount() {
        _authFeedback.value = null
        _authenticatedUid.value = null
        _authenticatedEmail.value = null
        _isCloudAdmin.value = false
        settingsStore.update { it.copy(isAccountRegistered = false) }
        VpnStateHolder.appendLog("CUENTA", "Sesión cerrada — regresa al inicio con Google", LogLevel.INFO)
    }

    fun signInPersonalClient(
        username: String,
        secret: String?,
        country: CountryRegion,
        onSuccess: () -> Unit
    ) {
        val cleanUser = username.trim()
        if (cleanUser.length < 3) {
            _authFeedback.value = false to "Escribe el usuario personal (mínimo 3 caracteres)."
            return
        }

        viewModelScope.launch {
            val deviceId = settingsStore.settings.value.deviceId
            when (val outcome = activatePersonalClientWithCloudFallback(cleanUser, secret, deviceId)) {
                is ActivationOutcome.Success -> {
                    val resolvedName = outcome.clientUsername ?: cleanUser
                    settingsStore.update {
                        it.copy(
                            isAccountRegistered = true,
                            termsAccepted = true,
                            userCountry = country,
                            userAlias = resolvedName,
                            personalTunnelUsername = resolvedName,
                            personalTunnelPassword = secret?.trim()?.takeIf { s -> s.isNotEmpty() }
                                ?: it.personalTunnelPassword,
                            activeSubscription = ActiveSubscription(
                                tier = outcome.tier,
                                activationCode = outcome.code,
                                activatedAt = outcome.activatedAt,
                                expiresAt = outcome.expiresAt
                            )
                        )
                    }
                    pushCurrentUserProfileToCloud()
                    _authFeedback.value = null
                    VpnStateHolder.appendLog(
                        "CUENTA",
                        "Cliente personal '$resolvedName' conectado • Plan ${outcome.tier.badgeLabel}",
                        LogLevel.SUCCESS
                    )
                    onSuccess()
                }
                is ActivationOutcome.Failure -> {
                    _authFeedback.value = false to outcome.reason
                }
            }
        }
    }

    fun updatePersonalTunnelCredentials(username: String, password: String) {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        settingsStore.update {
            it.copy(
                personalTunnelUsername = cleanUser,
                personalTunnelPassword = cleanPass
            )
        }
        VpnStateHolder.appendLog(
            "CONFIG",
            if (cleanUser.isEmpty()) "Credenciales de cliente personal limpiadas (usando servidor por defecto)"
            else "Usuario cliente personal guardado: $cleanUser",
            LogLevel.SUCCESS
        )
    }

    fun setHomeLayoutStyle(style: HomeLayoutStyle) = settingsStore.update { it.copy(homeLayoutStyle = style) }

    fun setThemePreset(preset: ThemePreset) {
        settingsStore.update { it.copy(themePreset = preset) }
        val s = settingsStore.settings.value
        if (s.christmasSoundsEnabled) {
            ChristmasSoundEngine.playUiSparkle(s.christmasSoundStyle)
        }
    }

    fun setBackgroundEffect(effect: BackgroundEffect) = settingsStore.update { it.copy(backgroundEffect = effect) }

    fun setServerAnimation(style: ServerAnimationStyle) = settingsStore.update { it.copy(serverAnimation = style) }

    fun setChristmasSoundsEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(christmasSoundsEnabled = enabled) }
        if (enabled) {
            ChristmasSoundEngine.playUiSparkle(settingsStore.settings.value.christmasSoundStyle)
        }
    }

    fun setChristmasAmbientEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(christmasAmbientEnabled = enabled) }
        val style = settingsStore.settings.value.christmasSoundStyle
        if (enabled && settingsStore.settings.value.christmasSoundsEnabled) {
            ChristmasSoundEngine.playUiSparkle(style)
        }
    }

    fun setChristmasSoundStyle(style: ChristmasSoundStyle) {
        settingsStore.update { it.copy(christmasSoundStyle = style) }
        ChristmasSoundEngine.playCarolPreviewSnippet(style)
    }

    fun cycleChristmasSoundStyle() {
        val entries = ChristmasSoundStyle.entries
        val current = settingsStore.settings.value.christmasSoundStyle
        val next = entries[(current.ordinal + 1) % entries.size]
        setChristmasSoundStyle(next)
    }

    fun playDrawerOpenedSound() {
        val s = settingsStore.settings.value
        if (s.christmasSoundsEnabled) {
            ChristmasSoundEngine.playDrawerOpenSleigh(s.christmasSoundStyle)
        }
    }

    fun playNavigationChimeSound() {
        val s = settingsStore.settings.value
        if (s.christmasSoundsEnabled) {
            ChristmasSoundEngine.playNavChime(s.christmasSoundStyle)
        }
    }

    fun playWelcomeChimeOnce() {
        if (welcomeChimePlayed) return
        welcomeChimePlayed = true
        val s = settingsStore.settings.value
        if (s.christmasSoundsEnabled) {
            ChristmasSoundEngine.playWelcomeChristmasChime(s.christmasSoundStyle)
        }
    }

    fun playBellBurstSound() {
        val s = settingsStore.settings.value
        if (s.christmasSoundsEnabled) {
            ChristmasSoundEngine.playTestSuccessBell(s.christmasSoundStyle)
        }
    }

    fun previewChristmasSound(kind: String) {
        val style = settingsStore.settings.value.christmasSoundStyle
        when (kind) {
            "connect" -> ChristmasSoundEngine.playConnectStart(style)
            "connected" -> ChristmasSoundEngine.playConnectedFanfare(style)
            "disconnect" -> ChristmasSoundEngine.playDisconnectedChime(style)
            "bells" -> ChristmasSoundEngine.playTestSuccessBell(style)
            "carol" -> ChristmasSoundEngine.playCarolPreviewSnippet(style)
            "popurri" -> ChristmasSoundEngine.playExtendedChristmasPopurri(style)
            else -> ChristmasSoundEngine.playUiSparkle(style)
        }
    }

    fun updateTransparencies(cardAlpha: Float, drawerAlpha: Float, buttonAlpha: Float) {
        settingsStore.update {
            it.copy(
                cardAlpha = cardAlpha.coerceIn(0.3f, 1f),
                drawerAlpha = drawerAlpha.coerceIn(0.4f, 1f),
                buttonAlpha = buttonAlpha.coerceIn(0.4f, 1f)
            )
        }
    }

    fun resetAppearance() = settingsStore.resetAppearance()

    override fun onCleared() {
        ChristmasSoundEngine.stopAmbientLoop()
        super.onCleared()
    }

    fun clearLogs() = VpnStateHolder.clearLogs()

    fun clearSessionHistory() {
        viewModelScope.launch { repository.clearSessions() }
    }

    fun dismissPingBanner() {
        _lastPingResult.value = null
    }

    private fun syncTelemetryWithProfile(profile: VpnProfileEntity) {
        VpnStateHolder.updateTelemetry {
            it.copy(
                activeProfileId = profile.id,
                activeProfileName = profile.profileName,
                serverHost = profile.host,
                serverPort = profile.port,
                carrier = CarrierType.fromName(profile.carrier),
                protocol = TunnelProtocol.fromName(profile.protocol),
                pingMs = profile.lastLatencyMs,
                statusMessage = null
            )
        }
    }
}
