package com.everettpenne.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** The file behind a "file:" address, or null for anything else (https, content:). Only files can have subtitle files beside them. */
internal fun fileOf(uri: String): File? = Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)

internal fun mediaItemOf(source: VideoSource): MediaItem {
    val builder = MediaItem.Builder().setUri(source.uri).setMediaId(source.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
    val file = fileOf(source.uri)
    if (file != null) {
        val subs = sidecarSubtitles(file).mapNotNull { f ->
            val mime = subtitleMime(f.extension) ?: return@mapNotNull null
            val language = sidecarLanguage(file, f)
            // "English · file", so a subtitle file beside the video is told apart from one inside it
            val name = language?.let { code -> java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.getDefault()).takeIf { it.isNotBlank() } }?.replaceFirstChar { it.uppercase() } ?: f.nameWithoutExtension
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f)).setMimeType(mime).setLanguage(language).setLabel("$name · file").build()
        }
        if (subs.isNotEmpty()) builder.setSubtitleConfigurations(subs)
    }
    return builder.build()
}

/**
 * A player for [items], starting at [startIndex] (and at [resumeAtMs] in that one). It takes audio focus, pauses when headphones are pulled out,
 * and starts playing at once. The caller releases it.
 */
internal fun buildPlayer(context: Context, items: List<VideoSource>, startIndex: Int, resumeAtMs: Long?): ExoPlayer =
    ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            setMediaItems(items.map(::mediaItemOf), startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), resumeAtMs ?: 0L)
            prepare(); playWhenReady = true
        }

/** One audio or subtitle track the user can pick. */
internal data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

internal fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> = buildList {
    var n = 0
    tracks.groups.filter { it.type == type }.forEach { g ->
        for (i in 0 until g.length) if (g.isTrackSupported(i)) {
            val f = g.getTrackFormat(i)
            add(TrackOption(g, i, trackLabel(f.label, f.language, f.channelCount, n, type == C.TRACK_TYPE_AUDIO), g.isTrackSelected(i)))
            n++
        }
    }
}

internal fun Player.selectTrack(option: TrackOption) {
    val type = option.group.type
    trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false)
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index)).build()
}

internal fun Player.disableTracks(type: Int) {
    trackSelectionParameters = trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, true).build()
}

/**
 * Adds a subtitle file the user picked to the playing item and turns it on. The item is replaced in place, so the position and the play state are
 * put back afterwards (the swap re-prepares it).
 */
internal fun Player.addSubtitle(uri: Uri, displayName: String) {
    val item = currentMediaItem ?: return
    val mime = subtitleMime(displayName.substringAfterLast('.', "")) ?: return
    val at = currentPosition; val wasPlaying = playWhenReady; val index = currentMediaItemIndex
    val config = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLabel(displayName.substringBeforeLast('.')).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
    val updated = item.buildUpon().setSubtitleConfigurations(item.localConfiguration?.subtitleConfigurations.orEmpty() + config).build()
    trackSelectionParameters = trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    replaceMediaItem(index, updated)
    seekTo(index, at); playWhenReady = wasPlaying
}

internal val Player.textDisabled: Boolean get() = trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

/** "1280×720 · H.264 · 2.4 Mbps" for the video now playing, as far as the file says. */
internal fun videoInfo(player: ExoPlayer): String? {
    val f = player.videoFormat ?: return null
    val codec = when {
        f.sampleMimeType == null -> null
        f.sampleMimeType!!.contains("avc") -> "H.264"
        f.sampleMimeType!!.contains("hevc") -> "H.265"
        f.sampleMimeType!!.contains("av01") -> "AV1"
        f.sampleMimeType!!.contains("vp9") -> "VP9"
        f.sampleMimeType!!.contains("vp8") -> "VP8"
        else -> f.sampleMimeType!!.substringAfter('/')
    }
    val rate = (if (f.averageBitrate > 0) f.averageBitrate else f.bitrate).takeIf { it > 0 }?.let { "%.1f Mbps".format(java.util.Locale.ROOT, it / 1_000_000f) }
    val size = if (f.width > 0 && f.height > 0) "${f.width}×${f.height}" else null
    return listOfNotNull(size, codec, rate).joinToString(" · ").ifEmpty { null }
}
