package com.tonotune.app.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/** Pure, device-independent maths used by the engine (unit tested). */
object EqMath {

    /**
     * Returns the target gain (dB) at [frequencyHz] by interpolating the 5-point
     * Tono curve on a log-frequency axis. Outside the curve the end values hold.
     * This lets the engine drive equalizers with any number of bands.
     */
    fun gainAt(frequencyHz: Float, bandsDb: List<Float>): Float {
        val freqs = EqSettings.BAND_FREQUENCIES_HZ
        if (frequencyHz <= freqs.first()) return bandsDb.first()
        if (frequencyHz >= freqs.last()) return bandsDb.last()
        for (i in 0 until freqs.size - 1) {
            val f0 = freqs[i]
            val f1 = freqs[i + 1]
            if (frequencyHz in f0..f1) {
                val t = (ln(frequencyHz) - ln(f0)) / (ln(f1) - ln(f0))
                return bandsDb[i] + (bandsDb[i + 1] - bandsDb[i]) * t
            }
        }
        return 0f
    }

    /**
     * Converts dB to millibels and clamps into the device range so
     * Equalizer.setBandLevel never throws IllegalArgumentException.
     */
    fun dbToClampedMillibels(db: Float, minMb: Short, maxMb: Short): Short {
        if (minMb > maxMb) return 0
        val mb = (db * 100f).roundToInt()
        return mb.coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
    }

    fun clampStrength(value: Int): Short = value.coerceIn(0, EqSettings.MAX_STRENGTH).toShort()

    /** Volume ratio 0..1 from raw stream values; safe for odd ranges. */
    fun volumeRatio(current: Int, min: Int, max: Int): Float {
        if (max <= min) return 0f
        return ((current - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }
}

/** Which Auto-Tweak correction is in effect. */
enum class TweakMode { OFF, LOUDNESS, NEUTRAL, PROTECT }

/**
 * Smart Auto-Tweak: volume-adaptive compensation for the Tono.
 *  - Low volume (< 35 %): +1.5 dB @ 60 Hz, +1.0 dB @ 14 kHz (equal-loudness compensation).
 *  - High volume (> 80 %): -1.5 dB @ 230 Hz and BassBoost scaled to 80 % (woofer protection).
 */
object AutoTweakEngine {
    const val LOW_VOLUME_THRESHOLD = 0.35f
    const val HIGH_VOLUME_THRESHOLD = 0.80f
    const val LOUDNESS_BASS_DB = 1.5f
    const val LOUDNESS_AIR_DB = 1.0f
    const val PROTECT_BODY_CUT_DB = -1.5f
    const val PROTECT_BASS_SCALE = 0.80f

    fun modeFor(volumeRatio: Float, enabled: Boolean): TweakMode = when {
        !enabled -> TweakMode.OFF
        volumeRatio < LOW_VOLUME_THRESHOLD -> TweakMode.LOUDNESS
        volumeRatio > HIGH_VOLUME_THRESHOLD -> TweakMode.PROTECT
        else -> TweakMode.NEUTRAL
    }

    fun apply(base: EqSettings, volumeRatio: Float, enabled: Boolean): EqSettings {
        val bands = base.bandsDb.toMutableList()
        var bass = base.bassBoost
        when (modeFor(volumeRatio, enabled)) {
            TweakMode.LOUDNESS -> {
                bands[0] += LOUDNESS_BASS_DB
                bands[4] += LOUDNESS_AIR_DB
            }
            TweakMode.PROTECT -> {
                bands[1] += PROTECT_BODY_CUT_DB
                bass = (bass * PROTECT_BASS_SCALE).roundToInt()
            }
            TweakMode.NEUTRAL, TweakMode.OFF -> Unit
        }
        return EqSettings(bands, bass, base.virtualizer).sanitized()
    }
}
