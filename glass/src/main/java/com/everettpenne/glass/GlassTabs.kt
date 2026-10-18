package com.everettpenne.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** How much a tab swells while the indicator is pressed (the Backdrop catalog's `LocalLiquidBottomTabScale`). */
internal val LocalTabScale = staticCompositionLocalOf { { 1f } }

/** The minimum width a tab's own content may take, so even the shortest label keeps a 44dp+ target and the indicator keeps its breathing room. */
private val TabMinWidth = TabWidths.minSlot.dp

/** The fixed slot width [LiquidTabs] gives each tab; the icon and label centre inside it. */
private val LocalTabSlotWidth = staticCompositionLocalOf { TabMinWidth }

/**
 * One tab of [LiquidTabs]: a fixed-width slot (the width [LiquidTabs] computed from [TabWidths]) whose icon and label are content-sized and
 * centred inside it. The whole slot is the tap target and swells with the indicator.
 */
@Composable
fun RowScope.LiquidTab(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalTabScale.current
    val slot = LocalTabSlotWidth.current
    Box(modifier
        .width(slot).fillMaxHeight()
        .clip(RoundedCornerShape(percent = 50)).clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
        .graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
        contentAlignment = Alignment.Center) {
        Column(Modifier
            .widthIn(min = TabMinWidth).padding(horizontal = 10.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * The tab bar of the Backdrop catalog (iOS 26's): a glass capsule with a glass indicator under the selected tab. Press the indicator and it swells into a
 * lens that bends the tabs behind it, and you can drag it to another tab; the tab under it shows in the accent colour. The whole capsule swells a little
 * while the indicator is held. [selectedIndex] positions the indicator; with [indicatorVisible] false (the selection is elsewhere) it fades away.
 *
 * Each tab is a fixed, equal-width slot ([TabWidths]), and the indicator fills the selected slot exactly — Apple's floating tab-bar look, where the
 * glass pill is the width of the tab, not of its label.
 */
@Composable
fun LiquidTabs(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    indicatorVisible: Boolean = true,
    accentColor: Color = LocalGlassColors.current.accent,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop: Backdrop = LocalGlassBackdrop.current ?: emptyBackdrop()
    val dark = isSystemInDarkTheme()
    val container = if (dark) Color(0xFF121212).copy(alpha = .4f) else Color(0xFFFAFAFA).copy(alpha = .4f)
    val tabsBackdrop = rememberLayerBackdrop()
    val latestSelected by rememberUpdatedState(selectedIndex)
    val latestSelect by rememberUpdatedState(onTabSelected)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val slot = remember(tabsCount, constraints.maxWidth, density) {
            TabWidths.slots(tabsCount, constraints.maxWidth / density.density).dp
        }
        val slotPx = with(density) { slot.toPx() }
        val insetPx = with(density) { TabWidths.capsuleInset.dp.toPx() }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }

        var panelWidthPx by remember { mutableIntStateOf(0) }

        fun indicatorLeft(value: Float): Float {
            val v = value.coerceIn(0f, (tabsCount - 1).toFloat())
            return insetPx + v * slotPx
        }

        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / panelWidthPx.coerceAtLeast(1)).coerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        val drag = remember(scope, tabsCount, slotPx) {
            DampedDragAnimation(
                animationScope = scope, initialValue = selectedIndex.toFloat(), valueRange = 0f..(tabsCount - 1).toFloat(), visibilityThreshold = .001f,
                initialScale = 1f, pressedScale = 78f / 56f, onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    currentIndex = target; animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, .5f)) }
                },
                onDrag = { _, amount ->
                    updateValue((targetValue + amount.x / slotPx * (if (isLtr) 1f else -1f)).coerceIn(0f, (tabsCount - 1).toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                },
            )
        }
        LaunchedEffect(selectedIndex) { currentIndex = selectedIndex }
        LaunchedEffect(drag) { snapshotFlow { currentIndex }.drop(1).collectLatest { i -> drag.animateToValue(i.toFloat()); if (i != latestSelected) latestSelect(i) } }
        val indicatorAlpha by animateFloatAsStateCompat(if (indicatorVisible) 1f else 0f)

        val highlight = remember(scope) {
            InteractiveHighlight(scope) { size, _ ->
                Offset(indicatorLeft(drag.value) + slotPx / 2f + panelOffset, size.height / 2f)
            }
        }
        val shape = { RoundedCornerShape(percent = 50) }

        // the panel: glass over the page, swelling a little while the indicator is held
        Row(Modifier
            .graphicsLayer { translationX = panelOffset }
            .drawBackdrop(
                backdrop = backdrop, shape = shape,
                effects = { glassColor(); blur(2f.dp.toPx()); lens(24f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true) },
                layerBlock = { val s = lerp(1f, 1f + 16f.dp.toPx() / size.width, drag.pressProgress); scaleX = s; scaleY = s },
                onDrawSurface = { drawRect(container) },
            )
            .then(highlight.modifier)
            .onGloballyPositioned { panelWidthPx = it.size.width }
            .height(64.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalTabSlotWidth provides slot) { content() }
        }

        // the same tabs again, in the accent colour, unseen: what the indicator's lens shows through itself
        CompositionLocalProvider(LocalTabScale provides { lerp(1f, 1.2f, drag.pressProgress) }, LocalTabSlotWidth provides slot) {
            Row(Modifier
                .clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop, shape = shape,
                    effects = { val p = drag.pressProgress; glassColor(); blur(2f.dp.toPx()); lens(24f.dp.toPx() * p, 24f.dp.toPx() * p, chromaticAberration = true) },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier).height(56.dp).padding(horizontal = 4.dp)
                .graphicsLayer { colorFilter = ColorFilter.tint(accentColor) }, verticalAlignment = Alignment.CenterVertically, content = content)
        }

        // the indicator: fills the selected slot, and slides one slot per tab while dragged.
        Box(Modifier
            .alpha(indicatorAlpha)
            .graphicsLayer { translationX = indicatorLeft(drag.value) + panelOffset }
            .then(if (indicatorVisible) Modifier.then(highlight.gestureModifier).then(drag.modifier) else Modifier)   // hidden: let taps reach the tab beneath
            .drawBackdrop(
                // Unfrosted: no blur and no wash, only the lens, so the tab beneath is seen through a clear bead of glass. It is always lit and
                // bent a little, and swells into the stronger lens when held.
                backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop), shape = shape,
                effects = { val p = .45f + .55f * drag.pressProgress; lens(10f.dp.toPx() * p, 14f.dp.toPx() * p, chromaticAberration = true) },
                highlight = { Highlight.Default.copy(alpha = .7f + .3f * drag.pressProgress) },
                shadow = { Shadow(alpha = .35f + .65f * drag.pressProgress) },
                innerShadow = { val p = .3f + .7f * drag.pressProgress; InnerShadow(radius = 6f.dp * p, alpha = p) },
                layerBlock = {
                    scaleX = drag.scaleX; scaleY = drag.scaleY
                    val v = drag.velocity / 10f
                    scaleX /= 1f - (v * .75f).coerceIn(-.2f, .2f); scaleY *= 1f - (v * .25f).coerceIn(-.2f, .2f)
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = if (dark) .06f else .1f)) },
            )
            .size(width = slot, height = 56.dp))
    }
}

@Composable
private fun animateFloatAsStateCompat(target: Float) = androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(220), label = "indicator")
