package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// =========================================================================
// ENUMS & CONSTANTS
// =========================================================================

enum class GarmentCategory(val label: String) {
    OUTERWEAR("Outerwear"),
    TOPS("Tops"),
    BOTTOMS("Bottoms"),
    FOOTWEAR("Footwear"),
    BAGS("Bags"),
    ACCESSORIES("Accessories");

    companion object {
        val allLabels = entries.map { it.label }
        fun fromLabel(raw: String?): GarmentCategory {
            if (raw.isNullOrBlank()) return TOPS
            val normalized = raw.trim().lowercase()
            return when {
                normalized.contains("outer") || normalized.contains("coat") || normalized.contains("jacket") || normalized.contains("blazer") -> OUTERWEAR
                normalized.contains("top") || normalized.contains("shirt") || normalized.contains("knit") || normalized.contains("sweater") || normalized.contains("tee") -> TOPS
                normalized.contains("bottom") || normalized.contains("pant") || normalized.contains("trouser") || normalized.contains("denim") || normalized.contains("skirt") -> BOTTOMS
                normalized.contains("foot") || normalized.contains("shoe") || normalized.contains("boot") || normalized.contains("sneaker") || normalized.contains("loafer") -> FOOTWEAR
                normalized.contains("bag") || normalized.contains("tote") || normalized.contains("clutch") -> BAGS
                normalized.contains("access") || normalized.contains("belt") || normalized.contains("scarf") || normalized.contains("watch") -> ACCESSORIES
                else -> entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) } ?: TOPS
            }
        }
    }
}

enum class GarmentStatus(val label: String) {
    AVAILABLE("Available"),
    IN_LAUNDRY("In Laundry"),
    AT_TAILOR("At Tailor"),
    OFF_SEASON("Off-Season");

    companion object {
        fun safeValueOf(raw: String?): GarmentStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: AVAILABLE
    }
}

enum class WeatherCondition(val id: String, val label: String) {
    SUNNY("sunny", "Sunny"),
    CLOUDY("cloudy", "Cloudy"),
    RAINY("rainy", "Rainy"),
    SNOWY("snowy", "Snowy"),
    WINDY("windy", "Windy"),
    HUMID("humid", "Humid");

    companion object {
        fun fromId(id: String): WeatherCondition =
            entries.firstOrNull { it.id == id } ?: CLOUDY
    }
}

enum class DressCodeMode(val label: String, val rules: String) {
    SMART_CASUAL("Smart Casual", "Structured tailoring paired with relaxed luxury basics; polished leather footwear."),
    BLACK_TIE("Black Tie", "Floor-length evening silhouette or sharp obsidian tailoring; minimal precious hardware."),
    WEDDING_GUEST("Wedding Guest", "Elevated cocktail tailoring in rich seasonal tones; strictly avoid optic white."),
    JOB_INTERVIEW("Job Interview", "Architectural blazer, crisp tonal layers, closed-toe footwear, zero visual distraction."),
    FIRST_DATE("First Date", "Effortless tactile fabrics (silk, cashmere, suede) with intentional proportions."),
    TRAVEL("Travel", "Wrinkle-resistant breathable natural fibers, layered outerwear, walk-all-day footwear."),
    GYM_TO_DINNER("Gym-to-Dinner", "Clean monochrome base elevated instantly with a structured coat and leather tote.")
}

enum class LookbookExportTemplate(val label: String, val width: Int, val height: Int, val subtitle: String) {
    FEED_CARD("1080×1350 Feed Card", 1080, 1350, "4:5 Editorial Portrait"),
    IG_STORY("1080×1920 IG Story", 1080, 1920, "9:16 Full-Bleed Runway"),
    MINIMALIST_GRID("Minimalist Grid Sheet", 1200, 1200, "1:1 Spec Archive Sheet")
}

val STYLE_ARCHETYPES = listOf(
    "Minimalist",
    "Quiet Luxury",
    "Smart Casual",
    "Streetwear",
    "Avant-Garde",
    "Classic Editorial"
)

val SILHOUETTE_OPTIONS = listOf("Tailored", "Relaxed", "Oversized")

val SEASON_PALETTES_12 = listOf(
    "Deep Autumn", "Warm Autumn", "Soft Autumn",
    "Cool Winter", "Deep Winter", "Bright Winter",
    "Light Spring", "Warm Spring", "Bright Spring",
    "Light Summer", "Cool Summer", "Soft Summer"
)

