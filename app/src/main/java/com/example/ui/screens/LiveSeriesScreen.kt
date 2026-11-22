package com.example.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import java.net.HttpURLConnection
import java.net.URL

enum class SeriesCatalogSource(
    val title: String,
    val playlistUrl: String
) {
    SERIES("Series", "https://iptv-org.github.io/iptv/categories/series.m3u"),
    SPANISH("Español / Latino", "https://iptv-org.github.io/iptv/languages/spa.m3u"),
    GUATEMALA("Guatemala", "https://iptv-org.github.io/iptv/countries/gt.m3u")
}

data class LiveSeriesChannel(
    val id: String,
    val name: String,
    val group: String,
    val streamUrl: String,
    val logoUrl: String? = null
)

internal fun parseM3uChannels(rawM3u: String, limit: Int = 250): List<LiveSeriesChannel> {
    if (rawM3u.isBlank()) return emptyList()
    val lines = rawM3u.lineSequence().map { it.trim() }.toList()
    val parsed = ArrayList<LiveSeriesChannel>()
    val seenUrls = HashSet<String>()

    var pendingName = ""
    var pendingGroup = "Series en Vivo"
    var pendingLogo: String? = null

    val groupRegex = Regex("""group-title="([^"]*)"""")
    val logoRegex = Regex("""tvg-logo="([^"]*)"""")

    for (line in lines) {
        if (line.isEmpty()) continue
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "Series en Vivo"
            pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.startsWith("http") }
            val commaIdx = line.lastIndexOf(',')
            pendingName = if (commaIdx >= 0 && commaIdx + 1 < line.length) {
                line.substring(commaIdx + 1).trim()
            } else {
                ""
            }
        } else if (!line.startsWith("#")) {
            val url = line
            val isHttp = url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
            if (isHttp && seenUrls.add(url)) {
                val safeName = pendingName.ifBlank { "Canal ${parsed.size + 1}" }
                parsed += LiveSeriesChannel(
                    id = "${parsed.size}_${url.hashCode()}",
                    name = safeName,
                    group = pendingGroup,
                    streamUrl = url,
                    logoUrl = pendingLogo
                )
            }
            pendingName = ""
            pendingGroup = "Series en Vivo"
            pendingLogo = null
        }
    }

    return parsed
        .sortedWith(
            compareByDescending<LiveSeriesChannel> { it.streamUrl.startsWith("https://", ignoreCase = true) }
                .thenByDescending { it.streamUrl.contains(".m3u8", ignoreCase = true) }
        )
        .take(limit)
}

private suspend fun fetchM3uChannels(playlistUrl: String): Result<List<LiveSeriesChannel>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(playlistUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 10000
                setRequestProperty("User-Agent", "JeffersVPNPro/1.0")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("HTTP $code al cargar lista de canales")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseM3uChannels(body)
            } finally {
                conn.disconnect()
            }
        }
    }

@OptIn(UnstableApi::class)
@Composable
fun LiveSeriesScreen(
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
            featureTitle = "Series en Vivo",
            featureSubtitle = "Canales 24/7 de series y entretenimiento",
            requiredPlanLabel = "Plan PRO o SUPER PRO",
            requiredPlanColor = Color(PlanTier.PRO.colorHex),
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

    var selectedSource by remember { mutableStateOf(SeriesCatalogSource.SERIES) }
    var searchQuery by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<LiveSeriesChannel>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }

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
                playbackError = "No se pudo conectar con este canal en este momento."
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

    fun loadSource(source: SeriesCatalogSource) {
        scope.launch {
            isLoadingList = true
            listError = null
            val result = fetchM3uChannels(source.playlistUrl)
            result.onSuccess { list ->
                channels = list
                isLoadingList = false
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
                listError = err.localizedMessage ?: "No se pudieron cargar los canales."
                isLoadingList = false
            }
        }
    }

    LaunchedEffect(selectedSource) {
        loadSource(selectedSource)
    }

    val filteredChannels = remember(channels, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) channels
        else channels.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
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
                    val liveBadgeColor = MaterialTheme.colorScheme.error
                    SubScreenTopBar(
                        title = "Series en Vivo",
                        subtitle = activeChannel?.name ?: "Selecciona un canal para reproducir",
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
                                        text = "TV HD",
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
                                        .testTag("add_custom_stream_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddLink,
                                        contentDescription = "Agregar enlace M3U8",
                                        tint = ChampagneGold
                                    )
                                }
                            }
                        }
                    )
                }
            }

        // Native Media3 ExoPlayer Stage
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

            // Subtle top-right quick controls (Mute & Fullscreen)
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
                        .testTag("toggle_fullscreen_series")
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
                        text = "Buscando señal estable en vivo...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            } else if (activeChannel == null && !isLoadingList) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC060A10)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LiveTv,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Elige un canal abajo para iniciar",
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
                        text = playbackError ?: "Canal no disponible",
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
                // Native category selector pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SeriesCatalogSource.entries.forEach { source ->
                        val selected = selectedSource == source
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) primaryColor.copy(alpha = 0.2f) else ObsidianCard,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) primaryColor else OutlineSubtle
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedSource = source }
                                .testTag("series_source_${source.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) primaryColor else TextMuted
                                )
                            }
                        }
                    }
                }

                // Native Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar canal o serie...") },
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
                    isLoadingList -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = primaryColor)
                                Text(
                                    text = "Cargando guía de canales...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    listError != null -> {
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
                                    text = listError ?: "Error al cargar canales",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Button(
                                    onClick = { loadSource(selectedSource) },
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
                                    color = if (isSelected) primaryColor.copy(alpha = 0.14f) else ObsidianCard,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) primaryColor else OutlineSubtle
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
                                                            primaryColor.copy(alpha = 0.25f),
                                                            Color(0xFF0E1824)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) primaryColor else OutlineSubtle,
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = channel.name.take(2).uppercase(),
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = if (isSelected) primaryColor else Color.White
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
                                                    tint = if (isSelected) ObsidianVoid else primaryColor,
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
            }
        }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Agregar canal directo (.m3u8)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text("Nombre del canal") },
                        placeholder = { Text("Ej. Canal Series HD") },
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
                            id = "custom_${System.currentTimeMillis()}",
                            name = customNameInput.trim().ifBlank { "Canal Personalizado" },
                            group = "Personalizado",
                            streamUrl = cleanUrl
                        )
                        channels = listOf(customChannel) + channels
                        activeChannel = customChannel
                        playbackTrigger++
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
