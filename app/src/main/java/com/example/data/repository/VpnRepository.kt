package com.example.data.repository

import com.example.data.local.ConnectionSessionEntity
import com.example.data.local.LicenseRecordEntity
import com.example.data.local.VpnDao
import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType
import com.example.data.model.CountryRegion
import com.example.data.model.PlanTier
import com.example.data.model.TunnelProtocol
import com.example.vpn.BhttpPayloadBuilder
import com.example.vpn.LicenseCodeEngine
import com.example.vpn.LicenseValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class ActivationOutcome {
    data class Success(
        val tier: PlanTier,
        val code: String,
        val activatedAt: Long,
        val expiresAt: Long,
        val clientUsername: String? = null
    ) : ActivationOutcome()

    data class Failure(val reason: String) : ActivationOutcome()
}

class VpnRepository(private val vpnDao: VpnDao) {

    private val seedMutex = Mutex()

    val profiles: Flow<List<VpnProfileEntity>> = vpnDao.observeAllProfiles()
    val selectedProfile: Flow<VpnProfileEntity?> = vpnDao.observeSelectedProfile()
    val sessions: Flow<List<ConnectionSessionEntity>> = vpnDao.observeSessions()
    val licenses: Flow<List<LicenseRecordEntity>> = vpnDao.observeLicenses()

    suspend fun ensureDefaultProfilesSeeded() {
        seedMutex.withLock {
            if (vpnDao.getProfileCount() == 0) {
                val presets = listOf(
                    VpnProfileEntity(
                        profileName = "VPSpremiumPRO • Tigo 8080",
                        vpsName = "VPSpremiumPRO",
                        host = "192.6.154.212",
                        port = 8080,
                        password = DEFAULT_VPS_PASSWORD,
                        carrier = CarrierType.TIGO.name,
                        protocol = TunnelProtocol.BHTTP.name,
                        country = CountryRegion.GENERAL.name,
                        tier = PlanTier.FREE.name,
                        sniHost = CarrierType.TIGO.defaultSniHost,
                        bhttpPayload = BhttpPayloadBuilder.DEFAULT_TIGO_PAYLOAD,
                        isSelected = true,
                        isPreset = true
                    ),
                    VpnProfileEntity(
                        profileName = "VPSpremiumPRO • Claro 80",
                        vpsName = "VPSpremiumPRO",
                        host = "192.6.154.212",
                        port = 80,
                        username = DEFAULT_SSH_USER,
                        password = DEFAULT_VPS_PASSWORD,
                        carrier = CarrierType.CLARO.name,
                        protocol = TunnelProtocol.BHTTP.name,
                        country = CountryRegion.GENERAL.name,
                        tier = PlanTier.FREE.name,
                        sniHost = CarrierType.CLARO.defaultSniHost,
                        bhttpPayload = BhttpPayloadBuilder.DEFAULT_CLARO_PAYLOAD,
                        isSelected = false,
                        isPreset = true
                    ),
                    buildGuatemalaProxyPreset(CarrierType.TIGO, port = 8080),
                    buildGuatemalaProxyPreset(CarrierType.CLARO, port = 80)
                )
                vpnDao.insertProfiles(presets)
            } else {
                // Preserve all user-configured and existing VPS profiles & credentials untouched;
                // only ensure at least one profile is marked selected if none is selected.
                if (vpnDao.getSelectedProfileOnce() == null) {
                    vpnDao.getAllProfilesOnce().firstOrNull()?.let { fallback ->
                        vpnDao.selectProfile(fallback.id)
                    }
                }
            }
        }
    }

    private fun buildGuatemalaProxyPreset(carrier: CarrierType, port: Int): VpnProfileEntity {
        val carrierLabel = if (carrier == CarrierType.CUSTOM) "Custom" else carrier.displayName
        return VpnProfileEntity(
            profileName = "Guatemala • $carrierLabel $port",
            vpsName = "VPSpremiumPRO",
            host = "192.6.154.212",
            port = port,
            username = DEFAULT_SSH_USER,
            password = DEFAULT_SSH_PASSWORD,
            carrier = carrier.name,
            protocol = TunnelProtocol.BHTTP.name,
            country = CountryRegion.GUATEMALA.name,
            tier = PlanTier.FREE.name,
            sniHost = carrier.defaultSniHost,
            bhttpPayload = BhttpPayloadBuilder.defaultPayloadFor(carrier),
            isSelected = false,
            isPreset = true
        )
    }

    suspend fun getSelectedProfile(): VpnProfileEntity? {
        ensureDefaultProfilesSeeded()
        return vpnDao.getSelectedProfileOnce()
    }

    suspend fun getProfileById(id: Long): VpnProfileEntity? = vpnDao.getProfileById(id)

    suspend fun selectProfile(id: Long) = vpnDao.selectProfile(id)

