package com.everettpenne.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.IosType

/*
 * The player's side panels (tracks, speed, more): a dark card over a dimmed picture, on the right of a landscape screen and at the bottom of a
 * portrait one. Drawn inside the player rather than as a sheet, so they do not open a second window over the full-screen one.
 */

private val PanelColor = Color(0xF21C1C1E)
private val Hairline = Color.White.copy(alpha = .12f)

@Composable
internal fun BoxScope.PlayerPanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
    BoxWithConstraints(Modifier.matchParentSize().safeDrawingPadding().padding(12.dp)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
            .then(if (landscape) Modifier.width(340.dp).heightIn(max = maxHeight) else Modifier.fillMaxWidth().heightIn(max = maxHeight * .8f))
            .background(PanelColor, RoundedCornerShape(26.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}   // taps inside the card must not close it
            .padding(vertical = 8.dp)) {
            Text(title, style = IosType.headline.copy(fontWeight = FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), content = content)
        }
    }
}

@Composable
internal fun PanelHeading(text: String) =
    Text(text.uppercase(), style = IosType.caption1, color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp))

/** One choice in a panel: a tick on the one in use. [onClick] null makes it a plain line of information. */
@Composable
internal fun PanelRow(label: String, selected: Boolean = false, detail: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = IosType.body, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = IosType.caption1, color = Color.White.copy(alpha = .6f), maxLines = 2)
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Color.White)
    }
    Box(Modifier.fillMaxWidth().padding(start = 20.dp).height(1.dp).background(Hairline))
}

/** A row of small choices (subtitle sizes). */
@Composable
internal fun PanelChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, text ->
            Box(Modifier.background(if (i == selected) Color.White else Color.White.copy(alpha = .14f), RoundedCornerShape(50)).clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text, style = IosType.subheadline, color = if (i == selected) Color.Black else Color.White)
            }
        }
    }
}
