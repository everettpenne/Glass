package com.everettpenne.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.everettpenne.glass.GlassCircleButton
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassBackdrop
import kotlin.math.abs

/** Opens a set of images full screen (urls, index to start on). [ImageViewerHost] provides it; the default does nothing. */
val LocalImageViewer = compositionLocalOf<(List<String>, Int) -> Unit> { { _, _ -> } }

/** Draw once at the root: it provides [LocalImageViewer] to the content and shows the viewer when something asks for it. */
@Composable
fun ImageViewerHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    CompositionLocalProvider(LocalImageViewer provides { urls, i -> request = urls to i }) {
        content()
        request?.let { (urls, i) -> ImageViewer(urls, i) { request = null } }
    }
}

/**
 * Full-screen images on black: pinch to zoom (up to 6x) and drag to pan, double-tap to zoom in and out, swipe sideways between images, swipe
 * down (or up) to dismiss, tap to hide or show the controls.
 */
@Composable
fun ImageViewer(urls: List<String>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
        var dragY by remember { mutableFloatStateOf(0f) }
        var zoomed by remember { mutableStateOf(false) }
        var chrome by remember { mutableStateOf(true) }
        val fade = (1f - abs(dragY) / 900f).coerceIn(0f, 1f)
        // like the video controls: no inherited page backdrop, so the close button is a plain dark disc on black
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                ZoomableImage(urls[page], onZoomed = { zoomed = it }, onDrag = { dragY = it }, onDismiss = onDismiss, onTap = { chrome = !chrome })
            }
            if (chrome && dragY == 0f) {
                GlassCircleButton(onDismiss, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).background(Color.Black.copy(alpha = .4f), CircleShape), size = 44.dp, description = "Close") {
                    Icon(Icons.Rounded.Close, null, tint = Color.White)
                }
                if (urls.size > 1) Text("${pager.currentPage + 1} / ${urls.size}", style = IosType.footnote, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)
                        .background(Color.Black.copy(alpha = .45f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, onZoomed: (Boolean) -> Unit, onDrag: (Float) -> Unit, onDismiss: () -> Unit, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val slop = LocalViewConfiguration.current.touchSlop
    fun clamp(size: androidx.compose.ui.unit.IntSize) {
        val mx = size.width * (scale - 1f) / 2f; val my = size.height * (scale - 1f) / 2f
        offsetX = offsetX.coerceIn(-mx, mx); offsetY = offsetY.coerceIn(-my, my)
    }
    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f; onZoomed(scale > 1f) },
            )
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var total = androidx.compose.ui.geometry.Offset.Zero
                var vertical = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pressed = event.changes.count { it.pressed }
                    val zoom = event.calculateZoom(); val pan = event.calculatePan()
                    if (pressed > 1 || scale > 1f) {
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y; clamp(size) } else { offsetX = 0f; offsetY = 0f }
                        onZoomed(scale > 1f)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (pressed == 1) {
                        total += pan
                        if (!vertical && abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.5f) vertical = true
                        if (vertical) { dragY += pan.y; onDrag(dragY); event.changes.forEach { if (it.positionChanged()) it.consume() } }
                    }
                } while (event.changes.any { it.pressed })
                if (vertical) { if (abs(dragY) > 220f) onDismiss() else { dragY = 0f; onDrag(0f) } }
            }
        }, contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY + dragY })
    }
}
