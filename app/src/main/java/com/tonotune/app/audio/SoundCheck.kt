package com.tonotune.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates an 8-second music loop (kick, bassline, snare, hats, chord pad, vocal-like lead)
 * that exercises every Tono band, so EQ changes are obvious on the speaker.
 */
object TestSignal {
    const val SAMPLE_RATE = 44_100
    private const val BPM = 120.0
    const val SECONDS = 8

    fun musicLoop(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val frames = sampleRate * SECONDS
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val beat = 60.0 / BPM
        val rnd = Random(7)
        val noise = FloatArray(frames) { rnd.nextFloat() * 2f - 1f }
        val bassNotes = doubleArrayOf(55.0, 55.0, 73.42, 65.41) // A1 A1 D2 C2 per bar
        val chords = arrayOf(
            doubleArrayOf(220.0, 261.63, 329.63), doubleArrayOf(220.0, 261.63, 329.63),
            doubleArrayOf(293.66, 349.23, 440.0), doubleArrayOf(261.63, 329.63, 392.0),
        )
        val lead = doubleArrayOf(659.25, 587.33, 523.25, 587.33, 659.25, 783.99, 659.25, 523.25)
        var hpPrev = 0f
        var hpOut = 0f
        for (n in 0 until frames) {
            val t = n.toDouble() / sampleRate
            val beatPos = t / beat
            val inBeat = (beatPos % 1.0) * beat
            val bar = ((beatPos / 4).toInt()) % 4
            val eighth = (beatPos * 2) % 1.0 * beat / 2
            // Kick on every beat: pitch-dropping sine (sub band)
            val kf = 50.0 + 90.0 * exp(-inBeat * 30)
            val kick = (sin(2 * PI * kf * inBeat) * exp(-inBeat * 7)).toFloat() * 0.9f
            // Bassline eighths (sub/body)
            val bf = bassNotes[bar]
            val bass = ((sin(2 * PI * bf * t) + 0.35 * sin(4 * PI * bf * t)) * exp(-eighth * 5)).toFloat() * 0.45f
            // Snare on 2 & 4 (body + presence)
            val beatIdx = beatPos.toInt() % 4
            val snare = if (beatIdx == 1 || beatIdx == 3) {
                ((noise[n] * 0.7 + sin(2 * PI * 190 * inBeat) * 0.5) * exp(-inBeat * 18)).toFloat() * 0.5f
            } else 0f
            // Hi-hats on eighths (air): crude high-pass of noise
            hpOut = 0.92f * (hpOut + noise[n] - hpPrev)
            hpPrev = noise[n]
            val hat = hpOut * exp(-eighth * 60).toFloat() * 0.22f
            // Chord pad (mids)
            val pad = chords[bar].sumOf { f -> sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t) }.toFloat() * 0.06f
            // Vocal-ish lead in the presence region
            val lf = lead[(beatPos.toInt()) % lead.size] * 2
            val vib = 1 + 0.004 * sin(2 * PI * 5.5 * t)
            val leadS = (sin(2 * PI * lf * vib * t) + 0.5 * sin(4 * PI * lf * vib * t) + 0.25 * sin(6 * PI * lf * t)).toFloat() * 0.07f
            val mono = kick + bass + snare
            left[n] = mono + hat * 0.8f + pad * 1.1f + leadS * 0.9f
            right[n] = mono + hat * 1.2f + pad * 0.9f + leadS * 1.1f
        }
        var peak = 1e-6f
        for (i in 0 until frames) peak = maxOf(peak, abs(left[i]), abs(right[i]))
        val scale = 0.5f / peak * Short.MAX_VALUE // -6 dBFS peak: headroom for EQ boosts
        return ShortArray(frames * 2) { i ->
            val v = if (i % 2 == 0) left[i / 2] else right[i / 2]
            (v * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

/**
 * Plays [TestSignal] on its own audio session with the Tono chain attached, so the user can
 * A/B the tuning on the speaker even when the ROM blocks effects on other apps.
 */
class SoundCheckPlayer {
    private var track: AudioTrack? = null
    private var stack: EffectStack? = null

    val isPlaying: Boolean get() = track != null

    /** @return null on success, otherwise an error message. */
    fun start(pcm: ShortArray, mode: EngineMode, settings: EqSettings, eqOn: Boolean): String? {
        stop()
        return try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(TestSignal.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size / 2, -1)
            val st = EffectStack(t.audioSessionId, mode)
            val err = st.open()
            if (err == null) st.apply(settings, eqOn) else st.release()
            t.play()
            track = t
            stack = if (err == null) st else null
            err?.let { "Playing, but effects failed: $it" }
        } catch (e: Exception) {
            Log.e("SoundCheck", "start failed", e)
            stop()
            e.message ?: e.javaClass.simpleName
        }
    }

    fun update(settings: EqSettings, eqOn: Boolean) {
        stack?.apply(settings, eqOn)
    }

    fun stop() {
        stack?.release(); stack = null
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
    }
}
