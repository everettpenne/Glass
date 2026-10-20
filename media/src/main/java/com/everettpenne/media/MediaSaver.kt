package com.everettpenne.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Saves pictures into the phone's photo library (Pictures/<album>), where Photos and every gallery app find them. It goes through MediaStore, which
 * on Android 10 and later needs no storage permission for files the app adds itself. The original bytes are copied as they are, so a GIF stays animated
 * and a JPEG keeps its quality and metadata; nothing is re-encoded.
 */
object MediaSaver {
    /** Bigger than any picture worth saving: a server that keeps sending past this is not sending a picture. */
    private const val MAX_BYTES = 80L * 1024 * 1024

    /** The outcome of a save: where it went, or why it did not. */
    sealed interface Result {
        data class Saved(val uri: Uri, val album: String) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Copies the picture at [url] (https, http, content: or file:) into Pictures/[album]. Runs on the IO dispatcher; safe to call from a coroutine
     * on the main thread. A half-written file is removed if the copy fails, so a failed save never leaves a broken picture in the library.
     */
    suspend fun saveImage(context: Context, url: String, album: String): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var target: Uri? = null
        try {
            openSource(context, url).use { source ->
                val mime = source.mime?.takeIf { it.startsWith("image/") } ?: mimeFromName(url) ?: "image/jpeg"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(url, mime))
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${album.ifBlank { "Saved" }}")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: return@withContext Result.Failed("The photo library would not take it.")
                resolver.openOutputStream(target!!, "w")!!.use { out ->
                    val buf = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = source.stream.read(buf); if (n < 0) break
                        total += n; if (total > MAX_BYTES) throw IllegalStateException("too large")
                        out.write(buf, 0, n)
                    }
                    if (total == 0L) throw IllegalStateException("empty")
                }
                resolver.update(target!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                Result.Saved(target!!, album)
            }
        } catch (e: Exception) {
            target?.let { runCatching { resolver.delete(it, null, null) } }
            Result.Failed(if (e is java.io.IOException) "Couldn't download the picture." else "Couldn't save the picture.")
        }
    }

    private class Source(val stream: InputStream, val mime: String?, private val close: () -> Unit) : AutoCloseable {
        override fun close() { runCatching { stream.close() }; close() }
    }

    private fun openSource(context: Context, url: String): Source {
        val uri = Uri.parse(url)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                }
                if (conn.responseCode !in 200..299) { conn.disconnect(); throw java.io.IOException("HTTP ${conn.responseCode}") }
                Source(conn.inputStream, conn.contentType?.substringBefore(';')?.trim()) { conn.disconnect() }
            }
            else -> Source(context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("unreadable"), context.contentResolver.getType(uri)) {}
        }
    }

    /** "image/png" style type from a file name or address, or null when it has no picture extension. */
    internal fun mimeFromName(url: String): String? {
        val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
            "heic" -> "image/heic"; "heif" -> "image/heif"; "avif" -> "image/avif"; "bmp" -> "image/bmp"
            else -> null
        }
    }

    /** A file name for the picture: the address's own name when it has a sensible one, with an extension that matches what was received. */
    internal fun fileName(url: String, mime: String): String {
        val ext = when (mime) {
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"
            "image/heic" -> "heic"; "image/heif" -> "heif"; "image/avif" -> "avif"; "image/bmp" -> "bmp"; else -> "jpg"
        }
        val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val stem = last.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(60)
        return (stem.ifBlank { "image-${System.currentTimeMillis()}" }) + "." + ext
    }
}
