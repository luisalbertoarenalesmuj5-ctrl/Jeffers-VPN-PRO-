package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.LicenseRecordEntity
import com.example.data.model.PlanTier
import com.example.vpn.LicenseCodeEngine
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class CloudDeviceLicense(
    val deviceId: String = "",
    val code: String = "",
    val tier: String = PlanTier.FREE.name,
    val durationDays: Int = 30,
    val expiresAtMillis: Long = 0L,
    val revoked: Boolean = false,
    val note: String = "",
    val issuerUid: String = "",
    val claimedByUid: String? = null,
    val activatedAtMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudLicenseCode(
    val code: String = "",
    val tier: String = PlanTier.FREE.name,
    val durationDays: Int = 30,
    val expiresAtMillis: Long = 0L,
    val boundDeviceId: String? = null,
    val revoked: Boolean = false,
    val note: String = "",
    val issuerUid: String = "",
    val claimedByUid: String? = null,
    val activatedByDeviceId: String? = null,
    val activatedAtMillis: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudPersonalClient(
    val usernameKey: String = "",
    val displayUsername: String = "",
    val code: String = "",
    val tier: String = PlanTier.FREE.name,
    val durationDays: Int = 30,
    val expiresAtMillis: Long = 0L,
    val hasPassword: Boolean = false,
    val passwordHash: String = "",
    val boundDeviceId: String? = null,
    val revoked: Boolean = false,
    val note: String = "",
    val issuerUid: String = "",
    val claimedByUid: String? = null,
    val activatedByDeviceId: String? = null,
    val activatedAtMillis: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

class CloudLicenseRepository(context: Context) {

    private val appContext = context.applicationContext

    private val db: FirebaseFirestore? by lazy {
        if (!GoogleAuthManager.isFirebaseAvailable(appContext)) {
            null
        } else {
            val databaseId = appContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(databaseId)
        }
    }

    fun isSignedIn(): Boolean = try {
        GoogleAuthManager.isFirebaseAvailable(appContext) && Firebase.auth.currentUser != null
    } catch (_: Exception) {
        false
    }

    fun currentEmail(): String? = try {
        if (GoogleAuthManager.isFirebaseAvailable(appContext)) Firebase.auth.currentUser?.email else null
    } catch (_: Exception) {
        null
    }

    fun isVerifiedCloudAdmin(): Boolean {
        val user = try {
            if (GoogleAuthManager.isFirebaseAvailable(appContext)) Firebase.auth.currentUser else null
        } catch (_: Exception) {
            null
        } ?: return false
        return user.email?.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true) == true
    }

    suspend fun ensureAdminRecordIfEligible(): Result<Boolean> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val user = Firebase.auth.currentUser ?: return Result.failure(IllegalStateException("Sesión requerida"))
        val email = user.email.orEmpty()
        if (!email.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true)) {
            return Result.success(false)
        }
        val path = "admins/${user.uid}"
        return try {
            val docRef = firestore.collection("admins").document(user.uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "email" to email,
                    "role" to "admin",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun syncUserProfile(
        uid: String,
        email: String?,
        displayName: String,
        userCountry: String,
        deviceId: String,
        activeTier: PlanTier,
        activationCode: String?,
        expiresAtMillis: Long
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val path = "users/$uid"
        return try {
            val docRef = firestore.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanAlias = displayName.trim().ifBlank { "jeffers_user" }.take(80)
            val cleanDeviceId = normalizeDeviceIdKey(deviceId)
            val cleanCode = activationCode?.trim().orEmpty().take(80)
            val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)

            if (!snap.exists()) {
                val createMap = mapOf<String, Any?>(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "displayName" to cleanAlias,
                    "userCountry" to userCountry.take(40),
                    "deviceId" to cleanDeviceId,
                    "activeTier" to activeTier.name,
                    "activationCode" to cleanCode,
                    "expiresAtMillis" to expiresAtMillis.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf<String, Any?>(
                    "email" to cleanEmail,
                    "displayName" to cleanAlias,
                    "userCountry" to userCountry.take(40),
                    "deviceId" to cleanDeviceId,
                    "activeTier" to activeTier.name,
                    "activationCode" to cleanCode,
                    "expiresAtMillis" to expiresAtMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    /**
     * Real-time listener for direct "Activar por ID" updates pushed by Admin from any device.
     */
    fun observeDeviceLicense(deviceId: String): Flow<CloudDeviceLicense?> {
        val firestore = db ?: return flowOf(null)
        val key = normalizeDeviceIdKey(deviceId)
        if (!isValidDocId(key)) return flowOf(null)
        val path = "device_licenses/$key"

        return firestore.collection("device_licenses")
            .document(key)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null else snapshot.toCloudDeviceLicense()
            }
            .catch { cause ->
                val unwrapped = if (cause is CancellationException) {
                    (cause.cause as? FirebaseFirestoreException) ?: throw cause
                } else cause
                handleFirestoreError(unwrapped, OperationType.GET, path)
                emit(null)
            }
    }

    suspend fun fetchDeviceLicenseOnce(deviceId: String): Result<CloudDeviceLicense?> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = normalizeDeviceIdKey(deviceId)
        if (!isValidDocId(key)) {
            return Result.failure(IllegalArgumentException("Device ID inválido para consulta en la nube."))
        }
        val path = "device_licenses/$key"
        return try {
            val snap = firestore.collection("device_licenses").document(key).get().await()
            Result.success(if (snap.exists()) snap.toCloudDeviceLicense() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.GET, path))
        }
    }

    suspend fun claimDeviceLicense(deviceId: String, uid: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = normalizeDeviceIdKey(deviceId)
        if (!isValidDocId(key)) return Result.success(Unit)
        val path = "device_licenses/$key"
        return try {
            val now = System.currentTimeMillis()
            firestore.collection("device_licenses")
                .document(key)
                .update(
                    mapOf(
                        "claimedByUid" to uid,
                        "activatedAtMillis" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal device license claim note on $path: ${e.message}")
            Result.failure(handleFirestoreError(e, OperationType.UPDATE, path))
        }
    }

    suspend fun publishDeviceLicense(record: LicenseRecordEntity): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val user = Firebase.auth.currentUser ?: return Result.failure(IllegalStateException("Debes iniciar sesión con Google."))
        val rawId = record.boundDeviceId ?: return Result.failure(IllegalArgumentException("Falta targetDeviceId."))
        val key = normalizeDeviceIdKey(rawId)
        if (!isValidDocId(key)) {
            return Result.failure(IllegalArgumentException("El Device ID '$rawId' contiene caracteres inválidos."))
        }
        val path = "device_licenses/$key"
        return try {
            val docRef = firestore.collection("device_licenses").document(key)
            val snap = docRef.get().await()
            val activatedMillis = record.activatedAt ?: record.createdAt
            if (!snap.exists()) {
                val createMap = mapOf(
                    "deviceId" to key,
                    "code" to record.code.uppercase(Locale.US).take(80),
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "activatedAtMillis" to activatedMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "code" to record.code.uppercase(Locale.US).take(80),
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "activatedAtMillis" to activatedMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun publishLicenseCode(record: LicenseRecordEntity): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val user = Firebase.auth.currentUser ?: return Result.failure(IllegalStateException("Debes iniciar sesión con Google."))
        val key = record.code.trim().uppercase(Locale.US)
        if (!isValidDocId(key)) return Result.failure(IllegalArgumentException("Código inválido."))
        val path = "license_codes/$key"
        return try {
            val docRef = firestore.collection("license_codes").document(key)
            val snap = docRef.get().await()
            val cleanBound = record.boundDeviceId?.let(::normalizeDeviceIdKey)?.takeIf { it.isNotEmpty() }
            if (!snap.exists()) {
                val createMap = mapOf<String, Any?>(
                    "code" to key,
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "boundDeviceId" to cleanBound,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf<String, Any?>(
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "boundDeviceId" to cleanBound,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun fetchLicenseCodeOnce(rawCode: String): Result<CloudLicenseCode?> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = rawCode.trim().uppercase(Locale.US)
        if (!isValidDocId(key)) return Result.success(null)
        val path = "license_codes/$key"
        return try {
            val snap = firestore.collection("license_codes").document(key).get().await()
            Result.success(if (snap.exists()) snap.toCloudLicenseCode() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.GET, path))
        }
    }

    suspend fun claimLicenseCode(rawCode: String, deviceId: String, uid: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = rawCode.trim().uppercase(Locale.US)
        if (!isValidDocId(key)) return Result.success(Unit)
        val path = "license_codes/$key"
        return try {
            firestore.collection("license_codes")
                .document(key)
                .update(
                    mapOf(
                        "claimedByUid" to uid,
                        "activatedByDeviceId" to normalizeDeviceIdKey(deviceId),
                        "activatedAtMillis" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.UPDATE, path))
        }
    }

    suspend fun publishPersonalClient(record: LicenseRecordEntity): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val user = Firebase.auth.currentUser ?: return Result.failure(IllegalStateException("Debes iniciar sesión con Google."))
        val displayUsername = record.clientUsername?.trim().orEmpty()
        val key = normalizeUsernameKey(displayUsername)
        if (!isValidUsernameKey(key)) {
            return Result.failure(IllegalArgumentException("Usuario '$displayUsername' inválido (usa letras/números/._- mínimo 3)."))
        }
        val path = "personal_clients/$key"
        val rawPass = record.clientPassword?.trim().orEmpty()
        val hasPass = rawPass.isNotEmpty()
        val passHash = if (hasPass) hashPersonalClientPassword(key, rawPass) else ""
        val cleanBound = record.boundDeviceId?.let(::normalizeDeviceIdKey)?.takeIf { it.isNotEmpty() }

        return try {
            val docRef = firestore.collection("personal_clients").document(key)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createMap = mapOf<String, Any?>(
                    "usernameKey" to key,
                    "displayUsername" to displayUsername.take(40),
                    "code" to record.code.uppercase(Locale.US).take(80),
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "hasPassword" to hasPass,
                    "passwordHash" to passHash,
                    "boundDeviceId" to cleanBound,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf<String, Any?>(
                    "displayUsername" to displayUsername.take(40),
                    "code" to record.code.uppercase(Locale.US).take(80),
                    "tier" to record.tier,
                    "durationDays" to record.durationDays.coerceIn(1, 3650),
                    "expiresAtMillis" to record.expiresAt,
                    "hasPassword" to hasPass,
                    "passwordHash" to passHash,
                    "boundDeviceId" to cleanBound,
                    "revoked" to record.isRevoked,
                    "note" to record.note.take(120),
                    "issuerUid" to user.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun fetchPersonalClientOnce(rawUsername: String): Result<CloudPersonalClient?> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = normalizeUsernameKey(rawUsername)
        if (!isValidUsernameKey(key)) return Result.success(null)
        val path = "personal_clients/$key"
        return try {
            val snap = firestore.collection("personal_clients").document(key).get().await()
            Result.success(if (snap.exists()) snap.toCloudPersonalClient() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.GET, path))
        }
    }

    suspend fun claimPersonalClient(rawUsername: String, deviceId: String, uid: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val key = normalizeUsernameKey(rawUsername)
        if (!isValidUsernameKey(key)) return Result.success(Unit)
        val path = "personal_clients/$key"
        return try {
            firestore.collection("personal_clients")
                .document(key)
                .update(
                    mapOf(
                        "claimedByUid" to uid,
                        "activatedByDeviceId" to normalizeDeviceIdKey(deviceId),
                        "activatedAtMillis" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.UPDATE, path))
        }
    }

    suspend fun setLicenseRevokedInCloud(license: LicenseRecordEntity, revoked: Boolean): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val updates = mapOf(
            "revoked" to revoked,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            if (!license.clientUsername.isNullOrBlank()) {
                val key = normalizeUsernameKey(license.clientUsername)
                if (isValidUsernameKey(key)) {
                    firestore.collection("personal_clients").document(key).update(updates).await()
                }
            }
            if (!license.boundDeviceId.isNullOrBlank()) {
                val key = normalizeDeviceIdKey(license.boundDeviceId)
                if (isValidDocId(key)) {
                    val ref = firestore.collection("device_licenses").document(key)
                    if (ref.get().await().exists()) ref.update(updates).await()
                }
            }
            val codeKey = license.code.trim().uppercase(Locale.US)
            if (isValidDocId(codeKey)) {
                val ref = firestore.collection("license_codes").document(codeKey)
                if (ref.get().await().exists()) ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.UPDATE, "license_revoke/${license.code}"))
        }
    }

    suspend fun extendLicenseInCloud(
        license: LicenseRecordEntity,
        newExpiry: Long,
        newDays: Int
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        val updates = mapOf(
            "expiresAtMillis" to newExpiry,
            "durationDays" to newDays.coerceIn(1, 3650),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            if (!license.clientUsername.isNullOrBlank()) {
                val key = normalizeUsernameKey(license.clientUsername)
                if (isValidUsernameKey(key)) {
                    val ref = firestore.collection("personal_clients").document(key)
                    if (ref.get().await().exists()) ref.update(updates).await()
                }
            }
            if (!license.boundDeviceId.isNullOrBlank()) {
                val key = normalizeDeviceIdKey(license.boundDeviceId)
                if (isValidDocId(key)) {
                    val ref = firestore.collection("device_licenses").document(key)
                    if (ref.get().await().exists()) ref.update(updates).await()
                }
            }
            val codeKey = license.code.trim().uppercase(Locale.US)
            if (isValidDocId(codeKey)) {
                val ref = firestore.collection("license_codes").document(codeKey)
                if (ref.get().await().exists()) ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.UPDATE, "license_extend/${license.code}"))
        }
    }

    suspend fun deleteLicenseInCloud(license: LicenseRecordEntity): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        return try {
            if (!license.clientUsername.isNullOrBlank()) {
                val key = normalizeUsernameKey(license.clientUsername)
                if (isValidUsernameKey(key)) {
                    firestore.collection("personal_clients").document(key).delete().await()
                }
            }
            if (!license.boundDeviceId.isNullOrBlank()) {
                val key = normalizeDeviceIdKey(license.boundDeviceId)
                if (isValidDocId(key)) {
                    firestore.collection("device_licenses").document(key).delete().await()
                }
            }
            val codeKey = license.code.trim().uppercase(Locale.US)
            if (isValidDocId(codeKey)) {
                firestore.collection("license_codes").document(codeKey).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.DELETE, "license_delete/${license.code}"))
        }
    }

    suspend fun fetchAllAdminLicenses(): Result<List<LicenseRecordEntity>> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore no disponible"))
        return try {
            val merged = mutableListOf<LicenseRecordEntity>()
            val deviceSnaps = firestore.collection("device_licenses")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            deviceSnaps.documents.mapNotNull { it.toCloudDeviceLicense() }.forEach { d ->
                merged += LicenseRecordEntity(
                    code = d.code,
                    boundDeviceId = d.deviceId,
                    tier = d.tier,
                    durationDays = d.durationDays,
                    createdAt = d.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    expiresAt = d.expiresAtMillis,
                    activatedAt = d.activatedAtMillis.takeIf { it > 0L },
                    activatedByDeviceId = d.deviceId,
                    isRevoked = d.revoked,
                    note = d.note
                )
            }

            val personalSnaps = firestore.collection("personal_clients")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            personalSnaps.documents.mapNotNull { it.toCloudPersonalClient() }.forEach { p ->
                merged += LicenseRecordEntity(
                    code = p.code,
                    boundDeviceId = p.boundDeviceId,
                    clientUsername = p.displayUsername,
                    tier = p.tier,
                    durationDays = p.durationDays,
                    createdAt = p.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    expiresAt = p.expiresAtMillis,
                    activatedAt = p.activatedAtMillis,
                    activatedByDeviceId = p.activatedByDeviceId,
                    isRevoked = p.revoked,
                    note = p.note
                )
            }

            val codeSnaps = firestore.collection("license_codes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            codeSnaps.documents.mapNotNull { it.toCloudLicenseCode() }.forEach { c ->
                merged += LicenseRecordEntity(
                    code = c.code,
                    boundDeviceId = c.boundDeviceId,
                    tier = c.tier,
                    durationDays = c.durationDays,
                    createdAt = c.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    expiresAt = c.expiresAtMillis,
                    activatedAt = c.activatedAtMillis,
                    activatedByDeviceId = c.activatedByDeviceId,
                    isRevoked = c.revoked,
                    note = c.note
                )
            }
            Result.success(merged)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(handleFirestoreError(e, OperationType.LIST, "admin_licenses"))
        }
    }

    private fun DocumentSnapshot.toCloudDeviceLicense(): CloudDeviceLicense? {
        val deviceIdVal = getString("deviceId") ?: id
        val codeVal = getString("code") ?: return null
        val tierVal = getString("tier") ?: return null
        return CloudDeviceLicense(
            deviceId = deviceIdVal,
            code = codeVal,
            tier = tierVal,
            durationDays = (getLong("durationDays") ?: 30L).toInt(),
            expiresAtMillis = getLong("expiresAtMillis") ?: 0L,
            revoked = getBoolean("revoked") ?: false,
            note = getString("note").orEmpty(),
            issuerUid = getString("issuerUid").orEmpty(),
            claimedByUid = getString("claimedByUid"),
            activatedAtMillis = getLong("activatedAtMillis") ?: 0L,
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toCloudLicenseCode(): CloudLicenseCode? {
        val codeVal = getString("code") ?: id
        val tierVal = getString("tier") ?: return null
        return CloudLicenseCode(
            code = codeVal,
            tier = tierVal,
            durationDays = (getLong("durationDays") ?: 30L).toInt(),
            expiresAtMillis = getLong("expiresAtMillis") ?: 0L,
            boundDeviceId = getString("boundDeviceId"),
            revoked = getBoolean("revoked") ?: false,
            note = getString("note").orEmpty(),
            issuerUid = getString("issuerUid").orEmpty(),
            claimedByUid = getString("claimedByUid"),
            activatedByDeviceId = getString("activatedByDeviceId"),
            activatedAtMillis = getLong("activatedAtMillis"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toCloudPersonalClient(): CloudPersonalClient? {
        val keyVal = getString("usernameKey") ?: id
        val displayVal = getString("displayUsername") ?: keyVal
        val codeVal = getString("code") ?: return null
        val tierVal = getString("tier") ?: return null
        return CloudPersonalClient(
            usernameKey = keyVal,
            displayUsername = displayVal,
            code = codeVal,
            tier = tierVal,
            durationDays = (getLong("durationDays") ?: 30L).toInt(),
            expiresAtMillis = getLong("expiresAtMillis") ?: 0L,
            hasPassword = getBoolean("hasPassword") ?: false,
            passwordHash = getString("passwordHash").orEmpty(),
            boundDeviceId = getString("boundDeviceId"),
            revoked = getBoolean("revoked") ?: false,
            note = getString("note").orEmpty(),
            issuerUid = getString("issuerUid").orEmpty(),
            claimedByUid = getString("claimedByUid"),
            activatedByDeviceId = getString("activatedByDeviceId"),
            activatedAtMillis = getLong("activatedAtMillis"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    companion object {
        private const val TAG = "CloudLicenseRepository"
        const val PRIMARY_ADMIN_EMAIL = "luisalbertoarenalesmuj5@gmail.com"
        private const val PERSONAL_PASS_SALT = "JeffersVPN::PersonalClient::2026"
        private val DOC_ID_REGEX = Regex("^[A-Za-z0-9._-]{4,80}$")
        private val USERNAME_ID_REGEX = Regex("^[a-z0-9._-]{3,40}$")

        fun normalizeDeviceIdKey(deviceId: String): String =
            deviceId.trim().lowercase(Locale.US)

        fun normalizeUsernameKey(username: String): String =
            username.trim().lowercase(Locale.US)

        fun isValidDocId(docId: String): Boolean = DOC_ID_REGEX.matches(docId)

        fun isValidUsernameKey(usernameKey: String): Boolean = USERNAME_ID_REGEX.matches(usernameKey)

        fun hashPersonalClientPassword(usernameKey: String, rawPassword: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$PERSONAL_PASS_SALT:${usernameKey.trim().lowercase(Locale.US)}:${rawPassword.trim()}".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