val DEFAULT_COLLECTIONS = listOf("All Looks", "Autumn Work", "Weekend", "333 Capsule", "Evening Edit")

// =========================================================================
// FIRESTORE MODELS (Strictly aligned with firestore.rules & blueprint)
// =========================================================================

fun formatInr(amount: Double): String = formatInr(amount.toInt())

fun formatInr(amount: Int): String {
    val negative = amount < 0
    val absStr = kotlin.math.abs(amount).toString()
    val formatted = if (absStr.length <= 3) {
        absStr
    } else {
        val lastThree = absStr.takeLast(3)
        val rest = absStr.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        "$grouped,$lastThree"
    }
    return if (negative) "-₹$formatted" else "₹$formatted"
}

enum class AiCritiqueTone(val label: String, val shortLabel: String, val subtitle: String) {
    BRUTALLY_HONEST(
        "Brutally Honest",
        "Brutal",
        "Zero sugarcoating — calls out clashing pieces, weak fits, and exact upgrades to buy."
    ),
    BALANCED(
        "Balanced Editor",
        "Balanced",
        "Direct, constructive fashion-editor critique with practical upgrades."
    ),
    GENTLE(
        "Supportive",
        "Gentle",
        "Encouraging styling guidance with softer suggestions."
    );

    companion object {
        fun safeValueOf(raw: String?): AiCritiqueTone =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BRUTALLY_HONEST
    }
}

enum class StyleGender(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val promptDirective: String,
    val storePrefix: String
) {
    MEN(
        label = "Men's Style",
        shortLabel = "Men",
        subtitle = "Menswear cuts, silhouettes, shoes, and Indian men's store links.",
        promptDirective = "GENDER & DEPARTMENT (STRICT): Men's fashion ONLY. Recommend strictly menswear cuts, garments, proportions, and footwear (tees, shirts, polos, chinos, trousers, jeans, jackets, blazers, sneakers, loafers, boots). NEVER suggest dresses, skirts, heels, blouses, or womenswear.",
        storePrefix = "men"
    ),
    WOMEN(
        label = "Women's Style",
        shortLabel = "Women",
        subtitle = "Womenswear cuts, silhouettes, shoes, and Indian women's store links.",
        promptDirective = "GENDER & DEPARTMENT (STRICT): Women's fashion ONLY. Recommend strictly womenswear cuts, garments, proportions, and footwear (tops, blouses, tees, trousers, jeans, skirts, dresses, blazers, sneakers, flats, loafers, heels, bags). NEVER suggest exclusively menswear cuts.",
        storePrefix = "women"
    ),
    UNISEX(
        label = "Unisex / All",
        shortLabel = "Unisex",
        subtitle = "Gender-neutral staples and relaxed everyday silhouettes.",
        promptDirective = "GENDER & DEPARTMENT: Gender-neutral / unisex styling. Focus on versatile unisex staples (heavyweight tees, overshirts, relaxed trousers, denim, knitwear, minimalist sneakers, totes).",
        storePrefix = "unisex"
    );

    companion object {
        fun safeValueOf(raw: String?): StyleGender =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNISEX
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Criel Member",
    val seasonPalette: String = "Deep Autumn",
    val undertone: String = "Warm Neutral",
    val contrastLevel: String = "Medium-High",
    val hairEyeDepth: String = "Deep Espresso / Warm Hazel",
    val paletteSummary: String = "Rich earthy neutrals, architectural obsidian, warm camel, and muted forest tones create effortless editorial depth.",
    val powerColors: List<String> = listOf("#8B5A3C", "#2E4034", "#6B2D2C", "#B8956A", "#1F2B44"),
    val neutralColors: List<String> = listOf("#FAF8F5", "#E6E1D8", "#33353A", "#141414", "#3B2B23"),
    val avoidColors: List<String> = listOf("#FF2E93", "#B7FF00", "#D6E8FF", "#E5B3FE"),
    val archetypes: List<String> = listOf("Quiet Luxury", "Minimalist"),
    val silhouette: String = "Tailored",
    val heightCm: Double = 172.0,
    val proportions: String = "Balanced vertical line, high-waist elongation",
    val monthlyBudget: Double = 15000.0,
    val virtualModelImagePath: String? = null,
    val darkMode: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GarmentItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val brand: String = "Archive",
    val category: String = "Tops",
    val subCategory: String = "",
    val colorName: String = "Obsidian Black",
    val colorHex: String = "#141414",
    val fabric: String = "Cotton",
    val season: String = "All-Season",
    val formality: Int = 3,
    val price: Double = 0.0,
    val wears: Int = 0,
    val status: String = GarmentStatus.AVAILABLE.name,
    val careInstructions: String = "Cold gentle cycle or dry clean",
    val careNotes: String = "",
    val tags: List<String> = emptyList(),
    val localImagePath: String? = null,
    val lastWornEpochMs: Long = 0L,
    val purchasedEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): GarmentStatus = GarmentStatus.safeValueOf(status)
    fun costPerWear(): Double? = if (wears > 0) price / wears.toDouble() else null
    fun formattedCpw(): String = when {
        wears > 0 -> "${formatInr(price / wears)}/wear"
        price > 0 -> "${formatInr(price)} (0 wears)"
        else -> "Unworn"
    }
    fun isUnwornOrStale90Days(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (wears == 0) return true
        if (lastWornEpochMs <= 0L) return false
        val ninetyDaysMs = 90L * 24L * 60L * 60L * 1000L
        return (nowMs - lastWornEpochMs) >= ninetyDaysMs
    }
}

