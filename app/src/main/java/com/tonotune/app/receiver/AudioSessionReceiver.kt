package com.tonotune.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.service.TonoAudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Players that address equalizer apps directly (explicit broadcast) land here even when the
 * engine was killed. Forward the session to the engine, starting it if the user wants the EQ on.
 * (While the engine runs, its own runtime receiver gets the implicit broadcasts too.)
 */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (session <= 0) return
        val action = when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> TonoAudioService.ACTION_SESSION_OPEN
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> TonoAudioService.ACTION_SESSION_CLOSE
            else -> return
        }
        val forward = Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))

        if (TonoAudioService.isRunning || action == TonoAudioService.ACTION_SESSION_CLOSE) {
            TonoAudioService.sendIfRunning(context, forward)
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Allowed in the background when battery optimisation is off; fails safely otherwise.
                if (TonoPreferences(app).current().masterEnabled) {
                    TonoAudioService.start(app, TonoAudioService.ACTION_SESSION_OPEN, forward)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
