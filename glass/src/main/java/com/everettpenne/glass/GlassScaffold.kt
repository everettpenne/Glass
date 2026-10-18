package com.everettpenne.glass

import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer

/** The bar across the top of a root screen: a large title on the left, glass buttons on the right, fading into the page beneath. */
@Composable
fun GlassTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null,
                actions: @Composable RowScope.() -> Unit = {}) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().glassTopBar().progressiveBlur(fromTop = true, tint = bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, style = if (onBack != null) MaterialTheme.typography.headlineMedium else IosType.largeTitle.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            actions()
        }
    }
}

/** The bar of a pushed screen (a post, settings): a back button, the title centred with an optional line under it, and a button on the right. */
@Composable
fun GlassCenteredTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().glassTopBar().progressiveBlur(fromTop = true, tint = bg)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp).heightIn(min = 44.dp)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, Modifier.align(Alignment.CenterStart))
            Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/** A glass capsule holding several icon buttons side by side, as the original's toolbar does. */
@Composable
fun GlassActionPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val ink = glassPalette().ink
    Row(modifier.height(48.dp)
        .frostedGlass(RoundedCornerShape(percent = 50), lensHeight = 12f, lensAmount = 24f)
        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** One icon inside a [GlassActionPill]. */
@Composable
fun PillIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = LocalGlassColors.current.accent) {
    val press = rememberGlassPress()
    Box(Modifier.size(width = 52.dp, height = 44.dp).graphicsLayer { with(press) { swell() } }.then(press.watcher)
        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
}

/**
 * A fallback for how far the top bar reaches down (status bar plus the bar itself), used only where no bar has reported a measured height —
 * e.g. a bar used outside a [GlassScreen], or the first frame before a bar is laid out. The real height is measured by [GlassTopBar] and
 * [GlassCenteredTopBar] and can differ with subtitles and the system font scale, so prefer [contentTopPadding] over this constant.
 */
val TopBarHeight = 72.dp

/**
 * The insets a [GlassScreen] derives from the bars it draws over its content, for the content to pad itself with. Both already include their
 * window insets: [top] is the top bar's full height (status bar included), and [bottom] is the tab bar's full height (mini player and navigation
 * bar included). They are measured from the real layout, so they track subtitles and the system font scale.
 */
@Stable
class GlassBarInsets {
    /** The top bar's measured height, from the top of the window to the bottom of the title row. 0 until a bar has been laid out. */
    var top by mutableStateOf(0.dp); internal set

    /** The tab bar's measured height, from the bottom of the window to the top of the tab bar. 0 when no tab bar is present. */
    var bottom by mutableStateOf(0.dp); internal set
}

/** Provided by [GlassScreen]; bars write their measured heights here and the content reads them back. Null outside a [GlassScreen]. */
val LocalGlassBarInsets = staticCompositionLocalOf<GlassBarInsets?> { null }

/** Breathing room left between the content and a bar, so the last row does not sit flush against the glass. */
private val BarBreathing = 20.dp

/**
 * Marks a composable as the screen's top bar: its measured height (status bar included) is reported into [LocalGlassBarInsets], so
 * [GlassScreen]'s content pads under it exactly. The kit's bars apply this themselves; a custom header should add it to keep the contract.
 */
@Composable
fun Modifier.glassTopBar(): Modifier {
    val insets = LocalGlassBarInsets.current ?: return this
    val density = LocalDensity.current
    return this.onGloballyPositioned { val h = with(density) { it.size.height.toDp() }; if (insets.top != h) insets.top = h }
}

/** The top inset for content under a [GlassScreen]'s top bar: the bar's measured height (status bar included). Falls back to the status bar plus [TopBarHeight] outside a [GlassScreen]. */
@Composable
fun contentTopPadding(): Dp =
    LocalGlassBarInsets.current?.top ?: (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight)

/** The bottom inset for content above a [GlassScreen]'s tab bar (or the navigation bar when there is no tab bar), plus a little breathing room. */
@Composable
fun contentBottomPadding(): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(LocalGlassBarInsets.current?.bottom ?: 0.dp, navBar) + BarBreathing
}

