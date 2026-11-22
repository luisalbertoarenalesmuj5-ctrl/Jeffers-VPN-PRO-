package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemePreset

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JeffersVpnTheme(
    preset: ThemePreset = ThemePreset.CHRISTMAS_LUXURY,
    content: @Composable () -> Unit
) {
    val primaryColor = Color(preset.primaryHex)
    val accentColor = Color(preset.accentHex)
    val bgColor = Color(preset.backgroundHex)
    val surfaceColor = Color(preset.surfaceHex)
    val errorColor = Color(preset.errorHex)
    val outlineColor = if (preset.isChristmas) {
        primaryColor.copy(alpha = 0.24f)
    } else {
        OutlineSubtle
    }
    val elevatedSurface = if (preset.isChristmas) {
        Color(0xFF0E251C)
    } else {
        ObsidianCardElevated
    }

    val dynamicScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = ObsidianVoid,
        primaryContainer = primaryColor.copy(alpha = 0.16f),
        onPrimaryContainer = primaryColor,
        secondary = accentColor,
        onSecondary = ObsidianVoid,
        secondaryContainer = accentColor.copy(alpha = 0.16f),
        onSecondaryContainer = accentColor,
        tertiary = if (preset.isChristmas) ChampagneGold else PulseAmber,
        onTertiary = ObsidianVoid,
        background = bgColor,
        onBackground = TextPrimary,
        surface = surfaceColor,
        onSurface = TextPrimary,
        surfaceVariant = surfaceColor,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = bgColor,
        surfaceContainerLow = surfaceColor,
        surfaceContainer = surfaceColor,
        surfaceContainerHigh = elevatedSurface,
        outline = outlineColor,
        outlineVariant = Color(0xFF141B26),
        error = errorColor,
        onError = TextPrimary,
        errorContainer = errorColor.copy(alpha = 0.18f),
        onErrorContainer = errorColor
    )

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
