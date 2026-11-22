package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_profiles")
data class VpnProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val vpsName: String,
    val host: String,
    val port: Int,
    val username: String = "root",
    val password: String,
    val carrier: String,
    val protocol: String,
    val country: String = "GENERAL",
    val tier: String = "FREE",
    val sniHost: String,
    val bhttpPayload: String,
    val dnsPrimary: String = "1.1.1.1",
    val dnsSecondary: String = "8.8.8.8",
    val lastLatencyMs: Int? = null,
    val isSelected: Boolean = false,
    val isPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "connection_sessions")
data class ConnectionSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val vpsName: String,
    val host: String,
    val port: Int,
    val carrier: String,
    val protocol: String,
    val durationSeconds: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val status: String,
    val startedAt: Long
)

@Entity(tableName = "license_records")
data class LicenseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val boundDeviceId: String? = null,
    val clientUsername: String? = null,
    val clientPassword: String? = null,
    val tier: String,
    val durationDays: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val activatedAt: Long? = null,
    val activatedByDeviceId: String? = null,
    val isRevoked: Boolean = false,
    val note: String = ""
)
