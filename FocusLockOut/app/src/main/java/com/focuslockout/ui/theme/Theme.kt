package com.focuslockout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Dark-only scheme: pure black surfaces, neon green accents, no tonal tint on elevated surfaces. */
private val FocusColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = PureBlack,
    primaryContainer = NeonGreenDeep,
    onPrimaryContainer = NeonGreen,
    inversePrimary = NeonGreenDim,
    secondary = NeonGreen,
    onSecondary = PureBlack,
    secondaryContainer = SurfaceHigh,
    onSecondaryContainer = NeonGreen,
    tertiary = NeonGreen,
    onTertiary = PureBlack,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = PureBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceTint = PureBlack,
    inverseSurface = TextPrimary,
    inverseOnSurface = PureBlack,
    error = DangerRed,
    onError = PureBlack,
    errorContainer = DangerDeep,
    onErrorContainer = DangerRed,
    outline = OutlineGreen,
    outlineVariant = OutlineMuted,
    scrim = PureBlack,
    surfaceBright = SurfaceHighest,
    surfaceDim = PureBlack,
    surfaceContainerLowest = PureBlack,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
)

@Composable
fun FocusLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FocusColorScheme,
        typography = FocusTypography,
        content = content,
    )
}
