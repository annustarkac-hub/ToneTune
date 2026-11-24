package com.tonotune.app

import com.tonotune.app.audio.AutoTweakEngine
import com.tonotune.app.audio.EqMath
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.TonoPresetLibrary
import com.tonotune.app.audio.TweakMode
import com.tonotune.app.data.TonoPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqMathTest {
    private val bands = listOf(3f, -2f, 1f, 4f, 3f)

    @Test fun gainAtCentreFrequenciesEqualsBand() {
        EqSettings.BAND_FREQUENCIES_HZ.forEachIndexed { i, f -> assertEquals(bands[i], EqMath.gainAt(f, bands), 1e-4f) }
    }

    @Test fun gainOutsideRangeHoldsEnds() {
        assertEquals(3f, EqMath.gainAt(20f, bands), 0f)
        assertEquals(3f, EqMath.gainAt(20000f, bands), 0f)
    }

    @Test fun gainBetweenBandsInterpolatesOnLogAxis() {
        // Geometric mean of 60 and 230 Hz is the log-midpoint.
        val mid = kotlin.math.sqrt(60f * 230f)
        assertEquals((3f + -2f) / 2f, EqMath.gainAt(mid, bands), 1e-3f)
    }

    @Test fun millibelsClampIntoDeviceRange() {
        assertEquals(1200.toShort(), EqMath.dbToClampedMillibels(12f, -1500, 1500))
        assertEquals(1000.toShort(), EqMath.dbToClampedMillibels(12f, -1000, 1000))
        assertEquals((-1000).toShort(), EqMath.dbToClampedMillibels(-12f, -1000, 1000))
        assertEquals(350.toShort(), EqMath.dbToClampedMillibels(3.5f, -1500, 1500))
        assertEquals(0.toShort(), EqMath.dbToClampedMillibels(5f, 10, -10)) // broken range
    }

    @Test fun strengthClamped() {
        assertEquals(1000.toShort(), EqMath.clampStrength(5000))
        assertEquals(0.toShort(), EqMath.clampStrength(-1))
    }

    @Test fun volumeRatio() {
        assertEquals(0.5f, EqMath.volumeRatio(5, 0, 10), 0f)
        assertEquals(0f, EqMath.volumeRatio(5, 10, 10), 0f)
        assertEquals(1f, EqMath.volumeRatio(20, 0, 15), 0f)
    }
}

class AutoTweakTest {
    private val base = TonoPresetLibrary.default.toSettings()

    @Test fun modes() {
        assertEquals(TweakMode.OFF, AutoTweakEngine.modeFor(0.1f, false))
        assertEquals(TweakMode.LOUDNESS, AutoTweakEngine.modeFor(0.34f, true))
        assertEquals(TweakMode.NEUTRAL, AutoTweakEngine.modeFor(0.35f, true))
        assertEquals(TweakMode.NEUTRAL, AutoTweakEngine.modeFor(0.80f, true))
        assertEquals(TweakMode.PROTECT, AutoTweakEngine.modeFor(0.81f, true))
    }

    @Test fun lowVolumeLoudness() {
        val out = AutoTweakEngine.apply(base, 0.2f, true)
        assertEquals(base.bandsDb[0] + 1.5f, out.bandsDb[0], 0f)
        assertEquals(base.bandsDb[4] + 1.0f, out.bandsDb[4], 0f)
        assertEquals(base.bandsDb.subList(1, 4), out.bandsDb.subList(1, 4))
        assertEquals(base.bassBoost, out.bassBoost)
    }

    @Test fun highVolumeProtection() {
        val out = AutoTweakEngine.apply(base, 0.9f, true)
        assertEquals(base.bandsDb[1] - 1.5f, out.bandsDb[1], 0f)
        assertEquals((base.bassBoost * 0.8f).toInt(), out.bassBoost)
        assertEquals(base.virtualizer, out.virtualizer)
    }

    @Test fun disabledIsPassThrough() = assertEquals(base, AutoTweakEngine.apply(base, 0.95f, false))

    @Test fun neverExceedsLimits() {
        val hot = EqSettings(List(5) { 12f }, 1000, 1000)
        val out = AutoTweakEngine.apply(hot, 0.1f, true)
        assertTrue(out.bandsDb.all { it <= 12f })
    }
}

class PreferencesCodecTest {
    @Test fun roundTrip() {
        val b = listOf(1.5f, -2f, 0f, 4.5f, -12f)
        assertEquals(b, TonoPreferences.decodeBands(TonoPreferences.encodeBands(b)))
    }

    @Test fun rejectsGarbage() {
        assertNull(TonoPreferences.decodeBands("1,2,3"))
        assertNull(TonoPreferences.decodeBands("a,b,c,d,e"))
    }
}
