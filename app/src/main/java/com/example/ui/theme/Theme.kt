package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AkritiIndigoPrimary,
    onPrimary = AkritiDarkTextPrimary,
    primaryContainer = AkritiIndigoContainer,
    onPrimaryContainer = AkritiDarkTextPrimary,
    secondary = AkritiCyanListening,
    onSecondary = AkritiDarkBackground,
    tertiary = AkritiEmeraldSpeaking,
    onTertiary = AkritiDarkBackground,
    error = AkritiRoseError,
    onError = AkritiDarkTextPrimary,
    background = AkritiDarkBackground,
    onBackground = AkritiDarkTextPrimary,
    surface = AkritiDarkSurface,
    onSurface = AkritiDarkTextPrimary,
    surfaceVariant = AkritiDarkSurfaceVariant,
    onSurfaceVariant = AkritiDarkTextSecondary,
    outline = AkritiDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = AkritiIndigoPrimary,
    onPrimary = AkritiLightSurface,
    primaryContainer = AkritiIndigoContainer,
    onPrimaryContainer = AkritiLightSurface,
    secondary = AkritiCyanListening,
    onSecondary = AkritiLightSurface,
    tertiary = AkritiEmeraldSpeaking,
    onTertiary = AkritiLightSurface,
    error = AkritiRoseError,
    onError = AkritiLightSurface,
    background = AkritiLightBackground,
    onBackground = AkritiLightTextPrimary,
    surface = AkritiLightSurface,
    onSurface = AkritiLightTextPrimary,
    surfaceVariant = AkritiLightSurfaceVariant,
    onSurfaceVariant = AkritiLightTextSecondary,
    outline = AkritiLightBorder
)

@Composable
fun AkritiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
