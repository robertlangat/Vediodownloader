package com.robertlangat.socialvideodownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandRed = Color(0xFFE53935)
val BrandRedDark = Color(0xFFC62828)
val Charcoal = Color(0xFF1C1C1E)
val CharcoalRaised = Color(0xFF2A2A2D)
val TextMuted = Color(0xFFB0B0B5)

private val Colors = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2224),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB4AB),
    background = Charcoal,
    onBackground = Color.White,
    surface = Charcoal,
    onSurface = Color.White,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = TextMuted,
    surfaceContainer = CharcoalRaised,
    surfaceContainerHigh = Color(0xFF323236),
    outline = Color(0xFF55555A),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF3B2526),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun SocialVideoDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
