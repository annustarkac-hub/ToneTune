package com.tonotune.app.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** Which effect implementation drives the EQ. */
enum class EngineMode(val label: String, val description: String) {
    MODERN("Modern", "DynamicsProcessing · 14-band curve + limiter (recommended)"),
    CLASSIC("Classic", "Legacy Equalizer + BassBoost (for older ROMs)"),
}

/** In-process controls shared by the UI and the service (same process). */
object EngineControl {
    /** While true the engine passes audio through untouched (A/B "hear original"). */
    val bypass = MutableStateFlow(false)
}

/** What is attached to one audio session, for the diagnostics list. */
data class SessionInfo(val sessionId: Int, val kind: String, val label: String?)
