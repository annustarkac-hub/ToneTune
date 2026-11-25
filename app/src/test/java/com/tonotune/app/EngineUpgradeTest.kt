package com.tonotune.app

import com.tonotune.app.audio.DpCurve
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.SessionScanner
import com.tonotune.app.audio.TestSignal
import com.tonotune.app.audio.TonoPresetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DpCurveTest {
    @Test fun cutoffsAscendAndCoverAudibleRange() {
        val c = DpCurve.CUTOFFS_HZ
        for (i in 1 until c.size) assertTrue(c[i] > c[i - 1])
        assertTrue(c.last() >= 20000f)
    }

    @Test fun flatIsFlat() = DpCurve.bands(EqSettings.FLAT).forEach { assertEquals(0f, it.gainDb, 1e-4f) }

    @Test fun followsPresetCurve() {
        val s = TonoPresetLibrary.find("dj_songs")!!.toSettings().copy(bassBoost = 0)
        val bands = DpCurve.bands(s)
        // Band containing 60 Hz ~ +6, band containing 3.6 kHz ~ +3, 230 Hz region negative.
        val at = { hz: Float -> bands[DpCurve.CUTOFFS_HZ.indexOfFirst { it >= hz }].gainDb }
        assertTrue(abs(at(60f) - 6f) < 1f)
        assertTrue(at(230f) < -1.5f)
        assertTrue(abs(at(3600f) - 3f) < 1f)
    }

    @Test fun bassBoostOnlyLiftsLows() {
        val base = EqSettings.FLAT
        val boosted = DpCurve.bands(base.copy(bassBoost = 1000))
        assertEquals(DpCurve.MAX_BASS_BOOST_DB, boosted.first().gainDb, 1e-3f)
        assertTrue(boosted.filter { it.cutoffHz > 400f }.all { it.gainDb == 0f })
        assertEquals(DpCurve.MAX_BASS_BOOST_DB / 2, DpCurve.bassBoostDb(70f, 500), 1e-3f)
    }

    @Test fun gainsClampedAndHeadroomNegative() {
        val hot = EqSettings(List(5) { 12f }, 1000, 0)
        val bands = DpCurve.bands(hot)
        assertTrue(bands.all { it.gainDb <= DpCurve.MAX_GAIN_DB })
        val h = DpCurve.headroomDb(bands)
        assertTrue(h < 0f && h >= -4f)
        assertEquals(0f, DpCurve.headroomDb(DpCurve.bands(EqSettings.FLAT)), 0f)
    }
}

class SessionScannerTest {
    private val android14 = """
        Playback activity:
          AudioPlaybackConfiguration piid:87 deviceIds:[3] type:android.media.MediaPlayer u/pid:10234/5555 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x800 tags= bundle=null sessionId:1985 mutedState:none
          AudioPlaybackConfiguration piid:95 deviceIds:[3] type:android.media.AudioTrack u/pid:10111/777 state:paused attr:AudioAttributes: usage=USAGE_NOTIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:2001 mutedState:none
          AudioPlaybackConfiguration piid:99 deviceIds:[3] type:android.media.AudioTrack u/pid:10500/888 state:released attr:AudioAttributes: usage=USAGE_MEDIA sessionId:2010 mutedState:none
          AudioPlaybackConfiguration piid:101 deviceIds:[3] type:android.media.AudioTrack u/pid:10077/999 state:started attr:AudioAttributes: usage=USAGE_GAME sessionId:2033 mutedState:none
          AudioPlaybackConfiguration piid:102 type:android.media.AudioTrack u/pid:10066/999 state:started attr:AudioAttributes: usage=USAGE_MEDIA sessionId:2044 mutedState:none
    """.trimIndent()

    private val android10 = """
          ID:23 -- type:android.media.SoundPool -- u/pid:10050/123 -- state:idle -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x0 tags= bundle=null -- session:345
          ID:31 -- type:android.media.AudioTrack -- u/pid:10088/456 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null -- session:777
    """.trimIndent()

    @Test fun parsesModernFormatAndFiltersNonMedia() {
        val r = SessionScanner.parse(android14, ownUid = 10066)
        assertEquals(listOf(1985, 2033), r.map { it.sessionId })
        assertEquals(10234, r.first().uid)
        assertEquals("started", r.first().state)
    }

    @Test fun parsesLegacyFormat() =
        assertEquals(listOf(345, 777), SessionScanner.parse(android10).map { it.sessionId })

    @Test fun ignoresUnrelatedText() =
        assertTrue(SessionScanner.parse("Permission Denial: can't dump audio\nsession:5").isEmpty())
}

class TestSignalTest {
    @Test fun loopIsStereoHeadroomAndHasEnergy() {
        val pcm = TestSignal.musicLoop()
        assertEquals(TestSignal.SAMPLE_RATE * TestSignal.SECONDS * 2, pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak should be ~ -6 dBFS", peak in 15000..17000)
        val rms = kotlin.math.sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
        assertTrue(rms > 1500)
    }
}
