package com.developermx.lockly.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Cyan80,
    onPrimary = Blue80,
    secondary = Green80,
    onSecondary = Blue80,
    tertiary = Orange80,
    onTertiary = Blue80,
    background = Blue80,
    onBackground = OffWhite,
    surface = BlueGrey80,
    onSurface = OffWhite,
    secondaryContainer = Cyan80,
    onSecondaryContainer = Blue80,
    tertiaryContainer = DarkCyan80
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    onPrimary = BlueGrey40,
    secondary = Green40,
    onSecondary = BlueGrey40,
    tertiary = Orange40,
    onTertiary = BlueGrey40,
    background = Blue40,
    onBackground = Grey40,
    surface = BlueGrey40,
    onSurface = Grey40,
    secondaryContainer = Cyan40,
    onSecondaryContainer = BlueGrey40,
    tertiaryContainer = DarkCyan40
)

@Composable
fun LocklyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}