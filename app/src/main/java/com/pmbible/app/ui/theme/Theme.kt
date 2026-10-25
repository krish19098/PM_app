package com.pmbible.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.pmbible.core.model.ThemeMode

// Calm teal palette with a warm accent; contrast checked for WCAG AA on body text.
private val Light = lightColorScheme(
    primary = Color(0xFF1F5C57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E3),
    onPrimaryContainer = Color(0xFF0A2E2B),
    secondary = Color(0xFF4F6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECEA),
    onSecondaryContainer = Color(0xFF1B2A28),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1A8),
    onTertiaryContainer = Color(0xFF2C1C00),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFAFAF7),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE6EBE9),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5F2),
    surfaceContainer = Color(0xFFEFF1EE),
    surfaceContainerHigh = Color(0xFFE9ECE9),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFC5CCCA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD1C8),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0F4A45),
    onPrimaryContainer = Color(0xFFCDE8E3),
    secondary = Color(0xFFB3CCC8),
    onSecondary = Color(0xFF1F3532),
    secondaryContainer = Color(0xFF354B48),
    onSecondaryContainer = Color(0xFFCFE8E3),
    tertiary = Color(0xFFF2B84B),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634000),
    onTertiaryContainer = Color(0xFFFFDDA6),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF0C0F0E),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D201F),
    surfaceContainerHigh = Color(0xFF272B2A),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
)

/** Success/positive colour used for correct answers and mastery, independent of the scheme. */
data class ExtraColors(val success: Color, val onSuccessContainer: Color, val successContainer: Color)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF2E7D32), Color(0xFF0B3D0F), Color(0xFFD7F0D9))
}

@Composable
fun PmBibleTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) ExtraColors(Color(0xFF8BD192), Color(0xFFD7F0D9), Color(0xFF1E4D22))
    else ExtraColors(Color(0xFF2E7D32), Color(0xFF0B3D0F), Color(0xFFD7F0D9))
    val density = LocalDensity.current
    // The learner's text-size setting multiplies the system font scale.
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale),
        LocalExtraColors provides extra,
    ) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
