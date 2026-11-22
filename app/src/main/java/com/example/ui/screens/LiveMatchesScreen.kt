package com.example.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.BackgroundEffect
import com.example.data.model.PlanTier
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SPORTS_M3U_URL = "https://iptv-org.github.io/iptv/categories/sports.m3u"
private const val SPANISH_M3U_URL = "https://iptv-org.github.io/iptv/languages/spa.m3u"
private const val SOCCER_SCOREBOARD_URL =
    "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?lang=es"

private val SPORTS_CHANNEL_KEYWORDS = listOf(
    "futbol", "fútbol", "soccer", "sport", "deporte", "espn", "fox",
    "tyc", "tudn", "gol", "win", "directv", "dazn", "tigo", "claro",
    "liga", "bein", "tv max", "teledeporte", "marca", "realmadrid", "barca"
)

enum class FootballTab(val title: String) {
    CHANNELS("Canales en Vivo"),
    MATCHES("Partidos de Hoy")
}

enum class SportsChannelSource(val title: String) {
    LATAM_SPANISH("Fútbol Español"),
    ALL_SPORTS("Deportes Global")
}

data class LiveMatchItem(
    val id: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val statusText: String,
    val isLive: Boolean
)

internal fun isSportsChannel(channel: LiveSeriesChannel): Boolean {
    val haystack = "${channel.name} ${channel.group}".lowercase()
    return SPORTS_CHANNEL_KEYWORDS.any { haystack.contains(it) }
}