@Keep
@IgnoreExtraProperties
data class SavedLook(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val occasion: String = "Editorial",
    val collectionName: String = "Autumn Work",
    val harmonyScore: Int = 92,
    val itemIds: List<String> = emptyList(),
    val notes: String = "",
    val localTryOnImagePath: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CalendarEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val lookId: String? = null,
    val lookTitle: String = "",
    val itemIds: List<String> = emptyList(),
    val occasion: String = "",
    val whoYouSaw: String = "",
    val worn: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WishlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val brand: String = "Wishlist",
    val category: String = "Tops",
    val colorName: String = "Espresso",
    val colorHex: String = "#4A3728",
    val price: Double = 0.0,
    val reason: String = "",
    val unlocks: String = "",
    val verdict: String = "",
    val localImagePath: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TripPlan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val destination: String = "",
    val days: Int = 4,
    val climate: String = "Cool & Crisp",
    val activities: String = "City walks, Galleries, Dinner",
    val bagSize: String = "Carry-on",
    val packingItemIds: List<String> = emptyList(),
    val checkedItemIds: List<String> = emptyList(),
    val itineraryLines: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OotdFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val action: String = "LOVE",
    val itemIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

// =========================================================================
// TRANSIENT UI & AI RESULT MODELS
// =========================================================================

data class WeatherState(
    val city: String = "Local Forecast",
    val tempC: Int = 16,
    val useFahrenheit: Boolean = false,
    val condition: WeatherCondition = WeatherCondition.CLOUDY,
    val windKmh: Int = 12,
    val humidityPct: Int = 55,
    val manualOverride: Boolean = false,
    val loading: Boolean = false,
    val liveSnapshot: WeatherSnapshot? = null
) {
    fun formattedTemp(): String = if (useFahrenheit) "${((tempC * 9) / 5) + 32}°F" else "${tempC}°C"
    fun displayTempValue(): Int = if (useFahrenheit) ((tempC * 9) / 5) + 32 else tempC
}

data class WeatherSnapshot(
    val city: String,
    val tempC: Int,
    val condition: WeatherCondition,
    val windKmh: Int,
    val humidityPct: Int
)

data class StylistOutfitCard(
    val title: String,
    val harmonyScore: Int,
    val rationale: String,
    val itemIds: List<String>,
    val dressCodeNote: String = "",
    val cohesionVerdict: String = "",
    val whatToGetNext: List<String> = emptyList()
)

data class StylistShoppingItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val colorName: String,
    val colorHex: String,
    val estimatedPriceInr: Double,
    val priority: String = "Must-Have", // Must-Have, High Impact, Nice-to-Have
    val fitAndFabric: String = "",
    val whyYouNeedIt: String,
    val photoSearchQuery: String = "",
    val storeSearchQuery: String = "",
    val referenceImageUrl: String? = null
)

data class StylistChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val attachedImagePath: String? = null,
    val outfit: StylistOutfitCard? = null,
    val shoppingListTitle: String = "",
    val shoppingList: List<StylistShoppingItem> = emptyList()
)

data class RateMyFitResult(
    val overallScore: Int,
    val proportionsScore: Int,
    val colorHarmonyScore: Int,
    val occasionMatchScore: Int,
    val styleDnaScore: Int,
    val editorialVerdict: String,
    val cohesionBreakdown: String = "",
    val quickTweaks: List<String>,
    val whatToGetToUpgrade: List<String> = emptyList(),
    val suggestedSwapItemIds: List<String>
)

