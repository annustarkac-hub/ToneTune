@file:Suppress("DEPRECATION") // Virtualizer is deprecated on API 36 but still the only system-wide spatial widener.

package com.tonotune.app.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * One Equalizer + BassBoost + Virtualizer chain bound to a single audio session.
 * Every platform call is guarded: OEM effect stacks (HyperOS included) can throw
 * RuntimeException / UnsupportedOperationException at any point.
 */
class EffectStack(val sessionId: Int, private val priority: Int = Int.MAX_VALUE) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    var lostControl = false
        private set

    val isAlive: Boolean get() = equalizer != null || bassBoost != null || virtualizer != null

    val bandCount: Int get() = runCatching { equalizer?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)

    val bandRangeDb: Pair<Float, Float>?
        get() = runCatching {
            equalizer?.bandLevelRange?.let { it[0] / 100f to it[1] / 100f }
        }.getOrNull()

    /** @return null on success, or the reason the chain could not be created. */
    fun open(): String? {
        val errors = mutableListOf<String>()
        equalizer = create("Equalizer", errors) { Equalizer(priority, sessionId) }
        bassBoost = create("BassBoost", errors) { BassBoost(priority, sessionId) }
        virtualizer = create("Virtualizer", errors) { Virtualizer(priority, sessionId) }
        listOfNotNull<AudioEffect>(equalizer, bassBoost, virtualizer).forEach { effect ->
            runCatching {
                effect.setControlStatusListener { _, controlGranted -> lostControl = !controlGranted }
            }
        }
        return if (isAlive) null else errors.joinToString("; ").ifEmpty { "No audio effects available" }
    }

    private inline fun <T : AudioEffect> create(name: String, errors: MutableList<String>, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$name unavailable on session $sessionId", t)
            errors += "$name: ${t.javaClass.simpleName}"
            null
        }

    /** True if another app has taken over one of our effects (HyperOS system EQ, etc.). */
    fun needsRelock(): Boolean {
        if (lostControl) return true
        return listOfNotNull<AudioEffect>(equalizer, bassBoost, virtualizer).any { e ->
            runCatching { !e.hasControl() }.getOrDefault(true)
        }
    }

    fun apply(settings: EqSettings, enabled: Boolean) {
        val s = settings.sanitized()
        equalizer?.let { eq ->
            runCatching {
                val range = eq.bandLevelRange
                val minMb = range[0]
                val maxMb = range[1]
                for (band in 0 until eq.numberOfBands) {
                    val centreHz = eq.getCenterFreq(band.toShort()) / 1000f
                    val db = EqMath.gainAt(centreHz, s.bandsDb)
                    eq.setBandLevel(band.toShort(), EqMath.dbToClampedMillibels(db, minMb, maxMb))
                }
                eq.enabled = enabled
            }.onFailure { Log.w(TAG, "EQ apply failed on $sessionId", it) }
        }
        bassBoost?.let { bb ->
            runCatching {
                if (bb.strengthSupported) bb.setStrength(EqMath.clampStrength(s.bassBoost))
                bb.enabled = enabled && s.bassBoost > 0
            }.onFailure { Log.w(TAG, "BassBoost apply failed on $sessionId", it) }
        }
        virtualizer?.let { v ->
            runCatching {
                if (v.strengthSupported) v.setStrength(EqMath.clampStrength(s.virtualizer))
                v.enabled = enabled && s.virtualizer > 0
            }.onFailure { Log.w(TAG, "Virtualizer apply failed on $sessionId", it) }
        }
    }

    fun release() {
        listOfNotNull<AudioEffect>(equalizer, bassBoost, virtualizer).forEach { e ->
            runCatching { e.enabled = false }
            runCatching { e.release() }
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        lostControl = false
    }

    companion object { private const val TAG = "TonoEffect" }
}

/**
 * Owns the global (session 0) chain plus one chain per media-app session announced via
 * ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, and re-locks chains that lose control.
 */
class TonoEffectEngine {
    private val stacks = linkedMapOf<Int, EffectStack>()
    private var lastSettings: EqSettings = EqSettings.FLAT
    private var lastEnabled = false

    var lastError: String? = null
        private set

    val globalOk: Boolean get() = stacks[GLOBAL_SESSION]?.isAlive == true
    val sessionCount: Int get() = stacks.count { it.key != GLOBAL_SESSION && it.value.isAlive }
    val primary: EffectStack? get() = stacks[GLOBAL_SESSION] ?: stacks.values.firstOrNull()
    val anyAttached: Boolean get() = stacks.values.any { it.isAlive }

    @Synchronized
    fun start() {
        attach(GLOBAL_SESSION)
    }

    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId < 0) return
        stacks[sessionId]?.let { if (it.isAlive) { it.apply(lastSettings, lastEnabled); return } }
        val stack = EffectStack(sessionId)
        val error = stack.open()
        if (error != null) {
            lastError = if (sessionId == GLOBAL_SESSION) "Global output: $error" else error
            stack.release()
            return
        }
        stacks[sessionId] = stack
        stack.apply(lastSettings, lastEnabled)
    }

    @Synchronized
    fun detach(sessionId: Int) {
        if (sessionId == GLOBAL_SESSION) return
        stacks.remove(sessionId)?.release()
    }

    @Synchronized
    fun apply(settings: EqSettings, enabled: Boolean) {
        lastSettings = settings
        lastEnabled = enabled
        stacks.values.forEach { it.apply(settings, enabled) }
    }

    /** Re-creates any chain whose control was stolen. @return number of chains re-locked. */
    @Synchronized
    fun relockIfNeeded(): Int {
        var count = 0
        val ids = stacks.filterValues { it.needsRelock() || !it.isAlive }.keys.toList()
        for (id in ids) {
            stacks.remove(id)?.release()
            attach(id)
            count++
        }
        if (!stacks.containsKey(GLOBAL_SESSION)) { attach(GLOBAL_SESSION); count++ }
        return count
    }

    @Synchronized
    fun reassert() = stacks.values.forEach { it.apply(lastSettings, lastEnabled) }

    @Synchronized
    fun release() {
        stacks.values.forEach { it.release() }
        stacks.clear()
    }

    companion object { const val GLOBAL_SESSION = 0 }
}
