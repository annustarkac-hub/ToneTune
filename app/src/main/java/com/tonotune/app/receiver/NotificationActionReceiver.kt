package com.tonotune.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tonotune.app.audio.TonoPresetLibrary
import com.tonotune.app.data.TonoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the "Next preset" and "Turn off" notification actions. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = TonoPreferences(context.applicationContext)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_NEXT_PRESET -> {
                        val presets = TonoPresetLibrary.presets
                        val index = presets.indexOfFirst { it.id == prefs.current().presetId }
                        prefs.selectPreset(presets[(index + 1).mod(presets.size)].id)
                    }
                    ACTION_TURN_OFF -> prefs.setMasterEnabled(false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEXT_PRESET = "com.tonotune.app.action.NEXT_PRESET"
        const val ACTION_TURN_OFF = "com.tonotune.app.action.TURN_OFF"

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
