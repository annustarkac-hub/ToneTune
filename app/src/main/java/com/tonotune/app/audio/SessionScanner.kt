package com.tonotune.app.audio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Optional "catch every app" mode. Most apps (YouTube, Instagram, many players) never announce
 * their audio session, so their audio may bypass a global effect on some ROMs. When the user has
 * granted android.permission.DUMP once via ADB, we read the playback list from `dumpsys audio`
 * (it includes session ids) and attach the Tono chain to every playing session.
 */
object SessionScanner {
    const val DUMP_PERMISSION = "android.permission.DUMP"

    data class Playback(val sessionId: Int, val uid: Int, val state: String, val usage: String?)

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} $DUMP_PERMISSION"

    private val SESSION = Regex("""\bsession(?:Id)?:\s*(-?\d+)""")
    private val UID = Regex("""u/pid:\s*(-?\d+)/""")
    private val STATE = Regex("""\bstate:\s*(\w+)""")
    private val USAGE = Regex("""usage=(\w+)""")
    private val MEDIA_USAGES = setOf("USAGE_MEDIA", "USAGE_GAME", "USAGE_UNKNOWN", "MEDIA", "GAME", "UNKNOWN")

    /** Parses player lines from `dumpsys audio` (formats from Android 8 to 16). */
    fun parse(dump: String, ownUid: Int = -2): List<Playback> = dump.lineSequence()
        .filter { (it.contains("AudioPlaybackConfiguration") || it.trimStart().startsWith("ID:")) && it.contains("u/pid") }
        .mapNotNull { line ->
            val session = SESSION.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val uid = UID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val state = STATE.find(line)?.groupValues?.get(1)?.lowercase() ?: "unknown"
            val usage = USAGE.find(line)?.groupValues?.get(1)
            Playback(session, uid, state, usage)
        }
        .filter { it.sessionId > 0 && it.uid != ownUid && it.state != "released" }
        .filter { it.usage == null || it.usage in MEDIA_USAGES }
        .distinctBy { it.sessionId }
        .toList()

    /** @return active media sessions, or null if the dump could not be read. */
    suspend fun scan(): List<Playback>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(3_000) {
            runCatching {
                val proc = ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start()
                val text = proc.inputStream.bufferedReader().use { it.readText() }
                proc.waitFor(1, TimeUnit.SECONDS)
                proc.destroy()
                if (text.contains("Permission Denial")) null else parse(text, Process.myUid())
            }.getOrNull()
        }
    }

    fun labelFor(context: Context, uid: Int): String? = runCatching {
        val pm = context.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return null
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()
}
