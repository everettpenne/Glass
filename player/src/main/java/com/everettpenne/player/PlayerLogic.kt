package com.everettpenne.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.everettpenne.media.playerClock
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The player's decisions that need no screen: what a swipe means, which speeds exist, which subtitle files sit beside a video, what to call a
 * track, where to resume. All pure, so they are unit-tested on the JVM; the UI only draws what these say.
 */

/** What happens when the last item ends: nothing, go round the whole list again, or play the same item again. */
enum class RepeatMode {
    OFF, ALL, ONE;
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
    fun toPlayer(): Int = when (this) { OFF -> Player.REPEAT_MODE_OFF; ALL -> Player.REPEAT_MODE_ALL; ONE -> Player.REPEAT_MODE_ONE }
}

/** How the picture fills the screen: whole picture with bars, cropped to fill, or stretched to fill. */
enum class FitMode {
    FIT, ZOOM, STRETCH;
    fun next(): FitMode = entries[(ordinal + 1) % entries.size]
    val label: String get() = when (this) { FIT -> "Fit"; ZOOM -> "Fill"; STRETCH -> "Stretch" }
}

/** The speeds on offer, slowest first. 1× is the normal one. */
val PlaybackSpeeds: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

/** The speed while a finger is held on the picture. */
const val HoldSpeed = 2f

fun speedLabel(speed: Float): String {
    val whole = speed == speed.toInt().toFloat()
    return (if (whole) speed.toInt().toString() else "%.2f".format(Locale.ROOT, speed).trimEnd('0').trimEnd('.')) + "×"
}

/** The speed one step slower or faster than [current] (direction −1 or +1), staying inside [PlaybackSpeeds]. A speed between two steps moves to the nearer one in that direction. */
fun stepSpeed(current: Float, direction: Int): Float {
    val i = PlaybackSpeeds.indexOfFirst { abs(it - current) < 0.001f }
    if (i >= 0) return PlaybackSpeeds[(i + direction).coerceIn(0, PlaybackSpeeds.lastIndex)]
    return if (direction > 0) PlaybackSpeeds.firstOrNull { it > current } ?: PlaybackSpeeds.last() else PlaybackSpeeds.lastOrNull { it < current } ?: PlaybackSpeeds.first()
}

/** One item to play. [uri] is an address (https, content: or file:); [title] is what the top bar shows. */
data class VideoSource(val uri: String, val title: String)

// ---- gestures ----

/**
 * The level (0..1) after dragging a finger [dyPx] down the screen from [start]. Dragging up raises it. 80% of the screen's height covers the whole
 * range, so a comfortable one-handed swipe goes from nothing to full.
 */
fun levelAfterDrag(start: Float, dyPx: Float, heightPx: Float): Float =
    if (heightPx <= 0f) start else (start - dyPx / (heightPx * 0.8f)).coerceIn(0f, 1f)

/** The screen never goes fully black from a swipe: that looks like a crash. */
const val MinBrightness = 0.02f

/** Where a horizontal drag of [dxPx] across a screen [widthPx] wide takes the playback from [startMs]. A full-width drag covers up to four minutes. */
fun seekTarget(startMs: Long, dxPx: Float, widthPx: Float, durationMs: Long): Long {
    if (widthPx <= 0f || durationMs <= 0) return startMs.coerceAtLeast(0)
    val span = durationMs.coerceAtMost(240_000L).toFloat()
    return (startMs + dxPx / widthPx * span).toLong().coerceIn(0L, durationMs)
}

/** "1:23 (+0:23)": where a seek drag will land and how far that is from where it began. */
fun seekLabel(startMs: Long, targetMs: Long): String {
    val delta = targetMs - startMs
    val sign = if (delta < 0) "−" else "+"
    return "${playerClock(targetMs)}  ($sign${playerClock(abs(delta))})"
}

/** The volume step (0..[max]) for a level of 0..1. */
fun volumeStep(level: Float, max: Int): Int = (level.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)

// ---- subtitles and tracks ----

