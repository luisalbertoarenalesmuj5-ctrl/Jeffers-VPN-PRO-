package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.VpnRepository
import com.example.ui.screens.parseFootballScoreboardJson
import com.example.vpn.BhttpPayloadBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Jeffers VPN`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jeffers VPN", appName)
    }

    @Test
    fun `default profiles seed VPSpremiumPRO for Tigo 8080 and Claro 80`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VpnRepository(db.vpnDao())
            repo.ensureDefaultProfilesSeeded()
            val profiles = repo.profiles.first()

            assertEquals(4, profiles.size)
            val tigo = profiles.first { it.country == "GENERAL" && it.carrier == "TIGO" && it.port == 8080 }
            val claro = profiles.first { it.country == "GENERAL" && it.carrier == "CLARO" && it.port == 80 }
            val gtTigo8080 = profiles.first { it.country == "GUATEMALA" && it.carrier == "TIGO" && it.port == 8080 }
            val gtClaro80 = profiles.first { it.country == "GUATEMALA" && it.carrier == "CLARO" && it.port == 80 }

            assertEquals("VPSpremiumPRO", tigo.vpsName)
            assertEquals("192.6.154.212", tigo.host)
            assertEquals(8080, tigo.port)
            assertEquals("BHTTP", tigo.protocol)
            assertEquals("7K1MlZfKz5XiiyT8VYQtTDqeFmuxl5KL", tigo.password)
            assertTrue(tigo.isSelected)

            assertEquals("VPSpremiumPRO", claro.vpsName)
            assertEquals("192.6.154.212", claro.host)
            assertEquals(80, claro.port)
            assertEquals("BHTTP", claro.protocol)

            assertEquals("192.6.154.212", gtTigo8080.host)
            assertEquals(8080, gtTigo8080.port)
            assertEquals("root", gtTigo8080.username)
            assertEquals("0xpvujE8ZPSnd3jOvRYPN4skJGPZWD8x", gtTigo8080.password)
            assertEquals("BHTTP", gtTigo8080.protocol)
            assertEquals("GUATEMALA", gtTigo8080.country)

            assertEquals("192.6.154.212", gtClaro80.host)
            assertEquals(80, gtClaro80.port)
            assertEquals("root", gtClaro80.username)
            assertEquals("0xpvujE8ZPSnd3jOvRYPN4skJGPZWD8x", gtClaro80.password)
            assertEquals("BHTTP", gtClaro80.protocol)
            assertEquals("GUATEMALA", gtClaro80.country)

            val countsByCountry = com.example.data.model.CountryRegion.entries.associateWith { region ->
                profiles.count { com.example.data.model.CountryRegion.fromName(it.country) == region }
            }
            assertEquals(2, countsByCountry[com.example.data.model.CountryRegion.GENERAL])
            assertEquals(2, countsByCountry[com.example.data.model.CountryRegion.GUATEMALA])
            assertEquals(0, countsByCountry[com.example.data.model.CountryRegion.ARGENTINA])

            val compiled = BhttpPayloadBuilder.compile(gtClaro80)
            assertTrue(compiled.contains("X-VPS-Name: VPSpremiumPRO"))
            assertTrue(compiled.contains("X-VPS-Auth: 0xpvujE8ZPSnd3jOvRYPN4skJGPZWD8x"))
            assertTrue(compiled.endsWith("\r\n\r\n"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `repository supports license creation, code redemption, and device ID activation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VpnRepository(db.vpnDao())
            val premiumRecord = repo.createLicenseRecord(
                tier = com.example.data.model.PlanTier.PREMIUM,
                durationDays = 30,
                targetDeviceId = null,
                note = "Cliente VIP"
            )
            val outcome = repo.redeemCode(premiumRecord.code, "JFF-DEV001")
            assertTrue(outcome is com.example.data.repository.ActivationOutcome.Success)
            assertEquals(
                com.example.data.model.PlanTier.PREMIUM,
                (outcome as com.example.data.repository.ActivationOutcome.Success).tier
            )

            repo.createLicenseRecord(
                tier = com.example.data.model.PlanTier.VIP,
                durationDays = 15,
                targetDeviceId = "JFF-DEV999",
                note = "Activación directa"
            )
            val deviceRecord = repo.findValidDeviceActivation("JFF-DEV999")
            assertEquals("VIP", deviceRecord?.tier)
        } finally {
            db.close()
        }
    }

    @Test
    fun `country ISO detection and onboarding account persistence work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(com.example.data.model.CountryRegion.GUATEMALA, com.example.data.model.CountryRegion.fromIsoCode("gt"))
        assertEquals(com.example.data.model.CountryRegion.EL_SALVADOR, com.example.data.model.CountryRegion.fromIsoCode("SV"))

        val store = com.example.data.local.SettingsStore(context)
        store.update {
            it.copy(
                isAccountRegistered = true,
                termsAccepted = true,
                userCountry = com.example.data.model.CountryRegion.GUATEMALA,
                userAlias = "jeffers2026"
            )
        }

        val reloaded = com.example.data.local.SettingsStore(context).settings.value
        assertTrue(reloaded.isAccountRegistered)
        assertTrue(reloaded.termsAccepted)
        assertEquals(com.example.data.model.CountryRegion.GUATEMALA, reloaded.userCountry)
        assertEquals("jeffers2026", reloaded.userAlias)
    }

    @Test
    fun `personal client username activation validates password, revocation, and persists tunnel credentials`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = VpnRepository(db.vpnDao())
            val record = repo.createLicenseRecord(
                tier = com.example.data.model.PlanTier.VIP,
                durationDays = 30,
                targetDeviceId = null,
                note = "Cliente personal",
                clientUsername = "cliente_juan",
                clientPassword = "secret123"
            )

            val wrongPass = repo.activateByClientUsername("cliente_juan", "badpass", "DEV-PERSONAL")
            assertTrue(wrongPass is com.example.data.repository.ActivationOutcome.Failure)

            val ok = repo.activateByClientUsername("cliente_juan", "secret123", "DEV-PERSONAL")
            assertTrue(ok is com.example.data.repository.ActivationOutcome.Success)
            val success = ok as com.example.data.repository.ActivationOutcome.Success
            assertEquals("cliente_juan", success.clientUsername)
            assertEquals(com.example.data.model.PlanTier.VIP, success.tier)

            repo.setLicenseRevoked(record.id, true)
            val revoked = repo.activateByClientUsername("cliente_juan", "secret123", "DEV-PERSONAL")
            assertTrue(revoked is com.example.data.repository.ActivationOutcome.Failure)

            val store = com.example.data.local.SettingsStore(context)
            store.update {
                it.copy(
                    personalTunnelUsername = "cliente_juan",
                    personalTunnelPassword = "secret123"
                )
            }
            val reloaded = com.example.data.local.SettingsStore(context).settings.value
            assertEquals("cliente_juan", reloaded.personalTunnelUsername)
            assertEquals("secret123", reloaded.personalTunnelPassword)
        } finally {
            db.close()
        }
    }

    @Test
    fun `parseFootballScoreboardJson parses live football events and scores`() {
        val sampleJson = """
            {
              "events": [
                {
                  "id": "match-1",
                  "season": { "slug": "uefa-champions" },
                  "competitions": [
                    {
                      "status": { "type": { "state": "in", "shortDetail": "68'" } },
                      "competitors": [
                        { "homeAway": "home", "score": "2", "team": { "shortDisplayName": "Real Madrid" } },
                        { "homeAway": "away", "score": "1", "team": { "shortDisplayName": "Arsenal" } }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsed = parseFootballScoreboardJson(sampleJson)
        assertEquals(1, parsed.size)
        assertEquals("Real Madrid", parsed[0].homeTeam)
        assertEquals("Arsenal", parsed[0].awayTeam)
        assertEquals("2", parsed[0].homeScore)
        assertEquals("1", parsed[0].awayScore)
        assertTrue(parsed[0].isLive)
    }

    @Test
    fun `christmas sound engine synthesizes non-silent chimes and persists settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.model.ChristmasSoundStyle.entries.forEach { style ->
            val chime = com.example.audio.ChristmasSoundEngine.synthesizeChimePcm(
                totalSeconds = 0.45,
                notes = listOf(
                    com.example.audio.ChristmasSoundEngine.NoteEvent(659.25, 0.0, 0.35, 0.7),
                    com.example.audio.ChristmasSoundEngine.NoteEvent(987.77, 0.1, 0.35, 0.75)
                ),
                style = style,
                includeSleighShimmer = true
            )
            assertTrue(chime.isNotEmpty())
            assertTrue(chime.any { kotlin.math.abs(it.toInt()) > 500 })

            val ambient = com.example.audio.ChristmasSoundEngine.synthesizeAmbientLoopPcm(style)
            assertEquals(com.example.audio.ChristmasSoundEngine.SAMPLE_RATE * 8, ambient.size)
            assertTrue(ambient.any { kotlin.math.abs(it.toInt()) > 500 })
        }

        val store = com.example.data.local.SettingsStore(context)
        store.update {
            it.copy(
                christmasSoundsEnabled = true,
                christmasAmbientEnabled = true,
                christmasSoundStyle = com.example.data.model.ChristmasSoundStyle.SLEIGH_BELLS
            )
        }
        val reloaded = com.example.data.local.SettingsStore(context).settings.value
        assertTrue(reloaded.christmasSoundsEnabled)
        assertTrue(reloaded.christmasAmbientEnabled)
        assertEquals(com.example.data.model.ChristmasSoundStyle.SLEIGH_BELLS, reloaded.christmasSoundStyle)
    }

    @Test
    fun `plan tiers enforce channels in PRO, football in PREMIUM, and both in SUPER_PRO`() {
        val free = com.example.data.model.PlanTier.FREE
        val pro = com.example.data.model.PlanTier.PRO
        val premium = com.example.data.model.PlanTier.PREMIUM
        val superPro = com.example.data.model.PlanTier.SUPER_PRO

        assertTrue(!free.includesChannels && !free.includesLiveFootball)
        assertTrue(pro.includesChannels && !pro.includesLiveFootball)
        assertTrue(!premium.includesChannels && premium.includesLiveFootball)
        assertTrue(superPro.includesChannels && superPro.includesLiveFootball)

        assertEquals(superPro, com.example.data.model.PlanTier.fromName("SUPER PRO"))
        assertEquals(superPro, com.example.data.model.PlanTier.fromName("SPR"))

        val expiry = com.example.vpn.LicenseCodeEngine.computeExpirationForDays(30)
        val code = com.example.vpn.LicenseCodeEngine.generateCode(
            tier = superPro,
            expiresAtMillis = expiry,
            targetDeviceId = null
        )
        assertTrue(code.startsWith("JFF-SPR-"))
        val validation = com.example.vpn.LicenseCodeEngine.validateCode(code, "ANYDEV01")
        assertTrue(validation is com.example.vpn.LicenseValidationResult.Valid)
        assertEquals(superPro, (validation as com.example.vpn.LicenseValidationResult.Valid).decoded.tier)
    }
}
