package com.example.data

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ColorHarmonyAnalysis(
    val score: Int,
    val scheme: String,
    val note: String,
    val seasonMatchBadge: String,
    val proportionNote: String
)

data class ClosetAnalyticsMetrics(
    val totalValuation: Double,
    val averageCpw: Double,
    val mvpItem: GarmentItem?,
    val mostWorn: GarmentItem?,
    val leastWorn: GarmentItem?,
    val versatilityScore: Int,
    val neutralPercent: Int,
    val accentPercent: Int,
    val categoryDistribution: List<Pair<String, Int>>,
    val colorDistribution: List<Triple<String, String, Int>>,
    val staleOrUnwornItems: List<GarmentItem>,
    val spentThisMonth: Double
)

object ColorTheoryEngine {

    data class Hsl(val h: Int, val s: Int, val l: Int)

    fun hexToRgb(hex: String?): Triple<Int, Int, Int> {
        val cleaned = (hex ?: "#141414").trim().removePrefix("#")
        if (cleaned.length < 6) return Triple(20, 20, 20)
        return try {
            val r = cleaned.substring(0, 2).toInt(16)
            val g = cleaned.substring(2, 4).toInt(16)
            val b = cleaned.substring(4, 6).toInt(16)
            Triple(r, g, b)
        } catch (_: Exception) {
            Triple(20, 20, 20)
        }
    }

