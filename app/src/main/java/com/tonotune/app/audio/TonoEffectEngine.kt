@file:Suppress("DEPRECATION") // Virtualizer is deprecated on API 36 but still the only system-wide spatial widener.

package com.tonotune.app.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log

/**
 * One effect chain bound to a single audio session.
 *
 * MODERN: DynamicsProcessing pre-EQ (14 bands, Bass Boost folded into the low bands) + limiter.
 *         This is the effect OEM ROMs keep working on the music path; the legacy Equalizer on
 *         HyperOS/MIUI is often accepted but silently bypassed.
 * CLASSIC: legacy Equalizer + BassBoost.
 * The Virtualizer is added in both modes when the device supports it.
 * If MODERN cannot be created on a session, the chain falls back to CLASSIC automatically.
 */
class EffectStack(
    val sessionId: Int,
    private val mode: EngineMode,
    private val priority: Int = Int.MAX_VALUE,
) {
    private var dynamics: AudioEffect? = null // DynamicsProcessing (typed as AudioEffect for API 26)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    @Volatile var lostControl = false
        private set

    val isAlive: Boolean get() = dynamics != null || equalizer != null || bassBoost != null || virtualizer != null

    val kind: String
        get() = when {
            dynamics != null -> "Modern"
            equalizer != null -> "Classic"
            virtualizer != null -> "Virtualizer only"
            else -> "None"
        }

    private val effects get() = listOfNotNull(dynamics, equalizer, bassBoost, virtualizer)

    /** @return null on success, or the reason the chain could not be created. */
    fun open(): String? {
        val errors = mutableListOf<String>()
        if (mode == EngineMode.MODERN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics = create("DynamicsProcessing", errors) { Modern.create(priority, sessionId) }
        }
        if (dynamics == null) {
            equalizer = create("Equalizer", errors) { Equalizer(priority, sessionId) }
            bassBoost = create("BassBoost", errors) { BassBoost(priority, sessionId) }
        }
        virtualizer = create("Virtualizer", errors) { Virtualizer(priority, sessionId) }
        effects.forEach { effect ->
            runCatching { effect.setControlStatusListener { _, granted -> lostControl = !granted } }
        }
        return if (dynamics != null || equalizer != null) null
        else errors.joinToString("; ").ifEmpty { "No equalizer available" }
    }

    private inline fun <T : AudioEffect> create(name: String, errors: MutableList<String>, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$name unavailable on session $sessionId", t)
            errors += "$name: ${t.javaClass.simpleName}"
            null
        }

    /** True if another app has taken over our main EQ effect. */
    fun needsRelock(): Boolean {
        if (lostControl) return true
        val main = dynamics ?: equalizer ?: return false
        return runCatching { !main.hasControl() }.getOrDefault(true)
    }

    fun apply(settings: EqSettings, enabled: Boolean) {
        val s = settings.sanitized()
        dynamics?.let { dp ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Modern.apply(dp, s)
                dp.enabled = enabled
            }.onFailure { Log.w(TAG, "DP apply failed on $sessionId", it) }
        }
        equalizer?.let { eq ->
            runCatching {
                val range = eq.bandLevelRange
                for (band in 0 until eq.numberOfBands) {
                    val centreHz = eq.getCenterFreq(band.toShort()) / 1000f
                    val db = EqMath.gainAt(centreHz, s.bandsDb)
                    eq.setBandLevel(band.toShort(), EqMath.dbToClampedMillibels(db, range[0], range[1]))
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
        effects.forEach { e ->
            runCatching { e.enabled = false }
            runCatching { e.release() }
        }
        dynamics = null; equalizer = null; bassBoost = null; virtualizer = null
        lostControl = false
    }

    /** API 28+ only code, isolated so class verification is safe on API 26/27. */
    private object Modern {
        fun create(priority: Int, session: Int): AudioEffect {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) error("API < 28")
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, // channels
                true, DpCurve.BAND_COUNT, // pre-EQ
                false, 0, // multi-band compressor
                false, 0, // post-EQ
                true, // limiter
            ).setPreferredFrameDuration(10f).build()
            return DynamicsProcessing(priority, session, config)
        }

        fun apply(effect: AudioEffect, s: EqSettings) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            val dp = effect as DynamicsProcessing
            val curve = DpCurve.bands(s)
            val eq = DynamicsProcessing.Eq(true, true, curve.size)
            curve.forEachIndexed { i, b -> eq.setBand(i, DynamicsProcessing.EqBand(true, b.cutoffHz, b.gainDb)) }
            dp.setPreEqAllChannelsTo(eq)
            dp.setInputGainAllChannelsTo(DpCurve.headroomDb(curve))
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(true, true, 0, 1f, 80f, 10f, DpCurve.LIMITER_THRESHOLD_DB, 0f),
            )
        }
    }

    companion object { private const val TAG = "TonoEffect" }
}

