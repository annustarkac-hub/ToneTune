# TonoTune

System-wide Bluetooth equalizer for the **Zebronics Tono** 50W tower speaker (10.16 cm subwoofer + dual 7.62 cm full-range drivers, Bluetooth 5.1).
Built with Kotlin and Jetpack Compose (Material 3). Targets Android 16 (API 36) / HyperOS 3, tuned for the Poco X8 Pro Max (arm64-v8a).

| Main | Settings | First run |
|---|---|---|
| ![](docs/screenshots/02_phone_dj_protect.jpg) | ![](docs/screenshots/06_settings.jpg) | ![](docs/screenshots/07_not_linked.jpg) |

## Install
1. Copy `dist/TonoTune-v1.2.0-release.apk`. It installs over older versions because it uses the same signing key. to the phone (SHA-256 in the `.sha256` file next to it).
2. Open it and allow "Install unknown apps" for your file manager.
3. Open TonoTune and allow **Nearby devices (Bluetooth)** and **Notifications**.
4. On HyperOS, tap **Battery → No restrictions** and **Autostart → on** in the "Keep TonoTune alive" card. Without this, Android 12+ blocks the EQ from starting by itself when the speaker connects, and TonoTune shows a one-tap "activate" notification instead.

## v1.2.0: clean, minimal UI
- **Main screen:** header (speaker status, settings, master switch), one notice at a time, the live curve with **Hold for A/B**, compact preset chips that scroll sideways, and fine-tune (5 faders plus Bass boost and 3D width sliders).
- **Settings:** Auto-Tweak, Engine, Sound check, *Every app* (with the list of linked apps) and background setup.
- **Removed:** the speaker animation, duplicate dB readouts, the volume-zone meter, the big preset cards, the knobs, the LIVE pill and the diagnostics table.
- **Engine auto-start:** if a player addresses TonoTune directly while the engine is stopped, the engine now starts and links that app.

## "I can't hear any difference": fixed in v1.1.0
v1.0.0 put the EQ only on the **global output mix (session 0)**. On an Android 11 device, a measurement with Visualizer showed that this changes **nothing** in other apps' audio (≈0 dB), while the same EQ attached to an app's own audio session changes it by 6–7 dB. The engine said "LIVE", but the music was untouched.

v1.1.0 fixes this:
1. **The EQ is attached per app.** Spotify, YT Music, Poweramp and most players announce their audio session when playback starts. TonoTune links to it, and the session appears under *Settings → Apps → Linked now*. Start TonoTune before pressing play.
2. **Catch every app (optional, one-time PC step).** YouTube, Instagram and many other apps never announce a session. Grant one permission once:
   - Developer options → USB debugging on. On Xiaomi/HyperOS, also turn on **USB debugging (Security settings)**.
   - On a PC, run: `adb shell pm grant com.tonotune.app android.permission.DUMP`
   - TonoTune then reads the playing sessions from `dumpsys audio` every 4 s and attaches to all of them. The in-app command has a copy button.
3. **Modern engine.** `DynamicsProcessing` provides a 14-band curve, with Bass Boost built into the low bands, plus a limiter so boosts don't clip. **Classic** (the legacy Equalizer + BassBoost) is still available as a switch.
4. **Sound Check.** A built-in test beat plays on the Tono through the Tono chain, with a **Tono EQ / Original** switch. If you hear a change here but not in your music app, use step 2.
5. **Hold to hear original (A/B).** Press and hold to bypass the EQ on your own music.

## Features
- **27 presets calibrated for the Tono** (Tono Pro Signature, Garhwali Song, DJ Songs, Dance Songs and 23 more) with category filters.
- **Five fine-tune faders** (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz, from −12 to +12 dB in 0.5 dB steps) with haptic ticks, plus rotary knobs for **Subwoofer Bass Boost** and **3D Virtualizer** (0–1000). Tweaks are saved, and **Reset** returns to the preset.
- **Auto-Tweak** (on by default) follows the media volume live:
  - below 35%: +1.5 dB at 60 Hz and +1.0 dB at 14 kHz (loudness)
  - above 80%: −1.5 dB at 230 Hz and Bass Boost at 80% (woofer protection)