internal fun parseFootballScoreboardJson(rawJson: String): List<LiveMatchItem> {
    if (rawJson.isBlank()) return emptyList()
    return try {
        val root = JSONObject(rawJson)
        val events = root.optJSONArray("events") ?: return emptyList()
        val matches = ArrayList<LiveMatchItem>(events.length())
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val competitions = event.optJSONArray("competitions")
            val comp = competitions?.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeName = ""
            var awayName = ""
            var homeScore = "0"
            var awayScore = "0"
            for (c in 0 until competitors.length()) {
                val item = competitors.optJSONObject(c) ?: continue
                val side = item.optString("homeAway")
                val teamObj = item.optJSONObject("team")
                val teamName = teamObj?.optString("shortDisplayName")
                    ?.takeIf { it.isNotBlank() }
                    ?: teamObj?.optString("displayName").orEmpty()
                val score = item.optString("score", "0").ifBlank { "0" }
                if (side == "home") {
                    homeName = teamName
                    homeScore = score
                } else {
                    awayName = teamName
                    awayScore = score
                }
            }
            if (homeName.isBlank() || awayName.isBlank()) continue

            val statusObj = comp.optJSONObject("status") ?: event.optJSONObject("status")
            val typeObj = statusObj?.optJSONObject("type")
            val state = typeObj?.optString("state").orEmpty()
            val detail = typeObj?.optString("shortDetail")
                ?.takeIf { it.isNotBlank() }
                ?: typeObj?.optString("description").orEmpty().ifBlank { "Programado" }
            val league = event.optJSONObject("season")?.optString("slug")
                ?.replace('-', ' ')?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?: comp.optJSONObject("venue")?.optString("fullName").orEmpty().ifBlank { "Fútbol Internacional" }

            matches += LiveMatchItem(
                id = event.optString("id", "$i"),
                league = league,
                homeTeam = homeName,
                awayTeam = awayName,
                homeScore = homeScore,
                awayScore = awayScore,
                statusText = detail,
                isLive = state == "in"
            )
        }
        matches.sortedByDescending { it.isLive }
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun fetchUrlText(urlString: String): String = withContext(Dispatchers.IO) {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        connectTimeout = 9000
        readTimeout = 10000
        setRequestProperty("User-Agent", "JeffersVPNPro/1.0")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) error("HTTP $code")
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LiveMatchesScreen(
    onBack: () -> Unit,
    hasAccess: Boolean = true,
    activeTier: PlanTier = PlanTier.FREE,
    onOpenActivatePlan: () -> Unit = {},
    onOpenContactPlans: () -> Unit = {},
    backgroundEffect: BackgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
    modifier: Modifier = Modifier
) {
    if (!hasAccess) {
        PlanLockedScreen(
            featureTitle = "Fútbol en Vivo",
            featureSubtitle = "Canales deportivos HD y partidos de hoy en vivo",
            requiredPlanLabel = "Plan PREMIUM o SUPER PRO",
            requiredPlanColor = Color(PlanTier.PREMIUM.colorHex),
            activeTier = activeTier,
            onOpenActivatePlan = onOpenActivatePlan,
            onOpenContactPlans = onOpenContactPlans,
            onBack = onBack,
            backgroundEffect = backgroundEffect,
            modifier = modifier
        )
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val liveBadgeColor = MaterialTheme.colorScheme.error

    var activeTab by remember { mutableStateOf(FootballTab.CHANNELS) }
    var selectedSource by remember { mutableStateOf(SportsChannelSource.LATAM_SPANISH) }
    var searchQuery by remember { mutableStateOf("") }

    var channels by remember { mutableStateOf<List<LiveSeriesChannel>>(emptyList()) }
    var isLoadingChannels by remember { mutableStateOf(true) }
    var channelsError by remember { mutableStateOf<String?>(null) }

    var matches by remember { mutableStateOf<List<LiveMatchItem>>(emptyList()) }
    var isLoadingMatches by remember { mutableStateOf(false) }
    var matchesError by remember { mutableStateOf<String?>(null) }

    var activeChannel by remember { mutableStateOf<LiveSeriesChannel?>(null) }
    var playbackTrigger by remember { mutableIntStateOf(0) }
    var isDiscoveringChannel by remember { mutableStateOf(false) }
    var unavailableUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customNameInput by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("") }

    val exoPlayer = remember(context) {
        buildResilientExoPlayer(context)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                activeChannel?.streamUrl?.let { url ->
                    unavailableUrls = unavailableUrls + url
                }
                playbackError = "Señal deportiva temporalmente no disponible."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activeChannel?.id, playbackTrigger) {
        val channel = activeChannel ?: return@LaunchedEffect
        playbackError = null
        isBuffering = true
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val reachable = probeStreamReachable(channel.streamUrl)
        if (!reachable) {
            isBuffering = false
            unavailableUrls = unavailableUrls + channel.streamUrl
            playbackError = "La señal de ${channel.name} no responde en este momento."
            return@LaunchedEffect
        }

        unavailableUrls = unavailableUrls - channel.streamUrl
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun loadChannels(source: SportsChannelSource) {
        scope.launch {
            isLoadingChannels = true
            channelsError = null
            runCatching {
                when (source) {
                    SportsChannelSource.LATAM_SPANISH -> {
                        val spaBody = fetchUrlText(SPANISH_M3U_URL)
                        val spanishSports = parseM3uChannels(spaBody, limit = 400).filter(::isSportsChannel)
                        if (spanishSports.isNotEmpty()) {
                            spanishSports
                        } else {
                            parseM3uChannels(fetchUrlText(SPORTS_M3U_URL), limit = 250)
                        }
                    }
                    SportsChannelSource.ALL_SPORTS -> {
                        parseM3uChannels(fetchUrlText(SPORTS_M3U_URL), limit = 250)
                    }
                }
            }.onSuccess { list ->
                channels = list
                isLoadingChannels = false
                if (activeChannel == null && list.isNotEmpty()) {
                    isDiscoveringChannel = true
                    val discovery = discoverFirstReachableChannel(
                        channels = list,
                        maxToProbe = 15,
                        skipUrls = unavailableUrls
                    )
                    unavailableUrls = unavailableUrls + discovery.unreachableUrls
                    if (discovery.reachableChannel != null && activeChannel == null) {
                        activeChannel = discovery.reachableChannel
                        playbackTrigger++
                    }
                    isDiscoveringChannel = false
                }
            }.onFailure { err ->
                channels = emptyList()
                channelsError = err.localizedMessage ?: "No se pudieron cargar los canales deportivos."
                isLoadingChannels = false
            }
        }
    }

    fun loadMatches() {
        scope.launch {
            isLoadingMatches = true
            matchesError = null
            runCatching {
                val json = fetchUrlText(SOCCER_SCOREBOARD_URL)
                parseFootballScoreboardJson(json)
            }.onSuccess { parsed ->
                matches = parsed
            }.onFailure { err ->
                matchesError = err.localizedMessage ?: "No se pudo actualizar la cartelera de partidos."
            }
            isLoadingMatches = false
        }
    }

    LaunchedEffect(selectedSource) {
        loadChannels(selectedSource)
    }

    LaunchedEffect(Unit) {
        loadMatches()
    }

    val filteredChannels = remember(channels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) channels
        else channels.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.group.contains(q, ignoreCase = true)
        }
    }

    fun playNextChannel() {
        val list = filteredChannels.ifEmpty { channels }
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.id == activeChannel?.id }
        val rotated = if (currentIdx < 0) list
        else list.drop(currentIdx + 1) + list.take(currentIdx + 1)
        val preferred = rotated.firstOrNull { it.streamUrl !in unavailableUrls } ?: rotated.first()
        activeChannel = preferred
        playbackTrigger++
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (!isFullscreen) {
            BackgroundEffectLayer(
                effect = backgroundEffect,
                primaryColor = primaryColor,
                accentColor = accentColor,
                modifier = Modifier.alpha(0.42f)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 920.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    SubScreenTopBar(
                        title = "Fútbol en Vivo",
                        subtitle = activeChannel?.name ?: "Selecciona un canal deportivo para ver en directo",
                        onBack = onBack,
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = liveBadgeColor.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, liveBadgeColor.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = "DEPORTES HD",
                                        style = MonoTypography.terminal.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = liveBadgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showCustomUrlDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("add_custom_football_stream")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddLink,
                                        contentDescription = "Agregar enlace deportivo M3U8",
                                        tint = ChampagneGold
                                    )
                                }
                            }
                        }
                    )
                }
            }

        // Native Media3 ExoPlayer Stage (zero WebView browser chrome)
        Box(
            modifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player !== exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x88070C14),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                            else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0x88070C14),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { isFullscreen = !isFullscreen }
                        .testTag("toggle_fullscreen_football")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit
                            else Icons.Rounded.Fullscreen,
                            contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (activeChannel == null && isDiscoveringChannel) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC060A10)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Buscando señal deportiva estable...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            } else if (activeChannel == null && !isLoadingChannels) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC060A10)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsSoccer,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Elige un canal deportivo abajo para ver el partido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            } else if (isBuffering && playbackError == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (playbackError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xD8080D14))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = playbackError ?: "Señal no disponible",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                playbackTrigger++
                            },
                            border = BorderStroke(1.dp, primaryColor)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                        Button(
                            onClick = ::playNextChannel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Siguiente canal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top-level Native Tabs: Canales en Vivo vs Partidos de Hoy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FootballTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) accentColor.copy(alpha = 0.2f) else ObsidianCard,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) accentColor else OutlineSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = tab }
                                .testTag("football_tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tab == FootballTab.CHANNELS) Icons.Rounded.LiveTv
                                    else Icons.Rounded.SportsSoccer,
                                    contentDescription = null,
                                    tint = if (selected) accentColor else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) accentColor else TextMuted
                                )
                            }
                        }
                    }
                }

                if (activeTab == FootballTab.CHANNELS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SportsChannelSource.entries.forEach { source ->
                            val selected = selectedSource == source
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) primaryColor.copy(alpha = 0.18f) else ObsidianCard,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) primaryColor else OutlineSubtle
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedSource = source }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selected) primaryColor else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar canal deportivo (ESPN, Fox, TyC, TUDN...)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = OutlineSubtle,
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    when {
                        isLoadingChannels -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = accentColor)
                                    Text(
                                        text = "Cargando canales deportivos en vivo...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        channelsError != null -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ObsidianCard,
                                border = BorderStroke(1.dp, OutlineSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = channelsError ?: "Error al cargar canales",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Button(
                                        onClick = { loadChannels(selectedSource) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentColor,
                                            contentColor = ObsidianVoid
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reintentar", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        filteredChannels.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron canales con \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 18.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredChannels, key = { it.id }) { channel ->
                                    val isSelected = activeChannel?.id == channel.id
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) accentColor.copy(alpha = 0.14f) else ObsidianCard,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) accentColor else OutlineSubtle
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                activeChannel = channel
                                                playbackTrigger++
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                accentColor.copy(alpha = 0.25f),
                                                                Color(0xFF0E1824)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) accentColor else OutlineSubtle,
                                                        RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = channel.name.take(2).uppercase(),
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.ExtraBold
                                                    ),
                                                    color = if (isSelected) accentColor else Color.White
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = channel.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = channel.group,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isSelected && isPlaying) {
                                                        Text(
                                                            text = "• EN VIVO",
                                                            style = MonoTypography.terminal.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = accentColor
                                                        )
                                                    } else if (channel.streamUrl in unavailableUrls) {
                                                        Text(
                                                            text = "• Sin señal",
                                                            style = MonoTypography.terminal.copy(
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium
                                                            ),
                                                            color = TextMuted
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) accentColor else Color(0xFF14202D),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = "Reproducir ${channel.name}",
                                                        tint = if (isSelected) ObsidianVoid else accentColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Partidos de Hoy native scoreboard list
                    when {
                        isLoadingMatches -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }

                        matchesError != null -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ObsidianCard,
                                border = BorderStroke(1.dp, OutlineSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = matchesError ?: "No se pudieron cargar los partidos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Button(onClick = ::loadMatches) {
                                        Text("Actualizar marcadores")
                                    }
                                }
                            }
                        }

                        matches.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay partidos activos en este momento.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 18.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(matches, key = { it.id }) { match ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = ObsidianCard,
                                        border = BorderStroke(
                                            1.dp,
                                            if (match.isLive) liveBadgeColor.copy(alpha = 0.55f) else OutlineSubtle
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { activeTab = FootballTab.CHANNELS }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = match.league,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (match.isLive) liveBadgeColor.copy(alpha = 0.2f)
                                                    else Color(0xFF14202D)
                                                ) {
                                                    Text(
                                                        text = if (match.isLive) "EN VIVO • ${match.statusText}"
                                                        else match.statusText,
                                                        style = MonoTypography.terminal.copy(fontSize = 10.sp),
                                                        color = if (match.isLive) liveBadgeColor else TextMuted,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = match.homeTeam,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${match.homeScore}  -  ${match.awayScore}",
                                                    style = MonoTypography.metric.copy(
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 10.dp)
                                                )
                                                Text(
                                                    text = match.awayTeam,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Agregar canal deportivo (.m3u8)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text("Nombre del canal") },
                        placeholder = { Text("Ej. Fútbol HD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = { Text("URL del stream (.m3u8)") },
                        placeholder = { Text("https://.../stream.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = customUrlInput.trim().startsWith("http", ignoreCase = true),
                    onClick = {
                        val cleanUrl = customUrlInput.trim()
                        val customChannel = LiveSeriesChannel(
                            id = "custom_football_${System.currentTimeMillis()}",
                            name = customNameInput.trim().ifBlank { "Canal Deportivo" },
                            group = "Deportes Personalizado",
                            streamUrl = cleanUrl
                        )
                        channels = listOf(customChannel) + channels
                        activeChannel = customChannel
                        playbackTrigger++
                        activeTab = FootballTab.CHANNELS
                        customNameInput = ""
                        customUrlInput = ""
                        showCustomUrlDialog = false
                    }
                ) {
                    Text("Reproducir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
