package com.tonotune.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.tonotune.app.audio.EngineStateHolder
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.TonoDevices
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.service.TonoAudioService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class PreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun defaultsAndPersistence() = runBlocking {
        val prefs = TonoPreferences(context)
        val d = prefs.current()
        assertEquals("tono_pro_signature", d.presetId)
        assertTrue(d.masterEnabled && d.autoTweakEnabled && d.autoStartOnConnect)
        assertNull(d.customSettings)

        prefs.selectPreset("garhwali_song")
        prefs.setAutoTweak(false)
        val custom = EqSettings(listOf(1f, 2f, 3f, 4f, 5f), 700, 100)
        prefs.setCustom(custom)
        val s = prefs.current()
        assertEquals("garhwali_song", s.presetId)
        assertFalse(s.autoTweakEnabled)
        assertEquals(custom, s.effectiveSettings)
        assertTrue(s.isCustomized)

        prefs.selectPreset("dj_songs") // selecting a preset clears tweaks
        assertNull(prefs.current().customSettings)
        prefs.selectPreset("does_not_exist") // ignored
        assertEquals("dj_songs", prefs.current().presetId)

        prefs.setCustom(EqSettings(listOf(99f, -99f, 0f, 0f, 0f), 5000, -3))
        val clamped = prefs.current().effectiveSettings
        assertEquals(12f, clamped.bandsDb[0]); assertEquals(-12f, clamped.bandsDb[1])
        assertEquals(1000, clamped.bassBoost); assertEquals(0, clamped.virtualizer)
    }
}

@RunWith(RobolectricTestRunner::class)
class DeviceMatchTest {
    @Test fun matchesTonoNames() {
        assertTrue(TonoDevices.matches("ZEB-TONO"))
        assertTrue(TonoDevices.matches("Zebronics Tono"))
        assertTrue(TonoDevices.matches("zebronics speaker"))
        assertFalse(TonoDevices.matches("JBL Flip 6"))
        assertFalse(TonoDevices.matches(null))
        assertFalse(TonoDevices.matches(""))
    }
}

@RunWith(RobolectricTestRunner::class)
class ServiceTest {
    @Test fun runsAsMediaPlaybackForegroundServiceAndStopsCleanly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(TonoAudioService::class.java).create().startCommand(0, 1)
        val service = controller.get()
        ShadowLooper.idleMainLooper()

        assertTrue(TonoAudioService.isRunning)
        val shadow = shadowOf(service)
        assertFalse("stopSelf must not be called", shadow.isStoppedBySelf)
        assertNotNull("startForeground must post a notification", shadow.lastForegroundNotification)
        assertEquals(TonoAudioService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, service.foregroundServiceType)

        val nm = app.getSystemService(NotificationManager::class.java)
        assertNotNull(nm.getNotificationChannel(TonoTuneApp.CHANNEL_ENGINE))

        controller.destroy()
        assertFalse(TonoAudioService.isRunning)
        assertFalse(EngineStateHolder.state.value.running)
    }
}
