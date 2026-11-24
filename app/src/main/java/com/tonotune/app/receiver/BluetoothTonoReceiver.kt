package com.tonotune.app.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import com.tonotune.app.TonoTuneApp
import com.tonotune.app.audio.TonoDevices
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.service.TonoAudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starts the engine when a Zebronics Tono (name contains "tono" or "zebronics") connects
 * and stops it when the speaker disconnects.
 */
class BluetoothTonoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val name = TonoDevices.nameOf(context, device)
        if (!TonoDevices.matches(name)) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = TonoPreferences(app).current()
                if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    if (settings.masterEnabled && settings.autoStartOnConnect) {
                        val started = TonoAudioService.start(app, TonoAudioService.ACTION_REAPPLY)
                        if (!started) TonoTuneApp.postActivationPrompt(app, name ?: "Zebronics Tono")
                    }
                } else if (TonoAudioService.isRunning && settings.autoStartOnConnect) {
                    TonoAudioService.stop(app)
                }
            } catch (t: Throwable) {
                Log.e("BluetoothTonoReceiver", "Failed handling $action (sdk ${Build.VERSION.SDK_INT})", t)
            } finally {
                pending.finish()
            }
        }
    }
}
