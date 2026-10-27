package com.everettpenne.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The pieces a list page in the suite needed and each app had copied: a section heading, a grouped card of rows, one row, a search
 * field, and an empty state. They are deliberately flat rather than glass-on-glass: a page can hold dozens of rows, and a card of
 * quiet surface colour stays smooth to scroll while the bars above it do the refracting.
 */

/** A heading above a group or a shelf, with room for a trailing "See all"-style control. */
@Composable
fun GlassSectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = IosType.title3.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A rounded card that groups its rows, as iOS's inset grouped lists do. The rows inside draw no background of their own. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, GlassCorner.cardShape),
        content = content,
    )
}

/** A hairline between rows, inset to start beside the text as iOS does. */
@Composable
fun GlassRowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 16.dp).height(0.6.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)))
}

/**
 * One row: optional leading content (artwork, an icon), a title, an optional subtitle, and trailing content (a duration, a switch,
 * a chevron). The whole row is the target when [onClick] is given, and it lights faintly under the finger instead of rippling.
 */
@Composable
fun GlassListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = .06f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = IosType.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = IosType.footnote, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/**
 * The search field the suite's pages use: a clear glass capsule with a magnifier, a caret and a clear button. It is meant for a
 * screen's bar (it is not itself inside the scrolling content), so it refracts the page below it. Set [autoFocus] on a page whose
 * only job is searching.
 */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val ink = glassPalette().ink
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier.fillMaxWidth().height(48.dp).frostedGlass(shape, lensHeight = 10f, lensAmount = 20f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = ink.copy(alpha = .55f), modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = IosType.body, color = ink.copy(alpha = .45f), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = IosType.body.copy(color = ink),
                singleLine = true,
                cursorBrush = SolidColor(LocalGlassColors.current.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onDone?.invoke() }),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(22.dp).clickable(interactionSource = null, indication = null, onClick = { onValueChange("") })
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, null, tint = ink.copy(alpha = .55f), modifier = Modifier.size(18.dp)) }
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * What a list says when it has nothing to show: a quiet glass disc with a symbol, a short title, a helpful line, and an optional
 * action. Every empty page gets the same shape, so "nothing here yet" never looks like a bug.
 */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val ink = glassPalette().ink
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(84.dp).glassSurface(androidx.compose.foundation.shape.CircleShape, tint = ink.copy(alpha = .05f), blurRadius = 8f, lensHeight = 16f, lensAmount = 30f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = LocalGlassColors.current.accent, modifier = Modifier.size(34.dp)) }
        Text(title, style = IosType.title3.copy(fontWeight = FontWeight.SemiBold), color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(message, style = IosType.subheadline, color = ink.copy(alpha = .62f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(4.dp)); action() }
    }
}
