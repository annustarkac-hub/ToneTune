package com.tonotune.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Detection helpers for the Zebronics Tono speaker. */
object TonoDevices {
    private val KEYWORDS = listOf("tono", "zebronics")

    fun matches(name: CharSequence?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.toString().lowercase()
        return KEYWORDS.any { lower.contains(it) }
    }

    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun nameOf(context: Context, device: BluetoothDevice?): String? {
        if (device == null || !hasConnectPermission(context)) return null
        return try { device.name } catch (_: SecurityException) { null }
    }

    private val BT_OUTPUT_TYPES = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= 31) {
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (Build.VERSION.SDK_INT >= 33) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    /**
     * Returns the product name of a connected Bluetooth audio output, preferring a Tono.
     * Uses AudioManager (no Bluetooth permission needed).
     */
    fun connectedBluetoothOutput(context: Context): String? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        val names = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in BT_OUTPUT_TYPES }
                .map { it.productName?.toString().orEmpty() }
        }.getOrDefault(emptyList())
        return names.firstOrNull(::matches) ?: names.firstOrNull { it.isNotBlank() }
    }
}
