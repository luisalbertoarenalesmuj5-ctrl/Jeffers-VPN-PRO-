package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.data.model.ChristmasSoundStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Motor de audio navideño polifónico en tiempo real (PCM 44.1 kHz).
 * Sintetiza 8 villancicos navideños reales con armonía de arpa/bajo/coro, cascabeles metálicos
 * multifrecuencia y efectos interactivos festivos sin depender de archivos externos.
 */
object ChristmasSoundEngine {

    const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var currentSfxJob: Job? = null

    @Volatile
    private var ambientJob: Job? = null

    @Volatile
    private var ambientPausedForMedia: Boolean = false

    @Volatile
    private var ambientPausedForBackground: Boolean = false

    @Volatile
    private var ambientWanted: Boolean = false

    @Volatile
    private var activeStyle: ChristmasSoundStyle = ChristmasSoundStyle.CELESTA_LUXURY

    @Volatile
    private var runningStyle: ChristmasSoundStyle? = null

    data class NoteEvent(
        val freqHz: Double,
        val startSec: Double,
        val durationSec: Double,
        val velocity: Double = 0.72
    )

    fun playWelcomeChristmasChime(style: ChristmasSoundStyle) {
        // Arpegio de bienvenida navideña (C5 -> E5 -> G5 -> C6 -> E6) con cascabeles suaves
        val notes = listOf(
            NoteEvent(523.25, 0.00, 0.36, 0.56),
            NoteEvent(659.25, 0.09, 0.38, 0.60),
            NoteEvent(783.99, 0.18, 0.44, 0.66),
            NoteEvent(1046.50, 0.27, 0.75, 0.74),
            NoteEvent(1318.51, 0.36, 0.75, 0.56),
            NoteEvent(261.63, 0.00, 1.05, 0.38)
        )
        playSynthesizedEffect(totalSeconds = 1.25, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playConnectStart(style: ChristmasSoundStyle) {
        // Motivo icónico de Jingle Bells ("E5 - E5 - E5 - G5") con cascabeles brillantes
        val notes = listOf(
            NoteEvent(659.25, 0.00, 0.24, 0.66), // E5
            NoteEvent(659.25, 0.14, 0.24, 0.66), // E5
            NoteEvent(659.25, 0.28, 0.34, 0.74), // E5
            NoteEvent(783.99, 0.44, 0.50, 0.78), // G5
            NoteEvent(261.63, 0.00, 0.85, 0.36)  // C4 base cálida
        )
        playSynthesizedEffect(totalSeconds = 1.00, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playConnectedFanfare(style: ChristmasSoundStyle) {
        // Fanfarria triunfal navideña ("E5 - G5 - C5 - D5 - E5" + acorde celestial C6/G5/C4)
        val notes = listOf(
            NoteEvent(659.25, 0.00, 0.24, 0.64), // E5
            NoteEvent(783.99, 0.12, 0.24, 0.68), // G5
            NoteEvent(523.25, 0.24, 0.32, 0.70), // C5
            NoteEvent(587.33, 0.36, 0.24, 0.68), // D5
            NoteEvent(659.25, 0.48, 0.90, 0.80), // E5
            NoteEvent(783.99, 0.50, 0.90, 0.52), // G5 acorde
            NoteEvent(1046.50, 0.52, 0.95, 0.58), // C6 brillante
            NoteEvent(1318.51, 0.56, 0.85, 0.36), // E6 destello
            NoteEvent(261.63, 0.48, 0.95, 0.46)   // C4 bajo cálido
        )
        playSynthesizedEffect(totalSeconds = 1.55, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playDisconnectedChime(style: ChristmasSoundStyle) {
        // Cadencia suave de Noche de Paz (G5 - E5 - C5 + bajo G3)
        val notes = listOf(
            NoteEvent(783.99, 0.00, 0.34, 0.60),
            NoteEvent(659.25, 0.12, 0.38, 0.56),
            NoteEvent(523.25, 0.24, 0.62, 0.60),
            NoteEvent(196.00, 0.24, 0.62, 0.30)
        )
        playSynthesizedEffect(totalSeconds = 0.92, notes = notes, style = style, includeSleighShimmer = false)
    }

    fun playErrorBell(style: ChristmasSoundStyle) {
        val notes = listOf(
            NoteEvent(329.63, 0.00, 0.32, 0.54),
            NoteEvent(261.63, 0.16, 0.48, 0.58)
        )
        playSynthesizedEffect(totalSeconds = 0.72, notes = notes, style = style, includeSleighShimmer = false)
    }

    fun playUiSparkle(style: ChristmasSoundStyle) {
        val notes = listOf(
            NoteEvent(1318.51, 0.00, 0.18, 0.44),
            NoteEvent(1975.53, 0.05, 0.24, 0.40)
        )
        playSynthesizedEffect(totalSeconds = 0.34, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playDrawerOpenSleigh(style: ChristmasSoundStyle) {
        // Cascabeles suaves ascendentes al abrir el menú navideño
        val notes = listOf(
            NoteEvent(783.99, 0.00, 0.16, 0.36),
            NoteEvent(987.77, 0.05, 0.18, 0.38),
            NoteEvent(1174.66, 0.10, 0.22, 0.40),
            NoteEvent(1567.98, 0.15, 0.28, 0.42)
        )
        playSynthesizedEffect(totalSeconds = 0.46, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playNavChime(style: ChristmasSoundStyle) {
        val notes = listOf(
            NoteEvent(783.99, 0.00, 0.18, 0.38),
            NoteEvent(1174.66, 0.06, 0.24, 0.42)
        )
        playSynthesizedEffect(totalSeconds = 0.32, notes = notes, style = style, includeSleighShimmer = false)
    }

    fun playTestSuccessBell(style: ChristmasSoundStyle) {
        // Tríada navideña cristalina al verificar latencia VPS
        val notes = listOf(
            NoteEvent(1046.50, 0.00, 0.24, 0.54), // C6
            NoteEvent(1318.51, 0.09, 0.26, 0.58), // E6
            NoteEvent(1567.98, 0.18, 0.48, 0.64), // G6
            NoteEvent(523.25, 0.18, 0.48, 0.36)   // C5 soporte
        )
        playSynthesizedEffect(totalSeconds = 0.72, notes = notes, style = style, includeSleighShimmer = true)
    }

    fun playCarolPreviewSnippet(style: ChristmasSoundStyle) {
        val score = scoreForStyle(style)
        val previewNotes = (score.melody + score.harmony).filter { it.startSec < 2.6 }
        playSynthesizedEffect(
            totalSeconds = 2.85,
            notes = previewNotes,
            style = style,
            includeSleighShimmer = true
        )
    }

    fun playExtendedChristmasPopurri(style: ChristmasSoundStyle) {
        // Popurrí festivo de 3.8 segundos combinando la frase inicial + cascabeles de catedral
        val score = scoreForStyle(style)
        val phrase = (score.melody + score.harmony).filter { it.startSec < 3.4 }
        playSynthesizedEffect(
            totalSeconds = 3.75,
            notes = phrase,
            style = style,
            includeSleighShimmer = true
        )
    }

    fun updateAmbientPreference(enabled: Boolean, style: ChristmasSoundStyle) {
        val styleChanged = activeStyle != style
        ambientWanted = enabled
        activeStyle = style
        syncAmbientPlayback(forceRestart = styleChanged)
    }

    fun pauseAmbientForMedia() {
        ambientPausedForMedia = true
        syncAmbientPlayback()
    }

    fun resumeAmbientIfEnabled() {
        ambientPausedForMedia = false
        syncAmbientPlayback()
    }

    fun pauseAmbientForAppBackground() {
        ambientPausedForBackground = true
        syncAmbientPlayback()
    }

    fun resumeAmbientFromForeground() {
        ambientPausedForBackground = false
        syncAmbientPlayback()
    }

    fun stopAmbientLoop() {
        ambientJob?.cancel()
        ambientJob = null
        runningStyle = null
    }

    private fun syncAmbientPlayback(forceRestart: Boolean = false) {
        val shouldPlay = ambientWanted && !ambientPausedForMedia && !ambientPausedForBackground
        if (!shouldPlay) {
            stopAmbientLoop()
            return
        }
        if (forceRestart || ambientJob?.isActive != true || runningStyle != activeStyle) {
            startAmbientLoop(activeStyle)
        }
    }

    private fun startAmbientLoop(style: ChristmasSoundStyle) {
        ambientJob?.cancel()
        runningStyle = style
        ambientJob = scope.launch {
            val loopSamples = synthesizeAmbientLoopPcm(style)
            val minBuffer = runCatching {
                AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            }.getOrDefault(SAMPLE_RATE / 4).coerceAtLeast(8192)

            val track = runCatching {
                AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    minBuffer * 2,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }.getOrNull() ?: return@launch

            try {
                track.play()
                val chunkSize = 4096
                while (isActive) {
                    var offset = 0
                    while (offset < loopSamples.size && isActive) {
                        val count = (loopSamples.size - offset).coerceAtMost(chunkSize)
                        val written = track.write(loopSamples, offset, count)
                        if (written < 0) break
                        offset += count
                    }
                }
            } catch (_: Throwable) {
                // Ignorar en entornos sin hardware de audio
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    private fun playSynthesizedEffect(
        totalSeconds: Double,
        notes: List<NoteEvent>,
        style: ChristmasSoundStyle,
        includeSleighShimmer: Boolean
    ) {
        currentSfxJob?.cancel()
        currentSfxJob = scope.launch {
            val pcm = synthesizeChimePcm(
                totalSeconds = totalSeconds,
                notes = notes,
                style = style,
                includeSleighShimmer = includeSleighShimmer
            )
            playOneShotPcm(pcm)
        }
    }

    private fun playOneShotPcm(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val byteLen = pcm.size * 2
        val track = runCatching {
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                byteLen.coerceAtLeast(4096),
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }.getOrNull() ?: return

        try {
            val written = track.write(pcm, 0, pcm.size)
            if (written > 0) {
                track.play()
                val durationMs = ((pcm.size.toDouble() / SAMPLE_RATE) * 1000.0).toLong() + 60L
                Thread.sleep(durationMs)
            }
        } catch (_: Throwable) {
            // Ignorar en entornos de prueba
        } finally {
            runCatching {
                track.pause()
                track.stop()
                track.release()
            }
        }
    }

    fun synthesizeChimePcm(
        totalSeconds: Double,
        notes: List<NoteEvent>,
        style: ChristmasSoundStyle,
        includeSleighShimmer: Boolean,
        masterGain: Double = 0.84
    ): ShortArray {
        val totalSamples = (totalSeconds * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val mix = DoubleArray(totalSamples)

        for (note in notes) {
            val startIdx = (note.startSec * SAMPLE_RATE).toInt().coerceIn(0, totalSamples - 1)
            val noteSamples = (note.durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
            val endIdx = (startIdx + noteSamples).coerceAtMost(totalSamples)
            val attackSamples = (0.008 * SAMPLE_RATE).toInt().coerceAtLeast(1)

            for (i in startIdx until endIdx) {
                val localIdx = i - startIdx
                val t = localIdx.toDouble() / SAMPLE_RATE
                val attack = if (localIdx < attackSamples) localIdx.toDouble() / attackSamples else 1.0
                val decayRate = when (style) {
                    ChristmasSoundStyle.CELESTA_LUXURY -> 4.6
                    ChristmasSoundStyle.SLEIGH_BELLS -> 5.0
                    ChristmasSoundStyle.CAROL_OF_THE_BELLS -> 4.8
                    ChristmasSoundStyle.DECK_THE_HALLS -> 4.4
                    ChristmasSoundStyle.ADESTE_FIDELES -> 3.9
                    ChristmasSoundStyle.RUDOLPH_BELLS -> 4.8
                    ChristmasSoundStyle.WINTER_WONDERLAND -> 4.2
                    ChristmasSoundStyle.MUSIC_BOX -> 6.4
                }
                val env = attack * exp(-decayRate * t) * note.velocity
                mix[i] += renderTimbre(note.freqHz, t, style) * env
            }
        }

        if (includeSleighShimmer || style == ChristmasSoundStyle.SLEIGH_BELLS || style == ChristmasSoundStyle.RUDOLPH_BELLS) {
            val shimmerGain = if (style == ChristmasSoundStyle.SLEIGH_BELLS || style == ChristmasSoundStyle.RUDOLPH_BELLS) 0.21 else 0.14
            listOf(0.0, 0.14, 0.28, 0.42).forEach { offsetSec ->
                addSleighBellHit(mix, totalSamples, offsetSec, gain = shimmerGain)
            }
        }

        val delay1 = (0.055 * SAMPLE_RATE).toInt()
        val delay2 = (0.118 * SAMPLE_RATE).toInt()
        val wet = DoubleArray(totalSamples)
        for (i in 0 until totalSamples) {
            var acc = mix[i]
            if (i >= delay1) acc += mix[i - delay1] * 0.30
            if (i >= delay2) acc += mix[i - delay2] * 0.16
            wet[i] = acc
        }

        return normalizeToPcm16(wet, masterGain = masterGain)
    }

    fun synthesizeAmbientLoopPcm(style: ChristmasSoundStyle): ShortArray {
        val loopSeconds = 8.0
        val totalSamples = (loopSeconds * SAMPLE_RATE).toInt()
        val mix = DoubleArray(totalSamples)

        val (melody, harmony, sleighBeats) = scoreForStyle(style)

        for (note in melody) {
            val startIdx = (note.startSec * SAMPLE_RATE).toInt()
            val noteSamples = (note.durationSec * SAMPLE_RATE).toInt()
            val attackSamples = (0.009 * SAMPLE_RATE).toInt().coerceAtLeast(1)
            for (local in 0 until noteSamples) {
                val idx = (startIdx + local) % totalSamples
                val t = local.toDouble() / SAMPLE_RATE
                val attack = if (local < attackSamples) local.toDouble() / attackSamples else 1.0
                val decay = exp(-3.3 * t)
                mix[idx] += renderTimbre(note.freqHz, t, style) * attack * decay * note.velocity
            }
        }

        for (note in harmony) {
            val startIdx = (note.startSec * SAMPLE_RATE).toInt()
            val noteSamples = (note.durationSec * SAMPLE_RATE).toInt()
            val attackSamples = (0.018 * SAMPLE_RATE).toInt().coerceAtLeast(1)
            for (local in 0 until noteSamples) {
                val idx = (startIdx + local) % totalSamples
                val t = local.toDouble() / SAMPLE_RATE
                val attack = if (local < attackSamples) local.toDouble() / attackSamples else 1.0
                val decay = exp(-2.3 * t)
                mix[idx] += renderWarmPad(note.freqHz, t) * attack * decay * note.velocity
            }
        }

        val bellGain = when (style) {
            ChristmasSoundStyle.SLEIGH_BELLS, ChristmasSoundStyle.RUDOLPH_BELLS -> 0.15
            ChristmasSoundStyle.DECK_THE_HALLS -> 0.13
            ChristmasSoundStyle.CELESTA_LUXURY, ChristmasSoundStyle.WINTER_WONDERLAND -> 0.115
            ChristmasSoundStyle.CAROL_OF_THE_BELLS, ChristmasSoundStyle.ADESTE_FIDELES -> 0.105
            ChristmasSoundStyle.MUSIC_BOX -> 0.055
        }
        sleighBeats.forEach { beatSec ->
            addSleighBellHitCircular(mix, totalSamples, beatSec, gain = bellGain)
        }

        val delay1 = (0.062 * SAMPLE_RATE).toInt()
        val delay2 = (0.145 * SAMPLE_RATE).toInt()
        val wet = DoubleArray(totalSamples)
        for (i in 0 until totalSamples) {
            val idx1 = (i - delay1 + totalSamples) % totalSamples
            val idx2 = (i - delay2 + totalSamples) % totalSamples
            wet[i] = mix[i] + mix[idx1] * 0.25 + mix[idx2] * 0.13
        }

        return normalizeToPcm16(wet, masterGain = 0.78)
    }

    private fun scoreForStyle(style: ChristmasSoundStyle): CarolScore = when (style) {
        ChristmasSoundStyle.CELESTA_LUXURY -> buildJingleBellsScore()
        ChristmasSoundStyle.SLEIGH_BELLS -> buildWeWishYouScore()
        ChristmasSoundStyle.CAROL_OF_THE_BELLS -> buildCarolOfTheBellsScore()
        ChristmasSoundStyle.DECK_THE_HALLS -> buildDeckTheHallsScore()
        ChristmasSoundStyle.ADESTE_FIDELES -> buildAdesteFidelesScore()
        ChristmasSoundStyle.RUDOLPH_BELLS -> buildRudolphScore()
        ChristmasSoundStyle.WINTER_WONDERLAND -> buildWinterWonderlandScore()
        ChristmasSoundStyle.MUSIC_BOX -> buildSilentNightScore()
    }

    private data class CarolScore(
        val melody: List<NoteEvent>,
        val harmony: List<NoteEvent>,
        val sleighBeats: List<Double>
    )

    private fun buildJingleBellsScore(): CarolScore {
        val e5 = 659.25
        val g5 = 783.99
        val c5 = 523.25
        val d5 = 587.33
        val f5 = 698.46
        val beat = 0.50

        val jingleMelody = listOf(
            NoteEvent(e5, 0.00, 0.24, 0.74),
            NoteEvent(e5, 0.25, 0.24, 0.70),
            NoteEvent(e5, 0.50, 0.45, 0.78),
            NoteEvent(e5, 1.00, 0.24, 0.74),
            NoteEvent(e5, 1.25, 0.24, 0.70),
            NoteEvent(e5, 1.50, 0.45, 0.78),
            NoteEvent(e5, 2.00, 0.24, 0.74),
            NoteEvent(g5, 2.25, 0.24, 0.78),
            NoteEvent(c5, 2.50, 0.34, 0.76),
            NoteEvent(d5, 2.86, 0.16, 0.70),
            NoteEvent(e5, 3.00, 0.92, 0.82),
            NoteEvent(f5, 4.00, 0.24, 0.74),
            NoteEvent(f5, 4.25, 0.24, 0.72),
            NoteEvent(f5, 4.50, 0.34, 0.76),
            NoteEvent(f5, 4.86, 0.16, 0.70),
            NoteEvent(f5, 5.00, 0.24, 0.74),
            NoteEvent(e5, 5.25, 0.24, 0.72),
            NoteEvent(e5, 5.50, 0.24, 0.70),
            NoteEvent(e5, 5.75, 0.14, 0.65),
            NoteEvent(e5, 5.88, 0.14, 0.65),
            NoteEvent(e5, 6.00, 0.24, 0.72),
            NoteEvent(d5, 6.25, 0.24, 0.70),
            NoteEvent(d5, 6.50, 0.24, 0.70),
            NoteEvent(e5, 6.75, 0.24, 0.74),
            NoteEvent(d5, 7.00, 0.48, 0.76),
            NoteEvent(g5, 7.50, 0.48, 0.78)
        )

        val c4 = 261.63
        val g3 = 196.00
        val f3 = 174.61
        val d4 = 293.66
        val b3 = 246.94
        val e4 = 329.63
        val a3 = 220.00

        val harmony = listOf(
            NoteEvent(c4, 0.00, 0.55, 0.36), NoteEvent(e4, 0.50, 0.45, 0.28),
            NoteEvent(c4, 1.00, 0.55, 0.36), NoteEvent(g3, 1.50, 0.45, 0.28),
            NoteEvent(c4, 2.00, 0.55, 0.36), NoteEvent(e4, 2.50, 0.45, 0.28),
            NoteEvent(c4, 3.00, 0.55, 0.38), NoteEvent(g3, 3.50, 0.45, 0.28),
            NoteEvent(f3, 4.00, 0.55, 0.36), NoteEvent(a3, 4.50, 0.45, 0.28),
            NoteEvent(c4, 5.00, 0.55, 0.36), NoteEvent(e4, 5.50, 0.45, 0.28),
            NoteEvent(d4, 6.00, 0.55, 0.34), NoteEvent(a3, 6.50, 0.45, 0.26),
            NoteEvent(g3, 7.00, 0.55, 0.38), NoteEvent(b3, 7.50, 0.45, 0.28)
        )

        val sleighBeats = List(16) { idx -> idx * beat }
        return CarolScore(jingleMelody, harmony, sleighBeats)
    }

    private fun buildWeWishYouScore(): CarolScore {
        val b = 0.3333
        val d5 = 587.33
        val g5 = 783.99
        val a5 = 880.00
        val fs5 = 739.99
        val e5 = 659.25
        val b5 = 987.77
        val c6 = 1046.50

        val melody = listOf(
            NoteEvent(d5, 0.00, 0.32, 0.72),
            NoteEvent(g5, 1 * b, 0.32, 0.76),
            NoteEvent(g5, 2 * b, 0.16, 0.68),
            NoteEvent(a5, 2.5 * b, 0.16, 0.70),
            NoteEvent(g5, 3 * b, 0.16, 0.70),
            NoteEvent(fs5, 3.5 * b, 0.16, 0.68),
            NoteEvent(e5, 4 * b, 0.32, 0.74),
            NoteEvent(e5, 5 * b, 0.32, 0.70),
            NoteEvent(e5, 6 * b, 0.32, 0.72),
            NoteEvent(a5, 7 * b, 0.32, 0.78),
            NoteEvent(a5, 8 * b, 0.16, 0.70),
            NoteEvent(b5, 8.5 * b, 0.16, 0.72),
            NoteEvent(a5, 9 * b, 0.16, 0.72),
            NoteEvent(g5, 9.5 * b, 0.16, 0.70),
            NoteEvent(fs5, 10 * b, 0.32, 0.74),
            NoteEvent(d5, 11 * b, 0.32, 0.70),
            NoteEvent(d5, 12 * b, 0.32, 0.72),
            NoteEvent(b5, 13 * b, 0.32, 0.80),
            NoteEvent(b5, 14 * b, 0.16, 0.72),
            NoteEvent(c6, 14.5 * b, 0.16, 0.76),
            NoteEvent(b5, 15 * b, 0.16, 0.74),
            NoteEvent(a5, 15.5 * b, 0.16, 0.72),
            NoteEvent(g5, 16 * b, 0.32, 0.76),
            NoteEvent(e5, 17 * b, 0.32, 0.72),
            NoteEvent(d5, 18 * b, 0.16, 0.68),
            NoteEvent(d5, 18.5 * b, 0.16, 0.68),
            NoteEvent(e5, 19 * b, 0.34, 0.74),
            NoteEvent(a5, 20 * b, 0.34, 0.78),
            NoteEvent(fs5, 21 * b, 0.34, 0.76),
            NoteEvent(g5, 22 * b, 0.65, 0.84)
        )

        val g3 = 196.00
        val c4 = 261.63
        val a3 = 220.00
        val d4 = 293.66
        val b3 = 246.94
        val e4 = 329.63
        val harmony = listOf(
            NoteEvent(g3, 1 * b, 0.90, 0.34), NoteEvent(b3, 2 * b, 0.60, 0.24),
            NoteEvent(c4, 4 * b, 0.90, 0.34), NoteEvent(e4, 5 * b, 0.60, 0.24),
            NoteEvent(a3, 7 * b, 0.90, 0.34), NoteEvent(d4, 10 * b, 0.90, 0.32),
            NoteEvent(b3, 13 * b, 0.90, 0.34), NoteEvent(e4, 16 * b, 0.90, 0.34),
            NoteEvent(c4, 19 * b, 0.60, 0.32), NoteEvent(d4, 21 * b, 0.60, 0.32),
            NoteEvent(g3, 22 * b, 0.75, 0.38)
        )

        val sleighBeats = List(12) { idx -> idx * (b * 2.0) }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildCarolOfTheBellsScore(): CarolScore {
        val bb5 = 932.33
        val a5 = 880.00
        val g5 = 783.99
        val d6 = 1174.66
        val c6 = 1046.50
        val f5 = 698.46
        val eb5 = 622.25
        val d5 = 587.33

        val g3 = 196.00
        val f3 = 174.61
        val eb3 = 155.56
        val d3 = 146.83
        val bb3 = 233.08
        val d4 = 293.66

        val melody = mutableListOf<NoteEvent>()
        for (bar in 0 until 4) {
            val t0 = bar * 1.0
            melody += NoteEvent(bb5, t0 + 0.00, 0.34, 0.78)
            melody += NoteEvent(a5, t0 + 0.33, 0.17, 0.68)
            melody += NoteEvent(bb5, t0 + 0.50, 0.17, 0.72)
            melody += NoteEvent(g5, t0 + 0.67, 0.32, 0.76)
        }
        for (bar in 4 until 6) {
            val t0 = bar * 1.0
            melody += NoteEvent(d6, t0 + 0.00, 0.34, 0.82)
            melody += NoteEvent(c6, t0 + 0.33, 0.17, 0.72)
            melody += NoteEvent(d6, t0 + 0.50, 0.17, 0.76)
            melody += NoteEvent(bb5, t0 + 0.67, 0.32, 0.80)
        }
        melody += NoteEvent(g5, 6.00, 0.30, 0.76)
        melody += NoteEvent(f5, 6.33, 0.17, 0.70)
        melody += NoteEvent(eb5, 6.50, 0.17, 0.70)
        melody += NoteEvent(d5, 6.67, 0.32, 0.74)

        melody += NoteEvent(bb5, 7.00, 0.34, 0.80)
        melody += NoteEvent(a5, 7.33, 0.17, 0.70)
        melody += NoteEvent(bb5, 7.50, 0.17, 0.74)
        melody += NoteEvent(g5, 7.67, 0.32, 0.80)

        val harmony = listOf(
            NoteEvent(g3, 0.00, 0.95, 0.42), NoteEvent(d4, 0.33, 0.60, 0.24),
            NoteEvent(f3, 1.00, 0.95, 0.42), NoteEvent(d4, 1.33, 0.60, 0.24),
            NoteEvent(eb3, 2.00, 0.95, 0.42), NoteEvent(bb3, 2.33, 0.60, 0.24),
            NoteEvent(d3, 3.00, 0.95, 0.44), NoteEvent(d4, 3.33, 0.60, 0.24),
            NoteEvent(g3, 4.00, 0.95, 0.44), NoteEvent(bb3, 4.50, 0.48, 0.28),
            NoteEvent(f3, 5.00, 0.95, 0.42), NoteEvent(d4, 5.50, 0.48, 0.26),
            NoteEvent(eb3, 6.00, 0.95, 0.42), NoteEvent(bb3, 6.50, 0.48, 0.28),
            NoteEvent(d3, 7.00, 0.95, 0.46), NoteEvent(g3, 7.50, 0.48, 0.34)
        )
        val sleighBeats = List(16) { idx -> idx * 0.50 }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildDeckTheHallsScore(): CarolScore {
        val c5 = 523.25
        val d5 = 587.33
        val e5 = 659.25
        val f5 = 698.46
        val g5 = 783.99
        val a5 = 880.00

        val melody = listOf(
            NoteEvent(g5, 0.00, 0.36, 0.78),
            NoteEvent(f5, 0.375, 0.14, 0.68),
            NoteEvent(e5, 0.50, 0.24, 0.74),
            NoteEvent(d5, 0.75, 0.24, 0.72),
            NoteEvent(c5, 1.00, 0.24, 0.76),
            NoteEvent(d5, 1.25, 0.24, 0.72),
            NoteEvent(e5, 1.50, 0.24, 0.74),
            NoteEvent(c5, 1.75, 0.24, 0.72),
            NoteEvent(d5, 2.00, 0.14, 0.70),
            NoteEvent(e5, 2.125, 0.14, 0.72),
            NoteEvent(f5, 2.25, 0.14, 0.74),
            NoteEvent(d5, 2.375, 0.14, 0.70),
            NoteEvent(e5, 2.50, 0.36, 0.78),
            NoteEvent(d5, 2.875, 0.14, 0.68),
            NoteEvent(c5, 3.00, 0.24, 0.76),
            NoteEvent(493.88, 3.25, 0.24, 0.70),
            NoteEvent(c5, 3.50, 0.48, 0.80),

            NoteEvent(g5, 4.00, 0.36, 0.78),
            NoteEvent(f5, 4.375, 0.14, 0.68),
            NoteEvent(e5, 4.50, 0.24, 0.74),
            NoteEvent(d5, 4.75, 0.24, 0.72),
            NoteEvent(c5, 5.00, 0.24, 0.76),
            NoteEvent(d5, 5.25, 0.24, 0.72),
            NoteEvent(e5, 5.50, 0.24, 0.74),
            NoteEvent(c5, 5.75, 0.24, 0.72),
            NoteEvent(d5, 6.00, 0.14, 0.70),
            NoteEvent(e5, 6.125, 0.14, 0.72),
            NoteEvent(f5, 6.25, 0.14, 0.74),
            NoteEvent(g5, 6.375, 0.14, 0.78),
            NoteEvent(a5, 6.50, 0.36, 0.82),
            NoteEvent(g5, 6.875, 0.14, 0.72),
            NoteEvent(f5, 7.00, 0.24, 0.74),
            NoteEvent(e5, 7.25, 0.24, 0.72),
            NoteEvent(c5, 7.50, 0.48, 0.82)
        )

        val c4 = 261.63
        val g3 = 196.00
        val f3 = 174.61
        val e4 = 329.63
        val harmony = listOf(
            NoteEvent(c4, 0.00, 0.90, 0.38), NoteEvent(e4, 0.50, 0.45, 0.26),
            NoteEvent(c4, 1.00, 0.90, 0.38), NoteEvent(g3, 1.50, 0.45, 0.26),
            NoteEvent(g3, 2.00, 0.45, 0.34), NoteEvent(c4, 2.50, 0.45, 0.34),
            NoteEvent(g3, 3.00, 0.45, 0.34), NoteEvent(c4, 3.50, 0.48, 0.38),
            NoteEvent(c4, 4.00, 0.90, 0.38), NoteEvent(e4, 4.50, 0.45, 0.26),
            NoteEvent(c4, 5.00, 0.90, 0.38), NoteEvent(g3, 5.50, 0.45, 0.26),
            NoteEvent(f3, 6.00, 0.48, 0.36), NoteEvent(c4, 6.50, 0.48, 0.34),
            NoteEvent(g3, 7.00, 0.48, 0.36), NoteEvent(c4, 7.50, 0.48, 0.40)
        )
        val sleighBeats = List(16) { idx -> idx * 0.50 }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildAdesteFidelesScore(): CarolScore {
        // "Venid Fieles Todos" (Adeste Fideles / O Come All Ye Faithful) en Sol mayor (8.0s)
        val g5 = 783.99
        val d5 = 587.33
        val a5 = 880.00
        val b5 = 987.77
        val fs5 = 739.99
        val e5 = 659.25
        val c6 = 1046.50
        val d6 = 1174.66

        val melody = listOf(
            NoteEvent(g5, 0.00, 0.48, 0.78),
            NoteEvent(g5, 0.50, 0.72, 0.80),
            NoteEvent(d5, 1.25, 0.48, 0.72),
            NoteEvent(g5, 1.75, 0.48, 0.76),
            NoteEvent(a5, 2.25, 0.72, 0.82),
            NoteEvent(d5, 3.00, 0.72, 0.74),
            NoteEvent(b5, 3.75, 0.48, 0.80),
            NoteEvent(a5, 4.25, 0.48, 0.76),
            NoteEvent(b5, 4.75, 0.48, 0.80),
            NoteEvent(c6, 5.25, 0.48, 0.82),
            NoteEvent(b5, 5.75, 0.72, 0.80),
            NoteEvent(a5, 6.50, 0.48, 0.76),
            NoteEvent(g5, 7.00, 0.48, 0.78),
            NoteEvent(fs5, 7.50, 0.25, 0.72),
            NoteEvent(g5, 7.75, 0.25, 0.82)
        )

        val g3 = 196.00
        val d4 = 293.66
        val b3 = 246.94
        val c4 = 261.63
        val e4 = 329.63
        val harmony = listOf(
            NoteEvent(g3, 0.00, 1.20, 0.42), NoteEvent(b3, 0.50, 0.75, 0.28),
            NoteEvent(d4, 1.25, 1.00, 0.36), NoteEvent(g3, 1.75, 0.50, 0.32),
            NoteEvent(d4, 2.25, 1.40, 0.40),
            NoteEvent(g3, 3.75, 1.00, 0.42), NoteEvent(b3, 4.25, 0.90, 0.30),
            NoteEvent(c4, 5.25, 1.00, 0.38), NoteEvent(e4, 5.75, 0.75, 0.28),
            NoteEvent(d4, 6.50, 0.95, 0.38), NoteEvent(g3, 7.50, 0.50, 0.42)
        )
        val sleighBeats = List(8) { idx -> idx * 1.0 }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildRudolphScore(): CarolScore {
        // "Rodolfo el Reno" (Rudolph the Red-Nosed Reindeer) en Do mayor (8.0s)
        val g5 = 783.99
        val a5 = 880.00
        val e5 = 659.25
        val c6 = 1046.50
        val f5 = 698.46
        val d5 = 587.33
        val b5 = 987.77

        val melody = listOf(
            // Ru-dolph the red-nosed rein-deer
            NoteEvent(g5, 0.00, 0.24, 0.74),
            NoteEvent(a5, 0.25, 0.36, 0.78),
            NoteEvent(g5, 0.625, 0.22, 0.72),
            NoteEvent(e5, 0.875, 0.34, 0.74),
            NoteEvent(c6, 1.25, 0.36, 0.82),
            NoteEvent(a5, 1.625, 0.34, 0.76),
            NoteEvent(g5, 2.00, 0.95, 0.80),

            // had a ve-ry shi-ny nose
            NoteEvent(g5, 3.00, 0.22, 0.72),
            NoteEvent(a5, 3.25, 0.22, 0.74),
            NoteEvent(g5, 3.50, 0.22, 0.72),
            NoteEvent(a5, 3.75, 0.22, 0.74),
            NoteEvent(g5, 4.00, 0.36, 0.78),
            NoteEvent(c6, 4.375, 0.36, 0.82),
            NoteEvent(b5, 4.75, 0.95, 0.80),

            // and if you e-ver saw it
            NoteEvent(f5, 5.75, 0.22, 0.72),
            NoteEvent(g5, 6.00, 0.34, 0.76),
            NoteEvent(f5, 6.375, 0.22, 0.72),
            NoteEvent(d5, 6.625, 0.34, 0.74),
            NoteEvent(b5, 7.00, 0.34, 0.80),
            NoteEvent(a5, 7.375, 0.32, 0.76),
            NoteEvent(g5, 7.70, 0.30, 0.78)
        )

        val c4 = 261.63
        val g3 = 196.00
        val f3 = 174.61
        val e4 = 329.63
        val d4 = 293.66
        val harmony = listOf(
            NoteEvent(c4, 0.00, 0.95, 0.38), NoteEvent(e4, 0.50, 0.45, 0.26),
            NoteEvent(c4, 1.25, 0.95, 0.38), NoteEvent(g3, 2.00, 0.90, 0.36),
            NoteEvent(c4, 3.00, 0.95, 0.38), NoteEvent(e4, 3.50, 0.45, 0.26),
            NoteEvent(g3, 4.75, 0.95, 0.38), NoteEvent(d4, 5.25, 0.45, 0.26),
            NoteEvent(f3, 5.75, 0.95, 0.36), NoteEvent(g3, 7.00, 0.95, 0.38)
        )
        val sleighBeats = List(16) { idx -> idx * 0.50 }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildWinterWonderlandScore(): CarolScore {
        // "Navidad Blanca Pro" — Arpegios cristalinos invernales y melodía nostálgica (8.0s)
        val g5 = 783.99
        val e5 = 659.25
        val f5 = 698.46
        val d5 = 587.33
        val c5 = 523.25
        val a5 = 880.00
        val c6 = 1046.50
        val b5 = 987.77

        val melody = listOf(
            NoteEvent(g5, 0.00, 0.48, 0.74),
            NoteEvent(g5, 0.50, 0.48, 0.72),
            NoteEvent(e5, 1.00, 0.90, 0.78),
            NoteEvent(f5, 2.00, 0.48, 0.74),
            NoteEvent(d5, 2.50, 0.48, 0.72),
            NoteEvent(c5, 3.00, 0.90, 0.78),
            NoteEvent(a5, 4.00, 0.48, 0.78),
            NoteEvent(b5, 4.50, 0.48, 0.78),
            NoteEvent(c6, 5.00, 0.90, 0.82),
            NoteEvent(a5, 6.00, 0.48, 0.76),
            NoteEvent(g5, 6.50, 0.48, 0.76),
            NoteEvent(e5, 7.00, 0.95, 0.78)
        )

        val c4 = 261.63
        val e4 = 329.63
        val g4 = 392.00
        val f3 = 174.61
        val a3 = 220.00
        val g3 = 196.00
        val d4 = 293.66
        val harmony = listOf(
            NoteEvent(c4, 0.00, 0.95, 0.34), NoteEvent(e4, 0.33, 0.70, 0.24), NoteEvent(g4, 0.66, 0.70, 0.22),
            NoteEvent(c4, 1.00, 0.95, 0.34), NoteEvent(g4, 1.50, 0.55, 0.22),
            NoteEvent(g3, 2.00, 0.95, 0.34), NoteEvent(d4, 2.50, 0.70, 0.24),
            NoteEvent(c4, 3.00, 0.95, 0.36), NoteEvent(e4, 3.50, 0.60, 0.24),
            NoteEvent(f3, 4.00, 0.95, 0.34), NoteEvent(a3, 4.50, 0.70, 0.24),
            NoteEvent(c4, 5.00, 0.95, 0.36), NoteEvent(g4, 5.50, 0.60, 0.24),
            NoteEvent(g3, 6.00, 0.95, 0.34), NoteEvent(c4, 7.00, 0.95, 0.36)
        )
        val sleighBeats = List(8) { idx -> idx * 1.0 }
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun buildSilentNightScore(): CarolScore {
        val g5 = 783.99
        val a5 = 880.00
        val e5 = 659.25
        val d6 = 1174.66
        val b5 = 987.77
        val c6 = 1046.50
        val a4 = 440.00
        val c5 = 523.25

        val melody = listOf(
            NoteEvent(g5, 0.00, 0.65, 0.70),
            NoteEvent(a5, 0.60, 0.25, 0.64),
            NoteEvent(g5, 0.85, 0.45, 0.68),
            NoteEvent(e5, 1.30, 0.90, 0.72),
            NoteEvent(g5, 2.25, 0.65, 0.70),
            NoteEvent(a5, 2.85, 0.25, 0.64),
            NoteEvent(g5, 3.10, 0.45, 0.68),
            NoteEvent(e5, 3.55, 0.90, 0.72),
            NoteEvent(d6, 4.50, 0.70, 0.74),
            NoteEvent(d6, 5.15, 0.35, 0.68),
            NoteEvent(b5, 5.50, 0.85, 0.74),
            NoteEvent(c6, 6.40, 0.70, 0.76),
            NoteEvent(c6, 7.05, 0.35, 0.68),
            NoteEvent(g5, 7.40, 0.60, 0.72)
        )

        val harmony = listOf(
            NoteEvent(c5, 0.00, 1.00, 0.32), NoteEvent(e5, 0.45, 0.80, 0.22),
            NoteEvent(c5, 1.30, 0.90, 0.32),
            NoteEvent(c5, 2.25, 1.00, 0.32), NoteEvent(e5, 2.70, 0.80, 0.22),
            NoteEvent(c5, 3.55, 0.90, 0.32),
            NoteEvent(392.00, 4.50, 1.40, 0.32), NoteEvent(493.88, 5.00, 1.10, 0.24),
            NoteEvent(c5, 6.40, 1.40, 0.34), NoteEvent(a4, 7.00, 0.80, 0.22)
        )

        val sleighBeats = listOf(0.0, 2.25, 4.50, 6.40)
        return CarolScore(melody, harmony, sleighBeats)
    }

    private fun addSleighBellHit(
        mix: DoubleArray,
        totalSamples: Int,
        offsetSec: Double,
        gain: Double
    ) {
        val startIdx = (offsetSec * SAMPLE_RATE).toInt()
        if (startIdx >= totalSamples) return
        val burstSamples = (0.26 * SAMPLE_RATE).toInt()
        val endIdx = (startIdx + burstSamples).coerceAtMost(totalSamples)
        for (i in startIdx until endIdx) {
            val t = (i - startIdx).toDouble() / SAMPLE_RATE
            val env = (1.0 - exp(-120.0 * t)) * exp(-13.5 * t) * gain
            val jingle =
                sin(TWO_PI * 2960.0 * t) * 0.38 +
                    sin(TWO_PI * 3729.0 * t) * 0.32 +
                    sin(TWO_PI * 4698.0 * t) * 0.24 +
                    sin(TWO_PI * 5874.0 * t) * 0.16 +
                    sin(TWO_PI * 7040.0 * t) * 0.10
            mix[i] += jingle * env
        }
    }

    private fun addSleighBellHitCircular(
        mix: DoubleArray,
        totalSamples: Int,
        offsetSec: Double,
        gain: Double
    ) {
        val startIdx = (offsetSec * SAMPLE_RATE).toInt()
        val burstSamples = (0.24 * SAMPLE_RATE).toInt()
        for (local in 0 until burstSamples) {
            val idx = (startIdx + local) % totalSamples
            val t = local.toDouble() / SAMPLE_RATE
            val env = (1.0 - exp(-120.0 * t)) * exp(-14.0 * t) * gain
            val jingle =
                sin(TWO_PI * 2960.0 * t) * 0.38 +
                    sin(TWO_PI * 3729.0 * t) * 0.32 +
                    sin(TWO_PI * 4698.0 * t) * 0.24 +
                    sin(TWO_PI * 5874.0 * t) * 0.16 +
                    sin(TWO_PI * 7040.0 * t) * 0.10
            mix[idx] += jingle * env
        }
    }

    private fun renderWarmPad(freqHz: Double, t: Double): Double {
        return sin(TWO_PI * freqHz * t) * 0.56 +
            sin(TWO_PI * (freqHz * 1.003) * t) * 0.24 +
            sin(TWO_PI * (freqHz * 0.5) * t) * 0.22 +
            sin(TWO_PI * (freqHz * 2.0) * t) * 0.12 * exp(-2.5 * t)
    }

    private fun renderTimbre(freqHz: Double, t: Double, style: ChristmasSoundStyle): Double {
        return when (style) {
            ChristmasSoundStyle.CELESTA_LUXURY, ChristmasSoundStyle.WINTER_WONDERLAND -> {
                sin(TWO_PI * freqHz * t) * 0.64 +
                    sin(TWO_PI * (freqHz * 1.002) * t) * 0.14 +
                    sin(TWO_PI * freqHz * 2.0 * t) * 0.24 * exp(-3.0 * t) +
                    sin(TWO_PI * freqHz * 3.0 * t) * 0.10 * exp(-5.0 * t) +
                    sin(TWO_PI * freqHz * 5.04 * t) * 0.06 * exp(-12.0 * t)
            }
            ChristmasSoundStyle.SLEIGH_BELLS, ChristmasSoundStyle.RUDOLPH_BELLS -> {
                sin(TWO_PI * freqHz * t) * 0.58 +
                    sin(TWO_PI * freqHz * 2.0 * t) * 0.30 * exp(-2.4 * t) +
                    sin(TWO_PI * freqHz * 4.02 * t) * 0.16 * exp(-8.5 * t)
            }
            ChristmasSoundStyle.CAROL_OF_THE_BELLS, ChristmasSoundStyle.ADESTE_FIDELES -> {
                sin(TWO_PI * freqHz * t) * 0.56 +
                    sin(TWO_PI * freqHz * 2.0 * t) * 0.28 * exp(-2.2 * t) +
                    sin(TWO_PI * freqHz * 2.756 * t) * 0.14 * exp(-6.5 * t) +
                    sin(TWO_PI * freqHz * 4.0 * t) * 0.12 * exp(-9.0 * t)
            }
            ChristmasSoundStyle.DECK_THE_HALLS -> {
                sin(TWO_PI * freqHz * t) * 0.60 +
                    sin(TWO_PI * freqHz * 2.0 * t) * 0.26 * exp(-1.8 * t) +
                    sin(TWO_PI * freqHz * 3.0 * t) * 0.14 * exp(-4.0 * t) +
                    sin(TWO_PI * (freqHz * 0.998) * t) * 0.12
            }
            ChristmasSoundStyle.MUSIC_BOX -> {
                sin(TWO_PI * freqHz * t) * 0.62 +
                    sin(TWO_PI * freqHz * 2.0 * t) * 0.28 +
                    sin(TWO_PI * freqHz * 4.0 * t) * 0.12 * exp(-8.0 * t)
            }
        }
    }

    private fun normalizeToPcm16(samples: DoubleArray, masterGain: Double): ShortArray {
        var peak = 0.0
        for (v in samples) {
            val abs = kotlin.math.abs(v)
            if (abs > peak) peak = abs
        }
        val norm = if (peak > 1.0) masterGain / peak else masterGain
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val shaped = tanh(samples[i] * norm * 1.15)
            out[i] = (shaped * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }
}
