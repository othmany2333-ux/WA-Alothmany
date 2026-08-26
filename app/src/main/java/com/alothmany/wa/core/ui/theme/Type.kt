package com.alothmany.wa.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WATypography = Typography(
    headlineLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 30.sp),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 24.sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 20.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 16.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 16.sp),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 14.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp),
)
