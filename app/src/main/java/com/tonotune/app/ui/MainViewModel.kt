package com.tonotune.app.ui

import android.app.Application
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonotune.app.audio.EngineState
import com.tonotune.app.audio.EngineStateHolder
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.audio.TonoDevices
import com.tonotune.app.data.TonoPreferences
import com.tonotune.app.data.UserSettings
import com.tonotune.app.service.TonoAudioService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = TonoPreferences(app)
    private val audioManager = app.getSystemService(AudioManager::class.java)

    val settings: StateFlow<UserSettings?> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val engine: StateFlow<EngineState> = EngineStateHolder.state

    /** In-flight slider edits, shown instantly and written to DataStore (debounced). */
    private val _draft = MutableStateFlow<EqSettings?>(null)
    val draft: StateFlow<EqSettings?> = _draft.asStateFlow()

    private val _bluetoothOutput = MutableStateFlow<String?>(null)
    val bluetoothOutput: StateFlow<String?> = _bluetoothOutput.asStateFlow()

    private val _category = MutableStateFlow<PresetCategory?>(null)
    val category: StateFlow<PresetCategory?> = _category.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshBluetooth()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshBluetooth()
    }

    init {
        runCatching { audioManager?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper())) }
        refreshBluetooth()

        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _draft.filterNotNull().collectLatest { d ->
                delay(DRAFT_WRITE_DELAY_MS)
                prefs.setCustom(d)
            }
        }
        // Drop the draft once persisted (or when the preset changed elsewhere, e.g. notification).
        viewModelScope.launch {
            var lastPreset: String? = null
            settings.filterNotNull().collect { s ->
                val d = _draft.value
                if (d != null && s.effectiveSettings == d.sanitized()) _draft.value = null
                if (lastPreset != null && lastPreset != s.presetId) _draft.value = null
                lastPreset = s.presetId
            }
        }
    }

    fun refreshBluetooth() {
        _bluetoothOutput.value = TonoDevices.connectedBluetoothOutput(getApplication())
    }

    fun setCategory(category: PresetCategory?) { _category.value = category }

    fun selectPreset(id: String) {
        _draft.value = null
        viewModelScope.launch { prefs.selectPreset(id) }
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMasterEnabled(enabled)
            if (enabled) TonoAudioService.start(getApplication()) else TonoAudioService.stop(getApplication())
        }
    }

    fun setAutoTweak(enabled: Boolean) = viewModelScope.launch { prefs.setAutoTweak(enabled) }

    fun setAutoStart(enabled: Boolean) = viewModelScope.launch { prefs.setAutoStart(enabled) }

    private fun base(): EqSettings =
        _draft.value ?: settings.value?.effectiveSettings ?: EqSettings.FLAT

    fun setBand(index: Int, db: Float) {
        val b = base()
        _draft.value = b.copy(bandsDb = b.bandsDb.toMutableList().also { it[index] = db }).sanitized()
    }

    fun setBassBoost(value: Int) { _draft.value = base().copy(bassBoost = value).sanitized() }

    fun setVirtualizer(value: Int) { _draft.value = base().copy(virtualizer = value).sanitized() }

    fun resetTweaks() {
        _draft.value = null
        viewModelScope.launch { prefs.resetCustom() }
    }

    /** Called when the screen becomes visible: (re)start the engine if the user wants it on. */
    fun ensureEngine() {
        viewModelScope.launch {
            if (prefs.current().masterEnabled) {
                TonoAudioService.start(getApplication(), TonoAudioService.ACTION_REAPPLY)
            }
        }
    }

    override fun onCleared() {
        runCatching { audioManager?.unregisterAudioDeviceCallback(deviceCallback) }
        super.onCleared()
    }

    private companion object { const val DRAFT_WRITE_DELAY_MS = 60L }
}