/**
 * Owns the global (session 0) chain plus one chain per media session (announced by broadcast
 * or found by the optional dumpsys scanner), and re-locks chains that lose control.
 */
class TonoEffectEngine(private var mode: EngineMode = EngineMode.MODERN) {
    private val stacks = linkedMapOf<Int, EffectStack>()
    private val labels = mutableMapOf<Int, String>()
    private val relockCount = mutableMapOf<Int, Int>()
    private var lastSettings: EqSettings = EqSettings.FLAT
    private var lastEnabled = false

    @Volatile var lastError: String? = null
        private set

    val sessionCount: Int @Synchronized get() = stacks.count { it.key != GLOBAL_SESSION && it.value.isAlive }
    val anyAttached: Boolean @Synchronized get() = stacks.values.any { it.isAlive }

    @Synchronized
    fun sessions(): List<SessionInfo> = stacks.map { (id, st) ->
        SessionInfo(id, st.kind, if (id == GLOBAL_SESSION) "Global output mix" else labels[id])
    }

    @Synchronized
    fun start() = attach(GLOBAL_SESSION)

    @Synchronized
    fun setMode(newMode: EngineMode) {
        if (newMode == mode) return
        mode = newMode
        val ids = stacks.keys.toList()
        stacks.values.forEach { it.release() }
        stacks.clear()
        ids.forEach { attach(it) }
        if (GLOBAL_SESSION !in stacks) attach(GLOBAL_SESSION)
    }

    @Synchronized
    fun attach(sessionId: Int, label: String? = null) {
        if (sessionId < 0) return
        if (label != null) labels[sessionId] = label
        stacks[sessionId]?.let { if (it.isAlive) return }
        val stack = EffectStack(sessionId, mode)
        val error = stack.open()
        if (error != null) {
            lastError = if (sessionId == GLOBAL_SESSION) "Global output: $error" else error
            stack.release()
            return
        }
        stacks[sessionId] = stack
        applyAll()
    }

    /**
     * App sessions get the EQ directly. Once any app session is linked, the global chain is
     * bypassed so ROMs that DO process session 0 don't apply the curve twice (double bass).
     */
    private fun applyAll() {
        val appLinked = stacks.any { it.key != GLOBAL_SESSION && it.value.isAlive }
        stacks.forEach { (id, st) ->
            st.apply(lastSettings, lastEnabled && !(id == GLOBAL_SESSION && appLinked))
        }
    }

    @Synchronized
    fun detach(sessionId: Int) {
        if (sessionId == GLOBAL_SESSION) return
        stacks.remove(sessionId)?.release()
        labels.remove(sessionId)
        relockCount.remove(sessionId)
        applyAll()
    }

    @Synchronized
    fun apply(settings: EqSettings, enabled: Boolean) {
        lastSettings = settings
        lastEnabled = enabled
        applyAll()
    }

    /**
     * Re-creates chains whose control was stolen (HyperOS re-lock). A session that keeps losing
     * control is retried at most [MAX_RELOCKS] times so we never fight another app in a loop.
     * @return number of chains re-created.
     */
    @Synchronized
    fun relockIfNeeded(): Int {
        var count = 0
        val ids = stacks.filterValues { it.needsRelock() }.keys.toList()
        for (id in ids) {
            val n = (relockCount[id] ?: 0) + 1
            relockCount[id] = n
            if (n > MAX_RELOCKS) continue
            stacks.remove(id)?.release()
            attach(id)
            count++
        }
        if (!stacks.containsKey(GLOBAL_SESSION)) { attach(GLOBAL_SESSION); count++ }
        return count
    }

    @Synchronized
    fun reassert() = applyAll()

    @Synchronized
    fun release() {
        stacks.values.forEach { it.release() }
        stacks.clear()
        labels.clear()
        relockCount.clear()
    }

    companion object {
        const val GLOBAL_SESSION = 0
        private const val MAX_RELOCKS = 5
    }
}
