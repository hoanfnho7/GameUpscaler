package com.nho.upscaler.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VoltLime,
    onPrimary = OnVoltLime,
    primaryContainer = VoltLimeContainer,
    onPrimaryContainer = VoltLime,
    secondary = TechAmber,
    onSecondary = TechAmberDark,
    secondaryContainer = Color(0x26EEC14A),
    onSecondaryContainer = TechAmber,
    tertiary = TechEmerald,
    onTertiary = TechEmeraldDark,
    tertiaryContainer = Color(0x266DFE9C),
    onTertiaryContainer = TechEmerald,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceLow,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceLowest,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = TechRed,
    onError = DarkBackground,
    errorContainer = TechRedDim,
    onErrorContainer = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = LightVoltLime,
    onPrimary = Color.White,
    primaryContainer = LightVoltContainer,
    onPrimaryContainer = LightVoltLime,
    secondary = TechAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0x26EEC14A),
    onSecondaryContainer = TechAmberDark,
    tertiary = TechEmerald,
    onTertiary = Color.White,
    tertiaryContainer = Color(0x266DFE9C),
    onTertiaryContainer = TechEmeraldDark,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = TechRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = TechRedDim
)

@Composable
fun GameUpscalerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = if (darkTheme) DarkSurfaceLowest.toArgb() else LightSurfaceContainer.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
