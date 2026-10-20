package com.everettpenne.media

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/** Plays a video (or a sound) file full screen: (address, title). [VideoViewerHost] provides it; the default does nothing. */
val LocalVideoViewer = compositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }

/** Draw once at the root: it provides [LocalVideoViewer] to the content and shows the player when something asks for it. */
@Composable
fun VideoViewerHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<Pair<String, String?>?>(null) }
    CompositionLocalProvider(LocalVideoViewer provides { uri, title -> request = uri to title }) {
        content()
        request?.let { (uri, title) -> VideoViewer(uri, title) { request = null } }
    }
}

/**
 * [FullscreenVideo] with a player of its own, for an app that just wants to show a file: an address (https, content: or file:) in, a full-screen
 * player out, released when it closes. It starts playing at once. Leaving the app pauses it, unless the video went into picture-in-picture: then the
 * host's shell draws the small window (from [VideoPip]) and the full-screen view comes back, still playing, when the window is expanded again.
 */
@Composable
fun VideoViewer(uri: String, title: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) {
        onDispose { if (VideoPip.player === player) VideoPip.player = null; player.release() }
    }
    // In picture-in-picture the activity is paused but not stopped; stopped means the person really left (or closed the small window).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!VideoPip.active) FullscreenVideo(player, title, onDismiss)
}
