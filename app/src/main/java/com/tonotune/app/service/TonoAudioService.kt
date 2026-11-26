package com.tonotune.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tonotune.app.R
import com.tonotune.app.TonoTuneApp
import com.tonotune.app.audio.AutoTweakEngine
import com.tonotune.app.audio.EngineControl
import com.tonotune.app.audio.EngineMode
import com.tonotune.app.audio.EngineStateHolder
import com.tonotune.app.audio.SessionScanner
import com.tonotune.app.audio.TonoEffectEngine
import com.tonotune.app.audio.TweakMode
import com.tonotune.app.audio.EqMath
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.data.UserSettings
import com.tonotune.app.receiver.NotificationActionReceiver
import com.tonotune.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent mediaPlayback foreground service that holds the Tono effect chain on the
 * global output mix (session 0) and on every media session apps announce, keeps it locked
 * against HyperOS system effects, and runs the volume-adaptive Auto-Tweak engine.
 */
class TonoAudioService : LifecycleService() {

    private val engine = TonoEffectEngine()
    private lateinit var prefs: TonoPreferences
    private lateinit var audioManager: AudioManager
    private val volumeRatio = MutableStateFlow(0.5f)
    private var lastNotificationKey: String? = null
    private var sessionReceiverRegistered = false
    @Volatile private var currentMode: EngineMode = EngineMode.MODERN
    @Volatile private var scannerActive = false
    /** Sessions announced by apps via broadcast; never auto-detached by the scanner. */
    private val announcedSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    /** Sessions found by the dumpsys scanner. */
    private val scannedSessions = mutableSetOf<Int>()

    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshVolume()
    }

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                VOLUME_CHANGED_ACTION -> refreshVolume()
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                    onSessionOpened(
                        intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1),
                        intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME),
                    )
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION ->
                    onSessionClosed(intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Promote to foreground immediately (must happen within 5 s of startForegroundService).
        if (!enterForeground(buildNotification(null, TweakMode.OFF))) {
            stopSelf()
            return
        }
        isRunning = true
        prefs = TonoPreferences(applicationContext)
        audioManager = getSystemService(AudioManager::class.java)

        lifecycleScope.launch(Dispatchers.Default) {
            currentMode = prefs.current().engineMode
            engine.setMode(currentMode)
            engine.start()
            publish()
        }
        refreshVolume()
        registerListeners()
        observeSettings()
        startWatchdogs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopSelfCleanly(); return START_NOT_STICKY }
            ACTION_SESSION_OPEN -> onSessionOpened(
                intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1),
                intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME),
            )
            ACTION_SESSION_CLOSE -> onSessionClosed(intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
            ACTION_REAPPLY -> lifecycleScope.launch(Dispatchers.Default) { engine.relockIfNeeded(); engine.reassert(); publish() }
        }
        return START_STICKY
    }

    private fun enterForeground(notification: Notification): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException / SecurityException on restricted starts.
        Log.e(TAG, "startForeground refused", e)
        false
    }

    private fun registerListeners() {
        runCatching {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching {
            ContextCompat.registerReceiver(this, runtimeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            sessionReceiverRegistered = true
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            combine(prefs.settings, volumeRatio, EngineControl.bypass) { s, v, b -> Triple(s, v, b) }
                .distinctUntilChanged()
                .collect { (settings, ratio, bypass) ->
                    if (!settings.masterEnabled) {
                        stopSelfCleanly()
                        return@collect
                    }
                    applySettings(settings, ratio, bypass)
                }
        }
        // Keep the notification in sync with preset + tweak mode.
        lifecycleScope.launch {
            combine(prefs.settings, volumeRatio) { s, v ->
                s to AutoTweakEngine.modeFor(v, s.autoTweakEnabled)
            }.map { (s, m) -> Triple(s.preset.name + if (s.isCustomized) " (Custom)" else "", m, s) }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second == b.second }
                .collect { (title, mode, _) -> updateNotification(title, mode) }
        }
    }

    private suspend fun applySettings(settings: UserSettings, ratio: Float, bypass: Boolean) = withContext(Dispatchers.Default) {
        if (settings.engineMode != currentMode) {
            currentMode = settings.engineMode
            engine.setMode(currentMode)
        }
        val tweaked = AutoTweakEngine.apply(settings.effectiveSettings, ratio, settings.autoTweakEnabled)
        engine.apply(tweaked, enabled = !bypass)
        publish(AutoTweakEngine.modeFor(ratio, settings.autoTweakEnabled))
    }

    private fun publish(mode: TweakMode? = null) {
        if (!isRunning) return
        EngineStateHolder.update { st ->
            // Atomic re-check: never resurrect "running" after onDestroy reset the state.
            if (!isRunning) return@update st
            st.copy(
                running = true,
                effectsAttached = engine.anyAttached,
                attachedSessions = engine.sessionCount,
                volumeRatio = volumeRatio.value,
                tweakMode = mode ?: st.tweakMode,
                lastError = if (engine.anyAttached) null else engine.lastError,
                mode = currentMode,
                sessions = engine.sessions(),
                scannerActive = scannerActive,
                bypassed = EngineControl.bypass.value,
            )
        }
    }

    private fun startWatchdogs() {
        // HyperOS 3 re-lock: periodically reclaim effects another app grabbed.
        lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(RELOCK_INTERVAL_MS)
                val relocked = engine.relockIfNeeded()
                if (relocked > 0) Log.i(TAG, "Re-locked $relocked effect chain(s)")
                publish()
            }
        }
        // Optional "catch every app" scanner (needs DUMP granted once via ADB).
        lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                scanSessions()
                delay(SCAN_INTERVAL_MS)
            }
        }
        // Fallback volume poll for ROMs that do not deliver volume broadcasts.
        lifecycleScope.launch {
            while (isActive) {
                delay(VOLUME_POLL_MS)
                refreshVolume()
            }
        }
    }

    private fun refreshVolume() {
        if (!::audioManager.isInitialized) return
        val ratio = runCatching {
            val stream = AudioManager.STREAM_MUSIC
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioManager.getStreamMinVolume(stream) else 0
            EqMath.volumeRatio(audioManager.getStreamVolume(stream), min, audioManager.getStreamMaxVolume(stream))
        }.getOrDefault(volumeRatio.value)
        volumeRatio.value = ratio
    }

    private suspend fun scanSessions() {
        if (!SessionScanner.isGranted(this)) {
            scannerActive = false
            return
        }
        val found = SessionScanner.scan()
        scannerActive = found != null
        if (found == null) return
        val ids = found.map { it.sessionId }.toSet()
        var changed = false
        found.filter { it.sessionId !in scannedSessions }.forEach { pb ->
            engine.attach(pb.sessionId, SessionScanner.labelFor(this, pb.uid))
            scannedSessions += pb.sessionId
            changed = true
        }
        (scannedSessions - ids).forEach { gone ->
            scannedSessions -= gone
            if (gone !in announcedSessions) engine.detach(gone)
            changed = true
        }
        if (changed) Log.i(TAG, "Scanner sessions: $scannedSessions")
        publish()
    }

    private fun onSessionOpened(sessionId: Int, packageName: String?) {
        if (sessionId <= 0) return
        announcedSessions += sessionId
        lifecycleScope.launch(Dispatchers.Default) {
            engine.attach(sessionId, packageName?.let(::appLabel))
            engine.reassert()
            publish()
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun onSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        announcedSessions -= sessionId
        if (sessionId in scannedSessions) return
        lifecycleScope.launch(Dispatchers.Default) {
            engine.detach(sessionId)
            engine.reassert()
            publish()
        }
    }

    private fun updateNotification(title: String, mode: TweakMode) {
        val key = "$title|$mode"
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(title, mode))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked; the FGS keeps running with its original notification.
        }
    }

    private fun buildNotification(presetName: String?, mode: TweakMode): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (mode) {
            TweakMode.LOUDNESS -> getString(R.string.tweak_loudness_short)
            TweakMode.PROTECT -> getString(R.string.tweak_protect_short)
            TweakMode.NEUTRAL -> getString(R.string.tweak_neutral_short)
            TweakMode.OFF -> getString(R.string.tweak_off_short)
        }
        return NotificationCompat.Builder(this, TonoTuneApp.CHANNEL_ENGINE)
            .setSmallIcon(R.drawable.ic_stat_tono)
            .setContentTitle(presetName ?: getString(R.string.notif_starting))
            .setContentText(text)
            .setSubText(getString(R.string.notif_subtext))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(ContextCompat.getColor(this, R.color.tono_accent))
            .addAction(
                R.drawable.ic_stat_next, getString(R.string.notif_next),
                NotificationActionReceiver.pendingIntent(this, NotificationActionReceiver.ACTION_NEXT_PRESET),
            )
            .addAction(
                R.drawable.ic_stat_power, getString(R.string.notif_turn_off),
                NotificationActionReceiver.pendingIntent(this, NotificationActionReceiver.ACTION_TURN_OFF),
            )
            .build()
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
        if (sessionReceiverRegistered) runCatching { unregisterReceiver(runtimeReceiver) }
        // Releasing effects can block on the engine lock; never do that on the main thread.
        Thread({ engine.release() }, "tono-release").start()
        EngineControl.bypass.value = false
        EngineStateHolder.reset()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TonoAudioService"
        const val NOTIFICATION_ID = 0x70A0
        private const val RELOCK_INTERVAL_MS = 8_000L
        private const val VOLUME_POLL_MS = 1_500L
        private const val SCAN_INTERVAL_MS = 4_000L
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        const val ACTION_START = "com.tonotune.app.action.START"
        const val ACTION_STOP = "com.tonotune.app.action.STOP"
        const val ACTION_REAPPLY = "com.tonotune.app.action.REAPPLY"
        const val ACTION_SESSION_OPEN = "com.tonotune.app.action.SESSION_OPEN"
        const val ACTION_SESSION_CLOSE = "com.tonotune.app.action.SESSION_CLOSE"

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Starts (or pokes) the engine as a foreground service.
         * @return false if Android refused a background start (Android 12+ restriction).
         */
        fun start(context: Context, action: String = ACTION_START, extras: Intent? = null): Boolean {
            val intent = Intent(context, TonoAudioService::class.java).setAction(action)
            extras?.extras?.let(intent::putExtras)
            return try {
                // Already foreground: a plain start just delivers the command, with no new
                // "must call startForeground within N seconds" obligation (avoids an ANR/crash).
                if (isRunning) context.startService(intent)
                else ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Background FGS start not allowed", e)
                } else {
                    Log.e(TAG, "Unable to start engine", e)
                }
                false
            }
        }

        /** Forwards a command to an already-running engine; no-op otherwise. */
        fun sendIfRunning(context: Context, intent: Intent) {
            if (!isRunning) return
            runCatching { context.startService(intent.setClass(context, TonoAudioService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TonoAudioService::class.java)) }
        }
    }
}
