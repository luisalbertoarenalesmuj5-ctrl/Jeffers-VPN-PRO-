package com.example.ui.screens

import android.content.Context
import android.security.NetworkSecurityPolicy
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.model.BackgroundEffect
import com.example.data.model.PlanTier
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal const val STREAM_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

internal data class StreamDiscoveryResult(
    val reachableChannel: LiveSeriesChannel?,
    val unreachableUrls: Set<String>
)

@OptIn(UnstableApi::class)
internal fun buildResilientExoPlayer(context: Context): ExoPlayer {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(STREAM_BROWSER_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(10_000)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .apply {
            playWhenReady = true
        }
}

internal fun isUrlAllowedByCleartextPolicy(urlString: String): Boolean {
    if (!urlString.startsWith("http://", ignoreCase = true)) {
        return urlString.startsWith("https://", ignoreCase = true)
    }
    return try {
        val host = URL(urlString).host
        if (host.isNullOrBlank()) false
        else NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)
    } catch (_: Throwable) {
        false
    }
}

/**
 * Pure JVM-safe HTTP pre-flight probe used before handing any IPTV URL to ExoPlayer.
 * Returning false avoids passing unreachable hosts (e.g. dead servers or HTML error pages)
 * into Media3's ExoPlayerImplInternal, preventing ConnectException playback errors.
 */
internal suspend fun probeStreamHttp(
    streamUrl: String,
    timeoutMs: Int = 3_200,
    urlPolicyChecker: (String) -> Boolean = { true }
): Boolean = withContext(Dispatchers.IO) {
    var currentUrl = streamUrl.trim()
    if (currentUrl.isEmpty()) return@withContext false

    repeat(5) {
        if (!urlPolicyChecker(currentUrl)) return@withContext false

        var connection: HttpURLConnection? = null
        try {
            val parsed = URL(currentUrl)
            connection = (parsed.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", STREAM_BROWSER_USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")?.trim().orEmpty()
                if (location.isEmpty()) return@withContext false
                currentUrl = URL(parsed, location).toString()
                return@repeat
            }

            if (code !in 200..299) {
                return@withContext false
            }

            val contentType = connection.contentType.orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                return@withContext false
            }

            return@withContext true
        } catch (_: Throwable) {
            return@withContext false
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }
    false
}

internal suspend fun probeStreamReachable(
    streamUrl: String,
    timeoutMs: Int = 3_200
): Boolean = probeStreamHttp(
    streamUrl = streamUrl,
    timeoutMs = timeoutMs,
    urlPolicyChecker = ::isUrlAllowedByCleartextPolicy
)

internal suspend fun discoverFirstReachableChannel(
    channels: List<LiveSeriesChannel>,
    maxToProbe: Int = 15,
    skipUrls: Set<String> = emptySet(),
    probeFn: suspend (String) -> Boolean = ::probeStreamReachable
): StreamDiscoveryResult {
    val candidates = channels
        .asSequence()
        .filter { it.streamUrl !in skipUrls }
        .take(maxToProbe)
        .toList()

    if (candidates.isEmpty()) {
        return StreamDiscoveryResult(reachableChannel = null, unreachableUrls = emptySet())
    }

    val failed = LinkedHashSet<String>()
    val batchSize = 5
    for (start in candidates.indices step batchSize) {
        val batch = candidates.subList(start, minOf(start + batchSize, candidates.size))
        val results = coroutineScope {
            batch.map { channel ->
                async {
                    channel to runCatching { probeFn(channel.streamUrl) }.getOrDefault(false)
                }
            }.awaitAll()
        }

        for ((channel, isReachable) in results) {
            if (isReachable) {
                return StreamDiscoveryResult(
                    reachableChannel = channel,
                    unreachableUrls = failed
                )
            } else {
                failed += channel.streamUrl
            }
        }
    }

    return StreamDiscoveryResult(reachableChannel = null, unreachableUrls = failed)
}

@Composable
internal fun PlanLockedScreen(
    featureTitle: String,
    featureSubtitle: String,
    requiredPlanLabel: String,
    requiredPlanColor: Color,
    activeTier: PlanTier,
    onOpenActivatePlan: () -> Unit,
    onOpenContactPlans: () -> Unit,
    onBack: () -> Unit,
    backgroundEffect: BackgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
    modifier: Modifier = Modifier
) {
    val activeTierColor = Color(activeTier.colorHex)
    val proColor = Color(PlanTier.PRO.colorHex)
    val premiumColor = Color(PlanTier.PREMIUM.colorHex)
    val superProColor = Color(PlanTier.SUPER_PRO.colorHex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = backgroundEffect,
            primaryColor = MaterialTheme.colorScheme.primary,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.alpha(0.48f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SubScreenTopBar(
                    title = featureTitle,
                    subtitle = featureSubtitle,
                    onBack = onBack
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianGlass,
                    border = BorderStroke(1.2.dp, requiredPlanColor.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan_locked_card")
                ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(requiredPlanColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = requiredPlanColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Acceso Exclusivo • $requiredPlanLabel",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = requiredPlanColor
                            )
                            Text(
                                text = "Tu plan actual es ${activeTier.badgeLabel}. Activa un plan compatible para desbloquear $featureTitle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = activeTierColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, activeTierColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "PLAN ACTUAL: ${activeTier.displayName.uppercase()} (${activeTier.badgeLabel})",
                            style = MonoTypography.terminal.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = activeTierColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Text(
                text = "PLANES DISPONIBLES",
                style = MaterialTheme.typography.labelSmall,
                color = superProColor
            )

            PlanTierComparisonCard(
                badge = "PRO",
                title = "Plan PRO — Canales en Vivo",
                detail = "Desbloquea Series en Vivo, Cine y Películas TV (canales 24/7) + Servidores PRO.",
                accent = proColor
            )
            PlanTierComparisonCard(
                badge = "PREMIUM",
                title = "Plan PREMIUM — Fútbol en Vivo",
                detail = "Desbloquea Fútbol en Vivo (canales deportivos HD + partidos de hoy) + VPSpremiumPRO + Hotspot.",
                accent = premiumColor
            )
            PlanTierComparisonCard(
                badge = "SUPER PRO",
                title = "Plan SUPER PRO — Fútbol en Vivo + Canales",
                detail = "Incluye Fútbol en Vivo + todos los Canales TV (Series y Cine) + acceso total VIP/PRO/PREMIUM.",
                accent = superProColor
            )

            Button(
                onClick = onOpenActivatePlan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = requiredPlanColor,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("locked_activate_plan_button")
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activar Código o Usuario en Mi Perfil", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenContactPlans,
                border = BorderStroke(1.dp, superProColor.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("locked_contact_plans_button")
            ) {
                Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = superProColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adquirir PRO, PREMIUM o SUPER PRO", color = superProColor, fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

@Composable
private fun PlanTierComparisonCard(
    badge: String,
    title: String,
    detail: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianGlass,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.50f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
            ) {
                Text(
                    text = badge,
                    style = MonoTypography.terminal.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
