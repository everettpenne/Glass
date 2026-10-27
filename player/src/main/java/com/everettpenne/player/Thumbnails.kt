package com.everettpenne.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlin.math.roundToInt

/*
 * Seek thumbnails: the picture at a moment, shown above the scrubber while a finger is on it and while dragging across the screen to seek. Taken
 * with the platform's own frame retriever from the file itself, so they need a file or content address (a stream would have to be fetched to be
 * sampled, so nothing is shown for one). The retriever is not thread-safe and slow: one at a time, off the main thread, and only the nearest
 * second is asked for, so a drag that wanders over a few seconds costs a few frames and not one per pixel.
 */

/** Frames are taken per whole second: a finger moves faster than a frame is decoded, and a second is finer than a thumbnail can show. */
fun thumbnailBucket(positionMs: Long): Long = (positionMs.coerceAtLeast(0) / 1000L) * 1000L

/** The left edge of a bubble [bubbleWidth] wide centred under a scrubber thumb at [fraction] of [trackWidth], kept inside the track. */
fun bubbleOffset(fraction: Float, trackWidth: Float, bubbleWidth: Float): Float =
    (fraction.coerceIn(0f, 1f) * trackWidth - bubbleWidth / 2f).coerceIn(0f, (trackWidth - bubbleWidth).coerceAtLeast(0f))

/** The size to ask for: [maxWidth] wide for a picture at least as wide as tall, and the same on the long side otherwise, keeping the shape. */
fun thumbnailSize(videoWidth: Int, videoHeight: Int, maxWidth: Int): Pair<Int, Int> {
    if (videoWidth <= 0 || videoHeight <= 0) return maxWidth to (maxWidth * 9 / 16)
    val ratio = videoWidth.toFloat() / videoHeight
    return if (ratio >= 1f) maxWidth to (maxWidth / ratio).roundToInt().coerceAtLeast(1) else (maxWidth * ratio).roundToInt().coerceAtLeast(1) to maxWidth
}

/** Whether [uri] can be sampled for thumbnails: a file or a content address. */
fun canThumbnail(uri: String?): Boolean = uri != null && Uri.parse(uri).scheme.let { it == "file" || it == "content" }

/** Frames of one video. Make one per video and [close] it. */
class Thumbnails(private val context: Context, private val uri: String) {
    private val lock = Any()
    private var retriever: MediaMetadataRetriever? = null
    private var width = 0
    private var height = 0
    private val cache = LruCache<Long, Bitmap>(24)

    /** The frame nearest [positionMs], or null when it cannot be had. Blocking: call it off the main thread. */
    fun frameAt(positionMs: Long, maxWidth: Int = 320): Bitmap? = synchronized(lock) {
        val key = thumbnailBucket(positionMs)
        cache.get(key)?.let { return it }
        val r = retriever ?: runCatching {
            MediaMetadataRetriever().also { m ->
                m.setDataSource(context, Uri.parse(uri))
                width = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
        }.getOrNull()?.also { retriever = it } ?: return null
        val (w, h) = thumbnailSize(width, height, maxWidth)
        val frame = runCatching { r.getScaledFrameAtTime(key * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h) }.getOrNull()
        if (frame != null) cache.put(key, frame)
        frame
    }

    fun close() = synchronized(lock) { runCatching { retriever?.release() }; retriever = null; cache.evictAll() }
}