data class OotdCardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val vibe: String,
    val harmonyScore: Int,
    val rationale: String,
    val itemIds: List<String>,
    val upgradeBuyTip: String = ""
)

data class ExtractedGarmentCandidate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val subCategory: String,
    val colorName: String,
    val colorHex: String,
    val fabric: String,
    val formality: Int,
    val estimatedPrice: Double,
    val ymin: Int,
    val xmin: Int,
    val ymax: Int,
    val xmax: Int,
    val croppedImagePath: String? = null,
    val selected: Boolean = true
)

data class AutoTagResult(
    val name: String,
    val brand: String,
    val category: String,
    val subCategory: String,
    val colorName: String,
    val colorHex: String,
    val fabric: String,
    val season: String,
    val formality: Int,
    val price: Double,
    val careInstructions: String,
    val tags: List<String>
)

data class ColorAnalysisResult(
    val seasonPalette: String,
    val undertone: String,
    val contrastLevel: String,
    val hairEyeDepth: String,
    val paletteSummary: String,
    val powerColors: List<String>,
    val neutralColors: List<String>,
    val avoidColors: List<String>
)

data class ShouldIBuyResult(
    val itemName: String,
    val category: String,
    val colorName: String,
    val colorHex: String,
    val verdict: String, // BUY, WAIT, SKIP
    val seasonalMatch: String,
    val duplicateWarning: String,
    val outfitMultiplierCount: Int,
    val unlockedOutfits: List<String>,
    val projectedCpw: String,
    val rationale: String
)

data class GapRecommendation(
    val name: String,
    val category: String,
    val colorName: String,
    val colorHex: String,
    val price: Double,
    val unlocks: String,
    val reason: String
)

data class ResaleListingResult(
    val garmentId: String,
    val estimatedResalePrice: String,
    val title: String,
    val description: String,
    val measurementsPrompt: String,
    val hashtags: List<String>
)

data class CollagePieceState(
    val id: String,
    val itemId: String,
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val flippedHorizontal: Boolean = false,
    val zIndex: Float = 1f
)

data class CollageTextSticker(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val xFraction: Float = 0.25f,
    val yFraction: Float = 0.08f
)

data class TryOnSlotSnapshot(
    val label: String,
    val title: String,
    val scene: String,
    val itemIds: List<String>,
    val imagePath: String
)

data class LookFitCritique(
    val headline: String,
    val fitScore: Int,
    val silhouetteAndDrape: String,
    val pairingHonesty: String = "",
    val sceneFitNote: String,
    val stylingTips: List<String>,
    val whatToGetToUpgrade: List<String> = emptyList()
)

// =========================================================================
// 14 REALISTIC EDITORIAL STARTER PIECES (Works immediately on first load)
// =========================================================================

object StarterArchive {
    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60L * 60L * 1000L