    suspend fun saveProfile(profile: VpnProfileEntity): Long {
        return if (profile.id == 0L) {
            val insertedId = vpnDao.insertProfile(profile)
            if (profile.isSelected) vpnDao.selectProfile(insertedId)
            insertedId
        } else {
            vpnDao.updateProfile(profile)
            if (profile.isSelected) vpnDao.selectProfile(profile.id)
            profile.id
        }
    }

    suspend fun deleteProfile(profile: VpnProfileEntity) {
        vpnDao.deleteProfileById(profile.id)
        if (profile.isSelected) {
            val remaining = vpnDao.getSelectedProfileOnce()
            if (remaining == null) ensureDefaultProfilesSeeded()
        }
    }

    suspend fun updateLatency(profileId: Long, latencyMs: Int?) = vpnDao.updateLatency(profileId, latencyMs)

    suspend fun updateProfileProtocol(profileId: Long, protocol: TunnelProtocol) =
        vpnDao.updateProtocol(profileId, protocol.name)

    suspend fun recordSession(session: ConnectionSessionEntity) = vpnDao.insertSession(session)

    suspend fun clearSessions() = vpnDao.clearSessions()

    suspend fun createLicenseRecord(
        tier: PlanTier,
        durationDays: Int,
        targetDeviceId: String?,
        note: String,
        clientUsername: String? = null,
        clientPassword: String? = null
    ): LicenseRecordEntity {
        val now = System.currentTimeMillis()
        val expiresAt = LicenseCodeEngine.computeExpirationForDays(durationDays, now)
        val cleanTarget = targetDeviceId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanUser = clientUsername?.trim()?.takeIf { it.isNotEmpty() }
        val cleanPass = clientPassword?.trim()?.takeIf { it.isNotEmpty() }
        val code = LicenseCodeEngine.generateCode(tier, expiresAt, cleanTarget)
        val record = LicenseRecordEntity(
            code = code,
            boundDeviceId = cleanTarget,
            clientUsername = cleanUser,
            clientPassword = cleanPass,
            tier = tier.name,
            durationDays = durationDays,
            createdAt = now,
            expiresAt = expiresAt,
            activatedAt = if (cleanTarget != null) now else null,
            activatedByDeviceId = cleanTarget,
            isRevoked = false,
            note = note.trim().ifBlank {
                when {
                    cleanUser != null -> "Cliente personal"
                    cleanTarget != null -> "Activado por ID"
                    else -> "Código de plan"
                }
            }
        )
        val id = vpnDao.insertLicense(record)
        return record.copy(id = id)
    }

    suspend fun redeemCode(rawCode: String, currentDeviceId: String): ActivationOutcome {
        val cleanCode = rawCode.trim().uppercase()
        val existing = vpnDao.findLicenseByCode(cleanCode)
        if (existing != null) {
            if (existing.isRevoked) {
                return ActivationOutcome.Failure("Este código fue revocado por el administrador.")
            }
            val now = System.currentTimeMillis()
            if (existing.expiresAt < now) {
                return ActivationOutcome.Failure("Este código ya venció.")
            }
            if (!existing.boundDeviceId.isNullOrBlank() &&
                !existing.boundDeviceId.equals(currentDeviceId, ignoreCase = true)
            ) {
                return ActivationOutcome.Failure("Código asignado al ID ${existing.boundDeviceId}.")
            }
            vpnDao.markLicenseActivated(existing.id, now, currentDeviceId)
            return ActivationOutcome.Success(
                tier = PlanTier.fromName(existing.tier),
                code = existing.code,
                activatedAt = existing.activatedAt ?: now,
                expiresAt = existing.expiresAt
            )
        }

        return when (val check = LicenseCodeEngine.validateCode(cleanCode, currentDeviceId)) {
            is LicenseValidationResult.Invalid -> ActivationOutcome.Failure(check.reason)
            is LicenseValidationResult.Valid -> {
                val now = System.currentTimeMillis()
                val decoded = check.decoded
                val days = (((decoded.expiresAtMillis - now).coerceAtLeast(0L)) / 86_400_000L).toInt().coerceAtLeast(1)
                vpnDao.insertLicense(
                    LicenseRecordEntity(
                        code = decoded.fullCode,
                        boundDeviceId = decoded.targetDevicePrefix,
                        tier = decoded.tier.name,
                        durationDays = days,
                        createdAt = now,
                        expiresAt = decoded.expiresAtMillis,
                        activatedAt = now,
                        activatedByDeviceId = currentDeviceId,
                        isRevoked = false,
                        note = "Canjeado por código"
                    )
                )
                ActivationOutcome.Success(
                    tier = decoded.tier,
                    code = decoded.fullCode,
                    activatedAt = now,
                    expiresAt = decoded.expiresAtMillis
                )
            }
        }
    }

