package com.example.data.local

import android.content.Context
import android.telephony.TelephonyManager
import com.example.data.model.ActiveSubscription
import com.example.data.model.AppSettingsState
import com.example.data.model.BackgroundEffect
import com.example.data.model.ChristmasSoundStyle
import com.example.data.model.CountryRegion
import com.example.data.model.DnsProvider
import com.example.data.model.HomeLayoutStyle
import com.example.data.model.PlanTier
import com.example.data.model.ServerAnimationStyle
import com.example.data.model.ThemePreset
import com.example.data.model.VpnPerformanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jeffers_vpn_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadInitial())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    fun detectDeviceCountry(): CountryRegion {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val networkIso = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        val simIso = tm?.simCountryIso?.takeIf { it.isNotBlank() }
        val localeIso = Locale.getDefault().country.takeIf { it.isNotBlank() }
        val chosenIso = networkIso ?: simIso ?: localeIso
        val detected = CountryRegion.fromIsoCode(chosenIso)
        return if (detected == CountryRegion.GENERAL && (chosenIso.isNullOrBlank() || chosenIso.equals("US", ignoreCase = true))) {
            CountryRegion.GUATEMALA
        } else {
            detected
        }
    }

    private fun loadInitial(): AppSettingsState {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("device_id", deviceId).apply()
        }

        val savedCountry = prefs.getString("user_country", null)
        val userCountry = if (savedCountry != null) {
            CountryRegion.fromName(savedCountry)
        } else {
            detectDeviceCountry()
        }

        val rawSub = ActiveSubscription(
            tier = PlanTier.fromName(prefs.getString("plan_tier", PlanTier.FREE.name) ?: ""),
            activationCode = prefs.getString("plan_code", null),
            activatedAt = prefs.getLong("plan_activated_at", 0L),
            expiresAt = prefs.getLong("plan_expires_at", 0L)
        )
        val effectiveSub = if (rawSub.isExpired()) {
            ActiveSubscription(tier = PlanTier.FREE)
        } else {
            rawSub
        }

        val christmasEditionApplied = prefs.getBoolean("christmas_edition_2026_applied", false)
        val themePreset = if (!christmasEditionApplied) {
            ThemePreset.CHRISTMAS_LUXURY
        } else {
            ThemePreset.fromName(
                prefs.getString("theme_preset", ThemePreset.CHRISTMAS_LUXURY.name) ?: ""
            )
        }
        val backgroundEffect = if (!christmasEditionApplied) {
            BackgroundEffect.CHRISTMAS_SNOW
        } else {
            BackgroundEffect.fromName(
                prefs.getString("background_effect", BackgroundEffect.CHRISTMAS_SNOW.name) ?: ""
            )
        }
        if (!christmasEditionApplied) {
            prefs.edit()
                .putBoolean("christmas_edition_2026_applied", true)
                .putString("theme_preset", themePreset.name)
                .putString("background_effect", backgroundEffect.name)
                .apply()
        }

        val christmasAudioApplied = prefs.getBoolean("christmas_audio_2026_applied", false)
        val christmasSoundsEnabled = if (!christmasAudioApplied) {
            true
        } else {
            prefs.getBoolean("christmas_sounds_enabled", true)
        }
        val christmasAmbientEnabled = if (!christmasAudioApplied) {
            true
        } else {
            prefs.getBoolean("christmas_ambient_enabled", true)
        }
        if (!christmasAudioApplied) {
            prefs.edit()
                .putBoolean("christmas_audio_2026_applied", true)
                .putBoolean("christmas_sounds_enabled", true)
                .putBoolean("christmas_ambient_enabled", true)
                .apply()
        }

        return AppSettingsState(
            isAccountRegistered = prefs.getBoolean("is_account_registered", false),
            termsAccepted = prefs.getBoolean("terms_accepted", false),
            userCountry = userCountry,
            userAlias = prefs.getString("user_alias", "jeffers_user") ?: "jeffers_user",
            deviceId = deviceId,
            personalTunnelUsername = prefs.getString("personal_tunnel_username", "") ?: "",
            personalTunnelPassword = prefs.getString("personal_tunnel_password", "") ?: "",
            performanceMode = VpnPerformanceMode.fromName(
                prefs.getString("performance_mode", VpnPerformanceMode.BALANCED.name) ?: ""
            ),
            showSpeedPanel = prefs.getBoolean("show_speed_panel", true),
            dnsProvider = DnsProvider.fromName(
                prefs.getString("dns_provider", DnsProvider.CLOUDFLARE.name) ?: ""
            ),
            fullTunnel = prefs.getBoolean("full_tunnel", true),
            autoReconnect = prefs.getBoolean("auto_reconnect", false),
            mtu = prefs.getInt("mtu", 1400),
            smartAlerts = prefs.getBoolean("smart_alerts", true),
            homeLayoutStyle = HomeLayoutStyle.fromName(
                prefs.getString("home_layout_style", HomeLayoutStyle.SPHERE.name) ?: ""
            ),
            themePreset = themePreset,
            backgroundEffect = backgroundEffect,
            serverAnimation = ServerAnimationStyle.fromName(
                prefs.getString("server_animation", ServerAnimationStyle.GALAXY.name) ?: ""
            ),
            cardAlpha = prefs.getFloat("card_alpha", 0.84f),
            drawerAlpha = prefs.getFloat("drawer_alpha", 0.92f),
            buttonAlpha = prefs.getFloat("button_alpha", 1.0f),
            christmasSoundsEnabled = christmasSoundsEnabled,
            christmasAmbientEnabled = christmasAmbientEnabled,
            christmasSoundStyle = ChristmasSoundStyle.fromName(
                prefs.getString("christmas_sound_style", ChristmasSoundStyle.CELESTA_LUXURY.name) ?: ""
            ),
            activeSubscription = effectiveSub,
            adminPasswordHash = prefs.getString("admin_password_hash", null)
        )
    }

    fun update(transform: (AppSettingsState) -> AppSettingsState) {
        _settings.update { current ->
            val updated = transform(current)
            persist(updated)
            updated
        }
    }

    fun resetAppearance() {
        update {
            it.copy(
                themePreset = ThemePreset.CHRISTMAS_LUXURY,
                backgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
                serverAnimation = ServerAnimationStyle.GALAXY,
                cardAlpha = 0.84f,
                drawerAlpha = 0.92f,
                buttonAlpha = 1.0f,
                christmasSoundsEnabled = true,
                christmasSoundStyle = ChristmasSoundStyle.CELESTA_LUXURY
            )
        }
    }

    private fun persist(state: AppSettingsState) {
        prefs.edit()
            .putBoolean("is_account_registered", state.isAccountRegistered)
            .putBoolean("terms_accepted", state.termsAccepted)
            .putString("user_country", state.userCountry.name)
            .putString("user_alias", state.userAlias)
            .putString("device_id", state.deviceId)
            .putString("personal_tunnel_username", state.personalTunnelUsername)
            .putString("personal_tunnel_password", state.personalTunnelPassword)
            .putString("performance_mode", state.performanceMode.name)
            .putBoolean("show_speed_panel", state.showSpeedPanel)
            .putString("dns_provider", state.dnsProvider.name)
            .putBoolean("full_tunnel", state.fullTunnel)
            .putBoolean("auto_reconnect", state.autoReconnect)
            .putInt("mtu", state.mtu)
            .putBoolean("smart_alerts", state.smartAlerts)
            .putString("home_layout_style", state.homeLayoutStyle.name)
            .putString("theme_preset", state.themePreset.name)
            .putString("background_effect", state.backgroundEffect.name)
            .putString("server_animation", state.serverAnimation.name)
            .putFloat("card_alpha", state.cardAlpha)
            .putFloat("drawer_alpha", state.drawerAlpha)
            .putFloat("button_alpha", state.buttonAlpha)
            .putBoolean("christmas_sounds_enabled", state.christmasSoundsEnabled)
            .putBoolean("christmas_ambient_enabled", state.christmasAmbientEnabled)
            .putString("christmas_sound_style", state.christmasSoundStyle.name)
            .putString("plan_tier", state.activeSubscription.tier.name)
            .putString("plan_code", state.activeSubscription.activationCode)
            .putLong("plan_activated_at", state.activeSubscription.activatedAt)
            .putLong("plan_expires_at", state.activeSubscription.expiresAt)
            .putString("admin_password_hash", state.adminPasswordHash)
            .apply()
    }
}
