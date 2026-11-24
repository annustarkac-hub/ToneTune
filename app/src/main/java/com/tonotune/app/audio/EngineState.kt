package com.tonotune.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live state published by [com.tonotune.app.service.TonoAudioService] for the UI. */
data class EngineState(
    val running: Boolean = false,
    val effectsAttached: Boolean = false,
    val globalSessionOk: Boolean = false,
    val attachedSessions: Int = 0,
    val deviceBandCount: Int = 0,
    val bandRangeDb: Pair<Float, Float>? = null,
    val volumeRatio: Float = 0.5f,
    val tweakMode: TweakMode = TweakMode.OFF,
    val applied: EqSettings? = null,
    val lastError: String? = null,
)

object EngineStateHolder {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun update(block: (EngineState) -> EngineState) = _state.update(block)

    fun reset() { _state.value = EngineState() }
}
