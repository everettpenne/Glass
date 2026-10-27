package com.everettpenne.player

import android.app.Activity
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Build
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.everettpenne.glass.GlassCircleButton
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassBackdrop
import com.everettpenne.media.PlayerScrubber
import com.everettpenne.media.VideoPip
import com.everettpenne.media.enterVideoPip
import com.everettpenne.media.pipRatio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays a list of videos starting at an index: (items, start). [PlayerHost] provides it; the default does nothing. */
val LocalVideoPlaylist = compositionLocalOf<(List<VideoSource>, Int) -> Unit> { { _, _ -> } }

/**
 * Draw once at the root. Provides both the single-video opener ([com.everettpenne.media.LocalVideoViewer], so an app already wired to Glass's simple
 * viewer needs no other change) and [LocalVideoPlaylist] for a folder of videos, and shows the player when something asks for it.
 */
@Composable
fun PlayerHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<Pair<List<VideoSource>, Int>?>(null) }
    CompositionLocalProvider(
        com.everettpenne.media.LocalVideoViewer provides { uri, title -> request = listOf(VideoSource(uri, title ?: uri.substringAfterLast('/'))) to 0 },
        LocalVideoPlaylist provides { items, start -> if (items.isNotEmpty()) request = items to start.coerceIn(0, items.lastIndex) },
    ) {
        content()
        request?.let { (items, start) -> VideoPlayer(items, start) { request = null } }
    }
}

/** What differs between the places a player is shown. The defaults suit a full-screen player an app owns end to end. */
data class PlayerOptions(
    /** Turn the phone to landscape for a wide picture (and back afterwards). Off where the host screen already decides. */
    val manageOrientation: Boolean = true,
    val showPictureInPicture: Boolean = true,
    /** The repeat button. Off where the host has a queue of its own that repeat would fight. */
    val showRepeat: Boolean = true,
    /** When not null, a "Keep playing when I leave" row is offered in More, showing this, and [onBackgroundPlay] is told of a change. */
    val backgroundPlay: Boolean? = null,
    val onBackgroundPlay: (Boolean) -> Unit = {},
    /** Picture-in-picture the host does itself (with its own window shape and actions); null uses Glass's. */
    val onPictureInPicture: (() -> Unit)? = null,
    /** A rotate / full-screen action the host owns (it decides the orientation itself); null uses the player's own when [manageOrientation] is on. */
    val onRotate: (() -> Unit)? = null,
)

/**
 * The full-screen player: [items] in order from [startIndex], with the controls people expect from a phone's own player. Swipe across to seek, up
 * and down on the left for brightness and on the right for volume, hold for double speed, double-tap a side to skip ten seconds; speed, audio and
 * subtitle tracks, subtitle files, fit or fill, repeat, lock, rotate, picture-in-picture, a preview picture while seeking, and a place remembered
 * for each video. Playback lives in [PlayerService], so sound can carry on when the app is left: always for a sound file, and for a video when the
 * person turns on "Keep playing when I leave". Closing the player stops it.
 */
