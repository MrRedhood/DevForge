package com.mrredhood.devforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.mrredhood.devforge.core.settings.DensityMode
import com.mrredhood.devforge.core.settings.ThemeMode
import androidx.compose.ui.graphics.Color

private val ForgeIndigo = Color(0xFF7C6CFF)
private val ForgeCyan = Color(0xFF4DD9E8)
private val ForgeInk = Color(0xFF090B12)
private val ForgePanel = Color(0xFF121621)
private val ForgePanel2 = Color(0xFF181D2A)

private val DarkScheme = darkColorScheme(
    primary = ForgeIndigo,
    secondary = ForgeCyan,
    tertiary = Color(0xFFFF8A65),
    background = ForgeInk,
    surface = ForgePanel,
    surfaceContainer = ForgePanel2,
    onPrimary = Color.White,
    onSecondary = ForgeInk,
    onBackground = Color(0xFFF3F5FB),
    onSurface = Color(0xFFF3F5FB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5E4DE3),
    secondary = Color(0xFF087F8D),
    tertiary = Color(0xFFB84617),
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F0F7),
)

@Composable
fun DevForgeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    densityMode: DensityMode = DensityMode.COMFORTABLE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val baseDensity = LocalDensity.current
    val densityScale = if (densityMode == DensityMode.COMPACT) 0.92f else 1f
    val density = Density(baseDensity.density * densityScale, baseDensity.fontScale)
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}
