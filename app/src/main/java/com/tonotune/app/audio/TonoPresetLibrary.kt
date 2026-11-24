package com.tonotune.app.audio

import com.tonotune.app.audio.PresetCategory.DANCE
import com.tonotune.app.audio.PresetCategory.DJ_BASS
import com.tonotune.app.audio.PresetCategory.MOOD
import com.tonotune.app.audio.PresetCategory.REGIONAL
import com.tonotune.app.audio.PresetCategory.SIGNATURE
import com.tonotune.app.audio.PresetCategory.UTILITY
import com.tonotune.app.audio.PresetCategory.VOCAL

/**
 * 27 presets calibrated for the Zebronics Tono 50W tower:
 * 10.16 cm subwoofer driver + dual 7.62 cm full-range drivers, Bluetooth 5.1.
 *
 * General rules applied: the 230 Hz band is cut on almost every preset to tame the
 * cabinet's boxy resonance, and 60 Hz boosts are paired with BassBoost so the
 * 10.16 cm woofer delivers punch without bloating the mids.
 */
object TonoPresetLibrary {

    const val DEFAULT_PRESET_ID = "tono_pro_signature"

    private fun p(
        id: String, name: String, cat: PresetCategory,
        b60: Int, b230: Int, b910: Int, b3600: Int, b14k: Int,
        bass: Int, virt: Int,
        featured: Boolean = false, tagline: String = "",
    ) = EqPreset(
        id, name, cat,
        b60.toFloat(), b230.toFloat(), b910.toFloat(), b3600.toFloat(), b14k.toFloat(),
        bass, virt, featured, tagline,
    )

    val presets: List<EqPreset> = listOf(
        p("tono_pro_signature", "Tono Pro Signature", SIGNATURE, 3, -2, 1, 4, 3, 450, 300, true, "Balanced house sound for the Tono"),
        p("garhwali_song", "Garhwali Song", REGIONAL, 4, -2, 2, 5, 4, 600, 450, true, "Dhol punch with clear pahadi vocals"),
        p("dj_songs", "DJ Songs", DJ_BASS, 6, -3, -1, 3, 5, 900, 650, true, "Max sub-woofer slam for DJ mixes"),
        p("dance_songs", "Dance Songs", DANCE, 5, -2, 1, 4, 5, 800, 600, true, "Bouncy kick and bright hats"),
        p("deep_sub_woofer", "Deep Sub-Woofer", DJ_BASS, 6, -1, 0, 2, 2, 850, 200, tagline = "Lowest octave, kept tight"),
        p("anti_muddy_clean", "Anti-Muddy Clean", SIGNATURE, 1, -4, 1, 3, 3, 250, 200, tagline = "Scoops cabinet boom"),
        p("bollywood_hindi_hits", "Bollywood & Hindi Hits", REGIONAL, 3, -1, 2, 4, 3, 500, 350, tagline = "Vocals forward, warm low end"),
        p("punjabi_bhangra", "Punjabi & Bhangra", DANCE, 5, -1, 0, 3, 4, 800, 350, tagline = "Dhol and tumbi energy"),
        p("edm_club_party", "EDM & Club Party", DJ_BASS, 6, -2, -1, 3, 5, 900, 600, tagline = "Big room drops"),
        p("party_floor_boost", "Party Floor Boost", DANCE, 5, -2, 0, 4, 4, 850, 550, tagline = "Fills the room"),
        p("lofi_chill", "Lo-Fi & Chill Beats", MOOD, 3, 1, -1, -2, -3, 400, 500, tagline = "Warm, soft and relaxed"),
        p("acoustic_unplugged", "Acoustic & Unplugged", VOCAL, 1, -2, 2, 4, 4, 200, 350, tagline = "Guitar detail and breath"),
        p("ghazal_classics", "Ghazal & Classics", VOCAL, 1, -2, 3, 4, 2, 200, 250, tagline = "Intimate, natural voice"),
        p("podcast_news", "Podcast & News Voice", VOCAL, -4, -3, 4, 5, 1, 0, 0, tagline = "Speech clarity, no boom"),
        p("night_mode", "Night Mode (Low Vol)", UTILITY, 4, -2, 1, 3, 3, 550, 200, tagline = "Full sound at low volume"),
        p("cinema_ott", "Cinema & OTT Action", UTILITY, 5, -2, -1, 4, 4, 750, 800, tagline = "Wide stage, deep explosions"),
        p("cricket_sports", "Cricket & Sports Live", UTILITY, -1, -2, 3, 4, 3, 150, 450, tagline = "Commentary up front, crowd wide"),
        p("karaoke_mic", "Karaoke Mic Balance", UTILITY, 1, -3, -2, 3, 3, 300, 200, tagline = "Leaves space for your voice"),
        p("rock_metal", "Rock & Metal", DANCE, 4, -1, -2, 3, 4, 550, 300, tagline = "Guitars with bite"),
        p("hiphop_rap", "Hip-Hop & Rap", DJ_BASS, 6, -2, 1, 4, 2, 850, 250, tagline = "808s and crisp bars"),
        p("classical_instrumental", "Classical & Instrumental", REGIONAL, 2, -1, 1, 3, 5, 250, 500, tagline = "Strings and open air"),
        p("devotional_bhajan", "Devotional & Bhajan", REGIONAL, 2, -2, 2, 4, 3, 300, 400, tagline = "Bells, harmonium and voice"),
        p("kumaoni_pahadi_folk", "Kumaoni & Pahadi Folk", REGIONAL, 4, -2, 2, 5, 3, 550, 400, tagline = "Hurka and folk vocals"),
        p("kids_rhymes", "Kids Story & Rhymes", VOCAL, 0, -2, 3, 4, 2, 150, 250, tagline = "Clear, gentle narration"),
        p("bass_safe_limiter", "Bass Safe Limiter", UTILITY, 2, -3, 1, 3, 2, 350, 150, tagline = "Protects the woofer when loud"),
        p("treble_sparkle", "Treble Sparkle Boost", UTILITY, 0, -1, 1, 4, 6, 200, 300, tagline = "Extra shimmer and detail"),
        p("flat_reference", "Flat Reference", SIGNATURE, 0, 0, 0, 0, 0, 0, 0, tagline = "No processing"),
    )

    private val byId = presets.associateBy { it.id }

    val default: EqPreset get() = byId.getValue(DEFAULT_PRESET_ID)

    fun find(id: String?): EqPreset? = id?.let(byId::get)

    fun byIdOrDefault(id: String?): EqPreset = find(id) ?: default

    fun inCategory(category: PresetCategory?): List<EqPreset> =
        if (category == null) presets else presets.filter { it.category == category }
}
