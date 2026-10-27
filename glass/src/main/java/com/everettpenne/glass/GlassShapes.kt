package com.everettpenne.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A rounded rectangle whose corners are a superellipse (|x|^n + |y|^n = 1, n = 5): the curve iOS's app-icon mask follows. Much squarer than a circular
 * arc, with curvature that rises and falls smoothly instead of switching on at the end of a straight edge. The corner takes in [REACH] radii of each
 * edge, which at an icon's radius (22.37 percent of its side) is half of it, so an icon is the whole superellipse. The launcher (Uno) draws its icons,
 * folders and widgets with the same curve, so the suite's surfaces match.
 *
 * Use it to clip, draw and border. The liquid-glass lens throws on any outline but a circular rounded rectangle, so glass surfaces keep
 * [RoundedCornerShape]; [GlassCorner] gives the same radii for them.
 */
@Immutable
class SquircleShape(private val radius: Dp = 0.dp, private val fraction: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = if (fraction > 0f) fraction * min(size.width, size.height) else with(density) { radius.toPx() }
        return Outline.Generic(Path().also { build(size.width, size.height, r, it) })
    }
    override fun equals(other: Any?) = other is SquircleShape && other.radius == radius && other.fraction == fraction
    override fun hashCode() = 31 * radius.hashCode() + fraction.hashCode()

    companion object {
        const val EXPONENT = 5f
        const val REACH = 2.235f
        private const val STEPS = 28

        fun build(width: Float, height: Float, radius: Float, path: Path) {
            val half = min(width, height) / 2f
            val r = radius.coerceIn(0f, half)
            if (r < 0.01f) { path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, width, height)); return }
            val c = min(r * REACH, half)
            val p = 2.0 / EXPONENT
            val us = FloatArray(STEPS + 1); val vs = FloatArray(STEPS + 1)
            for (i in 0..STEPS) {
                val t = (Math.PI / 2.0) * i / STEPS
                us[i] = sin(t).pow(p).toFloat(); vs[i] = cos(t).pow(p).toFloat()
            }
            path.moveTo(c, 0f)
            path.lineTo(width - c, 0f)
            for (i in 1..STEPS) path.lineTo(width - c + c * us[i], c - c * vs[i])
            path.lineTo(width, height - c)
            for (i in 1..STEPS) path.lineTo(width - c + c * vs[i], height - c + c * us[i])
            path.lineTo(c, height)
            for (i in 1..STEPS) path.lineTo(c - c * us[i], height - c + c * vs[i])
            path.lineTo(0f, c)
            for (i in 1..STEPS) path.lineTo(c - c * vs[i], c - c * us[i])
            path.close()
        }
    }
}

/**
 * The suite's corner scale, iOS's steps: small 8, medium 16, large 24, a widget or grouped card 26, and an app icon's 22.37 percent of its side. The
 * same numbers as the launcher's own scale, so a card in an app and a widget on Home read as one family.
 */
object GlassCorner {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    /** A widget and an inset grouped card. */
    val card = 26.dp
    const val ICON_FRACTION = 0.2237f

    val smallShape: Shape = SquircleShape(small)
    val mediumShape: Shape = SquircleShape(medium)
    val largeShape: Shape = SquircleShape(large)
    val cardShape: Shape = SquircleShape(card)
    val iconShape: Shape = SquircleShape(fraction = ICON_FRACTION)
}
