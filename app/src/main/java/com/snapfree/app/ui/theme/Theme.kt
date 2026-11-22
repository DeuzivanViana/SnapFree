package com.snapfree.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapfree.app.data.ThemeMode

// Monochrome palette: every role is a shade of gray. Only errors keep a muted red for clarity.
private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF474747),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDEDED),
    onTertiaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF5C5C5C),
    surfaceTint = Color(0xFF111111),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    inverseSurface = Color(0xFF1E1E1E),
    inverseOnSurface = Color(0xFFF2F2F2),
    inversePrimary = Color(0xFFE0E0E0),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFDCDCDC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFC4C4C4),
    onSecondary = Color(0xFF141414),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFA8A8A8),
    onTertiary = Color(0xFF141414),
    tertiaryContainer = Color(0xFF262626),
    onTertiaryContainer = Color(0xFFEAEAEA),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceTint = Color(0xFFF2F2F2),
    surfaceBright = Color(0xFF2C2C2C),
    surfaceDim = Color(0xFF0E0E0E),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF151515),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF292929),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF2E2E2E),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color(0xFF000000),
)

/** AMOLED variant: true black backgrounds for OLED screens. */
private fun ColorScheme.pureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
)

val SnapFreeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val BaseTypography = Typography()

val SnapFreeTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SnapFreeTheme(
    darkTheme: Boolean,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        darkTheme && pureBlack -> DarkColors.pureBlack()
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = SnapFreeShapes,
        typography = SnapFreeTypography,
        content = content,
    )
}
