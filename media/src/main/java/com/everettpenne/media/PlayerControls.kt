package com.everettpenne.media

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.GlassCircleButton
import com.everettpenne.glass.GlassSlider
import com.everettpenne.glass.IosType

/*
 * The one player every app in the suite uses: the same scrubber and the same transport row, so Seekur's videos, Spice's music and video and
 * Thumper's episodes are played with the same controls, in the same places, with the same feel. Each app keeps its own extras (skip lengths,
 * shuffle and repeat, speed and sleep) and passes them in either side of the play button.
 */

/** Elapsed time as m:ss, or h:mm:ss from an hour. */
fun playerClock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Where a scrubber's thumb is, 0..1: the finger's place while scrubbing, otherwise the playing position. Pure, so it is unit-tested. */
fun scrubberFraction(positionMs: Long, durationMs: Long, scrub: Float?): Float =
    scrub ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/**
 * The scrubber: a glass slider with the elapsed time under its left end and the time left under its right. Dragging moves only the thumb and
 * the times (so the picture or sound does not stutter under the finger); the player seeks once, where the finger lets go.
 */
@Composable
fun PlayerScrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier,
    timeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onScrubbing: (Boolean) -> Unit = {}) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0)
    val shown = scrub?.let { (it * duration).toLong() } ?: positionMs
    Column(modifier.fillMaxWidth()) {
        GlassSlider(value = scrubberFraction(positionMs, duration, scrub),
            onValueChange = { if (scrub == null) onScrubbing(true); scrub = it },
            onValueChangeFinished = { scrub?.let { onSeek((it * duration).toLong()) }; scrub = null; onScrubbing(false) },
            modifier = Modifier.fillMaxWidth(), description = "Seek")
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(playerClock(shown), style = IosType.caption1, color = timeColor)
            Spacer(Modifier.weight(1f))
            Text("-" + playerClock(duration - shown), style = IosType.caption1, color = timeColor)
        }
    }
}

/**
 * The transport row: whatever the app puts before the play button, the big play button itself (a spinner while it is [buffering]), and
 * whatever comes after, spread evenly. The play button is a glass circle in the app's [accent].
 */
@Composable
fun PlayerTransport(playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, accent: Color? = null, buffering: Boolean = false,
    size: Dp = 72.dp, start: @Composable RowScope.() -> Unit = {}, end: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        start()
        PlayButton(playing, onToggle, accent, buffering, size)
        end()
    }
}

/** The big play / pause button on its own (a mini-player or a video's controls use it without the row). */
@Composable
fun PlayButton(playing: Boolean, onToggle: () -> Unit, accent: Color? = null, buffering: Boolean = false, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val ink = if (accent != null) Color.White else MaterialTheme.colorScheme.onSurface
    GlassCircleButton(onToggle, modifier, size = size, tint = accent, description = if (playing) "Pause" else "Play") {
        if (buffering) CircularProgressIndicator(Modifier.size(size * .5f), color = ink, strokeWidth = 3.dp)
        else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = ink, modifier = Modifier.size(size * .58f))
    }
}