    suspend fun activateByClientUsername(
        rawUsername: String,
        rawSecret: String?,
        currentDeviceId: String
    ): ActivationOutcome {
        val cleanUser = rawUsername.trim()
        if (cleanUser.isEmpty()) {
            return ActivationOutcome.Failure("Escribe el usuario del cliente personal.")
        }
        val existing = vpnDao.findLicenseByClientUsername(cleanUser)
            ?: return ActivationOutcome.Failure("Usuario '$cleanUser' no encontrado. Verifica con el administrador.")

        if (existing.isRevoked) {
            return ActivationOutcome.Failure("Este usuario fue revocado por el administrador.")
        }
        val now = System.currentTimeMillis()
        if (existing.expiresAt < now) {
            return ActivationOutcome.Failure("La suscripción de '$cleanUser' ya venció.")
        }
        val requiredPass = existing.clientPassword?.trim().orEmpty()
        val providedSecret = rawSecret?.trim().orEmpty()
        if (requiredPass.isNotEmpty() &&
            providedSecret != requiredPass &&
            !providedSecret.equals(existing.code, ignoreCase = true)
        ) {
            return ActivationOutcome.Failure("Clave o código incorrecto para '$cleanUser'.")
        }
        if (!existing.boundDeviceId.isNullOrBlank() &&
            !existing.boundDeviceId.equals(currentDeviceId, ignoreCase = true)
        ) {
            return ActivationOutcome.Failure("Usuario '$cleanUser' vinculado al ID ${existing.boundDeviceId}.")
        }
        vpnDao.markLicenseActivated(existing.id, now, currentDeviceId)
        return ActivationOutcome.Success(
            tier = PlanTier.fromName(existing.tier),
            code = existing.code,
            activatedAt = existing.activatedAt ?: now,
            expiresAt = existing.expiresAt,
            clientUsername = existing.clientUsername ?: cleanUser
        )
    }

    suspend fun findValidDeviceActivation(deviceId: String): LicenseRecordEntity? {
        val record = vpnDao.findActiveLicenseForDevice(deviceId) ?: return null
        return if (record.expiresAt >= System.currentTimeMillis() && !record.isRevoked) record else null
    }

    suspend fun setLicenseRevoked(id: Long, revoked: Boolean) = vpnDao.setLicenseRevoked(id, revoked)

    suspend fun getLicenseById(id: Long): LicenseRecordEntity? = vpnDao.getLicenseById(id)

    suspend fun upsertLicenseFromCloud(incoming: LicenseRecordEntity): LicenseRecordEntity {
        val byUser = incoming.clientUsername?.takeIf { it.isNotBlank() }?.let {
            vpnDao.findLicenseByClientUsername(it)
        }
        val byCode = vpnDao.findLicenseByCode(incoming.code)
        val byDevice = incoming.boundDeviceId?.takeIf { it.isNotBlank() }?.let {
            vpnDao.findLicenseByBoundDeviceId(it)
        }
        val existing = byUser ?: byCode ?: byDevice
        return if (existing != null) {
            val merged = existing.copy(
                code = incoming.code,
                boundDeviceId = incoming.boundDeviceId ?: existing.boundDeviceId,
                clientUsername = incoming.clientUsername ?: existing.clientUsername,
                tier = incoming.tier,
                durationDays = incoming.durationDays,
                expiresAt = incoming.expiresAt,
                activatedAt = incoming.activatedAt ?: existing.activatedAt,
                activatedByDeviceId = incoming.activatedByDeviceId ?: existing.activatedByDeviceId,
                isRevoked = incoming.isRevoked,
                note = incoming.note.ifBlank { existing.note }
            )
            vpnDao.updateLicense(merged)
            merged
        } else {
            val insertedId = vpnDao.insertLicense(incoming.copy(id = 0L))
            incoming.copy(id = insertedId)
        }
    }

    suspend fun extendLicense(license: LicenseRecordEntity, extraDays: Int): Pair<Long, Int> {
        val base = maxOf(license.expiresAt, System.currentTimeMillis())
        val newExpiry = LicenseCodeEngine.computeExpirationForDays(extraDays, base)
        val newDays = license.durationDays + extraDays
        vpnDao.updateLicenseExpiry(license.id, newExpiry, newDays)
        return newExpiry to newDays
    }

    suspend fun deleteLicense(id: Long) = vpnDao.deleteLicenseById(id)

    companion object {
        const val DEFAULT_VPS_HOST = "192.6.154.212"
        const val DEFAULT_VPS_NAME = "VPSpremiumPRO"
        const val DEFAULT_VPS_PASSWORD = "7K1MlZfKz5XiiyT8VYQtTDqeFmuxl5KL"
        const val DEFAULT_SSH_USER = "root"
        const val DEFAULT_SSH_PASSWORD = "0xpvujE8ZPSnd3jOvRYPN4skJGPZWD8x"
    }
}
