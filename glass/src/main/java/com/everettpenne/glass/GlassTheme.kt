package com.everettpenne.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The colours the kit reads from the app: the brand [accent] (tab selection, icons, prominent buttons, progress), [red] for destructive actions
 * and errors, and [green] for the "on" colour of [LiquidSwitch]. Supplied by [GlassTheme]; the defaults are iOS system blue, red and green.
 */
data class GlassColors(
    val accent: Color = Color(0xFF0A84FF),
    val red: Color = Color(0xFFFF453A),
    val green: Color = Color(0xFF34C759),
)

val LocalGlassColors = compositionLocalOf { GlassColors() }

private fun scheme(colors: GlassColors, dark: Boolean) = if (dark) darkColorScheme(
    primary = colors.accent, onPrimary = Color.White, background = Color(0xFF000000), surface = Color(0xFF000000), onBackground = Color(0xFFF2F2F7), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF98989D), outlineVariant = Color(0xFF38383A), error = colors.red)
else lightColorScheme(
    primary = colors.accent, onPrimary = Color.White, background = Color(0xFFF2F2F7), surface = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E), onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF6C6C70), outlineVariant = Color(0xFFD1D1D6), error = colors.red)

/**
 * The iOS look as a Material theme: grouped-background colour scheme built around [colors], the [IosType] scale in Inter on Material's slots, iOS-sized
 * corner radii, and [LocalGlassColors] for the kit's own pieces. Light or dark follows the system.
 */
@Composable
fun GlassTheme(colors: GlassColors, content: @Composable () -> Unit) {
    val scheme = scheme(colors, isSystemInDarkTheme())
    MaterialTheme(colorScheme = scheme, typography = GlassTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))) {
        // Text outside a Material surface takes this colour. Without it, titles and settings rows were black on the black dark-mode page.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, LocalGlassColors provides colors, content = content)
    }
}
