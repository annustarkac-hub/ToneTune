package com.tonotune.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tonotune.app.audio.EngineMode
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.TonoPresetLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.tonoDataStore: DataStore<Preferences> by preferencesDataStore(name = "tonotune_settings")

/** Everything the user controls, as one immutable snapshot. */
data class UserSettings(
    val presetId: String = TonoPresetLibrary.DEFAULT_PRESET_ID,
    val masterEnabled: Boolean = true,
    val autoTweakEnabled: Boolean = true,
    val autoStartOnConnect: Boolean = true,
    /** Non-null when the user has fine-tuned the selected preset. */
    val customSettings: EqSettings? = null,
    val engineMode: EngineMode = EngineMode.MODERN,
) {
    val preset get() = TonoPresetLibrary.byIdOrDefault(presetId)
    val effectiveSettings: EqSettings get() = customSettings ?: preset.toSettings()
    val isCustomized: Boolean get() = customSettings != null && customSettings != preset.toSettings()
}

/** Jetpack DataStore persistence for TonoTune. */
class TonoPreferences(private val context: Context) {

    private object Keys {
        val PRESET_ID = stringPreferencesKey("preset_id")
        val MASTER = booleanPreferencesKey("master_enabled")
        val AUTO_TWEAK = booleanPreferencesKey("auto_tweak_enabled")
        val AUTO_START = booleanPreferencesKey("auto_start_on_connect")
        val CUSTOM_BANDS = stringPreferencesKey("custom_bands_db")
        val CUSTOM_BASS = intPreferencesKey("custom_bass_boost")
        val CUSTOM_VIRT = intPreferencesKey("custom_virtualizer")
        val ENGINE_MODE = stringPreferencesKey("engine_mode")
    }

    val settings: Flow<UserSettings> = context.tonoDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    suspend fun current(): UserSettings = settings.first()

    suspend fun selectPreset(id: String) {
        if (TonoPresetLibrary.find(id) == null) return
        context.tonoDataStore.edit {
            it[Keys.PRESET_ID] = id
            clearCustom(it)
        }
    }

    suspend fun setMasterEnabled(enabled: Boolean) = context.tonoDataStore.edit { it[Keys.MASTER] = enabled }

    suspend fun setAutoTweak(enabled: Boolean) = context.tonoDataStore.edit { it[Keys.AUTO_TWEAK] = enabled }

    suspend fun setAutoStart(enabled: Boolean) = context.tonoDataStore.edit { it[Keys.AUTO_START] = enabled }

    suspend fun setEngineMode(mode: EngineMode) = context.tonoDataStore.edit { it[Keys.ENGINE_MODE] = mode.name }

    suspend fun setCustom(settings: EqSettings) {
        val clean = settings.sanitized()
        context.tonoDataStore.edit {
            it[Keys.CUSTOM_BANDS] = encodeBands(clean.bandsDb)
            it[Keys.CUSTOM_BASS] = clean.bassBoost
            it[Keys.CUSTOM_VIRT] = clean.virtualizer
        }
    }

    suspend fun resetCustom() = context.tonoDataStore.edit { clearCustom(it) }

    private fun clearCustom(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(Keys.CUSTOM_BANDS)
        prefs.remove(Keys.CUSTOM_BASS)
        prefs.remove(Keys.CUSTOM_VIRT)
    }

    private fun read(p: Preferences): UserSettings {
        val presetId = p[Keys.PRESET_ID]?.takeIf { TonoPresetLibrary.find(it) != null }
            ?: TonoPresetLibrary.DEFAULT_PRESET_ID
        val bands = p[Keys.CUSTOM_BANDS]?.let(::decodeBands)
        val custom = if (bands != null) {
            EqSettings(bands, p[Keys.CUSTOM_BASS] ?: 0, p[Keys.CUSTOM_VIRT] ?: 0).sanitized()
        } else null
        return UserSettings(
            presetId = presetId,
            masterEnabled = p[Keys.MASTER] ?: true,
            autoTweakEnabled = p[Keys.AUTO_TWEAK] ?: true,
            autoStartOnConnect = p[Keys.AUTO_START] ?: true,
            customSettings = custom,
            engineMode = p[Keys.ENGINE_MODE]?.let { n -> EngineMode.entries.firstOrNull { it.name == n } } ?: EngineMode.MODERN,
        )
    }

    companion object {
        fun encodeBands(bands: List<Float>): String = bands.joinToString(",")

        fun decodeBands(raw: String): List<Float>? {
            val values = raw.split(',').map { it.trim().toFloatOrNull() ?: return null }
            return values.takeIf { it.size == EqSettings.BAND_COUNT }
        }
    }
}
