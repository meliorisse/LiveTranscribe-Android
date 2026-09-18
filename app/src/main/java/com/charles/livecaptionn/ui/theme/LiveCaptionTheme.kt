package com.charles.livecaptionn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neutral surfaces keep long settings screens calm; blue identifies interactive
// controls. Amber and red are reserved for caution and error states.
private val LightColors = lightColorScheme(
    primary = Color(0xFF245C9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDFA),
    onPrimaryContainer = Color(0xFF153C69),
    inversePrimary = Color(0xFFA8CBFA),
    secondary = Color(0xFF526377),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EBF2),
    onSecondaryContainer = Color(0xFF293B50),
    tertiary = Color(0xFF785515),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0CC),
    onTertiaryContainer = Color(0xFF573C08),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF1B2635),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF1B2635),
    surfaceVariant = Color(0xFFE8EDF4),
    onSurfaceVariant = Color(0xFF536174),
    surfaceTint = Color(0xFF245C9C),
    surfaceDim = Color(0xFFDCE3EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE8EDF4),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF737F90),
    outlineVariant = Color(0xFFCAD3DF),
    inverseSurface = Color(0xFF293442),
    inverseOnSurface = Color(0xFFF1F5FA),
    error = Color(0xFFAD3036),
    onError = Color.White,
    errorContainer = Color(0xFFFFE9E8),
    onErrorContainer = Color(0xFF7B2027),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CBFA),
    onPrimary = Color(0xFF10345F),
    primaryContainer = Color(0xFF253F60),
    onPrimaryContainer = Color(0xFFDCEAFF),
    inversePrimary = Color(0xFF245C9C),
    secondary = Color(0xFFB8C7DB),
    onSecondary = Color(0xFF233246),
    secondaryContainer = Color(0xFF303E51),
    onSecondaryContainer = Color(0xFFDAE5F3),
    tertiary = Color(0xFFE6C071),
    onTertiary = Color(0xFF412E08),
    tertiaryContainer = Color(0xFF433619),
    onTertiaryContainer = Color(0xFFF5DEAA),
    background = Color(0xFF10161F),
    onBackground = Color(0xFFE5ECF5),
    surface = Color(0xFF141C27),
    onSurface = Color(0xFFE5ECF5),
    surfaceVariant = Color(0xFF273342),
    onSurfaceVariant = Color(0xFFB5C1D1),
    surfaceTint = Color(0xFFA8CBFA),
    surfaceDim = Color(0xFF10161F),
    surfaceBright = Color(0xFF344050),
    surfaceContainerLowest = Color(0xFF0C1119),
    surfaceContainerLow = Color(0xFF18212D),
    surfaceContainer = Color(0xFF1C2633),
    surfaceContainerHigh = Color(0xFF263242),
    surfaceContainerHighest = Color(0xFF202B39),
    outline = Color(0xFF8593A6),
    outlineVariant = Color(0xFF3E4B5E),
    inverseSurface = Color(0xFFE5ECF5),
    inverseOnSurface = Color(0xFF293442),
    error = Color(0xFFFFB3B4),
    onError = Color(0xFF650F1A),
    errorContainer = Color(0xFF49262E),
    onErrorContainer = Color(0xFFFFDADD),
    scrim = Color.Black
)

/** App chrome follows Android's appearance; caption overlay styling is independent. */
@Composable
fun LiveCaptionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