- **Auto-start with Tono**: the EQ turns on when a Bluetooth device whose name contains "tono" or "zebronics" connects, and off when it disconnects.
- A **live frequency-response curve** that shows the actual output, Auto-Tweak included.
- **Engine diagnostics**: global session lock, attached app sessions, and the device's EQ band count and range.
- **Notification controls**: "Next preset" and "Turn off".

## How the audio engine works
- `TonoAudioService` is a `mediaPlayback` foreground service. It calls `startForeground` straight away in `onCreate`, and every `startForegroundService` call is wrapped so `ForegroundServiceStartNotAllowedException` falls back to the activation notification instead of crashing.
- `Equalizer`, `BassBoost` and `Virtualizer` are created with priority `Int.MAX_VALUE` on the global session `0`. A separate chain is created for every session that media apps announce with `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, and released on `CLOSE`.
- **HyperOS re-lock**: a watchdog runs every 8 s. It re-creates any chain that lost control (`hasControl()` or a control-status callback) to a system or other EQ.
- **Band mapping**: the 5-point curve is interpolated on a log-frequency axis onto the device's real band centres, so phones with something other than the AOSP 5 bands still get the right curve. Values are converted to millibels and clamped into `getBandLevelRange()`, so `setBandLevel` never throws.
- **Volume tracking**: a `Settings.System` ContentObserver plus the `VOLUME_CHANGED_ACTION` broadcast, with a 1.5 s poll as a fallback.

Note: `Virtualizer` is deprecated on API 36 but still works, and it is the only system-wide widener available. Some ROMs refuse global effects while Dolby or another EQ app holds them. The app then shows an "Audio effects blocked" card.

## Tests
- **38 JVM tests** (`./gradlew testDebugUnitTest`) cover presets, EQ maths, the DP curve, Auto-Tweak, the `dumpsys` session parser (Android 8–16 formats), the test signal, DataStore, the foreground-service lifecycle and UI screenshots.
- **On-device self-test** (`app/src/androidTest`) measures the real output with Visualizer:
  - Modern and Classic on an app session must change the bass/treble balance.
  - Session 0 is logged as a diagnostic.
  - An end-to-end test runs the real service, sends a "session opened" broadcast like a music player does, and checks that the A/B bypass changes that app's audio.

  Run it on a phone:
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```
  On an x86_64 emulator, add `-PtonoAllAbis`.

## Build
```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lintRelease
```
- JDK 17+, Android SDK platform 36. AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.3.
- Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), or the `TONO_KEYSTORE_PATH` / `TONO_KEYSTORE_PASSWORD` / `TONO_KEY_ALIAS` / `TONO_KEY_PASSWORD` env vars. Without them, the release build is signed with the debug key so it still installs.
- `ROBOLECTRIC_DEPS_DIR` (optional) points Robolectric at a pre-downloaded `android-all` jar for offline test runs.
- The UI screenshot tests write PNGs to `app/build/screenshots/`.

### CI (`.github/workflows/build-apk.yml`)
Runs on push to `main`, on pull requests, on `v*` tags and manually. It runs the unit tests, builds `app-debug.apk` and `app-release.apk`, runs lint, and uploads both APKs and the screenshots as artifacts. A `v*` tag also attaches the release APK to a GitHub Release.
For CI builds signed with your release key, add these repository secrets: `TONO_KEYSTORE_BASE64` (`base64 -w0 tonotune-release.jks`), `TONO_KEYSTORE_PASSWORD`, `TONO_KEY_ALIAS` and `TONO_KEY_PASSWORD`.

## Project layout
```
app/src/main/java/com/tonotune/app/
  audio/     EqPreset, TonoPresetLibrary (27 presets), EqMath (+ DpCurve, AutoTweakEngine), TonoEffectEngine,
             SessionScanner, SoundCheck (TestSignal + player), EngineControl, TonoDevices, EngineState
  service/   TonoAudioService (foreground service, re-lock, volume observer)
  receiver/  BluetoothTonoReceiver, AudioSessionReceiver, NotificationActionReceiver
  data/      TonoPreferences (DataStore)
  ui/        MainActivity, MainViewModel, TonoScreen, components/, theme/
```
