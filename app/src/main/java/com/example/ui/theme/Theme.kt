package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.AppTheme
import com.example.util.SettingsManager

private val CyberColorScheme = darkColorScheme(
    primary = CyberPurple,
    onPrimary = Color.White,
    secondary = CyberCyan,
    onSecondary = Color.Black,
    tertiary = Color(0xFFFD79A8),
    background = CyberBg,
    onBackground = Color(0xFFF1F2F6),
    surface = CyberSurface,
    onSurface = Color(0xFFF1F2F6),
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = Color(0xFFA4B0BE),
    outline = CyberBorder,
)

private val MatrixColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = Color.Black,
    secondary = MatrixCyan,
    onSecondary = Color.Black,
    tertiary = MatrixYellow,
    background = MatrixBlack,
    onBackground = MatrixGreen,
    surface = MatrixSurface,
    onSurface = MatrixGreen,
    surfaceVariant = MatrixSurfaceVariant,
    onSurfaceVariant = Color(0xFF70A070),
    outline = MatrixBorder,
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    secondary = Color(0xFF10B981),
    onSecondary = Color.Black,
    tertiary = Color(0xFFA855F7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2E2E2E),
)

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF00E5FF),
    onSecondary = Color.Black,
    tertiary = Color(0xFF70A1FF),
    background = OceanBg,
    onBackground = Color(0xFFE0E6ED),
    surface = OceanSurface,
    onSurface = Color(0xFFE0E6ED),
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = Color(0xFF8892B0),
    outline = OceanBorder,
)

private val CrimsonColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B81),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFA502),
    background = CrimsonBg,
    onBackground = Color(0xFFF1F2F6),
    surface = CrimsonSurface,
    onSurface = Color(0xFFF1F2F6),
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = Color(0xFFA4B0BE),
    outline = CrimsonBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = AccentGreen,
    onSecondary = Color.Black,
    tertiary = PrimaryBlueDark,
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightBorder,
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    SettingsManager.init(context)
    val settings by SettingsManager.settings.collectAsState()

    val colorScheme: ColorScheme = when (settings.theme) {
        AppTheme.CYBERPUNK_GREEN -> MatrixColorScheme
        AppTheme.AMOLED_DARK -> AmoledColorScheme
        AppTheme.CYBER_PURPLE -> CyberColorScheme
        AppTheme.OCEAN_BLUE -> OceanColorScheme
        AppTheme.CRIMSON_NIGHT -> CrimsonColorScheme
        AppTheme.MODERN_LIGHT -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

