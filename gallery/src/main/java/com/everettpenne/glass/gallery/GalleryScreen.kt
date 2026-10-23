package com.everettpenne.glass.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.*

private val tabs2 = listOf(
    GlassTabItem("home", "Home", Icons.Rounded.Home),
    GlassTabItem("discover", "Discover", Icons.Rounded.Explore),
)
private val tabs3 = listOf(
    GlassTabItem("home", "Home", Icons.Rounded.Home),
    GlassTabItem("library", "Library", Icons.Rounded.VideoLibrary),
    GlassTabItem("music", "Music", Icons.Rounded.MusicNote),
)
private val tabs4 = listOf(
    GlassTabItem("home", "Home", Icons.Rounded.Home),
    GlassTabItem("library", "Library", Icons.Rounded.VideoLibrary),
    GlassTabItem("music", "Music", Icons.Rounded.MusicNote),
    GlassTabItem("profile", "Profile", Icons.Rounded.Person),
)
private val search = GlassTabItem("search", "Search", Icons.Rounded.Search)

private val stripes = listOf(Color(0xFFFF9F0A), Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF453A))

/**
 * Every component of the kit in one scrolling page, over striped content so the lens, blur and rim are visible. Open it after any change to
 * the glass recipe. It is a plain composable, so the instrumented test and the activity share it.
 */
@Composable
fun GalleryScreen() {
    var tabCount by remember { mutableIntStateOf(3) }
    val tabs = when (tabCount) { 2 -> tabs2; 4 -> tabs4; else -> tabs3 }
    var tab by remember { mutableStateOf<GlassTabItem?>(tabs[0]) }
    val selected = if (tab in tabs) tab else tabs[0]
    var chip by remember { mutableStateOf(true) }
    var switch by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(.4f) }
    var searchText by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var destructive by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalTabBar provides { GlassTabBar(tabs, selected, { tab = it }, search = search) }) {
    GlassScreen(
        bars = {
            Column(Modifier.align(Alignment.TopCenter)) {
                GlassTopBar("Gallery", subtitle = "Every piece of the kit", actions = {
                    GlassActionPill { PillIcon(Icons.Rounded.Share, "Share", {}); PillIcon(Icons.Rounded.MoreHoriz, "More", { menu = true }) }
                })
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 120.dp, end = 16.dp)) {
                GlassDropdownMenu(menu, { menu = false }) {
                    GlassMenuItem("Refresh", { menu = false }, icon = Icons.Rounded.Refresh)
                    GlassMenuItem("Selected", { menu = false }, icon = Icons.Rounded.Check, selected = true)
                    GlassMenuItem("Delete", { menu = false }, icon = Icons.Rounded.Delete, destructive = true)
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 24.dp, bottom = contentPadding.calculateBottomPadding() + 40.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Header("Tab bar") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(2, 3, 4).forEach { n -> GlassChip(tabCount == n, { tabCount = n }) { Text("$n tabs") } }
                }
            }
            item { Header("Buttons") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("Default", {}); GlassButton("Prominent", {}, prominent = true); GlassButton("Disabled", {}, enabled = false)
                    GlassIconButton(Icons.Rounded.Favorite, "Favourite", {})
                }
            }
            item { Header("Chips and switch") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassChip(chip, { chip = !chip }) { Text("Chip") }
                    GlassChip(!chip, { chip = !chip }) { Text("Other") }
                    LiquidSwitch(switch, { switch = it })
                }
            }
            item { Header("Slider and progress") }
            item { GlassSlider(slider, { slider = it }) }
            item { GlassProgressBar(slider) }
            item { GlassProgressBar(null) }
            item { Header("Round button") }
            item { GlassCircleButton({}, size = 56.dp, description = "Play") { androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, tint = LocalGlassColors.current.accent) } }
            item { Header("Overlays") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Alert", { destructive = false; alert = true }); GlassButton("Destructive", { destructive = true; alert = true }); GlassButton("Sheet", { sheet = true })
                }
            }
            item { Header("Rows and lists") }
            item { GlassSearchField(searchText, { searchText = it }, placeholder = "Search the kit") }
            item {
                GlassGroup {
                    GlassListRow(
                        "Library", subtitle = "A grouped row with a leading icon", onClick = {},
                        leading = { Icon(Icons.Rounded.LibraryMusic, null, tint = LocalGlassColors.current.accent, modifier = Modifier.size(22.dp)) },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                    GlassRowDivider()
                    GlassListRow(
                        "Shuffle", subtitle = "Rows light up instead of rippling", onClick = {},
                        leading = { Icon(Icons.Rounded.Shuffle, null, tint = LocalGlassColors.current.accent, modifier = Modifier.size(22.dp)) },
                        trailing = { LiquidSwitch(switch, { switch = it }) },
                    )
                }
            }
            item {
                GlassEmptyState(
                    Icons.Rounded.LibraryMusic, "Nothing here yet",
                    "The same friendly empty state on every page, with room for one action.",
                    action = { GlassButton("Try again", {}) },
                )
            }
            item { Header("Backdrop") }
            items(stripes) { c -> Box(Modifier.fillMaxWidth().height(80.dp).background(Brush.horizontalGradient(listOf(c, c.copy(alpha = .35f))), MaterialTheme.shapes.large)) }
        }
    }
    }

    if (alert) GlassAlert("Delete everything?", "This is how a destructive confirmation looks.", if (destructive) "Delete" else "OK",
        onConfirm = { alert = false }, onDismiss = { alert = false }, destructive = destructive)
    if (sheet) GlassSheet({ sheet = false }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sheet", style = IosType.title3)
            Text("A floating glass card for forms and long lists.", style = MaterialTheme.typography.bodyMedium)
            GlassButton("Done", { sheet = false }, prominent = true)
        }
    }
}

@Composable
private fun Header(text: String) = Text(text, style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
