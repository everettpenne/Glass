package com.everettpenne.updater

import org.json.JSONArray
import kotlin.math.max

/**
 * Where an app finds its updates: the GitHub [repo] ("owner/name") whose releases carry the APK, and the version it is running now.
 * [apiBase] is only overridden to point tests at a local server.
 */
data class UpdateConfig(val repo: String, val currentVersion: String, val apiBase: String = "https://api.github.com")

/** One GitHub release that carries an APK. */
data class Release(
    val tag: String, val version: String, val name: String, val notes: String,
    val apkUrl: String, val apkName: String, val sizeBytes: Long, val prerelease: Boolean,
)

/** Reads GitHub's release list and picks the newest APK that is newer than what is installed. */
internal object ReleaseParser {
    /** "v0.3.1", "0.3.1-beta" and "Thumper 0.3.1" all read as [0, 3, 1]; null when there is no number in it. */
    fun parseVersion(text: String): List<Int>? =
        Regex("""\d+(\.\d+)*""").find(text)?.value?.split('.')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until max(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /**
     * The release list (newest first, as GitHub returns it) is searched in full rather than trusting its order or `/releases/latest`, which
     * skips pre-releases. Drafts and releases without an https APK are ignored. Returns null when nothing is newer than [current].
     */
    fun newest(json: String, current: String): Release? {
        val installed = parseVersion(current) ?: return null
        val releases = JSONArray(json)
        var best: Release? = null
        var bestVersion: List<Int>? = null
        for (i in 0 until releases.length()) {
            val o = releases.optJSONObject(i) ?: continue
            if (o.optBoolean("draft")) continue
            val tag = o.optString("tag_name")
            val version = parseVersion(tag) ?: continue
            val assets = o.optJSONArray("assets") ?: continue
            var apk: org.json.JSONObject? = null
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true) && a.optString("browser_download_url").startsWith("https://")) { apk = a; break }
            }
            if (apk == null) continue
            if (bestVersion == null || compare(version, bestVersion) > 0) {
                bestVersion = version
                best = Release(
                    tag = tag, version = version.joinToString("."),
                    name = if (o.isNull("name")) tag else o.getString("name"),
                    notes = if (o.isNull("body")) "" else o.getString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"), apkName = apk.getString("name"),
                    sizeBytes = apk.optLong("size", -1L), prerelease = o.optBoolean("prerelease"),
                )
            }
        }
        return best?.takeIf { compare(bestVersion!!, installed) > 0 }
    }
}
