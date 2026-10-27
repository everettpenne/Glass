package com.everettpenne.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Which side of the screen a touch started on: brightness is the left, volume the right. */
internal enum class Side { LEFT, RIGHT }

internal class GestureHandlers(
    /** A plain tap, at [x] of a screen [width] wide. */
    val onTap: (x: Float, width: Float) -> Unit,
    /** A finger held still on the picture: true when it goes down for long enough, false when it lifts. */
    val onHold: (Boolean) -> Unit,
    /** A horizontal drag: [dx] pixels from where it began, over a screen [width] wide; [finished] when the finger lifts. */
    val onSeekDrag: (dx: Float, width: Float, finished: Boolean) -> Unit,
    /** A vertical drag on one [side]: [dy] pixels from where it began, over a screen [height] tall; [finished] when the finger lifts. */
    val onLevelDrag: (side: Side, dy: Float, height: Float, finished: Boolean) -> Unit,
)

private enum class Phase { UP, DRAG, CANCEL }

/**
 * The touch language of the player, on one surface: tap, hold, drag across to seek, drag up and down on the left half for brightness or on the
 * right for volume. Which drag it is gets decided once, by the direction the finger first moves, and stays that way until the finger lifts.
 * Turned off (nothing handled) while the screen is locked, except that a tap is still reported so the lock button can be shown.
 */
@Composable
internal fun Modifier.playerGestures(handlers: GestureHandlers, dragsEnabled: Boolean): Modifier {
    val h by rememberUpdatedState(handlers)
    return pointerInput(dragsEnabled) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            val width = size.width.toFloat(); val height = size.height.toFloat()
            val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var phase = Phase.CANCEL
                while (true) {
                    val event = awaitPointerEvent()
                    val c = event.changes.firstOrNull { it.id == down.id }
                    if (c == null) { phase = Phase.CANCEL; break }
                    // a button under the finger consumes its own tap: that is the button's, not a tap on the picture
                    if (c.changedToUpIgnoreConsumed()) { phase = if (c.isConsumed) Phase.CANCEL else Phase.UP; break }
                    if ((c.position - start).getDistance() > slop) { phase = Phase.DRAG; break }
                }
                phase
            }
            when (first) {
                Phase.UP -> h.onTap(start.x, width)
                Phase.CANCEL -> Unit
                null -> if (dragsEnabled) {                       // held long enough without moving
                    h.onHold(true)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id }
                        if (c == null || c.changedToUpIgnoreConsumed()) break
                        c.consume()
                    }
                    h.onHold(false)
                }
                Phase.DRAG -> if (dragsEnabled) {
                    var current = start
                    // decide the axis from where the finger is now, which is just past the slop
                    val probe = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (probe != null) current = probe.position
                    val horizontal = abs(current.x - start.x) >= abs(current.y - start.y)
                    val side = if (start.x < width / 2f) Side.LEFT else Side.RIGHT
                    fun report(at: Offset, finished: Boolean) {
                        if (horizontal) h.onSeekDrag(at.x - start.x, width, finished) else h.onLevelDrag(side, at.y - start.y, height, finished)
                    }
                    report(current, false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id }
                        if (c == null || c.changedToUpIgnoreConsumed()) { report(current, true); break }
                        current = c.position
                        c.consume()
                        report(current, false)
                    }
                }
            }
        }
    }
}
