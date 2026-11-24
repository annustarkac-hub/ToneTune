package com.tonotune.app.audio

/** Preset categories, in the order shown in the filter chips. */
enum class PresetCategory(val label: String) {
    SIGNATURE("Signature"),
    REGIONAL("Regional"),
    DJ_BASS("DJ & Bass"),
    DANCE("Dance"),
    VOCAL("Vocal"),
    MOOD("Mood"),
    UTILITY("Utility"),
}

/**
 * One tuning for the Zebronics Tono: five band gains in dB, plus BassBoost and
 * Virtualizer strengths (0..1000, the AudioEffect native scale).
 */
data class EqPreset(
    val id: String,
    val name: String,
    val category: PresetCategory,
    val band60HzDb: Float,
    val band230HzDb: Float,
    val band910HzDb: Float,
    val band3600HzDb: Float,
    val band14000HzDb: Float,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val featured: Boolean = false,
    val tagline: String = "",
) {
    val bandsDb: FloatArray
        get() = floatArrayOf(band60HzDb, band230HzDb, band910HzDb, band3600HzDb, band14000HzDb)

    fun toSettings(): EqSettings = EqSettings(
        bandsDb = bandsDb.toList(),
        bassBoost = bassBoostStrength,
        virtualizer = virtualizerStrength,
    )
}

/** The concrete values the engine applies (a preset, possibly hand-tweaked). */
data class EqSettings(
    val bandsDb: List<Float>,
    val bassBoost: Int,
    val virtualizer: Int,
) {
    init {
        require(bandsDb.size == BAND_COUNT) { "Expected $BAND_COUNT bands, got ${bandsDb.size}" }
    }

    fun sanitized(): EqSettings = EqSettings(
        bandsDb = bandsDb.map { it.coerceIn(MIN_DB, MAX_DB) },
        bassBoost = bassBoost.coerceIn(0, MAX_STRENGTH),
        virtualizer = virtualizer.coerceIn(0, MAX_STRENGTH),
    )

    companion object {
        const val BAND_COUNT = 5
        const val MIN_DB = -12f
        const val MAX_DB = 12f
        const val MAX_STRENGTH = 1000

        /** Tono target centre frequencies in Hz (matches the AOSP 5-band layout). */
        val BAND_FREQUENCIES_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        val BAND_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        val BAND_ROLES = listOf("Sub", "Body", "Mids", "Presence", "Air")

        val FLAT = EqSettings(List(BAND_COUNT) { 0f }, 0, 0)
    }
}
