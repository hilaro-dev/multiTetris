package com.multitetris.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight 8-bit chiptune PCM synthesizer using Android AudioTrack (zero external audio files).
 */
class RetroSoundEngine {

    @Volatile
    var isMuted: Boolean = false

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MultiTetris-Audio").apply { isDaemon = true }
    }

    private val moveClip: ShortArray by lazy {
        synthesizeTone(freqStart = 420.0, freqEnd = 480.0, durationMs = 28, volume = 0.22)
    }

    private val rotateClip: ShortArray by lazy {
        synthesizeTone(freqStart = 520.0, freqEnd = 780.0, durationMs = 45, volume = 0.26)
    }

    private val softDropClip: ShortArray by lazy {
        synthesizeTone(freqStart = 240.0, freqEnd = 200.0, durationMs = 24, volume = 0.18)
    }

    private val hardDropClip: ShortArray by lazy {
        synthesizeTone(freqStart = 260.0, freqEnd = 85.0, durationMs = 85, volume = 0.36)
    }

    private val lineClearSingleClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(523.25, 659.25, 783.99, 1046.50),
            noteDurationMs = 55,
            volume = 0.32
        )
    }

    private val lineClearMultiClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(523.25, 659.25, 783.99, 1046.50, 1318.51),
            noteDurationMs = 60,
            volume = 0.36
        )
    }

    private val sabotageClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(880.0, 622.25, 880.0, 622.25),
            noteDurationMs = 55,
            volume = 0.32
        )
    }

    private val gameOverClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(440.0, 392.0, 349.23, 261.63),
            noteDurationMs = 130,
            volume = 0.34
        )
    }

    private val coinChimeClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(987.77, 1318.51),
            noteDurationMs = 75,
            volume = 0.32
        )
    }

    private val roomCreatedClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(523.25, 783.99), // C5 -> G5 (~120ms)
            noteDurationMs = 60,
            volume = 0.28
        )
    }

    private val friendRequestClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(659.25, 880.0), // E5 -> A5 (~180ms)
            noteDurationMs = 90,
            volume = 0.22
        )
    }

    private val roomInviteClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(783.99, 1046.50), // G5 -> C6 (~200ms)
            noteDurationMs = 100,
            volume = 0.34
        )
    }

    private val playerJoinedLobbyClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(523.25, 659.25, 783.99), // C5 -> E5 -> G5 (~220ms)
            noteDurationMs = 73,
            volume = 0.26
        )
    }

    private val matchStartClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(523.25, 659.25, 783.99, 1046.50), // C5 -> E5 -> G5 -> C6 (~320ms)
            noteDurationMs = 80,
            volume = 0.34
        )
    }

    private val selfEliminatedClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(392.0, 329.63, 261.63, 196.0), // G4 -> E4 -> C4 -> G3 (~400ms)
            noteDurationMs = 100,
            volume = 0.32
        )
    }

    private val otherEliminatedClip: ShortArray by lazy {
        synthesizeArpeggio(
            freqs = listOf(293.66, 220.0), // D4 -> A3 (~140ms, ~50% gain)
            noteDurationMs = 70,
            volume = 0.15
        )
    }

    fun playMove() = enqueueClip(moveClip)

    fun playRotate() = enqueueClip(rotateClip)

    fun playSoftDrop() = enqueueClip(softDropClip)

    fun playHardDrop() = enqueueClip(hardDropClip)

    fun playLineClear(linesCleared: Int = 1) {
        enqueueClip(if (linesCleared >= 2) lineClearMultiClip else lineClearSingleClip)
    }

    fun playSabotageBurst() = enqueueClip(sabotageClip)

    fun playGameOver() = enqueueClip(gameOverClip)

    fun playCoinChime() = enqueueClip(coinChimeClip)

    fun playRoomCreated() = enqueueClip(roomCreatedClip)

    fun playFriendRequestReceived() = enqueueClip(friendRequestClip)

    fun playRoomInviteReceived() = enqueueClip(roomInviteClip)

    fun playPlayerJoinedLobby() = enqueueClip(playerJoinedLobbyClip)

    fun playMatchStart() = enqueueClip(matchStartClip)

    fun playSelfEliminated() = enqueueClip(selfEliminatedClip)

    fun playOtherPlayerEliminated() = enqueueClip(otherEliminatedClip)

    fun shutdown() {
        runCatching { executor.shutdownNow() }
    }

    private fun enqueueClip(pcm: ShortArray) {
        if (isMuted) return
        runCatching {
            executor.execute {
                if (!isMuted) {
                    playBlocking(pcm)
                }
            }
        }
    }

    private fun playBlocking(pcm: ShortArray) {
        runCatching {
            val byteSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            val durationMs = (pcm.size * 1000L) / SAMPLE_RATE + 15L
            Thread.sleep(durationMs)
            track.stop()
            track.release()
        }
    }

    private fun synthesizeTone(
        freqStart: Double,
        freqEnd: Double,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count.coerceAtLeast(1)
            val freq = freqStart + (freqEnd - freqStart) * progress
            val envelope = (1.0 - progress).coerceIn(0.0, 1.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            // Crisp 8-bit square-ish wave
            val square = if (sin(phase) >= 0.0) 1.0 else -1.0
            out[i] = (square * envelope * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeArpeggio(
        freqs: List<Double>,
        noteDurationMs: Int,
        volume: Double
    ): ShortArray {
        val samplesPerNote = (SAMPLE_RATE * noteDurationMs) / 1000
        val out = ShortArray(samplesPerNote * freqs.size)
        var idx = 0
        for (freq in freqs) {
            var phase = 0.0
            for (i in 0 until samplesPerNote) {
                val progress = i.toDouble() / samplesPerNote.coerceAtLeast(1)
                val envelope = (1.0 - progress * 0.65).coerceIn(0.0, 1.0)
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val square = if (sin(phase) >= 0.0) 1.0 else -1.0
                out[idx++] = (square * envelope * volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    companion object {
        private const val SAMPLE_RATE = 22_050
    }
}
