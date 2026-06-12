package com.chatmod.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(red = 0.13f, green = 0.38f, blue = 0.80f),
    onPrimary = Color.White,
    secondary = Color(red = 0.00f, green = 0.52f, blue = 0.41f),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(red = 0.09f, green = 0.12f, blue = 0.18f),
    surface = Color(red = 0.96f, green = 0.97f, blue = 0.99f),
    onSurface = Color(red = 0.09f, green = 0.12f, blue = 0.18f),
    error = Color(red = 0.76f, green = 0.18f, blue = 0.12f),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(red = 0.47f, green = 0.65f, blue = 0.96f),
    onPrimary = Color(red = 0.03f, green = 0.06f, blue = 0.11f),
    secondary = Color(red = 0.46f, green = 0.85f, blue = 0.72f),
    onSecondary = Color(red = 0.03f, green = 0.07f, blue = 0.06f),
    background = Color(red = 0.05f, green = 0.05f, blue = 0.06f),
    onBackground = Color(red = 0.92f, green = 0.94f, blue = 0.97f),
    surface = Color(red = 0.09f, green = 0.10f, blue = 0.13f),
    onSurface = Color(red = 0.92f, green = 0.94f, blue = 0.97f),
    error = Color(red = 0.95f, green = 0.42f, blue = 0.32f),
    onError = Color(red = 0.12f, green = 0.02f, blue = 0.02f)
)

private val HighContrastLightColors = lightColorScheme(
    primary = Color(red = 0.00f, green = 0.18f, blue = 0.72f),
    onPrimary = Color.White,
    primaryContainer = Color(red = 0.82f, green = 0.88f, blue = 1.00f),
    onPrimaryContainer = Color.Black,
    secondary = Color(red = 0.00f, green = 0.38f, blue = 0.30f),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(red = 0.90f, green = 0.92f, blue = 0.96f),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    error = Color(red = 0.68f, green = 0.00f, blue = 0.00f),
    onError = Color.White
)

private val HighContrastDarkColors = darkColorScheme(
    primary = Color(red = 0.72f, green = 0.84f, blue = 1.00f),
    onPrimary = Color.Black,
    primaryContainer = Color(red = 0.05f, green = 0.16f, blue = 0.42f),
    onPrimaryContainer = Color.White,
    secondary = Color(red = 0.62f, green = 1.00f, blue = 0.86f),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(red = 0.10f, green = 0.11f, blue = 0.14f),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    error = Color(red = 1.00f, green = 0.72f, blue = 0.66f),
    onError = Color.Black
)

@Composable
fun ChatModTheme(
    useDynamicColor: Boolean = true,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme: ColorScheme =
        if (highContrast) {
            if (darkTheme) HighContrastDarkColors else HighContrastLightColors
        } else if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) {
            DarkColors
        } else {
            LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatModTypography,
        content = content
    )
}
