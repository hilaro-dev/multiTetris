package com.multitetris.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MusicTrack {
    NONE,
    LOBBY_CHILL,
    GAMEPLAY_KOROBEINIKI
}

/**
 * Procedural dual-track 8-bit chiptune synthesizer using Android AudioTrack (MODE_STREAM).
 * Zero external audio assets; supports smooth track switching, 1.15x high-tension tempo,
 * mute, and lifecycle pause/resume.
 */
class ChiptuneMusicPlayer {

    @Volatile
    private var requestedTrack: MusicTrack = MusicTrack.NONE

    @Volatile
    private var isMuted: Boolean = false

    @Volatile
    private var isLifecyclePaused: Boolean = false

    @Volatile
    private var isHighTension: Boolean = false

    @Volatile
    private var released: Boolean = false

    @Volatile
    private var requestedWindDownSamples: Int = 0

    @Volatile
    private var isWindingDown: Boolean = false

    private val wakeLock = Object()
    private var workerThread: Thread? = null

    fun setTrack(track: MusicTrack) {
        if (released) return
        requestedWindDownSamples = 0
        val wasWindingDown = isWindingDown
        isWindingDown = false
        val workerAlive = workerThread?.isAlive == true
        if (requestedTrack == track && !wasWindingDown && workerAlive) return
        requestedTrack = track
        synchronized(wakeLock) {
            wakeLock.notifyAll()
        }
        if (track != MusicTrack.NONE) {
            ensureWorkerStarted()
        }
    }

    fun windDownAndStop(durationMs: Long = 1100L) {
        if (released || requestedTrack == MusicTrack.NONE) return
        val samples = ((SAMPLE_RATE * durationMs.coerceAtLeast(100L)) / 1000L).toInt()
        requestedWindDownSamples = samples
        synchronized(wakeLock) {
            wakeLock.notifyAll()
        }
    }

    fun setMuted(muted: Boolean) {
        if (isMuted == muted) return
        isMuted = muted
        synchronized(wakeLock) {
            wakeLock.notifyAll()
        }
    }

    fun setLifecyclePaused(paused: Boolean) {
        if (isLifecyclePaused == paused) return
        isLifecyclePaused = paused
        synchronized(wakeLock) {
            wakeLock.notifyAll()
        }
    }

    fun setHighTension(highTension: Boolean) {
        isHighTension = highTension
    }

    fun release() {
        released = true
        requestedTrack = MusicTrack.NONE
        synchronized(wakeLock) {
            wakeLock.notifyAll()
        }
        runCatching { workerThread?.interrupt() }
        workerThread = null
    }