/** What the tab bar needs to know about a tab; an app's own tab enum implements it. [label] doubles as the content description. */
interface GlassTabSpec {
    val route: String
    val label: String
    val icon: ImageVector
}

/** A plain [GlassTabSpec], for apps that do not keep their tabs in an enum. */
data class GlassTabItem(override val route: String, override val label: String, override val icon: ImageVector) : GlassTabSpec

/**
 * The floating tab bar, laid out as in the original: a glass capsule with [tabs] at the bottom left (the chosen one in a darker pill with the
 * accent), and, if given, [search] as its own round glass button at the bottom right. [selected] may be [search] (then no pill is shown) or null.
 *
 * The capsule is sized from [TabWidths]: each tab is a fixed slot (Apple's iOS 26 floating tab-bar width, ~76dp), so the capsule scales linearly
 * with the tab count (2 tabs ≈ 160dp, 3 ≈ 236dp) and down uniformly only when more tabs beside [search] cannot fit a phone. The icon and label
 * stay centred in their slot and the glass selector fills the active slot exactly.
 */
@Composable
fun <T : GlassTabSpec> GlassTabBar(tabs: List<T>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, search: T? = null) {
    val ink = glassPalette().ink
    val accent = LocalGlassColors.current.accent
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidTabs(selectedIndex = tabs.indexOf(selected).coerceAtLeast(0), onTabSelected = { onSelect(tabs[it]) }, tabsCount = tabs.size,
            indicatorVisible = selected != null && selected != search, modifier = Modifier.weight(1f)) {
            tabs.forEachIndexed { index, tab ->
                val on = tab == selected
                LiquidTab({ onSelect(tab) }, Modifier.semantics { contentDescription = tab.label }) {
                    Icon(tab.icon, null, tint = if (on) accent else ink, modifier = Modifier.size(26.dp))
                    Text(tab.label, style = IosType.caption1.copy(fontWeight = FontWeight.SemiBold), color = if (on) accent else ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (search != null) GlassCircleButton({ onSelect(search) }, size = 64.dp, description = search.label) {
            Icon(search.icon, null, tint = if (selected == search) accent else ink, modifier = Modifier.size(30.dp))
        }
    }
}

/** Supplied by the root: the floating tab bar, drawn by each top-level screen so it refracts that screen's content. Null on pushed screens. */
val LocalTabBar = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * A screen's glass layout. [content] (the scrolling page) is recorded into a layer; [bars] (top bar, search field, the tab bar) are drawn over
 * it and sample that layer, so the page really bends and blurs under them as it scrolls. Menus and sheets opened from anywhere in the screen
 * sample the same layer through [LocalOverlayBackdrop].
 *
 * [content] receives the measured bar insets as a [PaddingValues] so it can pad itself under the bars at any font scale. Call sites that ignore
 * the parameter keep working unchanged (the content simply draws edge-to-edge as before).
 */
@Composable
fun GlassScreen(modifier: Modifier = Modifier, bars: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.(PaddingValues) -> Unit) {
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val tabBar = LocalTabBar.current
    val insets = remember { GlassBarInsets() }
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalOverlayBackdrop provides backdrop, LocalGlassBarInsets provides insets) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)) {
                content(PaddingValues(top = insets.top, bottom = maxOf(insets.bottom, navBar) + BarBreathing))
            }
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                bars()
                if (tabBar != null) Box(Modifier.align(Alignment.BottomCenter).onGloballyPositioned {
                    val h = with(density) { it.size.height.toDp() }
                    if (insets.bottom != h) insets.bottom = h
                }) { tabBar() }
            }
        }
    }
}


/** A bottom sheet that floats as an inset liquid-glass card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = .62f)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .22f), dragHandle = null) {
        CompositionLocalProvider(LocalGlassBackdrop provides (LocalOverlayBackdrop.current ?: LocalGlassBackdrop.current)) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp).fillMaxWidth()
            .glassSurface(RoundedCornerShape(32.dp), tint = surface, blurRadius = 8f, lensHeight = 24f, lensAmount = 48f, shadowed = false)
            .background(if (LocalGlassBackdrop.current == null) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(32.dp))
            .navigationBarsPadding().padding(bottom = 8.dp)) {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
        }
    }
}