    fun hexToHsl(hex: String?): Hsl {
        val (r255, g255, b255) = hexToRgb(hex)
        val r = r255 / 255f
        val g = g255 / 255f
        val b = b255 / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        var h = 0f
        var s = 0f
        if (maxC != minC) {
            val d = maxC - minC
            s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
            h = when (maxC) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
                g -> ((b - r) / d + 2f) * 60f
                else -> ((r - g) / d + 4f) * 60f
            }
        }
        return Hsl(h.roundToInt(), (s * 100f).roundToInt(), (l * 100f).roundToInt())
    }

    fun isLightColor(hex: String?): Boolean {
        val (r, g, b) = hexToRgb(hex)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 180.0
    }

    fun getColorFamily(hex: String?): String {
        val hsl = hexToHsl(hex)
        if (hsl.s < 16) {
            return when {
                hsl.l < 25 -> "Black/Charcoal"
                hsl.l > 78 -> "White/Ivory"
                else -> "Grey/Stone"
            }
        }
        return when {
            hsl.h in 18..56 -> "Earth/Camel"
            hsl.h in 185..255 -> "Blue/Indigo"
            hsl.h in 75..165 -> "Olive/Green"
            else -> "Accent"
        }
    }

    fun colorDistance(hexA: String?, hexB: String?): Double {
        val (r1, g1, b1) = hexToRgb(hexA)
        val (r2, g2, b2) = hexToRgb(hexB)
        return kotlin.math.sqrt(
            ((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble()
        )
    }

    fun evaluateOutfitHarmony(
        items: List<GarmentItem>,
        profile: UserProfile?
    ): ColorHarmonyAnalysis {
        if (items.isEmpty()) {
            return ColorHarmonyAnalysis(
                score = 0,
                scheme = "Empty Canvas",
                note = "Select garments across slots to inspect live color harmony.",
                seasonMatchBadge = profile?.seasonPalette ?: "Style DNA Ready",
                proportionNote = "Add a top and bottom to check silhouette balance."
            )
        }

        val hsls = items.map { hexToHsl(it.colorHex) }
        val chromatic = hsls.filter { it.s >= 18 && it.l in 15..88 }

        var scheme: String
        var score: Int
        var note: String

        when (chromatic.size) {
            0 -> {
                val spread = (hsls.maxOfOrNull { it.l } ?: 0) - (hsls.minOfOrNull { it.l } ?: 0)
                scheme = "Monochrome"
                score = if (spread > 35) 96 else 92
                note = if (spread > 35) {
                    "High-contrast architectural monochrome with crisp depth."
                } else {
                    "Sleek tonal monochrome with quiet-luxury restraint."
                }
            }
            1 -> {
                scheme = "Neutral-Pop"
                score = 95
                note = "Single focal tone anchored cleanly by editorial neutrals."
            }
            else -> {
                val hues = chromatic.map { it.h }
                var maxDiff = 0
                for (i in hues.indices) {
                    for (j in i + 1 until hues.size) {
                        val d = abs(hues[i] - hues[j])
                        maxDiff = max(maxDiff, min(d, 360 - d))
                    }
                }
                when {
                    maxDiff <= 45 -> {
                        scheme = "Analogous"
                        score = 94
                        note = "Neighboring tonal hues create a fluid, elongated silhouette."
                    }
                    maxDiff >= 135 -> {
                        scheme = "Complementary"
                        score = 92
                        note = "Confident runway contrast balanced by neutral anchors."
                    }
                    else -> {
                        scheme = "Editorial Triad"
                        score = 88
                        note = "Expressive multi-tone mix — keep footwear and bag understated."
                    }
                }
            }
        }

        // Seasonal Color Palette alignment check
        var seasonMatchBadge = "Neutral Foundation"
        if (profile != null) {
            val allGood = profile.powerColors + profile.neutralColors
            val hitsAvoid = items.any { g ->
                profile.avoidColors.any { avoid -> colorDistance(g.colorHex, avoid) < 55.0 }
            }
            val matchesGood = items.count { g ->
                allGood.any { good -> colorDistance(g.colorHex, good) < 90.0 }
            }
            seasonMatchBadge = when {
                hitsAvoid -> "Watch Avoid Tone · ${profile.seasonPalette}"
                matchesGood >= (items.size + 1) / 2 -> "✓ ${profile.seasonPalette} Match"
                else -> "Balanced for ${profile.seasonPalette}"
            }
            if (hitsAvoid) score = max(72, score - 8)
            else if (matchesGood >= (items.size + 1) / 2) score = min(99, score + 2)
        }

        // Proportion analysis
        val hasOuter = items.any { it.category == "Outerwear" }
        val hasTop = items.any { it.category == "Tops" }
        val hasBottom = items.any { it.category == "Bottoms" }
        val hasShoes = items.any { it.category == "Footwear" }
        val proportionNote = when {
            hasOuter && hasTop && hasBottom && hasShoes ->
                "Complete 3rd-piece vertical frame (${profile?.silhouette ?: "Tailored"} balance)."
            hasTop && hasBottom && hasShoes ->
                "Clean 2-piece silhouette — add outerwear or a belt to sharpen waist definition."
            hasTop && hasBottom ->
                "Core ratio set — pair with footwear to anchor visual weight."
            else ->
                "Building ensemble — add complementary layers for balanced proportions."
        }

        if (items.size >= 4) score = min(99, score + 1)

        return ColorHarmonyAnalysis(
            score = score.coerceIn(0, 100),
            scheme = scheme,
            note = note,
            seasonMatchBadge = seasonMatchBadge,
            proportionNote = proportionNote
        )
    }

    fun computeClosetAnalytics(
        wardrobe: List<GarmentItem>,
        nowMs: Long = System.currentTimeMillis()
    ): ClosetAnalyticsMetrics {
        val totalValuation = wardrobe.sumOf { it.price }
        val worn = wardrobe.filter { it.wears > 0 }
        val averageCpw = if (worn.isNotEmpty()) {
            worn.sumOf { it.price / it.wears.toDouble() } / worn.size.toDouble()
        } else 0.0

        val mvp = worn.minByOrNull { it.price / it.wears.toDouble() }
        val mostWorn = wardrobe.maxByOrNull { it.wears }
        val leastWorn = wardrobe.minByOrNull { it.wears }

        val countsByCat = GarmentCategory.allLabels.associateWith { cat ->
            wardrobe.count { it.category == cat }
        }
        val combos = max(1, countsByCat["Tops"] ?: 0) *
            max(1, countsByCat["Bottoms"] ?: 0) *
            max(1, countsByCat["Footwear"] ?: 0) *
            max(1, countsByCat["Outerwear"] ?: 0)
        val versatility = min(99, (52.0 + min(47.0, (ln(combos.toDouble() + 1.0) / ln(2.0)) * 7.2)).roundToInt())

        val neutralCount = wardrobe.count { hexToHsl(it.colorHex).s < 26 }
        val neutralPct = if (wardrobe.isNotEmpty()) ((neutralCount * 100.0) / wardrobe.size).roundToInt() else 70

        val catDist = countsByCat.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val colorMap = linkedMapOf<String, Pair<String, Int>>()
        wardrobe.forEach { item ->
            val prev = colorMap[item.colorHex]
            colorMap[item.colorHex] = (prev?.first ?: item.colorName) to ((prev?.second ?: 0) + 1)
        }
        val colorDist = colorMap.entries
            .sortedByDescending { it.value.second }
            .map { Triple(it.key, it.value.first, it.value.second) }

        val stale = wardrobe.filter { it.isUnwornOrStale90Days(nowMs) }
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val spentThisMonth = wardrobe
            .filter { it.purchasedEpochMs > 0L && (nowMs - it.purchasedEpochMs) <= thirtyDaysMs }
            .sumOf { it.price }

        return ClosetAnalyticsMetrics(
            totalValuation = totalValuation,
            averageCpw = averageCpw,
            mvpItem = mvp,
            mostWorn = mostWorn,
            leastWorn = leastWorn,
            versatilityScore = versatility,
            neutralPercent = neutralPct,
            accentPercent = (100 - neutralPct).coerceIn(0, 100),
            categoryDistribution = catDist,
            colorDistribution = colorDist,
            staleOrUnwornItems = stale,
            spentThisMonth = spentThisMonth
        )
    }

    /**
     * Checks if an outfit with overlapping items or same lookId was already scheduled within +/- 14 days.
     */
    fun findRepeatWearAlert(
        targetDateKey: String,
        candidateItemIds: List<String>,
        candidateLookId: String?,
        entries: List<CalendarEntry>
    ): String? {
        if (candidateItemIds.isEmpty()) return null
        val targetDays = dateKeyToEpochDay(targetDateKey) ?: return null
        val candidateSet = candidateItemIds.toSet()

        for (entry in entries) {
            if (entry.dateKey == targetDateKey) continue
            val entryDays = dateKeyToEpochDay(entry.dateKey) ?: continue
            val gap = abs(targetDays - entryDays)
            if (gap in 1..14) {
                val sameLook = !candidateLookId.isNullOrBlank() && candidateLookId == entry.lookId
                val overlap = entry.itemIds.count { it in candidateSet }
                if (sameLook || overlap >= max(2, candidateSet.size - 1)) {
                    val whoNote = if (entry.whoYouSaw.isNotBlank()) " (with ${entry.whoYouSaw})" else ""
                    return "Repeat-Wear Alert: Similar outfit logged on ${entry.dateKey} ($gap days apart)$whoNote."
                }
            }
        }
        return null
    }

    private fun dateKeyToEpochDay(key: String): Long? {
        val parts = key.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        // Simple monotonic day count adequate for +/- 14 day gap detection
        return (y.toLong() * 365L) + (m.toLong() * 30L) + d.toLong()
    }
}
