package com.tonotune.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tonotune.app.audio.EngineState
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.audio.TweakMode
import com.tonotune.app.data.UserSettings
import com.tonotune.app.ui.ScreenActions
import com.tonotune.app.ui.ScreenState
import com.tonotune.app.ui.TonoScreen
import com.tonotune.app.ui.theme.TonoTuneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real screen on the JVM and writes PNGs to app/build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun state(
        presetId: String = "garhwali_song",
        volume: Float = 0.22f,
        connected: Boolean = true,
        category: PresetCategory? = null,
        helpers: Boolean = false,
        draft: EqSettings? = null,
        master: Boolean = true,
    ) = ScreenState(
        settings = UserSettings(presetId = presetId, masterEnabled = master),
        draft = draft,
        engine = EngineState(
            running = master, effectsAttached = true, globalSessionOk = true, attachedSessions = 2,
            deviceBandCount = 5, bandRangeDb = -15f to 15f, volumeRatio = volume,
            tweakMode = com.tonotune.app.audio.AutoTweakEngine.modeFor(volume, true),
        ),
        bluetoothOutput = if (connected) "ZEB-TONO" else null,
        category = category,
        permissionsMissing = helpers,
        batteryOptimized = helpers,
    )

    private fun shoot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w411dp-h2900dp-xxhdpi")
    fun fullPage() {
        compose.setContent { TonoTuneTheme { TonoScreen(state(), ScreenActions()) } }
        shoot("01_full_page")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneViewportProtectMode() {
        compose.setContent { TonoTuneTheme { TonoScreen(state("dj_songs", volume = 0.9f), ScreenActions()) } }
        shoot("02_phone_dj_protect")
    }

    @Test @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    fun disconnectedWithHelpers() {
        compose.setContent {
            TonoTuneTheme { TonoScreen(state(connected = false, helpers = true, volume = 0.5f, master = false), ScreenActions()) }
        }
        shoot("03_disconnected_helpers")
    }

    @Test @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    fun filteredCustomized() {
        val custom = EqSettings(listOf(7f, -4f, 0.5f, 6f, 8f), 950, 700)
        compose.setContent {
            TonoTuneTheme { TonoScreen(state("dance_songs", category = PresetCategory.DANCE, draft = custom, volume = 0.5f), ScreenActions()) }
        }
        shoot("04_dance_filter_custom")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun smallPhone() {
        compose.setContent { TonoTuneTheme { TonoScreen(state(), ScreenActions()) } }
        shoot("05_small_phone")
    }

    @Test @Config(qualifiers = "w411dp-h2900dp-xxhdpi")
    fun presetClickAndMasterToggleCallBack() {
        var picked: String? = null
        var master: Boolean? = null
        compose.setContent {
            TonoTuneTheme {
                TonoScreen(state(), ScreenActions(onPreset = { picked = it }, onMaster = { master = it }))
            }
        }
        compose.onNodeWithText("DJ Songs").performClick()
        compose.onNodeWithText("Master EQ").performClick()
        assertEquals("dj_songs", picked)
        assertEquals(false, master)
        assertEquals(TweakMode.LOUDNESS, com.tonotune.app.audio.AutoTweakEngine.modeFor(0.22f, true))
    }
}
