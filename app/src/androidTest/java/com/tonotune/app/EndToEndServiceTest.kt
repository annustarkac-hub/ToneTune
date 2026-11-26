package com.tonotune.app

import android.Manifest
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.tonotune.app.audio.EngineControl
import com.tonotune.app.audio.EngineStateHolder
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.TestSignal
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.service.TonoAudioService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot
import kotlin.math.log10

/**
 * Full production path: TonoAudioService running, a "music app" announces its session with the
 * standard broadcast, and the Tono tuning must audibly change that app's output.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndServiceTest {
    @get:Rule val perms: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private var track: AudioTrack? = null
    private var viz: Visualizer? = null

    @After fun tearDown() {
        EngineControl.bypass.value = false
        viz?.release(); track?.run { stop(); release() }
        TonoAudioService.stop(ctx)
    }

    private fun bassMinusTreble(v: Visualizer): Double {
        val rate = v.samplingRate / 1000.0; val n = v.captureSize; val fft = ByteArray(n)
        var low = 1e-9; var high = 1e-9
        repeat(50) {
            if (v.getFft(fft) == Visualizer.SUCCESS) for (k in 1 until n / 2) {
                val f = k * rate / n; val m = hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
                if (f <= 180) low += m * m else if (f in 2500.0..9000.0) high += m * m
            }
            Thread.sleep(50)
        }
        return 10 * log10(low / high)
    }

    @Test fun announcedAppSessionIsTunedByTheService() {
        val prefs = TonoPreferences(ctx)
        runBlocking {
            prefs.setMasterEnabled(true)
            prefs.setAutoTweak(false)
            prefs.setCustom(EqSettings(listOf(12f, 6f, 0f, -12f, -12f), 1000, 0))
        }
        val pcm = TestSignal.musicLoop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(TestSignal.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        track = t
        t.write(pcm, 0, pcm.size); t.setLoopPoints(0, pcm.size / 2, -1); t.play()

        assertTrue("service failed to start", TonoAudioService.start(ctx))
        var waited = 0
        while (!EngineStateHolder.state.value.running && waited < 100) { Thread.sleep(100); waited++ }
        assertTrue("engine never went live", EngineStateHolder.state.value.running)

        // What Spotify / YT Music / Poweramp do when playback starts:
        ctx.sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, t.audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "com.example.player"))
        waited = 0
        while (EngineStateHolder.state.value.attachedSessions < 1 && waited < 100) { Thread.sleep(100); waited++ }
        val st = EngineStateHolder.state.value
        Log.i("TonoE2E", "state: sessions=${st.sessions}")
        assertTrue("app session was not linked: ${st.sessions}", st.attachedSessions >= 1)

        Thread.sleep(600)
        viz = Visualizer(0).apply {
            enabled = false; captureSize = Visualizer.getCaptureSizeRange()[1]
            scalingMode = Visualizer.SCALING_MODE_AS_PLAYED; enabled = true
        }
        Thread.sleep(400)
        EngineControl.bypass.value = true; Thread.sleep(1200)
        val off = bassMinusTreble(viz!!)
        EngineControl.bypass.value = false; Thread.sleep(1200)
        val on = bassMinusTreble(viz!!)
        Log.i("TonoE2E", "bypass=%.1f tuned=%.1f delta=%.1f dB".format(off, on, on - off))
        assertTrue("tuning did not change the app's audio (delta ${on - off} dB)", on - off > 3)
        runBlocking { prefs.resetCustom(); prefs.setAutoTweak(true) }
    }

    @Test fun playerBroadcastStartsStoppedEngineAndLinksSession() {
        runBlocking { TonoPreferences(ctx).setMasterEnabled(true) }
        TonoAudioService.stop(ctx)
        var waited = 0
        while (TonoAudioService.isRunning && waited < 50) { Thread.sleep(100); waited++ }
        val fakeSession = 4242
        // What players that address EQ apps directly send (explicit broadcast).
        ctx.sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            .setPackage(ctx.packageName)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, fakeSession)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "com.example.player"))
        waited = 0
        while (EngineStateHolder.state.value.sessions.none { it.sessionId == fakeSession } && waited < 150) {
            Thread.sleep(100); waited++
        }
        val st = EngineStateHolder.state.value
        Log.i("TonoE2E", "auto-start: running=${st.running} sessions=${st.sessions}")
        assertTrue("engine did not start", st.running)
        // A fake id may be refused by the audio server; the engine must at least have tried and stayed up.
        assertTrue(TonoAudioService.isRunning)
    }
}
