package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Criel — Airy Luxury Palette (Powder Blue, Sky Blue, Porcelain Off-White & Pure White)
val Alabaster = Color(0xFFF6F9FC)       // Crisp porcelain off-white background
val Ivory = Color(0xFFEAF2FA)           // Soft powder-blue surface tint
val Taupe = Color(0xFFD2E3F4)           // Delicate sky-mist border
val WarmStone = Color(0xFFB5CFE9)       // Powder-blue outline accent
val MutedInk = Color(0xFF5A7188)        // Refined slate-blue secondary text
val Obsidian = Color(0xFF1B2B3E)        // Deep midnight-slate primary ink

val ObsidianBg = Color(0xFF0B0B0D)      // Deep obsidian dark background
val ObsidianSurface = Color(0xFF141417) // Obsidian card surface
val ObsidianElevated = Color(0xFF1C1C21)// Elevated charcoal surface
val ObsidianBorder = Color(0xFF2A2A31)  // Crisp obsidian divider
val IvoryText = Color(0xFFF6F9FC)       // Crisp porcelain off-white text
val SilverMuted = Color(0xFFA4AAB2)     // Neutral cool silver secondary text

val Champagne = Color(0xFF6DA6DA)       // Luminous sky blue accent
val ChampagneLight = Color(0xFFA9CFEF)  // Airy powder blue highlight
val ChampagneDark = Color(0xFF3A72A8)   // Refined French sky blue (WCAG AA on white)
val ChampagneWash = Color(0x296DA6DA)   // Soft translucent sky wash

val EditorialEmerald = Color(0xFF2E7D6B)
val EditorialEmeraldLight = Color(0xFF5BB8A2)
val EditorialAmber = Color(0xFFC2822A)
val EditorialCrimson = Color(0xFFB5434F)
val EditorialCrimsonLight = Color(0xFFEA848E)
val EditorialRose = Color(0xFFB5434F)

fun parseHexColor(hex: String?, fallback: Color = Obsidian): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val parsed = when (cleaned.length) {
            6 -> ("FF$cleaned").toLong(16)
            8 -> cleaned.toLong(16)
            3 -> {
                val expanded = cleaned.map { "$it$it" }.joinToString("")
                ("FF$expanded").toLong(16)
            }
            else -> return fallback
        }
        Color(parsed)
    } catch (_: Exception) {
        fallback
    }
}