    fun starterGarments(uid: String): List<Pair<String, GarmentItem>> = listOf(
        "starter_01" to GarmentItem(
            userId = uid, name = "Double-Breasted Virgin Wool Coat", brand = "The Row",
            category = "Outerwear", subCategory = "Overcoat", colorName = "Camel Tan", colorHex = "#B8956A",
            fabric = "100% Virgin Wool", season = "Fall/Winter", formality = 5, price = 890.0, wears = 11,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Dry clean only; steam vertically",
            tags = listOf("quiet-luxury", "tailored", "winter"), lastWornEpochMs = now - 5 * dayMs,
            purchasedEpochMs = now - 140 * dayMs
        ),
        "starter_02" to GarmentItem(
            userId = uid, name = "Structured Obsidian Wool Blazer", brand = "Acne Studios",
            category = "Outerwear", subCategory = "Blazer", colorName = "Obsidian Black", colorHex = "#141414",
            fabric = "Wool Gabardine", season = "All-Season", formality = 4, price = 540.0, wears = 16,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Dry clean; brush after wear",
            tags = listOf("office", "minimalist", "evening"), lastWornEpochMs = now - 2 * dayMs,
            purchasedEpochMs = now - 180 * dayMs
        ),
        "starter_03" to GarmentItem(
            userId = uid, name = "Storm-Flap Waterproof Trench", brand = "Burberry",
            category = "Outerwear", subCategory = "Trench Coat", colorName = "Warm Stone", colorHex = "#CFC5B4",
            fabric = "Waterproof Gabardine", season = "Spring/Summer", formality = 3, price = 780.0, wears = 7,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Specialist dry clean",
            tags = listOf("rainy-day", "classic", "travel"), lastWornEpochMs = now - 14 * dayMs,
            purchasedEpochMs = now - 210 * dayMs
        ),
        "starter_04" to GarmentItem(
            userId = uid, name = "Fine-Gauge Cashmere Turtleneck", brand = "Loro Piana",
            category = "Tops", subCategory = "Knitwear", colorName = "Alabaster Ivory", colorHex = "#EFECE6",
            fabric = "100% Cashmere", season = "Fall/Winter", formality = 4, price = 420.0, wears = 14,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Hand wash cold, dry flat",
            tags = listOf("quiet-luxury", "layering", "warm"), lastWornEpochMs = now - 3 * dayMs,
            purchasedEpochMs = now - 120 * dayMs
        ),
        "starter_05" to GarmentItem(
            userId = uid, name = "Heavyweight Boxy Organic Tee", brand = "COS",
            category = "Tops", subCategory = "T-Shirt", colorName = "Chalk White", colorHex = "#F7F7F5",
            fabric = "Heavy Organic Cotton", season = "All-Season", formality = 2, price = 65.0, wears = 28,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Machine wash cold inside out",
            tags = listOf("minimalist", "essential", "weekend"), lastWornEpochMs = now - 1 * dayMs,
            purchasedEpochMs = now - 200 * dayMs
        ),
        "starter_06" to GarmentItem(
            userId = uid, name = "Relaxed Mulberry Silk Camp Shirt", brand = "Toteme",
            category = "Tops", subCategory = "Shirt", colorName = "Deep Espresso", colorHex = "#3B2B23",
            fabric = "100% Mulberry Silk", season = "Spring/Summer", formality = 4, price = 310.0, wears = 0,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Dry clean or delicate silk wash",
            tags = listOf("date-night", "editorial", "evening"), lastWornEpochMs = 0L,
            purchasedEpochMs = now - 120 * dayMs
        ),
        "starter_07" to GarmentItem(
            userId = uid, name = "Crisp Poplin Striped Overshirt", brand = "Jil Sander",
            category = "Tops", subCategory = "Button-Down", colorName = "Slate Blue", colorHex = "#6E8296",
            fabric = "Italian Cotton Poplin", season = "All-Season", formality = 3, price = 285.0, wears = 8,
            status = GarmentStatus.IN_LAUNDRY.name, careInstructions = "Machine wash 30C, steam press",
            tags = listOf("smart-casual", "office", "layering"), lastWornEpochMs = now - 4 * dayMs,
            purchasedEpochMs = now - 95 * dayMs
        ),
        "starter_08" to GarmentItem(
            userId = uid, name = "Pleated Wide-Leg Merino Trousers", brand = "Studio Nicholson",
            category = "Bottoms", subCategory = "Trousers", colorName = "Charcoal Slate", colorHex = "#33353A",
            fabric = "Merino Wool", season = "All-Season", formality = 4, price = 360.0, wears = 19,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Dry clean; hang by cuffs",
            tags = listOf("tailored", "office", "minimalist"), lastWornEpochMs = now - 2 * dayMs,
            purchasedEpochMs = now - 160 * dayMs
        ),
        "starter_09" to GarmentItem(
            userId = uid, name = "Selvedge Raw Indigo Denim", brand = "A.P.C.",
            category = "Bottoms", subCategory = "Denim", colorName = "Raw Indigo", colorHex = "#1F2B44",
            fabric = "Japanese Selvedge Denim", season = "All-Season", formality = 2, price = 245.0, wears = 22,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Spot clean or cold soak rarely",
            tags = listOf("weekend", "versatile", "streetwear"), lastWornEpochMs = now - 6 * dayMs,
            purchasedEpochMs = now - 240 * dayMs
        ),
        "starter_10" to GarmentItem(
            userId = uid, name = "Tailored Belgian Linen Trousers", brand = "Lemaire",
            category = "Bottoms", subCategory = "Linen Pants", colorName = "Oatmeal Sand", colorHex = "#D8CBB8",
            fabric = "100% Belgian Linen", season = "Spring/Summer", formality = 2, price = 290.0, wears = 0,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Gentle wash cold, steam while damp",
            tags = listOf("resort", "warm-weather", "relaxed"), lastWornEpochMs = 0L,
            purchasedEpochMs = now - 135 * dayMs
        ),
        "starter_11" to GarmentItem(
            userId = uid, name = "Calfskin Lug-Sole Chelsea Boots", brand = "Bottega Veneta",
            category = "Footwear", subCategory = "Boots", colorName = "Noir Black", colorHex = "#141416",
            fabric = "Weatherproof Calfskin", season = "Fall/Winter", formality = 4, price = 680.0, wears = 15,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Wipe damp cloth, condition monthly",
            tags = listOf("rainy-day", "evening", "winter"), lastWornEpochMs = now - 2 * dayMs,
            purchasedEpochMs = now - 170 * dayMs
        ),
        "starter_12" to GarmentItem(
            userId = uid, name = "Minimalist Italian Leather Sneakers", brand = "Common Projects",
            category = "Footwear", subCategory = "Sneakers", colorName = "Optic White", colorHex = "#F3F2EE",
            fabric = "Nappa Leather", season = "All-Season", formality = 2, price = 410.0, wears = 31,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Wipe soles, cedar shoe trees",
            tags = listOf("casual", "travel", "minimalist"), lastWornEpochMs = now - 1 * dayMs,
            purchasedEpochMs = now - 260 * dayMs
        ),
        "starter_13" to GarmentItem(
            userId = uid, name = "Architectural Calfskin Tote", brand = "Polene",
            category = "Bags", subCategory = "Tote Bag", colorName = "Cognac Saddle", colorHex = "#8B5A3C",
            fabric = "Full-Grain Calfskin", season = "All-Season", formality = 4, price = 460.0, wears = 12,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Store stuffed in dust bag",
            tags = listOf("office", "travel", "quiet-luxury"), lastWornEpochMs = now - 3 * dayMs,
            purchasedEpochMs = now - 150 * dayMs
        ),
        "starter_14" to GarmentItem(
            userId = uid, name = "Sculpted Brass & Leather Belt", brand = "Khaite",
            category = "Accessories", subCategory = "Belt", colorName = "Obsidian & Gold", colorHex = "#1E1C1A",
            fabric = "Italian Bridle Leather", season = "All-Season", formality = 4, price = 220.0, wears = 18,
            status = GarmentStatus.AVAILABLE.name, careInstructions = "Buff brass buckle gently",
            tags = listOf("quiet-luxury", "tailored", "signature"), lastWornEpochMs = now - 2 * dayMs,
            purchasedEpochMs = now - 190 * dayMs
        )
    )