val SubtitleExtensions = listOf("srt", "vtt", "ass", "ssa")

fun subtitleMime(extension: String): String? = when (extension.lowercase(Locale.ROOT)) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    else -> null
}

/** Subtitle files that sit beside [video] and belong to it: the same name ("film.srt") or the name plus a language ("film.en.srt"). */
fun sidecarSubtitles(video: File): List<File> {
    val dir = video.parentFile ?: return emptyList()
    val stem = video.nameWithoutExtension
    return dir.listFiles()?.filter { f ->
        f.isFile && f.extension.lowercase(Locale.ROOT) in SubtitleExtensions && (f.nameWithoutExtension == stem || f.nameWithoutExtension.startsWith("$stem."))
    }?.sortedBy { it.name } ?: emptyList()
}

/** The language code in "film.en.srt", or null when the file has none. */
fun sidecarLanguage(video: File, subtitle: File): String? {
    val rest = subtitle.nameWithoutExtension.removePrefix(video.nameWithoutExtension).removePrefix(".")
    return rest.takeIf { it.isNotBlank() && it.length in 2..8 && it.all { c -> c.isLetter() || c == '-' } }
}

/** A person-readable name for a track: its own label, else its language, else "Track n"; with the channel layout for sound ("5.1"). */
fun trackLabel(label: String?, language: String?, channelCount: Int, index: Int, isAudio: Boolean): String {
    val lang = language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
        val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }?.replaceFirstChar { c -> c.uppercase() } ?: code
    }
    val base = label?.takeIf { it.isNotBlank() } ?: lang ?: "Track ${index + 1}"
    val layout = if (isAudio) channelLayout(channelCount) else null
    return if (layout != null) "$base · $layout" else base
}

fun channelLayout(channels: Int): String? = when (channels) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }

// ---- resume ----

/** Where playback was left, when it was, and how long the item is. */
data class ResumeEntry(val positionMs: Long, val durationMs: Long, val savedAt: Long)

/**
 * "Pick up where you left off": remembers the last position of up to [max] items (the oldest are forgotten first). An item is worth resuming only
 * when it is long enough to matter, you were past its start, and you had not nearly finished it.
 */
class ResumeBook(private val entries: LinkedHashMap<String, ResumeEntry> = LinkedHashMap(), private val max: Int = 300, private val now: () -> Long = System::currentTimeMillis) {
    fun resumeAt(key: String): Long? = entries[key]?.takeIf { worthResuming(it.positionMs, it.durationMs) }?.positionMs

    fun save(key: String, positionMs: Long, durationMs: Long) {
        if (!worthResuming(positionMs, durationMs)) { entries.remove(key); return }
        entries.remove(key)
        entries[key] = ResumeEntry(positionMs, durationMs, now())
        while (entries.size > max) entries.remove(entries.entries.minByOrNull { it.value.savedAt }!!.key)
    }

    /** The item played to its end: forget it, so it starts from the beginning next time. */
    fun finish(key: String) { entries.remove(key) }

    val size: Int get() = entries.size

    fun encode(): String = entries.entries.joinToString("\n") { (k, v) -> listOf(java.net.URLEncoder.encode(k, "UTF-8"), v.positionMs, v.durationMs, v.savedAt).joinToString("\t") }

    companion object {
        fun worthResuming(positionMs: Long, durationMs: Long): Boolean = durationMs >= 30_000 && positionMs >= 5_000 && positionMs <= durationMs - 10_000

        fun decode(text: String, max: Int = 300, now: () -> Long = System::currentTimeMillis): ResumeBook {
            val map = LinkedHashMap<String, ResumeEntry>()
            text.lineSequence().forEach { line ->
                val p = line.split('\t')
                if (p.size == 4) runCatching { map[java.net.URLDecoder.decode(p[0], "UTF-8")] = ResumeEntry(p[1].toLong(), p[2].toLong(), p[3].toLong()) }
            }
            return ResumeBook(map, max, now)
        }
    }
}
