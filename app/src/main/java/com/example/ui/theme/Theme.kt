package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AtelierLightColorScheme = lightColorScheme(
    primary = ChampagneDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBF8),
    onPrimaryContainer = Obsidian,
    secondary = Champagne,
    onSecondary = Color.White,
    secondaryContainer = Ivory,
    onSecondaryContainer = ChampagneDark,
    tertiary = EditorialEmerald,
    onTertiary = Color.White,
    background = Alabaster,
    onBackground = Obsidian,
    surface = Color.White,
    onSurface = Obsidian,
    surfaceVariant = Ivory,
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Alabaster,
    surfaceContainer = Ivory,
    surfaceContainerHigh = Color(0xFFDFECF8),
    outline = Taupe,
    outlineVariant = WarmStone,
    error = EditorialCrimson,
    onError = Color.White
)

private val AtelierDarkColorScheme = darkColorScheme(
    primary = ChampagneLight,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF23272E),
    onPrimaryContainer = ChampagneLight,
    secondary = Champagne,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF1E2025),
    onSecondaryContainer = ChampagneLight,
    tertiary = EditorialEmeraldLight,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = SilverMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = Color(0xFF232329),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF383842),
    error = EditorialCrimsonLight,
    onError = ObsidianBg
)

val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AtelierTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AtelierDarkColorScheme else AtelierLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AtelierTypography,
        shapes = AtelierShapes,
        content = content
    )
}
