package com.focuslockout.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** High-contrast type scale: heavy headlines, monospace numerals for timers and challenges. */
val FocusTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

/** Neon glow for hero text (wordmark, countdown). */
fun TextStyle.neonGlow(): TextStyle = copy(
    color = NeonGreen,
    shadow = Shadow(color = NeonGreen.copy(alpha = 0.55f), blurRadius = 28f),
)
