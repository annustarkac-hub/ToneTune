package com.tonotune.app

import com.tonotune.app.audio.EqSettings
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.audio.TonoPresetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetLibraryTest {

    private val presets = TonoPresetLibrary.presets

    @Test fun has27Presets() = assertEquals(27, presets.size)

    @Test fun idsAndNamesUnique() {
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        assertEquals(presets.size, presets.map { it.name }.toSet().size)
    }

    @Test fun requiredPresetsExistWithExactValues() {
        fun check(name: String, cat: PresetCategory, bands: List<Int>, bass: Int, virt: Int) {
            val p = presets.firstOrNull { it.name == name }
            assertNotNull("Missing preset $name", p)
            p!!
            assertEquals(cat, p.category)
            assertEquals(bands.map(Int::toFloat), p.bandsDb.toList())
            assertEquals(bass, p.bassBoostStrength)
            assertEquals(virt, p.virtualizerStrength)
        }
        check("Tono Pro Signature", PresetCategory.SIGNATURE, listOf(3, -2, 1, 4, 3), 450, 300)
        check("Garhwali Song", PresetCategory.REGIONAL, listOf(4, -2, 2, 5, 4), 600, 450)
        check("DJ Songs", PresetCategory.DJ_BASS, listOf(6, -3, -1, 3, 5), 900, 650)
        check("Dance Songs", PresetCategory.DANCE, listOf(5, -2, 1, 4, 5), 800, 600)
        check("Lo-Fi & Chill Beats", PresetCategory.MOOD, listOf(3, 1, -1, -2, -3), 400, 500)
        check("Podcast & News Voice", PresetCategory.VOCAL, listOf(-4, -3, 4, 5, 1), 0, 0)
        check("Treble Sparkle Boost", PresetCategory.UTILITY, listOf(0, -1, 1, 4, 6), 200, 300)
        check("Flat Reference", PresetCategory.SIGNATURE, listOf(0, 0, 0, 0, 0), 0, 0)
    }

    @Test fun categoryCountsMatchSpec() {
        val counts = presets.groupingBy { it.category }.eachCount()
        assertEquals(3, counts[PresetCategory.SIGNATURE])
        assertEquals(5, counts[PresetCategory.REGIONAL])
        assertEquals(4, counts[PresetCategory.DJ_BASS])
        assertEquals(4, counts[PresetCategory.DANCE])
        assertEquals(4, counts[PresetCategory.VOCAL])
        assertEquals(1, counts[PresetCategory.MOOD])
        assertEquals(6, counts[PresetCategory.UTILITY])
    }

    @Test fun allValuesWithinLimits() = presets.forEach { p ->
        p.bandsDb.forEach { assertTrue(p.name, it in EqSettings.MIN_DB..EqSettings.MAX_DB) }
        assertTrue(p.bassBoostStrength in 0..1000)
        assertTrue(p.virtualizerStrength in 0..1000)
    }

    @Test fun defaultIsSignatureAndUnknownFallsBack() {
        assertEquals("Tono Pro Signature", TonoPresetLibrary.default.name)
        assertEquals(TonoPresetLibrary.default, TonoPresetLibrary.byIdOrDefault("nope"))
        assertEquals(TonoPresetLibrary.default, TonoPresetLibrary.byIdOrDefault(null))
    }

    @Test fun featuredPresets() = assertEquals(
        setOf("Tono Pro Signature", "Garhwali Song", "DJ Songs", "Dance Songs"),
        presets.filter { it.featured }.map { it.name }.toSet(),
    )
}