    fun starterLooks(uid: String): List<Pair<String, SavedLook>> = listOf(
        "look_starter_1" to SavedLook(
            userId = uid, title = "Parisian Gallery Evening", occasion = "Evening",
            collectionName = "Autumn Work", harmonyScore = 96,
            itemIds = listOf("starter_02", "starter_04", "starter_08", "starter_11", "starter_13", "starter_14"),
            notes = "Architectural high-contrast monochrome warmed by cognac leather."
        ),
        "look_starter_2" to SavedLook(
            userId = uid, title = "Off-Duty SoHo Minimal", occasion = "Weekend",
            collectionName = "Weekend", harmonyScore = 93,
            itemIds = listOf("starter_03", "starter_05", "starter_09", "starter_12", "starter_13"),
            notes = "Relaxed proportions with crisp optic white sneakers."
        ),
        "look_starter_3" to SavedLook(
            userId = uid, title = "Camel Overcoat Capsule", occasion = "Smart Casual",
            collectionName = "333 Capsule", harmonyScore = 97,
            itemIds = listOf("starter_01", "starter_04", "starter_08", "starter_11", "starter_14"),
            notes = "Signature Deep Autumn power palette for cool city days."
        )
    )

    fun starterWishlist(uid: String): List<Pair<String, WishlistItem>> = listOf(
        "wl_starter_1" to WishlistItem(
            userId = uid, name = "Deep Olive Merino Knit Polo", brand = "Sunspel",
            category = "Tops", colorName = "Forest Olive", colorHex = "#3E4F3C",
            price = 240.0, reason = "Unlocks warm earth-tone layering under camel coat, obsidian blazer, and trench.",
            unlocks = "+6 outfits", verdict = "BUY"
        ),
        "wl_starter_2" to WishlistItem(
            userId = uid, name = "Espresso Suede Penny Loafers", brand = "Morjas",
            category = "Footwear", colorName = "Espresso Suede", colorHex = "#4A3728",
            price = 295.0, reason = "Bridges tailored wool trousers and raw indigo denim for smart-casual evenings.",
            unlocks = "+8 outfits", verdict = "BUY"
        )
    )
}
