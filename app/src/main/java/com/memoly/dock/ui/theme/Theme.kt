package com.memoly.dock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Memoly dark color scheme — the default premium experience.
 */
private val MemolyDarkColorScheme = darkColorScheme(
    primary = MemolyPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = MemolyPrimaryContainer,
    onPrimaryContainer = MemolyOnPrimaryContainer,
    secondary = MemolySecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = MemolySecondaryContainer,
    onSecondaryContainer = MemolyOnSecondaryContainer,
    tertiary = MemolyTertiaryDark,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = MemolyError,
    onError = Color.White
)

/**
 * Memoly light color scheme — clean and minimal.
 */
private val MemolyLightColorScheme = lightColorScheme(
    primary = MemolyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E5FF),
    onPrimaryContainer = Color(0xFF1A1640),
    secondary = MemolySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F5F2),
    onSecondaryContainer = Color(0xFF0D2B29),
    tertiary = MemolyTertiary,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = MemolyError,
    onError = Color.White
)

@Composable
fun MemolyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MemolyDarkColorScheme
        else -> MemolyLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MemolyTypography,
        content = content
    )
}