@Composable
fun VideoPlayer(items: List<VideoSource>, startIndex: Int = 0, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resume = remember { ResumeStore.of(context) }
    val start = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val resumedAt = remember(items) { items.getOrNull(start)?.let { resume.resumeAt(it.uri) } }
    val controller = rememberPlayerController()
    var background by remember { mutableStateOf(resume.backgroundPlay) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(controller, items) {
        val c = controller ?: return@LaunchedEffect
        c.setMediaItems(items.map(::mediaItemOf), start, resumedAt ?: 0L)
        c.prepare(); c.playWhenReady = true
    }
    DisposableEffect(controller) {
        val c = controller
        onDispose {
            if (c != null) {
                saveProgress(c, resume)
                if (VideoPip.player === c) VideoPip.player = null
                c.stop(); c.clearMediaItems()
            }
        }
    }
    // In picture-in-picture the activity is paused but not stopped; stopped means the person really left (or closed the small window). Sound carries
    // on then if it is a sound file or they asked for it; a video that nobody is watching is paused.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller, background) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && controller != null) {
                saveProgress(controller, resume)
                val soundOnly = controller.currentTracks.groups.isNotEmpty() && !controller.currentTracks.isTypeSupported(C.TRACK_TYPE_VIDEO)
                if (!soundOnly && !background) controller.pause()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (controller != null && !VideoPip.active) FullscreenDialog { window ->
        PlayerContent(controller, window, onDismiss, resume = resume, resumedAt = resumedAt, options = PlayerOptions(
            backgroundPlay = background,
            onBackgroundPlay = { on ->
                background = on; resume.backgroundPlay = on
                // the media notification (and so the controls outside the app) needs this permission on Android 13+
                if (on && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }))
    }
}

/**
 * The same player for a [player] the app owns (a controller to its own media service, say): it plays, pauses and seeks it and releases nothing.
 * For Thumper's and Spice's video; they keep their queues, their progress saving and their notification, and get the controls and gestures.
 */
@Composable
fun FullscreenPlayer(player: Player, title: String? = null, options: PlayerOptions = PlayerOptions(showRepeat = false), onDismiss: () -> Unit) {
    FullscreenDialog { window -> PlayerContent(player, window, onDismiss, title = title, options = options) }
}

/** A full-screen window on black with the system bars hidden and the glass backdrop switched off (controls on video are plain dark discs). */
@Composable
fun FullscreenDialog(content: @Composable (Window?) -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
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
        // The dialog inherits the page's glass backdrop, which would paint pale discs on the video.
        CompositionLocalProvider(LocalGlassBackdrop provides null) { content(window) }
    }
}

@Composable
internal fun rememberPlayerController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlayerService::class.java))).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future); controller = null }
    }
    return controller
}

private fun saveProgress(player: Player, resume: ResumeStore) {
    val id = player.currentMediaItem?.mediaId ?: return
    if (player.playbackState == Player.STATE_ENDED) resume.finish(id) else resume.save(id, player.currentPosition, player.duration.coerceAtLeast(0))
}

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

private enum class PanelKind { NONE, TRACKS, SPEED, MORE }
private enum class Hud { NONE, BRIGHTNESS, VOLUME }

/**
 * Everything on screen for a player: the picture, the gestures, the controls and their panels. It draws into whatever it is placed in (a full-screen
 * dialog, or an activity's own screen) and works on any [Player]. [window] is the window whose brightness the left-edge swipe changes; [resume]
 * remembers places when given; [onClose] is the close button and Back.
 */
