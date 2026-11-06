package com.simpletools.area.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.simpletools.area.core.settings.ThemeMode

val Blue = Color(0xFF1565C0)
val BlueLight = Color(0xFFD6E4FF)
val Gray50 = Color(0xFFF3F5F8)

private val Light = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    primaryContainer = BlueLight, onPrimaryContainer = Color(0xFF0B2E63),
    secondary = Color(0xFF4A6FA5), secondaryContainer = Color(0xFFE3EBF7),
    background = Gray50, surface = Color.White, surfaceVariant = Color(0xFFE6EBF2),
    onSurface = Color(0xFF1B2330), onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8), error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF062B5E),
    primaryContainer = Color(0xFF123A73), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFA9C1E8), secondaryContainer = Color(0xFF22344F),
    background = Color(0xFF0F1722), surface = Color(0xFF17212E), surfaceVariant = Color(0xFF243244),
    onSurface = Color(0xFFE5EAF2), onSurfaceVariant = Color(0xFFAAB6C8),
    outline = Color(0xFF64748B), error = Color(0xFFF2B8B5),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AreaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light, content = content)
}
