package com.everettpenne.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.glass.IosType
import com.everettpenne.glass.glassPalette
import com.everettpenne.glass.glassSurface
import com.everettpenne.glass.pressSwell

/**
 * The now-playing capsule that floats above the tab bar: artwork, title and artist, play/pause and, when there is one, next. A hairline
 * of progress runs along its bottom edge, so you can see where you are without opening the player.
 *
 * One implementation for the whole suite (Seekur, Spice and Thumper each had their own): the same 64dp height, artwork radius, typography
 * and press behaviour, so the capsule looks the same whichever app is playing. It is the caller's job to hide it when nothing is playing.
 */
@Composable
fun GlassMiniPlayer(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    artwork: ImageBitmap? = null,
    progress: Float = 0f,
    playing: Boolean = false,
    accent: Color? = null,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    val ink = glassPalette().ink
    val fill = accent ?: MaterialTheme.colorScheme.primary
    val hairline = 3.dp
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp)
            .glassSurface(RoundedCornerShape(percent = 50), tint = ink.copy(alpha = .06f), blurRadius = 8f, lensHeight = 22f, lensAmount = 34f)
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind {
                if (progress > 0f) {
                    val height = hairline.toPx()
                    drawRect(fill, topLeft = Offset(0f, size.height - height), size = Size(size.width * progress.coerceIn(0f, 1f), height))
                }
            }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(ink.copy(alpha = .08f))) {
            if (artwork != null) {
                Image(artwork, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = ink.copy(alpha = .5f), modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = IosType.caption1, color = ink.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniControl(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", ink, onToggle)
        if (onNext != null) MiniControl(Icons.Rounded.SkipNext, "Next", ink, onNext)
    }
}

@Composable
private fun MiniControl(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressSwell().clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
}
