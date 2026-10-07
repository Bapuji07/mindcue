package com.secondmemory.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand colours: the hero gradient, avatar and splash look the same in light and dark themes.
val Evergreen = Color(0xFF176B52)
val EvergreenDark = Color(0xFF0F4E3B)
val DeepGreen = Color(0xFF10231D)
val Accent = Color(0xFF7FE0B2)
val BrandGradient = Brush.linearGradient(listOf(Evergreen, EvergreenDark))

// Every role is set so Material components (dialogs, menus, chips, snackbars) stay on brand
// instead of falling back to the default purple-tinted baseline palette.
private val LightColors = lightColorScheme(
    primary = Evergreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F4EC), onPrimaryContainer = DeepGreen,
    inversePrimary = Color(0xFF8AD7B4),
    secondary = Color(0xFF4C6359), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DC), onSecondaryContainer = Color(0xFF082018),
    tertiary = Color(0xFF8A5A00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE7C3), onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF7F8F5), onBackground = DeepGreen,
    surface = Color.White, onSurface = DeepGreen,
    surfaceVariant = Color(0xFFDCE5DF), onSurfaceVariant = Color(0xFF5E6D67),
    surfaceTint = Evergreen,
    inverseSurface = Color(0xFF2B322E), inverseOnSurface = Color(0xFFEDF2EE),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF6F7A74), outlineVariant = Color(0xFFE1E7E3),
    surfaceBright = Color(0xFFF9FBF9), surfaceDim = Color(0xFFD8DED9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F7F4),
    surfaceContainer = Color(0xFFEEF2EF), surfaceContainerHigh = Color(0xFFE8EDE9),
    surfaceContainerHighest = Color(0xFFE2E8E4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6AE), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1E4A3B), onPrimaryContainer = Color(0xFFC6F1DC),
    inversePrimary = Evergreen,
    secondary = Color(0xFFB3CCC0), onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF354B42), onSecondaryContainer = Color(0xFFCFE9DC),
    tertiary = Color(0xFFF2C26B), onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF5C4300), onTertiaryContainer = Color(0xFFFFDFA0),
    background = Color(0xFF0E1412), onBackground = Color(0xFFDDE5E0),
    surface = Color(0xFF172019), onSurface = Color(0xFFDDE5E0),
    surfaceVariant = Color(0xFF3F4943), onSurfaceVariant = Color(0xFFA3B2AB),
    surfaceTint = Color(0xFF7FD6AE),
    inverseSurface = Color(0xFFDDE5E0), inverseOnSurface = Color(0xFF2B322E),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF2B3631),
    surfaceBright = Color(0xFF333B37), surfaceDim = Color(0xFF0E1412),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF141B18),
    surfaceContainer = Color(0xFF18201C), surfaceContainerHigh = Color(0xFF222A26),
    surfaceContainerHighest = Color(0xFF2D3531)
)

@Composable
fun MindCueTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

// Short names for the theme roles the screens use most.
val Primary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val ScreenBackground: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val CardSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val PrimarySoft: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val ErrorColor: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val Warning: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val CardBorder: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
