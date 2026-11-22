package com.example.vpn

import com.example.data.model.PlanTier
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class DecodedLicenseCode(
    val fullCode: String,
    val tier: PlanTier,
    val expiresAtMillis: Long,
    val targetDevicePrefix: String?
)

sealed class LicenseValidationResult {
    data class Valid(val decoded: DecodedLicenseCode) : LicenseValidationResult()
    data class Invalid(val reason: String) : LicenseValidationResult()
}

object LicenseCodeEngine {

    // SHA-256 of ultra-strong default admin password (with fixed salt)
    private const val ADMIN_SALT = "JeffersVPN::AdminSalt::2026"
    private const val DEFAULT_ADMIN_HASH =
        "0620c0a8554a11143217c9a6f2fabfd2670ae3a842bf39809338bf4ca3584278"
    private const val HMAC_SECRET = "JFF-VPSpremiumPRO-BHTTP-2026-Key"

    fun hashAdminPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ADMIN_SALT:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyAdminPassword(input: String, storedHash: String?): Boolean {
        val expected = storedHash?.takeIf { it.length == 64 } ?: DEFAULT_ADMIN_HASH
        return hashAdminPassword(input) == expected
    }

    fun isUltraStrongAdminPassword(password: String): Boolean {
        if (password.length < 12) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        return hasUpper && hasLower && hasDigit && hasSymbol
    }

    fun computeExpirationForDays(days: Int, fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            add(Calendar.DAY_OF_YEAR, days.coerceAtLeast(1))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun generateCode(
        tier: PlanTier,
        expiresAtMillis: Long,
        targetDeviceId: String?
    ): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(expiresAtMillis)
        val targetToken = targetDeviceId
            ?.let(::normalizeDeviceToken)
            ?.takeIf { it.isNotEmpty() }
            ?: "ALL"
        val sig = computeSignature(tier.shortCode, dateStr, targetToken)
        return "JFF-${tier.shortCode}-$dateStr-$targetToken-$sig"
    }

    fun validateCode(
        rawCode: String,
        currentDeviceId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LicenseValidationResult {
        val normalized = rawCode.trim().uppercase(Locale.US)
        val parts = normalized.split("-")
        if (parts.size != 5 || parts[0] != "JFF") {
            return LicenseValidationResult.Invalid("Formato inválido. Usa JFF-PLAN-FECHA-ID-FIRMA.")
        }

        val tierCode = parts[1]
        val dateStr = parts[2]
        val targetToken = parts[3]
        val providedSig = parts[4]

        val tier = PlanTier.entries.firstOrNull { it.shortCode == tierCode }
            ?: return LicenseValidationResult.Invalid("Plan desconocido en el código ($tierCode).")

        if (dateStr.length != 8 || dateStr.any { !it.isDigit() }) {
            return LicenseValidationResult.Invalid("Fecha de vencimiento inválida en el código.")
        }

        val expectedSig = computeSignature(tierCode, dateStr, targetToken)
        if (providedSig != expectedSig) {
            return LicenseValidationResult.Invalid("Firma de código inválida o alterada.")
        }

        val parsedExpiry = parseEndOfDay(dateStr)
            ?: return LicenseValidationResult.Invalid("No se pudo interpretar la fecha ($dateStr).")

        if (parsedExpiry < nowMillis) {
            return LicenseValidationResult.Invalid("Este código ya expiró ($dateStr).")
        }

        if (targetToken != "ALL") {
            val deviceToken = normalizeDeviceToken(currentDeviceId)
            if (!deviceToken.startsWith(targetToken)) {
                return LicenseValidationResult.Invalid(
                    "Código vinculado a otro ID ($targetToken). Tu ID es $currentDeviceId."
                )
            }
        }

        return LicenseValidationResult.Valid(
            DecodedLicenseCode(
                fullCode = normalized,
                tier = tier,
                expiresAtMillis = parsedExpiry,
                targetDevicePrefix = if (targetToken == "ALL") null else targetToken
            )
        )
    }

    private fun normalizeDeviceToken(deviceId: String): String =
        deviceId.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() }.take(8)

    private fun parseEndOfDay(yyyyMMdd: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply { isLenient = false }
            val date = sdf.parse(yyyyMMdd) ?: return null
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun computeSignature(tierCode: String, dateStr: String, targetToken: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal("$tierCode|$dateStr|$targetToken".toByteArray(Charsets.UTF_8))
        return bytes.take(2).joinToString("") { "%02X".format(it) }
    }
}
