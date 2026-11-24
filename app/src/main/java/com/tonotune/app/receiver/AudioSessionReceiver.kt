package com.tonotune.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.tonotune.app.service.TonoAudioService

/**
 * Media apps (YouTube, YT Music, Spotify, local players) broadcast their session ids.
 * Forward them to the running engine so the active tuning is re-asserted per session.
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
        TonoAudioService.sendIfRunning(
            context,
            Intent(action).putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session),
        )
    }
}
