package com.tonotune.app

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.tonotune.app.audio.EffectStack
import com.tonotune.app.audio.EngineMode
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.TestSignal
import com.tonotune.app.audio.TonoPresetLibrary
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot
import kotlin.math.log10

/**
 * Real-device self-test: plays the Sound Check loop, measures the mixed output spectrum with
 * Visualizer, and proves each engine audibly changes the bass/treble balance.
 */
@RunWith(AndroidJUnit4::class)
class AudioEngineSelfTest {
    @get:Rule val perms: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private lateinit var track: AudioTrack
    private lateinit var viz: Visualizer

    /** Extreme "bass up, treble down" curve so the measurement is unambiguous. */
    private val bassHeavy = EqSettings(listOf(12f, 6f, 0f, -12f, -12f), 1000, 0)

    @Before fun setUp() {
        val pcm = TestSignal.musicLoop()
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(TestSignal.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size / 2, -1)
        track.play()
        Thread.sleep(500)
    }

    /** Created AFTER the effect under test so it always sits behind it in the output chain. */
    private fun startVisualizer() {
        viz = Visualizer(0).apply {
            enabled = false
            captureSize = Visualizer.getCaptureSizeRange()[1]
            scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            enabled = true
        }
        Thread.sleep(500)
    }

    @After fun tearDown() {
        if (::viz.isInitialized) runCatching { viz.release() }
        runCatching { track.stop(); track.release() }
    }

    /** Low (<=180 Hz) minus high (2.5–9 kHz) energy, in dB, averaged over ~3 s. */
    private fun bassMinusTrebleDb(): Double {
        val rate = viz.samplingRate / 1000.0 // mHz -> Hz
        val size = viz.captureSize
        val fft = ByteArray(size)
        var low = 1e-9; var high = 1e-9
        repeat(60) {
            if (viz.getFft(fft) == Visualizer.SUCCESS) {
                for (k in 1 until size / 2) {
                    val f = k * rate / size
                    val mag = hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
                    if (f <= 180) low += mag * mag else if (f in 2500.0..9000.0) high += mag * mag
                }
            }
            Thread.sleep(50)
        }
        return 10 * log10(low / high)
    }

    private fun measure(session: Int, mode: EngineMode): Double {
        val stack = EffectStack(session, mode)
        val err = stack.open()
        assertTrue("[$mode/session $session] could not open: $err", err == null)
        startVisualizer()
        try {
            stack.apply(bassHeavy, enabled = false); Thread.sleep(400)
            val off = bassMinusTrebleDb()
            stack.apply(bassHeavy, enabled = true); Thread.sleep(400)
            val on = bassMinusTrebleDb()
            // Also make sure a normal preset applies without throwing.
            stack.apply(TonoPresetLibrary.default.toSettings(), enabled = true)
            Log.i("TonoSelfTest", "mode=$mode session=$session kind=${stack.kind} off=%.1f on=%.1f delta=%.1f dB".format(off, on, on - off))
            return on - off
        } finally {
            stack.release()
        }
    }

    // Visualizer FFT is 8-bit, so its noise floor compresses big changes; > 4 dB is a clear effect.
    @Test fun modernOnOwnSession() = assertTrue(measure(track.audioSessionId, EngineMode.MODERN) > 4)
    @Test fun classicOnOwnSession() = assertTrue(measure(track.audioSessionId, EngineMode.CLASSIC) > 4)

    /** Diagnostic only: logs whether this ROM applies global (session 0) effects to app audio. */
    @Test fun globalMixDiagnostic() {
        measure(0, EngineMode.MODERN)
        measure(0, EngineMode.CLASSIC)
    }
}