@Composable
fun PlayerContent(
    player: Player,
    window: Window?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    resume: ResumeStore? = null,
    resumedAt: Long? = null,
    options: PlayerOptions = PlayerOptions(),
) {
    val context = LocalContext.current
    val activity = context.activity()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var ratio by remember { mutableFloatStateOf(player.videoSize.ratioOr()) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var index by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var itemTitle by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var itemUri by remember { mutableStateOf(player.currentMediaItem?.localConfiguration?.uri?.toString()) }

    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var panel by remember { mutableStateOf(PanelKind.NONE) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var holding by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(FitMode.FIT) }
    var repeat by remember { mutableStateOf(RepeatMode.entries.firstOrNull { it.toPlayer() == player.repeatMode } ?: RepeatMode.OFF) }
    /** 0 follows the video's shape, 1 is landscape locked, 2 is portrait locked. */
    var orientation by remember { mutableIntStateOf(0) }
    var subtitleSize by remember { mutableFloatStateOf(resume?.subtitleSize ?: 0.0533f) }
    var resumedPill by remember { mutableStateOf(resumedAt) }

    var hud by remember { mutableStateOf(Hud.NONE) }
    var hudLevel by remember { mutableFloatStateOf(0f) }
    var seekText by remember { mutableStateOf<String?>(null) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableFloatStateOf(-1f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { ratio = videoSize.ratioOr() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_ENDED) { player.currentMediaItem?.let { resume?.finish(it.mediaId) }; controls = true } }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) { if (!holding) speed = p.speed }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                index = player.currentMediaItemIndex; itemTitle = mediaItem?.mediaMetadata?.title?.toString().orEmpty(); position = 0; error = null
                itemUri = mediaItem?.localConfiguration?.uri?.toString()
            }
            override fun onPlayerError(e: PlaybackException) { error = "This video can't be played" }
        }
        player.addListener(listener)
        VideoPip.player = player; VideoPip.fullscreenUp = true
        onDispose {
            player.removeListener(listener); VideoPip.fullscreenUp = false
            // Going into the small window closes this screen too; the shell still needs the player then, to show its picture.
            if (VideoPip.player === player && !VideoPip.active) VideoPip.player = null
        }
    }
    // A sound has no picture to put in a small window, so leaving the app must not try: only a player showing video is offered to picture-in-picture.
    LaunchedEffect(player, tracks) {
        val sound = tracks.groups.isNotEmpty() && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)
        if (sound) { if (VideoPip.player === player) VideoPip.player = null } else VideoPip.player = player
    }
    LaunchedEffect(player, controls, scrubbing) { if (controls && !scrubbing) while (true) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(250) } }
    if (resume != null) LaunchedEffect(player) { while (true) { delay(3000); saveProgress(player, resume) } }
    LaunchedEffect(controls, playing, scrubbing, panel, locked) { if (controls && playing && !scrubbing && panel == PanelKind.NONE && !locked) { delay(3500); controls = false } }
    LaunchedEffect(showUnlock) { if (showUnlock) { delay(2500); showUnlock = false } }
    LaunchedEffect(resumedPill) { if (resumedPill != null) { delay(5000); resumedPill = null } }
    LaunchedEffect(toast) { if (toast != null) { delay(1400); toast = null } }
    SideEffect { view.keepScreenOn = playing || buffering }
    DisposableEffect(view) { onDispose { view.keepScreenOn = false } }

    // Back closes a panel first, then a lock, then the player.
    BackHandler { if (panel != PanelKind.NONE) panel = PanelKind.NONE else if (locked) showUnlock = true else onClose() }

    // the screen's turn: follows a wide video to landscape unless the person chose
    if (options.manageOrientation) {
        DisposableEffect(activity) {
            val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onDispose { activity?.requestedOrientation = previous }
        }
        LaunchedEffect(ratio, orientation, tracks) {
            // While the next item is still loading its tracks are unknown; turning the phone on that guess would flip it back and forth between videos.
            if (orientation == 0 && tracks.groups.isEmpty()) return@LaunchedEffect
            val hasVideo = tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)
            activity?.requestedOrientation = when (orientation) {
                1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> if (hasVideo && ratio > 1.05f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // seek thumbnails: a frame of the moment under the finger, when the video is a file we can sample
    val thumbs = remember(itemUri) { if (canThumbnail(itemUri)) Thumbnails(context, itemUri!!) else null }
    DisposableEffect(thumbs) { onDispose { thumbs?.close() } }
    val previewMs: Long? = scrubFraction?.let { (it * duration).toLong() } ?: if (seekText != null) seekTargetMs else null
    val previewBucket = previewMs?.let(::thumbnailBucket)
    val preview by produceState<Bitmap?>(null, thumbs, previewBucket) {
        if (thumbs == null || previewBucket == null) { value = null; return@produceState }
        delay(50)   // a finger sweeping over many seconds asks for the last one only
        value = withContext(Dispatchers.IO) { thumbs.frameAt(previewBucket) } ?: value
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
            if (subtitleMime(name.substringAfterLast('.', "")) == null) toast = "Not a subtitle file (.srt, .vtt or .ass)" else { player.addSubtitle(uri, name); toast = "Subtitles added" }
        }
    }

    fun seekBy(deltaMs: Long) {
        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, end)
        player.seekTo(target); position = target; toast = if (deltaMs < 0) "−${deltaMs / -1000} s" else "+${deltaMs / 1000} s"
    }
    fun togglePlay() { if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0) }; if (player.isPlaying) player.pause() else player.play() }
    fun setSpeed(s: Float) { speed = s; if (!holding) player.setPlaybackSpeed(s); toast = speedLabel(s) }
    fun applyBrightness(v: Float) { brightness = v; window?.let { w -> w.attributes = w.attributes.also { a -> a.screenBrightness = v } } }
    fun currentBrightness(): Float = if (brightness >= 0f) brightness else window?.attributes?.screenBrightness?.takeIf { it >= 0f }
        ?: (android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    DisposableEffect(window) { onDispose { window?.let { w -> w.attributes = w.attributes.also { a -> a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } } }

    // gesture bookkeeping
    var seekStart by remember { mutableStateOf<Long?>(null) }
    var levelStart by remember { mutableStateOf<Float?>(null) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var lastTapRight by remember { mutableStateOf(false) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var hudJob by remember { mutableStateOf<Job?>(null) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val handlers = remember(player, locked) {
        GestureHandlers(
            onTap = { x, width ->
                if (locked) { showUnlock = true; return@GestureHandlers }
                val now = SystemClock.uptimeMillis()
                val right = x > width * 2f / 3f; val left = x < width / 3f
                if (now - lastTap < 300 && (left || right) && right == lastTapRight) {
                    tapJob?.cancel(); lastTap = 0
                    seekBy(if (right) 10_000L else -10_000L); controls = true
                } else if (now - lastTap < 300 && !left && !right) {
                    tapJob?.cancel(); lastTap = 0; togglePlay()
                } else {
                    lastTap = now; lastTapRight = right
                    tapJob?.cancel()
                    tapJob = scope.launch { delay(300); controls = !controls; if (controls) position = player.currentPosition }
                }
            },
            onHold = { on ->
                holding = on
                player.setPlaybackSpeed(if (on) HoldSpeed else speed)
                if (!on) toast = null
            },
            onSeekDrag = { dx, width, finished ->
                val from = seekStart ?: player.currentPosition.also { seekStart = it }
                val target = seekTarget(from, dx, width, player.duration.coerceAtLeast(0))
                seekTargetMs = target
                if (finished) { player.seekTo(target); position = target; seekStart = null; seekText = null }
                else seekText = seekLabel(from, target)
            },
            onLevelDrag = { side, dy, height, finished ->
                if (finished) { levelStart = null; hudJob?.cancel(); hudJob = scope.launch { delay(900); hud = Hud.NONE } }
                else {
                    hudJob?.cancel()
                    if (side == Side.LEFT) {
                        val from = levelStart ?: currentBrightness().also { levelStart = it }
                        val v = levelAfterDrag(from, dy, height).coerceAtLeast(MinBrightness)
                        applyBrightness(v); hud = Hud.BRIGHTNESS; hudLevel = v
                    } else {
                        val from = levelStart ?: (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume).also { levelStart = it }
                        val v = levelAfterDrag(from, dy, height)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeStep(v, maxVolume), 0)
                        hud = Hud.VOLUME; hudLevel = v
                    }
                }
            },
        )
    }

    val title = title ?: itemTitle
    // Controls on video are plain dark discs: the page's glass backdrop (when this is drawn inside an app's own screen) would paint pale ones.
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
    Box(modifier.fillMaxSize().background(Color.Black).playerGestures(handlers, dragsEnabled = !locked)) {
        PlayerSurface(player, fit, subtitleSize, Modifier.fillMaxSize().semantics { contentDescription = "Video" })
        if (!tracks.isTypeSupported(C.TRACK_TYPE_VIDEO) && tracks.groups.isNotEmpty() && error == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .35f), modifier = Modifier.align(Alignment.Center).size(96.dp))
        }

        // ---- what the gestures are doing ----
        seekText?.let { SeekPreview(preview, it, Modifier.align(Alignment.Center)) }
        (if (holding) "${speedLabel(HoldSpeed)} ▶▶" else toast)?.let { Pill(it, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 20.dp)) }
        error?.let { Pill(it, Modifier.align(Alignment.Center)) }
        if (hud != Hud.NONE) LevelHud(if (hud == Hud.BRIGHTNESS) Icons.Rounded.BrightnessMedium else if (hudLevel <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, hudLevel,
            Modifier.align(if (hud == Hud.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd).safeDrawingPadding().padding(horizontal = 32.dp))
        if (buffering && error == null && seekText == null) androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Color.White, strokeWidth = 3.dp)
        resumedPill?.let { at ->
            Row(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = if (controls) 150.dp else 28.dp)
                .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(50)).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Resumed at ${com.everettpenne.media.playerClock(at)}", style = IosType.subheadline, color = Color.White)
                Box(Modifier.clickable { player.seekTo(0); resumedPill = null }.padding(horizontal = 14.dp, vertical = 12.dp)) { Text("Start over", style = IosType.subheadline, color = Color.White.copy(alpha = .75f)) }
            }
        }

        // ---- locked ----
        if (locked && showUnlock) Ctl({ locked = false; showUnlock = false; controls = true }, "Unlock", Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(24.dp), 56.dp) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White)
        }

        // ---- controls ----
        if (controls && !locked) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f)))
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().safeDrawingPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ctl(onClose, "Close") { Icon(Icons.Rounded.Close, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = IosType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (player.mediaItemCount > 1) Text("${index + 1} of ${player.mediaItemCount}", style = IosType.caption1, color = Color.White.copy(alpha = .7f))
                }
                Ctl({ panel = PanelKind.TRACKS }, "Audio and subtitles", size = 40.dp) { Icon(Icons.Rounded.Subtitles, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                if (options.showPictureInPicture) Ctl({ options.onPictureInPicture?.invoke() ?: activity?.let { enterVideoPip(it) } }, "Picture in picture", size = 40.dp) { Icon(Icons.Rounded.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Ctl({ panel = PanelKind.MORE }, "More", size = 40.dp) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (player.mediaItemCount > 1) Ctl({ player.seekToPreviousMediaItem() }, "Previous video", size = 48.dp) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Ctl({ seekBy(-10_000) }, "Back 10 seconds", size = 52.dp) { Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Ctl({ togglePlay() }, if (playing) "Pause" else "Play", size = 72.dp) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
                Ctl({ seekBy(10_000) }, "Forward 10 seconds", size = 52.dp) { Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                if (player.mediaItemCount > 1) Ctl({ player.seekToNextMediaItem() }, "Next video", size = 48.dp) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PlayerScrubber(position, duration, onSeek = { player.seekTo(it); position = it }, timeColor = Color.White, onScrubbing = { scrubbing = it }, onScrub = { scrubFraction = it })
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tool(Icons.Rounded.LockOpen, "Lock controls", null, true) { locked = true; controls = false; showUnlock = true }
                    Tool(Icons.Rounded.Speed, "Playback speed", speedLabel(speed), speed != 1f) { panel = PanelKind.SPEED }
                    Tool(Icons.Rounded.AspectRatio, "Picture fit", fit.label, fit != FitMode.FIT) { fit = fit.next(); toast = fit.label }
                    if (options.showRepeat) Tool(if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", when (repeat) { RepeatMode.OFF -> null; RepeatMode.ALL -> "All"; RepeatMode.ONE -> "One" }, repeat != RepeatMode.OFF) {
                        repeat = repeat.next(); player.repeatMode = repeat.toPlayer(); toast = "Repeat: " + when (repeat) { RepeatMode.OFF -> "off"; RepeatMode.ALL -> "all"; RepeatMode.ONE -> "this video" }
                    }
                    Spacer(Modifier.weight(1f))
                    if (options.manageOrientation || options.onRotate != null) Tool(Icons.Rounded.ScreenRotation, "Rotate", null, orientation != 0) {
                        options.onRotate?.invoke() ?: run { orientation = if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 2 else 1 }
                    }
                }
            }
            // the picture under the finger while scrubbing, above the thumb
            if (scrubFraction != null) BoxWithConstraints(Modifier.align(Alignment.BottomStart).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp).padding(bottom = 120.dp)) {
                val density = LocalDensity.current
                val bubbleWidth = 176.dp
                val x = bubbleOffset(scrubFraction ?: 0f, with(density) { maxWidth.toPx() }, with(density) { bubbleWidth.toPx() })
                ScrubBubble(preview, playerClock((scrubFraction!! * duration).toLong()), bubbleWidth, Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) })
            }
        }

        // ---- panels ----
        when (panel) {
            PanelKind.NONE -> Unit
            PanelKind.SPEED -> PlayerPanel("Playback speed", { panel = PanelKind.NONE }) {
                PlaybackSpeeds.forEach { s -> PanelRow(speedLabel(s) + if (s == 1f) "  Normal" else "", selected = s == speed, onClick = { setSpeed(s); panel = PanelKind.NONE }) }
                PanelRow("Hold the picture to play at ${speedLabel(HoldSpeed)} while you press.")
            }
            PanelKind.TRACKS -> {
                val audioOptions = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
                val textOptions = trackOptions(tracks, C.TRACK_TYPE_TEXT)
                val textOff = player.textDisabled || textOptions.none { it.selected }
                PlayerPanel("Audio and subtitles", { panel = PanelKind.NONE }) {
                    if (audioOptions.size > 1) {
                        PanelHeading("Audio")
                        audioOptions.forEach { o -> PanelRow(o.label, selected = o.selected, onClick = { player.selectTrack(o); panel = PanelKind.NONE }) }
                    }
                    PanelHeading("Subtitles")
                    PanelRow("Off", selected = textOff, onClick = { player.disableTracks(C.TRACK_TYPE_TEXT); panel = PanelKind.NONE })
                    textOptions.forEach { o -> PanelRow(o.label, selected = o.selected && !textOff, onClick = { player.selectTrack(o); panel = PanelKind.NONE }) }
                    PanelRow("Add a subtitle file…", onClick = { panel = PanelKind.NONE; subtitlePicker.launch(arrayOf("*/*")) })
                    PanelHeading("Subtitle size")
                    val sizes = listOf(0.0427f, 0.0533f, 0.0693f, 0.09f)
                    PanelChips(listOf("Small", "Medium", "Large", "Huge"), sizes.indexOfFirst { kotlin.math.abs(it - subtitleSize) < 0.002f }.coerceAtLeast(1)) { subtitleSize = sizes[it]; resume?.subtitleSize = sizes[it] }
                }
            }
            PanelKind.MORE -> PlayerPanel("More", { panel = PanelKind.NONE }) {
                PanelRow(if (muted) "Unmute" else "Mute", onClick = { muted = !muted; player.volume = if (muted) 0f else 1f; panel = PanelKind.NONE })
                PanelRow("Start over", onClick = { player.seekTo(0); panel = PanelKind.NONE })
                PanelRow("Playback speed", detail = speedLabel(speed), onClick = { panel = PanelKind.SPEED })
                options.backgroundPlay?.let { on -> PanelRow("Keep playing when I leave", selected = on, detail = "Sound carries on with a notification; the picture waits.", onClick = { options.onBackgroundPlay(!on) }) }
                videoInfo(player)?.let { PanelRow("Video", detail = it) }
            }
        }
    }
    }
}

