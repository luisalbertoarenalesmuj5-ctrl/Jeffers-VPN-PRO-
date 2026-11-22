package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {

    @Query("SELECT * FROM vpn_profiles ORDER BY isSelected DESC, isPreset DESC, id ASC")
    fun observeAllProfiles(): Flow<List<VpnProfileEntity>>

    @Query("SELECT * FROM vpn_profiles WHERE isSelected = 1 LIMIT 1")
    fun observeSelectedProfile(): Flow<VpnProfileEntity?>

    @Query("SELECT * FROM vpn_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfileOnce(): VpnProfileEntity?

    @Query("SELECT * FROM vpn_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): VpnProfileEntity?

    @Query("SELECT COUNT(*) FROM vpn_profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT * FROM vpn_profiles ORDER BY id ASC")
    suspend fun getAllProfilesOnce(): List<VpnProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VpnProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<VpnProfileEntity>)

    @Update
    suspend fun updateProfile(profile: VpnProfileEntity)

    @Query("UPDATE vpn_profiles SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE vpn_profiles SET isSelected = 1 WHERE id = :id")
    suspend fun markSelected(id: Long)

    @Transaction
    suspend fun selectProfile(id: Long) {
        clearSelection()
        markSelected(id)
    }

    @Query("UPDATE vpn_profiles SET lastLatencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Int?)

    @Query("DELETE FROM vpn_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("SELECT * FROM connection_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeSessions(): Flow<List<ConnectionSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConnectionSessionEntity): Long

    @Query("DELETE FROM connection_sessions")
    suspend fun clearSessions()

    @Query("SELECT * FROM license_records ORDER BY createdAt DESC")
    fun observeLicenses(): Flow<List<LicenseRecordEntity>>

    @Query("SELECT * FROM license_records WHERE UPPER(code) = UPPER(:code) LIMIT 1")
    suspend fun findLicenseByCode(code: String): LicenseRecordEntity?

    @Query(
        "SELECT * FROM license_records WHERE boundDeviceId IS NOT NULL " +
            "AND UPPER(TRIM(boundDeviceId)) = UPPER(TRIM(:deviceId)) AND isRevoked = 0 " +
            "ORDER BY expiresAt DESC LIMIT 1"
    )
    suspend fun findActiveLicenseForDevice(deviceId: String): LicenseRecordEntity?

    @Query(
        "SELECT * FROM license_records WHERE boundDeviceId IS NOT NULL " +
            "AND UPPER(TRIM(boundDeviceId)) = UPPER(TRIM(:deviceId)) " +
            "ORDER BY expiresAt DESC LIMIT 1"
    )
    suspend fun findLicenseByBoundDeviceId(deviceId: String): LicenseRecordEntity?

    @Query("SELECT * FROM license_records WHERE id = :id LIMIT 1")
    suspend fun getLicenseById(id: Long): LicenseRecordEntity?

    @Query(
        "SELECT * FROM license_records WHERE clientUsername IS NOT NULL " +
            "AND LOWER(TRIM(clientUsername)) = LOWER(TRIM(:username)) " +
            "ORDER BY expiresAt DESC LIMIT 1"
    )
    suspend fun findLicenseByClientUsername(username: String): LicenseRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(license: LicenseRecordEntity): Long

    @Update
    suspend fun updateLicense(license: LicenseRecordEntity)

    @Query("UPDATE license_records SET activatedAt = :activatedAt, activatedByDeviceId = :deviceId WHERE id = :id")
    suspend fun markLicenseActivated(id: Long, activatedAt: Long, deviceId: String)

    @Query("UPDATE license_records SET isRevoked = :revoked WHERE id = :id")
    suspend fun setLicenseRevoked(id: Long, revoked: Boolean)

    @Query("UPDATE license_records SET expiresAt = :expiresAt, durationDays = :durationDays WHERE id = :id")
    suspend fun updateLicenseExpiry(id: Long, expiresAt: Long, durationDays: Int)

    @Query("DELETE FROM license_records WHERE id = :id")
    suspend fun deleteLicenseById(id: Long)
}