    private fun ensureWorkerStarted() {
        if (workerThread?.isAlive == true || released) return
        synchronized(wakeLock) {
            if (workerThread?.isAlive == true || released) return
            workerThread = Thread(::runAudioLoop, "MultiTetris-ChiptuneMusic").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun runAudioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SAMPLES * 4)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        val buffer = ShortArray(CHUNK_SAMPLES)
        var activeTrack = MusicTrack.NONE
        var isPlaying = false
        var fadeGain = 0.0
        var windDownTotalSamples = 0
        var windDownElapsedSamples = 0

        // Synthesizer state
        var stepPosSamples = 0.0
        var lobbyStepIdx = 0
        var koroEventIdx = 0
        var koroEighthUnitsInEvent = 0
        var koroBassStepIdx = 0
        var leadPhase = 0.0
        var bassPhase = 0.0
        var lowPassState = 0.0

        try {
            while (!released) {
                val windReq = requestedWindDownSamples
                if (windReq > 0) {
                    requestedWindDownSamples = 0
                    if (activeTrack != MusicTrack.NONE && !isMuted && !isLifecyclePaused) {
                        isWindingDown = true
                        windDownTotalSamples = windReq
                        windDownElapsedSamples = 0
                    } else {
                        isWindingDown = false
                        requestedTrack = MusicTrack.NONE
                    }
                }

                val targetTrack = requestedTrack
                val shouldSilence = targetTrack == MusicTrack.NONE || isMuted || isLifecyclePaused

                if (shouldSilence && fadeGain <= 0.001) {
                    isWindingDown = false
                    if (isPlaying) {
                        runCatching {
                            track.pause()
                            track.flush()
                        }
                        isPlaying = false
                    }
                    activeTrack = targetTrack
                    synchronized(wakeLock) {
                        while (!released &&
                            (requestedTrack == MusicTrack.NONE || isMuted || isLifecyclePaused)
                        ) {
                            wakeLock.wait(250L)
                        }
                    }
                    continue
                }

                // Switch track cleanly after fading out to zero
                if (activeTrack != targetTrack) {
                    isWindingDown = false
                    if (fadeGain > 0.001 && activeTrack != MusicTrack.NONE) {
                        // Ramp down over one chunk before switching
                        renderChunk(
                            trackType = activeTrack,
                            out = buffer,
                            fadeStart = fadeGain,
                            fadeEnd = 0.0,
                            highTension = isHighTension,
                            getStepSamples = { stepPosSamples },
                            setStepSamples = { stepPosSamples = it },
                            advanceLobbyStep = { lobbyStepIdx = (lobbyStepIdx + 1) % LOBBY_STEPS.size },
                            getLobbyStep = { lobbyStepIdx },
                            advanceKoroEighth = {
                                koroBassStepIdx = (koroBassStepIdx + 1) % KORO_BASS_NOTES.size
                                koroEighthUnitsInEvent++
                                if (koroEighthUnitsInEvent >= KORO_EVENTS[koroEventIdx].eighths) {
                                    koroEighthUnitsInEvent = 0
                                    koroEventIdx = (koroEventIdx + 1) % KORO_EVENTS.size
                                }
                            },
                            getKoroEvent = { KORO_EVENTS[koroEventIdx] },
                            getKoroUnit = { koroEighthUnitsInEvent },
                            getKoroBassMidi = { KORO_BASS_NOTES[koroBassStepIdx] },
                            getLeadPhase = { leadPhase },
                            setLeadPhase = { leadPhase = it },
                            getBassPhase = { bassPhase },
                            setBassPhase = { bassPhase = it },
                            getLowPass = { lowPassState },
                            setLowPass = { lowPassState = it }
                        )
                        fadeGain = 0.0
                        if (!isPlaying) {
                            runCatching { track.play() }
                            isPlaying = true
                        }
                        track.write(buffer, 0, buffer.size)
                    }
                    activeTrack = targetTrack
                    stepPosSamples = 0.0
                    lobbyStepIdx = 0
                    koroEventIdx = 0
                    koroEighthUnitsInEvent = 0
                    koroBassStepIdx = 0
                    leadPhase = 0.0
                    bassPhase = 0.0
                    lowPassState = 0.0
                    continue
                }

                val nextFade = if (shouldSilence) 0.0 else 1.0
                val fadeEnd = when {
                    nextFade > fadeGain -> (fadeGain + 0.35).coerceAtMost(1.0)
                    nextFade < fadeGain -> (fadeGain - 0.5).coerceAtLeast(0.0)
                    else -> fadeGain
                }

                val windProgressStart: Double
                val windProgressEnd: Double
                if (isWindingDown && windDownTotalSamples > 0) {
                    windProgressStart = (windDownElapsedSamples.toDouble() / windDownTotalSamples).coerceIn(0.0, 1.0)
                    windProgressEnd = ((windDownElapsedSamples + CHUNK_SAMPLES).toDouble() / windDownTotalSamples).coerceIn(0.0, 1.0)
                } else {
                    windProgressStart = 0.0
                    windProgressEnd = 0.0
                }

                renderChunk(
                    trackType = activeTrack,
                    out = buffer,
                    fadeStart = fadeGain,
                    fadeEnd = fadeEnd,
                    windProgressStart = windProgressStart,
                    windProgressEnd = windProgressEnd,
                    highTension = isHighTension,
                    getStepSamples = { stepPosSamples },
                    setStepSamples = { stepPosSamples = it },
                    advanceLobbyStep = { lobbyStepIdx = (lobbyStepIdx + 1) % LOBBY_STEPS.size },
                    getLobbyStep = { lobbyStepIdx },
                    advanceKoroEighth = {
                        koroBassStepIdx = (koroBassStepIdx + 1) % KORO_BASS_NOTES.size
                        koroEighthUnitsInEvent++
                        if (koroEighthUnitsInEvent >= KORO_EVENTS[koroEventIdx].eighths) {
                            koroEighthUnitsInEvent = 0
                            koroEventIdx = (koroEventIdx + 1) % KORO_EVENTS.size
                        }
                    },
                    getKoroEvent = { KORO_EVENTS[koroEventIdx] },
                    getKoroUnit = { koroEighthUnitsInEvent },
                    getKoroBassMidi = { KORO_BASS_NOTES[koroBassStepIdx] },
                    getLeadPhase = { leadPhase },
                    setLeadPhase = { leadPhase = it },
                    getBassPhase = { bassPhase },
                    setBassPhase = { bassPhase = it },
                    getLowPass = { lowPassState },
                    setLowPass = { lowPassState = it }
                )
                fadeGain = fadeEnd

                if (isWindingDown) {
                    windDownElapsedSamples += CHUNK_SAMPLES
                    if (windDownElapsedSamples >= windDownTotalSamples) {
                        isWindingDown = false
                        requestedTrack = MusicTrack.NONE
                        fadeGain = 0.0
                    }
                }

                if (!isPlaying) {
                    runCatching { track.play() }
                    isPlaying = true
                }
                val written = track.write(buffer, 0, buffer.size)
                if (written < 0) break
            }
        } catch (_: InterruptedException) {
            // Normal shutdown
        } finally {
            synchronized(wakeLock) {
                if (workerThread === Thread.currentThread()) {
                    workerThread = null
                }
            }
            runCatching {
                track.pause()
                track.flush()
                track.stop()
                track.release()
            }
        }
    }

