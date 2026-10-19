package com.everettpenne.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.everettpenne.glass.GlassCircleButton
import com.everettpenne.glass.GlassSlider
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassBackdrop
import kotlinx.coroutines.delay

/** Width over height of what [player] is showing, or 16:9 before the first frame. */
fun videoAspectRatio(player: Player): Float = player.videoSize.ratio()

/**
 * Picture-in-picture for full-screen video, shared process-wide so the host activity and the composables agree without threading a player
 * reference through every layer. [player] is the [FullscreenVideo]'s player while it is up; [active] is true while the activity is in the
 * small window. The host calls [enterVideoPip] from its `onUserLeaveHint`.
 */
object VideoPip {
    @Volatile var player: Player? = null
    @Volatile var active: Boolean = false
}

/** Enters picture-in-picture for the currently playing full-screen video, if any. Returns true when it did. */
fun enterVideoPip(activity: Activity): Boolean {
    val p = VideoPip.player ?: return false
    if (!p.isPlaying) return false
    if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
    VideoPip.active = true
    return activity.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
}

private fun VideoSize.ratio(): Float = if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else 16f / 9f

/** Just the picture: a Media3 surface bound to [player] while [enabled]. Pass `enabled = false` while a [FullscreenVideo] has the player. */
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AndroidView(
        factory = { c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) } },
        modifier = modifier, update = { it.player = if (enabled) player else null }, onRelease = { it.player = null })
}

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

private fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * [player]'s video full screen on black, system bars hidden. Wide video turns the phone to landscape while it is up (and back after). Tap shows or
 * hides the controls (they hide themselves after a few seconds of playing); double-tap the left or right half to skip 10 seconds; Back or the
 * button in the corner leaves. The player is the caller's, so playback carries on across the switch with no re-buffering.
 */
@Composable
fun FullscreenVideo(player: Player, title: String? = null, onDismiss: () -> Unit) {
    val activity = LocalContext.current.activity()
    var ratio by remember { mutableFloatStateOf(videoAspectRatio(player)) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var controls by remember { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { ratio = videoSize.ratio() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        VideoPip.player = player
        onDispose { player.removeListener(listener); if (VideoPip.player === player) VideoPip.player = null }
    }
    // the clock only needs to move while the controls are showing; hidden, nothing is redrawn
    LaunchedEffect(player, controls) { if (controls) while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(250) } }
    LaunchedEffect(controls, playing, scrub) { if (controls && playing && scrub == null) { delay(3500); controls = false } }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = previous }
    }
    LaunchedEffect(ratio) { if (ratio > 1.05f) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {}
        }
        // The dialog inherits the page's glass backdrop, which would paint pale discs on the video; on black the controls are plain dark discs.
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
        Box(Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(player) {
                detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { at ->
                    val target = (player.currentPosition + if (at.x < size.width / 2f) -10_000L else 10_000L).coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                    player.seekTo(target); position = target; controls = true
                })
            }) {
            VideoSurface(player, Modifier.fillMaxSize().semantics { contentDescription = "Video" })
            if (controls) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().displayCutoutPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Control({ onDismiss() }, "Exit full screen") { Icon(Icons.Rounded.FullscreenExit, null, tint = Color.White) }
                    if (title != null) Text(title, style = IosType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Control({ if (player.isPlaying) player.pause() else player.play() }, if (playing) "Pause" else "Play", Modifier.align(Alignment.Center), size = 72.dp) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().displayCutoutPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val d = duration.coerceAtLeast(1)
                    GlassSlider(value = scrub ?: (position.toFloat() / d).coerceIn(0f, 1f), onValueChange = { scrub = it },
                        onValueChangeFinished = { scrub?.let { player.seekTo((it * d).toLong()) }; scrub = null }, description = "Seek")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(scrub?.let { (it * d).toLong() } ?: position), style = IosType.caption1, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text("-" + clock(duration - (scrub?.let { (it * d).toLong() } ?: position)), style = IosType.caption1, color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Control({ activity?.let { enterVideoPip(it) } }, "Picture in picture", size = 36.dp) {
                            Icon(Icons.Rounded.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Control({ muted = !muted; player.volume = if (muted) 0f else 1f }, if (muted) "Unmute" else "Mute", size = 36.dp) {
                            Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        }
    }
}

/** A round control on video: a dark disc under glass, readable on any frame. */
@Composable
private fun Control(onClick: () -> Unit, description: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp, content: @Composable BoxScope.() -> Unit) {
    GlassCircleButton(onClick, modifier.background(Color.Black.copy(alpha = .4f), CircleShape), size = size, description = description, shadowed = false, content = content)
}
