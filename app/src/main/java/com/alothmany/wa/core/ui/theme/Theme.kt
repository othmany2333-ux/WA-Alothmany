package com.alothmany.wa.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alothmany.wa.core.model.AppTheme

private val DarkColors = darkColorScheme(
    primary = Cyan400,
    onPrimary = Night900,
    secondary = Teal400,
    tertiary = Gold400,
    background = Night900,
    onBackground = TextPrimaryDark,
    surface = Night850,
    onSurface = TextPrimaryDark,
    surfaceVariant = Night800,
    onSurfaceVariant = TextSecondaryDark,
    error = Red400,
)

private val LightColors = lightColorScheme(
    primary = Cyan500,
    onPrimary = Color.White,
    secondary = Teal400,
    tertiary = Gold400,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFE8F0F6),
    onSurfaceVariant = LightSecondary,
    error = Red400,
)

@Composable
fun WAAlOthmanyTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit,
) {
    val dark = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = WATypography,
        content = content,
    )
}
