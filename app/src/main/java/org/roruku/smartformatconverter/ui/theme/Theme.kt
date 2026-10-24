package org.roruku.smartformatconverter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PixelShiftDark = darkColorScheme(
    primary = PixelCyan,
    onPrimary = PixelBackground,
    primaryContainer = Color(0xFF163B5A),
    onPrimaryContainer = Color(0xFFD7F8FF),
    secondary = PixelBlue,
    onSecondary = PixelBackground,
    secondaryContainer = Color(0xFF293A65),
    onSecondaryContainer = Color(0xFFDEEAFF),
    tertiary = PixelViolet,
    onTertiary = PixelBackground,
    background = PixelBackground,
    onBackground = PixelText,
    surface = PixelSurface,
    onSurface = PixelText,
    surfaceVariant = PixelSurfaceRaised,
    onSurfaceVariant = PixelMuted,
    surfaceContainerLowest = Color(0xFF0D1529),
    surfaceContainerLow = Color(0xFF121D34),
    surfaceContainer = Color(0xFF17223B),
    surfaceContainerHigh = Color(0xFF1D2D4B),
    surfaceContainerHighest = Color(0xFF253758),
    outline = Color(0xFF7184A3),
    outlineVariant = Color(0xFF354767),
    inverseSurface = PixelText,
    inverseOnSurface = PixelBackground,
    error = Color(0xFFFF8897),
    onError = PixelBackground
)

/** Optional light preview, still bound to the same cyan/violet brand. */
private val PixelShiftLight = lightColorScheme(
    primary = Color(0xFF006781),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F2FF),
    onPrimaryContainer = Color(0xFF003544),
    secondary = Color(0xFF415EC1),
    tertiary = Color(0xFF6E43BA),
    background = Color(0xFFF2F7FF),
    onBackground = Color(0xFF101C32),
    surface = Color.White,
    onSurface = Color(0xFF101C32),
    surfaceVariant = Color(0xFFDDE7F7),
    onSurfaceVariant = Color(0xFF45566B)
)

/**
 * Use the brand palette on every phone. Wallpaper-driven Dynamic Color caused
 * off-brand white/purple surfaces and notifications. Dark is the brand default.
 */
@Composable
fun SmartFormatConverterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Kept as an API parameter for existing callers; dynamic wallpaper colors
    // intentionally do not override the Pixel Shift identity.
    @Suppress("UNUSED_VARIABLE")
    val ignoreDynamicColor = dynamicColor
    MaterialTheme(
        colorScheme = if (darkTheme) PixelShiftDark else PixelShiftLight,
        typography = Typography,
        content = content
    )
}