    private inline fun renderChunk(
        trackType: MusicTrack,
        out: ShortArray,
        fadeStart: Double,
        fadeEnd: Double,
        windProgressStart: Double = 0.0,
        windProgressEnd: Double = 0.0,
        highTension: Boolean,
        getStepSamples: () -> Double,
        setStepSamples: (Double) -> Unit,
        advanceLobbyStep: () -> Unit,
        getLobbyStep: () -> Int,
        advanceKoroEighth: () -> Unit,
        getKoroEvent: () -> NoteEvent,
        getKoroUnit: () -> Int,
        getKoroBassMidi: () -> Int,
        getLeadPhase: () -> Double,
        setLeadPhase: (Double) -> Unit,
        getBassPhase: () -> Double,
        setBassPhase: (Double) -> Unit,
        getLowPass: () -> Double,
        setLowPass: (Double) -> Unit
    ) {
        var stepPos = getStepSamples()
        var lPhase = getLeadPhase()
        var bPhase = getBassPhase()
        var lp = getLowPass()
        val n = out.size

        if (trackType == MusicTrack.LOBBY_CHILL) {
            // 95 BPM, 16th-note step duration
            val stepSamples = SAMPLE_RATE * (60.0 / 95.0 / 4.0)
            for (i in 0 until n) {
                val sampleRatio = i.toDouble() / n
                val windP = windProgressStart + (windProgressEnd - windProgressStart) * sampleRatio
                val slowdown = 1.0 - 0.40 * windP
                val windFade = (1.0 - windP).coerceIn(0.0, 1.0)

                stepPos += slowdown
                if (stepPos >= stepSamples) {
                    stepPos -= stepSamples
                    advanceLobbyStep()
                }
                val progress = (stepPos / stepSamples).coerceIn(0.0, 1.0)
                val step = LOBBY_STEPS[getLobbyStep()]

                val leadFreq = midiToHz(step.arpMidi) * slowdown
                val bassFreq = midiToHz(step.bassMidi) * slowdown

                lPhase = (lPhase + leadFreq / SAMPLE_RATE) % 1.0
                bPhase = (bPhase + bassFreq / SAMPLE_RATE) % 1.0

                // Mellow 25% pulse filtered with a 1-pole low-pass + soft pluck decay
                val rawPulse = if (lPhase < 0.25) 1.0 else -1.0
                lp += 0.16 * (rawPulse - lp)
                val arpEnv = if (progress < 0.06) progress / 0.06 else (1.0 - (progress - 0.06) * 0.72).coerceAtLeast(0.15)

                // Warm triangle bass with gentle sustain
                val triBass = 2.0 * kotlin.math.abs(2.0 * bPhase - 1.0) - 1.0
                val bassEnv = 0.85

                val mix = (lp * arpEnv * 0.52 + triBass * bassEnv * 0.48) * 0.20
                val fade = (fadeStart + (fadeEnd - fadeStart) * sampleRatio) * windFade
                val sample = (mix * fade * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[i] = sample.toShort()
            }
        } else if (trackType == MusicTrack.GAMEPLAY_KOROBEINIKI) {
            val tempoMul = if (highTension) 1.15 else 1.0
            val eighthSamples = SAMPLE_RATE * (60.0 / (144.0 * tempoMul) / 2.0)
            for (i in 0 until n) {
                val sampleRatio = i.toDouble() / n
                val windP = windProgressStart + (windProgressEnd - windProgressStart) * sampleRatio
                val slowdown = 1.0 - 0.40 * windP
                val windFade = (1.0 - windP).coerceIn(0.0, 1.0)

                stepPos += slowdown
                if (stepPos >= eighthSamples) {
                    stepPos -= eighthSamples
                    advanceKoroEighth()
                }
                val unitProgress = (stepPos / eighthSamples).coerceIn(0.0, 1.0)
                val event = getKoroEvent()
                val unitIndex = getKoroUnit()
                val eventProgress = (unitIndex + unitProgress) / event.eighths.toDouble()

                // Crisp 8-bit square wave lead with slight note separation at tail
                val leadSample = if (event.midi > 0) {
                    val leadFreq = midiToHz(event.midi) * slowdown
                    lPhase = (lPhase + leadFreq / SAMPLE_RATE) % 1.0
                    val sq = if (lPhase < 0.5) 1.0 else -1.0
                    val env = when {
                        eventProgress < 0.03 -> eventProgress / 0.03
                        eventProgress > 0.88 -> ((1.0 - eventProgress) / 0.12).coerceAtLeast(0.0)
                        else -> 0.92 - eventProgress * 0.18
                    }
                    sq * env
                } else {
                    0.0
                }

                // Punchy triangle bass on every 8th note
                val bassMidi = getKoroBassMidi()
                val bassSample = if (bassMidi > 0) {
                    val bassFreq = midiToHz(bassMidi) * slowdown
                    bPhase = (bPhase + bassFreq / SAMPLE_RATE) % 1.0
                    val tri = 2.0 * kotlin.math.abs(2.0 * bPhase - 1.0) - 1.0
                    val bEnv = if (unitProgress < 0.05) unitProgress / 0.05 else (1.0 - unitProgress * 0.45)
                    tri * bEnv
                } else {
                    0.0
                }

                val mix = (leadSample * 0.56 + bassSample * 0.44) * 0.28
                val fade = (fadeStart + (fadeEnd - fadeStart) * sampleRatio) * windFade
                val sample = (mix * fade * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[i] = sample.toShort()
            }
        } else {
            out.fill(0)
        }

        setStepSamples(stepPos)
        setLeadPhase(lPhase)
        setBassPhase(bPhase)
        setLowPass(lp)
    }

    private fun midiToHz(midi: Int): Double {
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    private data class LobbyStep(
        val arpMidi: Int,
        val bassMidi: Int
    )

    private data class NoteEvent(
        val midi: Int,
        val eighths: Int
    )

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val CHUNK_SAMPLES = 1024

        // Track 1: LOBBY_CHILL (64 16th-note steps: Am9 -> Fmaj7 -> Dm9 -> E7)
        private val LOBBY_STEPS: List<LobbyStep> = buildList {
            val bars = listOf(
                45 to listOf(69, 72, 76, 71, 69, 76, 72, 71, 69, 72, 76, 79, 76, 72, 71, 69), // Am9 (A2)
                41 to listOf(65, 69, 72, 76, 72, 69, 64, 72, 65, 69, 72, 76, 72, 69, 64, 69), // Fmaj7 (F2)
                38 to listOf(62, 65, 69, 76, 74, 69, 65, 69, 62, 65, 69, 74, 72, 69, 65, 69), // Dm9 (D2)
                40 to listOf(64, 69, 71, 74, 64, 68, 71, 74, 64, 68, 71, 76, 74, 71, 68, 64)  // E7sus4 -> E7 (E2)
            )
            for ((bass, arps) in bars) {
                for (arp in arps) {
                    add(LobbyStep(arpMidi = arp, bassMidi = bass))
                }
            }
        }

        // Track 2: GAMEPLAY_KOROBEINIKI (1861 public-domain folk melody, 16-bar A+B loop)
        // MIDI: E5=76, D5=74, C5=72, B4=71, A4=69, G#4=68, F5=77, G5=79, A5=81, G#5=80
        private val KORO_EVENTS: List<NoteEvent> = listOf(
            // Bar 1
            NoteEvent(76, 2), NoteEvent(71, 1), NoteEvent(72, 1), NoteEvent(74, 2), NoteEvent(72, 1), NoteEvent(71, 1),
            // Bar 2
            NoteEvent(69, 2), NoteEvent(69, 1), NoteEvent(72, 1), NoteEvent(76, 2), NoteEvent(74, 1), NoteEvent(72, 1),
            // Bar 3
            NoteEvent(71, 3), NoteEvent(72, 1), NoteEvent(74, 2), NoteEvent(76, 2),
            // Bar 4
            NoteEvent(72, 2), NoteEvent(69, 2), NoteEvent(69, 4),
            // Bar 5
            NoteEvent(0, 1), NoteEvent(74, 2), NoteEvent(77, 1), NoteEvent(81, 2), NoteEvent(79, 1), NoteEvent(77, 1),
            // Bar 6
            NoteEvent(76, 3), NoteEvent(72, 1), NoteEvent(76, 2), NoteEvent(74, 1), NoteEvent(72, 1),
            // Bar 7
            NoteEvent(71, 2), NoteEvent(71, 1), NoteEvent(72, 1), NoteEvent(74, 2), NoteEvent(76, 2),
            // Bar 8
            NoteEvent(72, 2), NoteEvent(69, 2), NoteEvent(69, 4),
            // Part B (Bars 9-16)
            // Bar 9
            NoteEvent(76, 4), NoteEvent(72, 4),
            // Bar 10
            NoteEvent(74, 4), NoteEvent(71, 4),
            // Bar 11
            NoteEvent(72, 4), NoteEvent(69, 4),
            // Bar 12
            NoteEvent(68, 4), NoteEvent(71, 4),
            // Bar 13
            NoteEvent(76, 4), NoteEvent(72, 4),
            // Bar 14
            NoteEvent(74, 4), NoteEvent(71, 4),
            // Bar 15
            NoteEvent(72, 2), NoteEvent(76, 2), NoteEvent(81, 4),
            // Bar 16
            NoteEvent(80, 6), NoteEvent(0, 2)
        )

        // Alternating octave 8th-note bass matching the 16 bars (128 eighth notes)
        private val KORO_BASS_NOTES: List<Int> = buildList {
            fun addBar(low: Int, high: Int) {
                repeat(4) {
                    add(low)
                    add(high)
                }
            }
            addBar(40, 52) // Bar 1: E2/E3
            addBar(33, 45) // Bar 2: A1/A2
            addBar(44, 56) // Bar 3: G#2/G#3
            addBar(33, 45) // Bar 4: A1/A2
            addBar(38, 50) // Bar 5: D2/D3
            addBar(36, 48) // Bar 6: C2/C3
            addBar(40, 52) // Bar 7: E2/E3
            addBar(33, 45) // Bar 8: A1/A2
            // Part B
            addBar(33, 45) // Bar 9: Am
            addBar(40, 52) // Bar 10: E7
            addBar(33, 45) // Bar 11: Am
            addBar(40, 52) // Bar 12: E7
            addBar(33, 45) // Bar 13: Am
            addBar(40, 52) // Bar 14: E7
            addBar(36, 48) // Bar 15: C -> Am
            addBar(40, 52) // Bar 16: E7
        }
    }
}
