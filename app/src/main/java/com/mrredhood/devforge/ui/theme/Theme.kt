package com.mrredhood.devforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mrredhood.devforge.core.settings.DensityMode
import com.mrredhood.devforge.core.settings.ThemeMode

private data class Palette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val container: Color,
    val on: Color,
)

private fun palette(theme: ThemeMode): Palette = when (theme) {
    ThemeMode.NORD -> Palette(Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFFA3BE8C), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E), Color(0xFFE5E9F0))
    ThemeMode.OCEAN -> Palette(Color(0xFF67E8F9), Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF071923), Color(0xFF0C2531), Color(0xFF103344), Color(0xFFE6FFFD))
    ThemeMode.FOREST -> Palette(Color(0xFF8BE28B), Color(0xFF5FD19A), Color(0xFFE4D27A), Color(0xFF0B1711), Color(0xFF14251B), Color(0xFF1C3325), Color(0xFFE7F6E9))
    ThemeMode.AMETHYST -> Palette(Color(0xFFC4A7FF), Color(0xFF9F8CFF), Color(0xFFFF9AD7), Color(0xFF130D1C), Color(0xFF21162E), Color(0xFF2C1D3C), Color(0xFFF5EAFF))
    ThemeMode.SUNSET -> Palette(Color(0xFFFFB38A), Color(0xFFFF8A65), Color(0xFFFFD166), Color(0xFF1E1010), Color(0xFF301817), Color(0xFF3E201E), Color(0xFFFFEEE6))
    ThemeMode.CYBER -> Palette(Color(0xFF00F5D4), Color(0xFF00BBF9), Color(0xFFF15BB5), Color(0xFF070912), Color(0xFF0D1020), Color(0xFF141833), Color(0xFFE8F7FF))
    ThemeMode.DRACULA -> Palette(Color(0xFFBD93F9), Color(0xFF8BE9FD), Color(0xFFFF79C6), Color(0xFF282A36), Color(0xFF343746), Color(0xFF44475A), Color(0xFFF8F8F2))
    ThemeMode.MONOKAI -> Palette(Color(0xFFA6E22E), Color(0xFF66D9EF), Color(0xFFF92672), Color(0xFF272822), Color(0xFF30312B), Color(0xFF3E4038), Color(0xFFF8F8F2))
    ThemeMode.SOLARIZED -> Palette(Color(0xFF268BD2), Color(0xFF2AA198), Color(0xFFB58900), Color(0xFF002B36), Color(0xFF073642), Color(0xFF0A4653), Color(0xFFE8F1F2))
    ThemeMode.OBSIDIAN, ThemeMode.DARK, ThemeMode.SYSTEM -> Palette(Color(0xFF7C6CFF), Color(0xFF4DD9E8), Color(0xFFFF8A65), Color(0xFF090B12), Color(0xFF121621), Color(0xFF181D2A), Color(0xFFF3F5FB))
    ThemeMode.LIGHT -> Palette(Color(0xFF5E4DE3), Color(0xFF087F8D), Color(0xFFB84617), Color(0xFFF7F7FB), Color(0xFFFFFFFF), Color(0xFFF0F0F7), Color(0xFF17181C))
}

@Composable
fun DevForgeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    densityMode: DensityMode = DensityMode.COMFORTABLE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        else -> true
    }
    val p = palette(themeMode)
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.primary,
            secondary = p.secondary,
            tertiary = p.tertiary,
            background = p.background,
            surface = p.surface,
            surfaceContainer = p.container,
            onBackground = p.on,
            onSurface = p.on,
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            secondary = p.secondary,
            tertiary = p.tertiary,
            background = p.background,
            surface = p.surface,
            surfaceContainer = p.container,
            onBackground = p.on,
            onSurface = p.on,
        )
    }
    val base = LocalDensity.current
    val scale = if (densityMode == DensityMode.COMPACT) 0.92f else 1f
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