private fun VideoSize.ratioOr(): Float = if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else 16f / 9f

/** The picture, with subtitles drawn over it in white with an outline (readable on any frame), sized by [subtitleSize]. */
@Composable
private fun PlayerSurface(player: Player, fit: FitMode, subtitleSize: Float, modifier: Modifier) {
    AndroidView(
        factory = { c -> PlayerView(c).apply {
            useController = false; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            subtitleView?.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
            subtitleView?.setApplyEmbeddedStyles(true)
        } },
        modifier = modifier,
        update = {
            it.player = player
            it.resizeMode = when (fit) { FitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT; FitMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; FitMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL }
            it.subtitleView?.setFractionalTextSize(subtitleSize)
        },
        onRelease = { it.player = null })
}

/** A round control on video: a dark disc under glass, readable on any frame. */
@Composable
private fun Ctl(onClick: () -> Unit, description: String, modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable BoxScope.() -> Unit) {
    GlassCircleButton(onClick, modifier.background(Color.Black.copy(alpha = .4f), CircleShape), size = size, description = description, shadowed = false, content = content)
}

/** A small tool under the scrubber: an icon and, when it has a state to show, a word. Brighter when it is on. */
@Composable
private fun Tool(icon: ImageVector, description: String, label: String?, on: Boolean, onClick: () -> Unit) {
    val ink = Color.White.copy(alpha = if (on) 1f else .75f)
    Row(Modifier.heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 10.dp).semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        if (label != null) Text(label, style = IosType.subheadline, color = ink)
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) =
    Text(text, style = IosType.headline, color = Color.White, maxLines = 1, modifier = modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp))

/** The brightness or volume gauge shown while a finger drags: an icon over a vertical bar. */
@Composable
private fun LevelHud(icon: ImageVector, level: Float, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Box(Modifier.width(6.dp).height(110.dp).background(Color.White.copy(alpha = .25f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).background(Color.White, RoundedCornerShape(3.dp)))
        }
        Text("${(level * 100).toInt()}", style = IosType.caption1, color = Color.White)
    }
}

private fun playerClock(ms: Long) = com.everettpenne.media.playerClock(ms)

/** The pill shown while dragging across to seek: where it will land, with a picture of that moment above it when there is one. */
@Composable
private fun SeekPreview(frame: Bitmap?, text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (frame != null) Image(frame.asImageBitmap(), null, Modifier.width(200.dp).aspectRatio(frame.width.toFloat() / frame.height).background(Color.Black, RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
        Pill(text)
    }
}

/** The small picture and time that follow the scrubber's thumb. */
@Composable
private fun ScrubBubble(frame: Bitmap?, time: String, width: Dp, modifier: Modifier = Modifier) {
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (frame != null) Image(frame.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp)))
        Text(time, style = IosType.subheadline, color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
