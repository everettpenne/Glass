package com.everettpenne.player

import android.content.Context

/** [ResumeBook] kept in the app's private preferences, so "resume where you left off" survives the app being closed. One per process. */
class ResumeStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("glass_player", Context.MODE_PRIVATE)
    private val book = ResumeBook.decode(prefs.getString("resume", "").orEmpty())

    @Synchronized fun resumeAt(key: String): Long? = book.resumeAt(key)

    @Synchronized fun save(key: String, positionMs: Long, durationMs: Long) { book.save(key, positionMs, durationMs); prefs.edit().putString("resume", book.encode()).apply() }

    @Synchronized fun finish(key: String) { book.finish(key); prefs.edit().putString("resume", book.encode()).apply() }

    /** Remembered preferences that belong to the player rather than to one video. */
    var subtitleSize: Float
        get() = prefs.getFloat("subtitleSize", 0.0533f)
        set(v) { prefs.edit().putFloat("subtitleSize", v).apply() }

    /** "Keep playing when I leave": whether a video's sound carries on in the background. Off until asked for. */
    var backgroundPlay: Boolean
        get() = prefs.getBoolean("backgroundPlay", false)
        set(v) { prefs.edit().putBoolean("backgroundPlay", v).apply() }

    companion object {
        @Volatile private var instance: ResumeStore? = null
        fun of(context: Context): ResumeStore = instance ?: synchronized(this) { instance ?: ResumeStore(context).also { instance = it } }
    }
}
