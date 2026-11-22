package com.example.data.model

enum class PlanTier(
    val shortCode: String,
    val displayName: String,
    val badgeLabel: String,
    val colorHex: Long,
    val featuresSummary: String
) {
    FREE(
        shortCode = "FRE",
        displayName = "Plan Gratuito",
        badgeLabel = "FREE",
        colorHex = 0xFF39D353,
        featuresSummary = "Servidores gratuitos • Velocidad estándar • Túnel BHTTP"
    ),
    PRO(
        shortCode = "PRO",
        displayName = "Plan Pro",
        badgeLabel = "PRO",
        colorHex = 0xFF00D4FF,
        featuresSummary = "Canales TV y Cine • Servidores PRO • Reconexión • DNS optimizado"
    ),
    VIP(
        shortCode = "VIP",
        displayName = "Plan VIP",
        badgeLabel = "VIP",
        colorHex = 0xFFA855F7,
        featuresSummary = "Servidores VIP • Modo Juego baja latencia • Prioridad TCP"
    ),
    PREMIUM(
        shortCode = "PRM",
        displayName = "Plan Premium",
        badgeLabel = "PREMIUM",
        colorHex = 0xFFF59E0B,
        featuresSummary = "Fútbol en Vivo • Acceso VPSpremiumPRO • Hotspot • Máximo rendimiento"
    ),
    SUPER_PRO(
        shortCode = "SPR",
        displayName = "Plan Super Pro",
        badgeLabel = "SUPER PRO",
        colorHex = 0xFF00F5D4,
        featuresSummary = "Fútbol en Vivo + Canales TV • Todo VIP/PRO/PREMIUM desbloqueado"
    );

    val includesChannels: Boolean
        get() = this == PRO || this == SUPER_PRO

    val includesLiveFootball: Boolean
        get() = this == PREMIUM || this == SUPER_PRO

    val companyQuotaPerCountry: Int
        get() = if (this == FREE) 4 else 5

    fun unlocksServerTier(requiredTier: PlanTier): Boolean =
        this == SUPER_PRO || this.ordinal >= requiredTier.ordinal

    companion object {
        fun fromName(value: String): PlanTier {
            val clean = value.trim()
            val normalized = clean.replace(" ", "_")
            return entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.shortCode.equals(clean, ignoreCase = true) ||
                    it.badgeLabel.equals(clean, ignoreCase = true)
            } ?: FREE
        }
    }
}

data class ActiveSubscription(
    val tier: PlanTier = PlanTier.FREE,
    val activationCode: String? = null,
    val activatedAt: Long = 0L,
    val expiresAt: Long = 0L
) {
    fun effectiveTier(nowMillis: Long = System.currentTimeMillis()): PlanTier =
        if (isExpired(nowMillis)) PlanTier.FREE else tier

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (tier == PlanTier.FREE && expiresAt <= 0L) return false
        return expiresAt in 1 until nowMillis
    }

    fun remainingDays(nowMillis: Long = System.currentTimeMillis()): Int {
        if (expiresAt <= 0L) return 0
        val diff = expiresAt - nowMillis
        if (diff <= 0L) return 0
        return ((diff + 86_399_999L) / 86_400_000L).toInt()
    }

    fun progressFraction(nowMillis: Long = System.currentTimeMillis()): Float {
        if (expiresAt <= 0L || activatedAt <= 0L || expiresAt <= activatedAt) {
            return if (tier == PlanTier.FREE) 1f else 0f
        }
        val total = (expiresAt - activatedAt).toFloat()
        val remaining = (expiresAt - nowMillis).coerceAtLeast(0L).toFloat()
        return (remaining / total).coerceIn(0f, 1f)
    }
}
