package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private const val AI_TAG = "AiStylistService"
private const val MODEL_FAST_VISION = "gemini-3.5-flash-lite"
private const val MODEL_CHAT_SMART = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 50_000L

/**
 * 100% Free-Tier Firebase AI Logic service powered exclusively by gemini-3.5-flash-lite
 * (high free-tier quota, zero billing required) for all styling, vision, fit critique, shopping, and trip workflows.
 */
class AiStylistService(
    private val referencePhotoService: ReferencePhotoService = ReferencePhotoService()
) {

    private val lookFitCritiqueModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "headline" to Schema.string("Blunt, sharp editorial headline for this look"),
                "fitScore" to Schema.integer("Honest fit & pairing score 0-100 (do not inflate; 40-65 for clashing/flat looks, 70-82 for decent, 85+ only for standout)"),
                "silhouetteAndDrape" to Schema.string("2 candid sentences on silhouette balance, fabric weight clash, and proportions"),
                "pairingHonesty" to Schema.string("2 brutally honest sentences on how well these pieces actually go together—name any color, texture, or formality clashes directly"),
                "sceneFitNote" to Schema.string("1 candid sentence on whether this look actually works for the chosen setting"),
                "stylingTips" to Schema.array(Schema.string(), "3 concrete styling adjustments to fix flaws immediately"),
                "whatToGetToUpgrade" to Schema.array(Schema.string(), "2-3 specific pieces to buy (with realistic INR ₹ price range) that would dramatically improve this outfit")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val seasonalColorModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "seasonPalette" to Schema.string("One of the 12 seasons e.g. Deep Autumn, Cool Winter"),
                "undertone" to Schema.string("Warm, Cool, Warm Neutral, or Cool Neutral"),
                "contrastLevel" to Schema.string("High, Medium-High, Medium, or Low"),
                "hairEyeDepth" to Schema.string("Short hair and eye depth description"),
                "paletteSummary" to Schema.string("2 concise editorial sentences"),
                "powerColors" to Schema.array(Schema.string(), "5 hex codes starting with #"),
                "neutralColors" to Schema.array(Schema.string(), "5 hex codes starting with #"),
                "avoidColors" to Schema.array(Schema.string(), "4 hex codes starting with #")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val autoTagModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string("Concise garment title"),
                "brand" to Schema.string("Visible brand or Archive"),
                "category" to Schema.string("One of: Outerwear, Tops, Bottoms, Footwear, Bags, Accessories"),
                "subCategory" to Schema.string("Specific type like Blazer, Tee, Sneaker, Tote"),
                "colorName" to Schema.string("Color name"),
                "colorHex" to Schema.string("6-digit hex code like #141414"),
                "fabric" to Schema.string("Primary material"),
                "season" to Schema.string("All-Season, Spring/Summer, or Fall/Winter"),
                "formality" to Schema.integer("1 to 5"),
                "price" to Schema.double("Estimated retail price in Indian Rupees (INR)"),
                "careInstructions" to Schema.string("Short care instruction"),
                "tags" to Schema.array(Schema.string(), "3-4 lowercase tags")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val extractorModel by lazy {
        val itemSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string("Garment name"),
                "category" to Schema.string("Outerwear, Tops, Bottoms, Footwear, Bags, or Accessories"),
                "subCategory" to Schema.string("Piece type"),
                "colorName" to Schema.string("Color name"),
                "colorHex" to Schema.string("6-digit hex code"),
                "fabric" to Schema.string("Material"),
                "formality" to Schema.integer("1 to 5"),
                "estimatedPrice" to Schema.double("Estimated price in Indian Rupees (INR)"),
                "ymin" to Schema.integer("0-1000"),
                "xmin" to Schema.integer("0-1000"),
                "ymax" to Schema.integer("0-1000"),
                "xmax" to Schema.integer("0-1000")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf("garments" to Schema.array(itemSchema, "Visible garments"))
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.15f
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private fun buildShoppingItemSchema(): Schema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string("Specific item name e.g. Crisp White Heavyweight Crewneck Tee or Minimal White Leather Sneakers"),
            "category" to Schema.enumeration(GarmentCategory.entries.map { it.label }),
            "colorName" to Schema.string("Specific color name e.g. Off-White, Deep Navy, Olive"),
            "colorHex" to Schema.string("6-digit hex color code e.g. #F5F5F0"),
            "estimatedPriceInr" to Schema.double("Realistic Indian retail price in INR rupees (number only, e.g. 2499)"),
            "priority" to Schema.enumeration(listOf("Must-Have", "High Impact", "Nice-to-Have")),
            "fitAndFabric" to Schema.string("Concrete cut, silhouette, fabric weight/material, and visual details to look for (1-2 sharp sentences)"),
            "whyYouNeedIt" to Schema.string("1 blunt, practical sentence on why their closet needs this exact piece and how it pairs"),
            "photoSearchQuery" to Schema.string("Simple 3-4 word English clothing photography search query using explicit garment nouns (NO animal/food words like camel/olive/peach; e.g. 'white cotton t-shirt', 'brown leather loafer shoes', 'beige chino trousers')"),
            "storeSearchQuery" to Schema.string("Concise Indian store search query for Myntra/Ajio/Zara/H&M starting with men/women/unisex as appropriate e.g. 'men white heavyweight cotton crewneck t-shirt'")
        )
    )

    private val shoppingAdvisorModel by lazy {
        val advisorSchema = Schema.obj(
            properties = mapOf(
                "reply" to Schema.string("Direct, candid stylist intro summarizing the top gaps or buying advice in 2-3 sharp sentences"),
                "shoppingListTitle" to Schema.string("Clear title for the checklist e.g. 'Essential Wardrobe Basics' or 'Everyday Sneakers to Get'"),
                "shoppingList" to Schema.array(
                    buildShoppingItemSchema(),
                    "REQUIRED non-empty list of concrete items the user should get, ordered Must-Have first, then High Impact, then Nice-to-Have"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_CHAT_SMART,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = advisorSchema
            }
        )
    }

    private val stylistChatModel by lazy {
        val outfitSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Outfit title"),
                "harmonyScore" to Schema.integer("Honest cohesion score 0-100"),
                "rationale" to Schema.string("Candid styling rationale"),
                "cohesionVerdict" to Schema.string("Honest critique of how well these pieces go together and any weak link"),
                "whatToGetNext" to Schema.array(Schema.string(), "1-3 concrete pieces to buy (with INR ₹ estimate) to elevate this fit"),
                "dressCodeNote" to Schema.string("Dress code tip"),
                "itemIds" to Schema.array(Schema.string(), "Exact wardrobe item IDs")
            ),
            optionalProperties = listOf("dressCodeNote", "cohesionVerdict", "whatToGetNext")
        )
        val chatSchema = Schema.obj(
            properties = mapOf(
                "reply" to Schema.string("Direct, candid stylist response answering the user's exact question with zero sugarcoating"),
                "outfit" to outfitSchema,
                "shoppingListTitle" to Schema.string("Short title for the shopping or basics list (empty string if shoppingList is empty)"),
                "shoppingList" to Schema.array(
                    buildShoppingItemSchema(),
                    "ALWAYS populate this array whenever you recommend ANY items to buy/get, basics, or companion pieces. Return an empty array [] ONLY when giving pure outfit-from-closet or conversational advice with zero item recommendations."
                )
            ),
            optionalProperties = listOf("outfit")
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_CHAT_SMART,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = chatSchema
            }
        )
    }

    private val rateMyFitModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer("Honest 0-100 score; do not inflate weak fits"),
                "proportionsScore" to Schema.integer("0-100"),
                "colorHarmonyScore" to Schema.integer("0-100"),
                "occasionMatchScore" to Schema.integer("0-100"),
                "styleDnaScore" to Schema.integer("0-100"),
                "editorialVerdict" to Schema.string("2-3 blunt, honest sentences judging the fit as a whole"),
                "cohesionBreakdown" to Schema.string("2 direct sentences on how well the individual pieces actually go together — call out exact clashes or mismatches"),
                "quickTweaks" to Schema.array(Schema.string(), "3 concrete 10-second fixes"),
                "whatToGetToUpgrade" to Schema.array(Schema.string(), "2-3 specific pieces to buy (with realistic INR ₹ price range) that would make this fit noticeably better"),
                "suggestedSwapItemIds" to Schema.array(Schema.string(), "0-2 wardrobe item IDs")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.35f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val ootdDeckModel by lazy {
        val cardSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Look title"),
                "vibe" to Schema.string("Short vibe label"),
                "harmonyScore" to Schema.integer("Honest score 0-100"),
                "rationale" to Schema.string("Candid 1-2 sentence breakdown of how well these pieces pair"),
                "upgradeBuyTip" to Schema.string("1 specific piece to buy (with INR ₹ price) that would take this look to the next level"),
                "itemIds" to Schema.array(Schema.string(), "Exact wardrobe item IDs")
            )
        )
        val schema = Schema.obj(
            properties = mapOf("outfits" to Schema.array(cardSchema, "Up to 5 distinct outfits"))
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.6f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val completeLookModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Editorial outfit title"),
                "Outerwear" to Schema.string("Item ID or empty"),
                "Tops" to Schema.string("Item ID or empty"),
                "Bottoms" to Schema.string("Item ID or empty"),
                "Footwear" to Schema.string("Item ID or empty"),
                "Bags" to Schema.string("Item ID or empty"),
                "Accessories" to Schema.string("Item ID or empty")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.4f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val shouldIBuyModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "itemName" to Schema.string("Item name"),
                "category" to Schema.string("Outerwear, Tops, Bottoms, Footwear, Bags, or Accessories"),
                "colorName" to Schema.string("Color name"),
                "colorHex" to Schema.string("6-digit hex"),
                "verdict" to Schema.string("BUY, WAIT, or SKIP"),
                "seasonalMatch" to Schema.string("Fit with Style DNA palette"),
                "duplicateWarning" to Schema.string("Duplicate check against closet"),
                "outfitMultiplierCount" to Schema.integer("Number of unlocked outfits"),
                "unlockedOutfits" to Schema.array(Schema.string(), "3 concrete outfit pairings"),
                "projectedCpw" to Schema.string("e.g. ₹250/wear"),
                "rationale" to Schema.string("2-sentence brutally honest verdict")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.3f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val gapAnalysisModel by lazy {
        val recSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string("Piece name"),
                "category" to Schema.string("Category"),
                "colorName" to Schema.string("Color"),
                "colorHex" to Schema.string("6-digit hex"),
                "price" to Schema.double("Estimated price in Indian Rupees (INR)"),
                "unlocks" to Schema.string("e.g. +8 outfits"),
                "reason" to Schema.string("Why this piece multiplies outfits")
            )
        )
        val schema = Schema.obj(
            properties = mapOf("recommendations" to Schema.array(recSchema, "Top 5 pieces"))
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.4f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val resaleModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "estimatedResalePrice" to Schema.string("e.g. ₹2,500 - ₹3,500"),
                "title" to Schema.string("SEO resale title"),
                "description" to Schema.string("Marketplace listing copy"),
                "measurementsPrompt" to Schema.string("Key measurements checklist"),
                "hashtags" to Schema.array(Schema.string(), "6 hashtags")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.4f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val tripPlanModel by lazy {
        val daySchema = Schema.obj(
            properties = mapOf(
                "day" to Schema.integer("Day number"),
                "occasion" to Schema.string("Occasion"),
                "note" to Schema.string("Short tip"),
                "itemIds" to Schema.array(Schema.string(), "Wardrobe IDs")
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "packingItemIds" to Schema.array(Schema.string(), "Minimal capsule IDs"),
                "itinerary" to Schema.array(daySchema, "Day-by-day looks")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_VISION,
            generationConfig = generationConfig {
                temperature = 0.4f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private fun styleDnaContext(profile: UserProfile, gender: StyleGender = StyleGender.UNISEX): String =
        "${gender.promptDirective}\n" +
            "DNA: ${profile.seasonPalette} (${profile.undertone}, ${profile.contrastLevel}); " +
            "Power:${profile.powerColors.take(4).joinToString(",")}; " +
            "Avoid:${profile.avoidColors.take(3).joinToString(",")}; " +
            "Archetypes:${profile.archetypes.joinToString(",")}; Fit:${profile.silhouette}."

    private fun toneInstruction(tone: AiCritiqueTone): String = when (tone) {
        AiCritiqueTone.BRUTALLY_HONEST ->
            "TONE: Brutally honest fashion director. Zero sugarcoating, zero fake flattery. If pieces clash in color, proportion, fabric, or formality, call it out bluntly and score honestly (30-65 for clashing/flat fits, 68-82 for decent everyday fits, 85+ ONLY for genuinely sharp fits). Always state clearly how well pieces work together and give concrete items to buy (with realistic INR ₹ prices) to upgrade the fit."
        AiCritiqueTone.BALANCED ->
            "TONE: Direct, constructive fashion editor. Be honest about what clashes or falls flat, explain how well pieces pair together, and suggest concrete items to buy in INR (₹) to elevate the fit."
        AiCritiqueTone.GENTLE ->
            "TONE: Supportive personal stylist. Highlight what works, gently note what could pair better, and recommend practical pieces to buy in INR (₹) to elevate the fit."
    }

    private fun compactWardrobeJson(items: List<GarmentItem>): String {
        if (items.isEmpty()) return "[]"
        val arr = JSONArray()
        items.take(45).forEach { g ->
            arr.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("cat", g.category)
                    put("color", "${g.colorName}(${g.colorHex})")
                    put("fabric", g.fabric)
                    put("season", g.season)
                    put("form", g.formality)
                }
            )
        }
        return arr.toString()
    }

    private suspend fun prepareVisionBitmap(source: Bitmap, maxDim: Int = 512): Bitmap =
        withContext(Dispatchers.Default) {
            val w = source.width
            val h = source.height
            val longest = max(w, h)
            if (longest <= maxDim) return@withContext source
            val scale = maxDim.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(
                source,
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
                true
            )
        }

    // -------------------------------------------------------------------------
    // 1. AI SEASONAL COLOR ANALYSIS
    // -------------------------------------------------------------------------
    suspend fun analyzeSeasonalColor(selfie: Bitmap): ColorAnalysisResult {
        val scaled = prepareVisionBitmap(selfie, 512)
        return try {
            val text = executeWithTimeout {
                seasonalColorModel.generateContent(
                    content {
                        image(scaled)
                        text("Analyze skin undertone, contrast, and hair/eye depth. Assign one of the 12 seasons (${SEASON_PALETTES_12.joinToString(", ")}) with flattering 6-digit hex swatches.")
                    }
                ).text
            }
            val json = parseJsonObject(text)
            val rawSeason = json.optString("seasonPalette", "Deep Autumn")
            val validSeason = SEASON_PALETTES_12.firstOrNull { it.equals(rawSeason, ignoreCase = true) } ?: "Deep Autumn"
            ColorAnalysisResult(
                seasonPalette = validSeason,
                undertone = json.optString("undertone", "Warm Neutral"),
                contrastLevel = json.optString("contrastLevel", "Medium-High"),
                hairEyeDepth = json.optString("hairEyeDepth", "Deep Espresso"),
                paletteSummary = json.optString("paletteSummary", "Curated editorial tones harmonize with your natural contrast."),
                powerColors = json.optStringList("powerColors").map { normalizeHex(it) }.ifEmpty { listOf("#8B5A3C", "#2E4034", "#6B2D2C", "#B8956A", "#1F2B44") },
                neutralColors = json.optStringList("neutralColors").map { normalizeHex(it) }.ifEmpty { listOf("#FAF8F5", "#E6E1D8", "#33353A", "#141414", "#3B2B23") },
                avoidColors = json.optStringList("avoidColors").map { normalizeHex(it) }.ifEmpty { listOf("#FF2E93", "#B7FF00", "#D6E8FF", "#E5B3FE") }
            )
        } finally {
            if (scaled !== selfie) scaled.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 2. FAST GEMINI VISION GARMENT AUTO-TAGGING
    // -------------------------------------------------------------------------
    suspend fun autoTagGarment(garmentPhoto: Bitmap): AutoTagResult {
        val scaled = prepareVisionBitmap(garmentPhoto, 512)
        return try {
            val text = executeWithTimeout {
                autoTagModel.generateContent(
                    content {
                        image(scaled)
                        text("Identify this garment concisely. Fill every JSON field accurately: name, brand, category (Outerwear/Tops/Bottoms/Footwear/Bags/Accessories), subCategory, colorName, colorHex, fabric, season, formality (1-5), price (in Indian Rupees INR, e.g. 2500), careInstructions, tags.")
                    }
                ).text
            }
            val json = parseJsonObject(text)
            val rawSeason = json.optString("season", "All-Season")
            val cleanSeason = when {
                rawSeason.contains("spring", true) || rawSeason.contains("summer", true) -> "Spring/Summer"
                rawSeason.contains("fall", true) || rawSeason.contains("winter", true) || rawSeason.contains("autumn", true) -> "Fall/Winter"
                else -> "All-Season"
            }
            AutoTagResult(
                name = json.optString("name", "Editorial Garment").ifBlank { "Editorial Garment" }.take(110),
                brand = json.optString("brand", "Archive").ifBlank { "Archive" }.take(70),
                category = GarmentCategory.fromLabel(json.optString("category")).label,
                subCategory = json.optString("subCategory", "").take(50),
                colorName = json.optString("colorName", "Charcoal").ifBlank { "Charcoal" }.take(50),
                colorHex = normalizeHex(json.optString("colorHex", "#141414")),
                fabric = json.optString("fabric", "Cotton").ifBlank { "Cotton" }.take(70),
                season = cleanSeason,
                formality = json.optInt("formality", 3).coerceIn(1, 5),
                price = json.optDouble("price", 2500.0).let { if (it.isNaN()) 2500.0 else it.coerceIn(0.0, 5000000.0) },
                careInstructions = json.optString("careInstructions", "Gentle cold wash or dry clean").take(250),
                tags = json.optStringList("tags").take(8)
            )
        } finally {
            if (scaled !== garmentPhoto) scaled.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 3. FULL-BODY OUTFIT EXTRACTOR
    // -------------------------------------------------------------------------
    suspend fun extractOutfitFromMirrorSelfie(mirrorSelfie: Bitmap): List<ExtractedGarmentCandidate> {
        val scaled = prepareVisionBitmap(mirrorSelfie, 640)
        return try {
            val text = executeWithTimeout {
                extractorModel.generateContent(
                    content {
                        image(scaled)
                        text("Detect each visible garment/accessory in this outfit photo with 0-1000 bounding boxes [ymin, xmin, ymax, xmax], metadata, and realistic estimatedPrice in Indian Rupees (INR).")
                    }
                ).text
            }
            val arr = parseJsonObject(text).optJSONArray("garments") ?: JSONArray()
            (0 until arr.length()).map { idx ->
                val g = arr.getJSONObject(idx)
                ExtractedGarmentCandidate(
                    name = g.optString("name", "Extracted Piece").take(110),
                    category = GarmentCategory.fromLabel(g.optString("category")).label,
                    subCategory = g.optString("subCategory", ""),
                    colorName = g.optString("colorName", "Neutral"),
                    colorHex = normalizeHex(g.optString("colorHex", "#222222")),
                    fabric = g.optString("fabric", "Cotton Blend"),
                    formality = g.optInt("formality", 3).coerceIn(1, 5),
                    estimatedPrice = g.optDouble("estimatedPrice", 2500.0).let { if (it.isNaN()) 2500.0 else it.coerceIn(0.0, 2000000.0) },
                    ymin = g.optInt("ymin", 100).coerceIn(0, 950),
                    xmin = g.optInt("xmin", 100).coerceIn(0, 950),
                    ymax = g.optInt("ymax", 800).coerceIn(50, 1000),
                    xmax = g.optInt("xmax", 900).coerceIn(50, 1000)
                )
            }
        } finally {
            if (scaled !== mirrorSelfie) scaled.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 5. WARDROBE-AWARE AI STYLIST CHAT (MULTI-INTENT + VISION + BASICS LISTS)
    // -------------------------------------------------------------------------
    private fun isShoppingOrBasicsIntent(message: String): Boolean {
        val lower = message.lowercase()
        val outfitOnlySignals = listOf(
            "build an outfit", "build a fit", "build a sharp", "what should i wear",
            "put together an outfit", "make an outfit", "clean dinner outfit", "dress me"
        )
        if (outfitOnlySignals.any { lower.contains(it) }) return false

        val shoppingSignals = listOf(
            "need to get", "should i get", "what to get", "what to buy", "should i buy",
            "basic", "basics", "essential", "essentials", "shopping list", "wishlist",
            "missing from my closet", "wardrobe staples", "starter", "capsule",
            "list of", "all the things i need", "things i need", "items i need",
            "recommend", "suggest some", "where to buy", "to buy"
        )
        return shoppingSignals.any { lower.contains(it) }
    }

    private fun replyPromisesItems(reply: String): Boolean {
        val lower = reply.lowercase()
        val phrases = listOf(
            "here are the items", "here is the list", "here's the list",
            "you need to get", "items you need", "basics you need",
            "essentials you need", "here are some", "below are", "start with these"
        )
        return phrases.any { lower.contains(it) }
    }

    private fun parseShoppingItems(shoppingArr: JSONArray?): List<StylistShoppingItem> {
        val priorityRank = mapOf("Must-Have" to 0, "High Impact" to 1, "Nice-to-Have" to 2)
        return buildList {
            if (shoppingArr != null) {
                for (i in 0 until shoppingArr.length()) {
                    val itemObj = shoppingArr.optJSONObject(i) ?: continue
                    val name = itemObj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val rawPrice = itemObj.optDouble("estimatedPriceInr", 2499.0)
                    val safePrice = if (rawPrice.isNaN() || rawPrice <= 0.0) 2499.0 else rawPrice.coerceIn(199.0, 500000.0)
                    val priority = itemObj.optString("priority", "Must-Have").let { p ->
                        when (p) {
                            "Must-Have", "High Impact", "Nice-to-Have" -> p
                            else -> "Must-Have"
                        }
                    }
                    val category = GarmentCategory.fromLabel(itemObj.optString("category", "Tops")).label
                    val colorName = itemObj.optString("colorName", "Neutral").ifBlank { "Neutral" }
                    val fitAndFabric = itemObj.optString("fitAndFabric", "").trim()
                    val photoQuery = itemObj.optString("photoSearchQuery", "").trim()
                        .ifBlank { "$colorName $name" }
                    val storeQuery = itemObj.optString("storeSearchQuery", "").trim()
                        .ifBlank { "$colorName $name" }
                    add(
                        StylistShoppingItem(
                            name = name,
                            category = category,
                            colorName = colorName,
                            colorHex = normalizeHex(itemObj.optString("colorHex", "#3A4B5C")),
                            estimatedPriceInr = safePrice,
                            priority = priority,
                            fitAndFabric = fitAndFabric,
                            whyYouNeedIt = itemObj.optString("whyYouNeedIt", "Versatile staple that anchors multiple everyday looks."),
                            photoSearchQuery = photoQuery,
                            storeSearchQuery = storeQuery
                        )
                    )
                }
            }
        }
            .sortedBy { priorityRank[it.priority] ?: 1 }
            .take(12)
    }

    suspend fun chatWithStylist(
        userMessage: String,
        profile: UserProfile,
        availableWardrobe: List<GarmentItem>,
        weather: WeatherState,
        dressCode: DressCodeMode?,
        lovedItemIds: Set<String>,
        skippedItemIds: Set<String>,
        tone: AiCritiqueTone = AiCritiqueTone.BRUTALLY_HONEST,
        gender: StyleGender = StyleGender.UNISEX,
        attachedPhoto: Bitmap? = null,
        recentHistory: List<StylistChatMessage> = emptyList(),
        forceShoppingAdvisor: Boolean = false
    ): StylistChatMessage {
        val scaledPhoto = attachedPhoto?.let { prepareVisionBitmap(it, 512) }
        return try {
            val useShoppingAdvisor = forceShoppingAdvisor || (scaledPhoto == null && isShoppingOrBasicsIntent(userMessage))

            if (useShoppingAdvisor) {
                val advisorJson = runShoppingAdvisor(
                    userMessage = userMessage,
                    profile = profile,
                    availableWardrobe = availableWardrobe,
                    weather = weather,
                    dressCode = dressCode,
                    tone = tone,
                    gender = gender,
                    recentHistory = recentHistory,
                    scaledPhoto = scaledPhoto
                )
                val reply = advisorJson.optString("reply", "Here is your prioritized list of pieces to get.")
                val listTitle = advisorJson.optString("shoppingListTitle", "Essentials & What to Get").ifBlank { "Essentials & What to Get" }
                val rawItems = parseShoppingItems(advisorJson.optJSONArray("shoppingList"))
                val hydratedItems = referencePhotoService.resolvePhotosForItems(rawItems, gender)
                return StylistChatMessage(
                    fromUser = false,
                    text = reply,
                    outfit = null,
                    shoppingListTitle = listTitle,
                    shoppingList = hydratedItems
                )
            }

            val prompt = buildString {
                appendLine("You are Criel, a sharp personal stylist and wardrobe buyer.")
                appendLine(toneInstruction(tone))
                appendLine("Always quote prices in Indian Rupees (₹ INR).")
                appendLine(styleDnaContext(profile, gender))
                appendLine("Weather context: ${weather.formattedTemp()} ${weather.condition.label}, wind ${weather.windKmh}km/h, humidity ${weather.humidityPct}%.")
                if (dressCode != null) appendLine("Dress Code: ${dressCode.label} (${dressCode.rules})")
                if (lovedItemIds.isNotEmpty()) appendLine("Loved IDs: ${lovedItemIds.take(8).joinToString(",")}")
                if (skippedItemIds.isNotEmpty()) appendLine("Skipped IDs: ${skippedItemIds.take(8).joinToString(",")}")

                if (recentHistory.isNotEmpty()) {
                    appendLine("Recent conversation context:")
                    recentHistory.takeLast(4).forEach { msg ->
                        val who = if (msg.fromUser) "User" else "Criel"
                        appendLine("- $who: ${msg.text.take(220)}")
                    }
                }

                if (availableWardrobe.isEmpty()) {
                    appendLine("The user's wardrobe is currently empty. Omit 'outfit' because there are no wardrobe IDs.")
                } else {
                    appendLine("Wardrobe JSON (ONLY pick exact itemIds from here if building an outfit): ${compactWardrobeJson(availableWardrobe)}")
                }

                appendLine(
                    """
                    CRITICAL RULES:
                    1. STRICTLY follow the user's selected gender/department (${gender.label}) for every single suggestion, silhouette, and storeSearchQuery.
                    2. NEVER say "here are the items you need to get" without populating 'shoppingList'. Whenever you suggest ANY pieces to buy, basics, upgrades, or companion pieces, you MUST populate 'shoppingList' with concrete items (each with name, category, colorName, colorHex, estimatedPriceInr, priority, fitAndFabric, whyYouNeedIt, photoSearchQuery, storeSearchQuery).
                    3. ATTACHED PHOTO: If an image is attached, inspect the photo carefully (garment, shoes, screenshot, or fit). Give a brutally honest verdict in 'reply' (fit/fabric/color match with their Style DNA & closet, buy vs skip) AND populate 'shoppingList' with 2-5 exact companion pieces or better alternatives to get. Only include 'outfit' if they asked how to style it with owned wardrobe IDs.
                    4. OUTFIT REQUESTS: Only populate 'outfit' (using exact itemIds from Wardrobe JSON) when the user explicitly asks for an outfit or what to wear from their closet. Even then, if key upgrade pieces are missing, list them in 'shoppingList' too.
                    5. photoSearchQuery MUST be a clean 3-4 word English clothing description using explicit garment nouns (e.g. "white leather sneakers", "blue denim jacket", "beige chino trousers" — avoid animal/food words like camel, olive, peach).
                    6. storeSearchQuery MUST start with '${gender.storePrefix}' (for men/women) and target Indian fashion stores (Myntra, Ajio, Zara, H&M).
                    """.trimIndent()
                )
                if (scaledPhoto != null) {
                    appendLine("An image is attached by the user — analyze it directly.")
                }
                appendLine("User message: $userMessage")
            }

            val text = executeWithTimeout {
                if (scaledPhoto != null) {
                    stylistChatModel.generateContent(
                        content {
                            image(scaledPhoto)
                            text(prompt)
                        }
                    ).text
                } else {
                    stylistChatModel.generateContent(prompt).text
                }
            }
            val json = parseJsonObject(text)
            var reply = json.optString("reply", "Here is my honest take.")
            val validIds = availableWardrobe.map { it.id }.toSet()
            val outfitJson = json.optJSONObject("outfit")
            val parsedOutfit = if (outfitJson != null && validIds.isNotEmpty()) {
                val ids = outfitJson.optStringList("itemIds").filter { it in validIds }
                if (ids.isNotEmpty()) {
                    StylistOutfitCard(
                        title = outfitJson.optString("title", "Criel Curated Edit"),
                        harmonyScore = outfitJson.optInt("harmonyScore", 76).coerceIn(20, 100),
                        rationale = outfitJson.optString("rationale", "Balanced proportions aligned with your Style DNA."),
                        itemIds = ids,
                        dressCodeNote = outfitJson.optString("dressCodeNote", ""),
                        cohesionVerdict = outfitJson.optString("cohesionVerdict", ""),
                        whatToGetNext = outfitJson.optStringList("whatToGetNext").take(3)
                    )
                } else null
            } else null

            var listTitle = json.optString("shoppingListTitle", "")
            var parsedShoppingList = parseShoppingItems(json.optJSONArray("shoppingList"))

            // Auto-recovery safeguard: if the AI promised items to buy/get but returned an empty list,
            // run the strict Shopping Advisor schema so the user NEVER gets an empty stub.
            if (parsedShoppingList.isEmpty() && parsedOutfit == null && (replyPromisesItems(reply) || isShoppingOrBasicsIntent(userMessage))) {
                val recoveryJson = runShoppingAdvisor(
                    userMessage = userMessage,
                    profile = profile,
                    availableWardrobe = availableWardrobe,
                    weather = weather,
                    dressCode = dressCode,
                    tone = tone,
                    gender = gender,
                    recentHistory = recentHistory,
                    scaledPhoto = scaledPhoto
                )
                val recovered = parseShoppingItems(recoveryJson.optJSONArray("shoppingList"))
                if (recovered.isNotEmpty()) {
                    parsedShoppingList = recovered
                    if (listTitle.isBlank()) {
                        listTitle = recoveryJson.optString("shoppingListTitle", "Essentials & What to Get")
                    }
                    val betterReply = recoveryJson.optString("reply", "").trim()
                    if (betterReply.length > reply.length) {
                        reply = betterReply
                    }
                }
            }

            val hydratedShoppingList = if (parsedShoppingList.isNotEmpty()) {
                referencePhotoService.resolvePhotosForItems(parsedShoppingList, gender)
            } else {
                emptyList()
            }

            StylistChatMessage(
                fromUser = false,
                text = reply,
                outfit = parsedOutfit,
                shoppingListTitle = listTitle,
                shoppingList = hydratedShoppingList
            )
        } finally {
            if (scaledPhoto != null && scaledPhoto !== attachedPhoto) {
                scaledPhoto.recycle()
            }
        }
    }

    private suspend fun runShoppingAdvisor(
        userMessage: String,
        profile: UserProfile,
        availableWardrobe: List<GarmentItem>,
        weather: WeatherState,
        dressCode: DressCodeMode?,
        tone: AiCritiqueTone,
        gender: StyleGender,
        recentHistory: List<StylistChatMessage>,
        scaledPhoto: Bitmap?
    ): JSONObject {
        val isFullBasics = userMessage.lowercase().let {
            it.contains("basic") || it.contains("essential") || it.contains("starter") || it.contains("capsule") || it.contains("all")
        }
        val itemCountTarget = if (isFullBasics) "7 to 9" else "3 to 5"

        val prompt = buildString {
            appendLine("You are Criel, a sharp Indian-market personal stylist and wardrobe buyer.")
            appendLine(toneInstruction(tone))
            appendLine("Always quote realistic Indian market prices in INR (₹) matching brands like Myntra, Ajio, Zara, and H&M.")
            appendLine(styleDnaContext(profile, gender))
            appendLine("Weather context: ${weather.formattedTemp()} ${weather.condition.label}.")
            if (dressCode != null) appendLine("Dress Code: ${dressCode.label} (${dressCode.rules})")
            if (recentHistory.isNotEmpty()) {
                appendLine("Recent conversation:")
                recentHistory.takeLast(3).forEach { msg ->
                    val who = if (msg.fromUser) "User" else "Criel"
                    appendLine("- $who: ${msg.text.take(180)}")
                }
            }
            if (availableWardrobe.isNotEmpty()) {
                appendLine("Already owned wardrobe (DO NOT duplicate basics they already own unless replacing a weak piece): ${compactWardrobeJson(availableWardrobe)}")
            } else {
                appendLine("The user's wardrobe is currently empty — build from foundational staples up.")
            }
            appendLine(
                """
                REQUIREMENTS (STRICT):
                - Strictly respect the user's selected gender/department (${gender.label}) for EVERY item.
                - You MUST return $itemCountTarget concrete items inside 'shoppingList'. NEVER return an empty shoppingList.
                - Spread priorities thoughtfully across 'Must-Have' (most urgent staples first), 'High Impact' (elevating layers/footwear), and 'Nice-to-Have' (finishing touches).
                - For EVERY item, fill ALL fields thoroughly:
                  * name: exact garment name for ${gender.label}
                  * fitAndFabric: specific cut, silhouette, fabric weight/composition, and construction details to look for when shopping
                  * whyYouNeedIt: blunt, practical reason why this piece unlocks outfits or fixes a gap in their closet
                  * photoSearchQuery: simple 3-4 word English clothing noun phrase (NO animal/food words like camel, olive, peach, salmon; e.g. "white cotton t-shirt", "beige chino trousers", "white leather sneakers", "brown leather loafer shoes")
                  * storeSearchQuery: clean search query for Myntra, Ajio, Zara, and H&M starting with '${gender.storePrefix}' when Men/Women
                  * estimatedPriceInr: realistic single number in INR (e.g. 1499, 3499, 4999)
                - In 'reply': give a sharp 2-3 sentence candid summary of the biggest wardrobe gaps and buying strategy (do NOT just say "here are the items" without real insight).
                """.trimIndent()
            )
            appendLine("User request: $userMessage")
        }

        val text = executeWithTimeout {
            if (scaledPhoto != null) {
                shoppingAdvisorModel.generateContent(
                    content {
                        image(scaledPhoto)
                        text(prompt)
                    }
                ).text
            } else {
                shoppingAdvisorModel.generateContent(prompt).text
            }
        }
        return parseJsonObject(text)
    }

    // -------------------------------------------------------------------------
    // 6. "RATE MY FIT" LIVE MIRROR CHECK
    // -------------------------------------------------------------------------
    suspend fun rateMyFit(
        mirrorSelfie: Bitmap,
        occasion: String,
        profile: UserProfile,
        availableWardrobe: List<GarmentItem>,
        tone: AiCritiqueTone = AiCritiqueTone.BRUTALLY_HONEST,
        gender: StyleGender = StyleGender.UNISEX
    ): RateMyFitResult {
        val scaled = prepareVisionBitmap(mirrorSelfie, 512)
        return try {
            val prompt = buildString {
                appendLine("You are Criel performing a mirror-check fit critique for '$occasion'.")
                appendLine(toneInstruction(tone))
                appendLine("Score 0-100 honestly across overallScore, proportionsScore, colorHarmonyScore, occasionMatchScore, styleDnaScore. Do NOT hand out inflated 85+ scores unless the outfit genuinely earns it.")
                appendLine("In cohesionBreakdown: state plainly how well the visible pieces actually go together — call out clashing colors, mismatched formality, awkward hem/fit, or fabric fights.")
                appendLine("In quickTweaks: give 3 concrete 10-second fixes they can do right now.")
                appendLine("In whatToGetToUpgrade: list 2-3 specific pieces they should buy (with realistic INR ₹ price range, matching their gender/department) to noticeably elevate this exact outfit.")
                appendLine(styleDnaContext(profile, gender))
                append("Owned closet JSON for suggestedSwapItemIds: ${compactWardrobeJson(availableWardrobe)}")
            }
            val text = executeWithTimeout {
                rateMyFitModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                ).text
            }
            val json = parseJsonObject(text)
            val validIds = availableWardrobe.map { it.id }.toSet()
            RateMyFitResult(
                overallScore = json.optInt("overallScore", 72).coerceIn(0, 100),
                proportionsScore = json.optInt("proportionsScore", 70).coerceIn(0, 100),
                colorHarmonyScore = json.optInt("colorHarmonyScore", 74).coerceIn(0, 100),
                occasionMatchScore = json.optInt("occasionMatchScore", 72).coerceIn(0, 100),
                styleDnaScore = json.optInt("styleDnaScore", 73).coerceIn(0, 100),
                editorialVerdict = json.optString("editorialVerdict", "Clean base silhouette, but the proportions and contrast need sharper intention."),
                cohesionBreakdown = json.optString("cohesionBreakdown", ""),
                quickTweaks = json.optStringList("quickTweaks").take(3).ifEmpty {
                    listOf("Cuff sleeves to break up boxy volume.", "Tuck the top cleanly to sharpen the waist line.", "Match leather & metal tones across shoes and belt.")
                },
                whatToGetToUpgrade = json.optStringList("whatToGetToUpgrade").take(3),
                suggestedSwapItemIds = json.optStringList("suggestedSwapItemIds").filter { it in validIds }.take(2)
            )
        } finally {
            if (scaled !== mirrorSelfie) scaled.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 7. SWIPEABLE DAILY OOTD DECK
    // -------------------------------------------------------------------------
    suspend fun generateDailyOotdDeck(
        profile: UserProfile,
        availableWardrobe: List<GarmentItem>,
        weather: WeatherState,
        lovedItemIds: Set<String>,
        skippedItemIds: Set<String>,
        tone: AiCritiqueTone = AiCritiqueTone.BRUTALLY_HONEST,
        gender: StyleGender = StyleGender.UNISEX
    ): List<OotdCardItem> {
        val prompt = buildString {
            appendLine("Create 5 distinct daily outfits for ${weather.formattedTemp()} ${weather.condition.label}.")
            appendLine(toneInstruction(tone))
            appendLine("For each outfit, honestly assess how well the pieces pair in rationale, score harmonyScore honestly, and give 1 concrete upgradeBuyTip (a piece to buy with INR ₹ price) that would make the fit stronger.")
            appendLine("${styleDnaContext(profile, gender)} Loved:${lovedItemIds.take(6).joinToString(",")} Skipped:${skippedItemIds.take(6).joinToString(",")}.")
            append("Wardrobe: ${compactWardrobeJson(availableWardrobe)}")
        }
        val text = executeWithTimeout { ootdDeckModel.generateContent(prompt).text }
        val validIds = availableWardrobe.map { it.id }.toSet()
        val arr = parseJsonObject(text).optJSONArray("outfits") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val ids = o.optStringList("itemIds").filter { it in validIds }
            if (ids.isEmpty()) null else OotdCardItem(
                title = o.optString("title", "Daily Edit #${i + 1}"),
                vibe = o.optString("vibe", "Editorial"),
                harmonyScore = o.optInt("harmonyScore", 78).coerceIn(30, 100),
                rationale = o.optString("rationale", "Balanced for today's forecast."),
                itemIds = ids,
                upgradeBuyTip = o.optString("upgradeBuyTip", "")
            )
        }
    }

    // -------------------------------------------------------------------------
    // 8. STUDIO "AI COMPLETE LOOK"
    // -------------------------------------------------------------------------
    suspend fun completeStudioLook(
        lockedSlots: Map<String, GarmentItem>,
        availableWardrobe: List<GarmentItem>,
        profile: UserProfile,
        weather: WeatherState,
        gender: StyleGender = StyleGender.UNISEX
    ): Pair<String, Map<String, String?>> {
        val lockedSummary = lockedSlots.entries.joinToString("; ") { "${it.key}=${it.value.id}(${it.value.name})" }
        val prompt = "Complete unlocked slots around locked: [$lockedSummary]. Weather:${weather.formattedTemp()} ${weather.condition.label}. ${styleDnaContext(profile, gender)} Wardrobe:${compactWardrobeJson(availableWardrobe)}"
        val text = executeWithTimeout { completeLookModel.generateContent(prompt).text }
        val json = parseJsonObject(text)
        val validIds = availableWardrobe.map { it.id }.toSet()
        val slots = GarmentCategory.allLabels.associateWith { cat ->
            val locked = lockedSlots[cat]?.id
            if (locked != null) locked
            else json.optString(cat, "").takeIf { it in validIds }
        }
        return json.optString("title", "Criel Curated Edit") to slots
    }

    // -------------------------------------------------------------------------
    // 9. FREE-TIER AI FIT, DRAPE & SCENE PREVIEW CRITIQUE
    // -------------------------------------------------------------------------
    suspend fun generateLookFitCritique(
        items: List<GarmentItem>,
        sceneBackdrop: String,
        profile: UserProfile,
        virtualModelPhoto: Bitmap?,
        tone: AiCritiqueTone = AiCritiqueTone.BRUTALLY_HONEST,
        gender: StyleGender = StyleGender.UNISEX
    ): LookFitCritique {
        val outfitLines = items.joinToString("; ") { "${it.category}: ${it.name} (${it.colorName}, ${it.fabric}, formality ${it.formality}/5)" }
        val scaledModel = virtualModelPhoto?.let { prepareVisionBitmap(it, 512) }
        return try {
            val prompt = buildString {
                appendLine("Evaluate how well this exact outfit works together, drapes, and fits for a $sceneBackdrop setting.")
                appendLine(toneInstruction(tone))
                appendLine(styleDnaContext(profile, gender))
                appendLine("Ensemble: $outfitLines")
                if (scaledModel != null) {
                    appendLine("Reference the attached Virtual Model photo for proportions and coloring.")
                }
                append("Return blunt headline, honest fitScore (0-100), silhouetteAndDrape, pairingHonesty (how well the pieces go together or clash), sceneFitNote, 3 concrete stylingTips, and 2-3 whatToGetToUpgrade pieces to buy with INR ₹ prices.")
            }
            val text = executeWithTimeout {
                lookFitCritiqueModel.generateContent(
                    content {
                        if (scaledModel != null) image(scaledModel)
                        text(prompt)
                    }
                ).text
            }
            val json = parseJsonObject(text)
            LookFitCritique(
                headline = json.optString("headline", "$sceneBackdrop Editorial Preview"),
                fitScore = json.optInt("fitScore", 76).coerceIn(20, 100),
                silhouetteAndDrape = json.optString("silhouetteAndDrape", "Balanced layering aligned with your silhouette preference."),
                pairingHonesty = json.optString("pairingHonesty", ""),
                sceneFitNote = json.optString("sceneFitNote", "Evaluated for a $sceneBackdrop setting."),
                stylingTips = json.optStringList("stylingTips").take(3),
                whatToGetToUpgrade = json.optStringList("whatToGetToUpgrade").take(3)
            )
        } finally {
            if (scaledModel != null && scaledModel !== virtualModelPhoto) scaledModel.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 10. "SHOULD I BUY THIS?" SCANNER
    // -------------------------------------------------------------------------
    suspend fun evaluateProspectivePurchase(
        productPhoto: Bitmap?,
        itemDescription: String,
        priceInr: Double,
        profile: UserProfile,
        wardrobe: List<GarmentItem>,
        tone: AiCritiqueTone = AiCritiqueTone.BRUTALLY_HONEST,
        gender: StyleGender = StyleGender.UNISEX
    ): ShouldIBuyResult {
        val scaled = productPhoto?.let { prepareVisionBitmap(it, 512) }
        return try {
            val prompt = buildString {
                appendLine("Evaluate buying '${itemDescription.ifBlank { "garment in photo" }}' at ${formatInr(priceInr)} (INR).")
                appendLine(toneInstruction(tone))
                appendLine("${styleDnaContext(profile, gender)} Closet:${compactWardrobeJson(wardrobe)}.")
                append("Return BUY, WAIT, or SKIP with duplicateWarning, outfitMultiplierCount, 3 unlockedOutfits, projectedCpw in INR (e.g. ₹180/wear), and a candid rationale on whether it is actually worth the rupees.")
            }
            val text = executeWithTimeout {
                shouldIBuyModel.generateContent(
                    content {
                        if (scaled != null) image(scaled)
                        text(prompt)
                    }
                ).text
            }
            val json = parseJsonObject(text)
            val rawVerdict = json.optString("verdict", "WAIT").uppercase()
            val verdict = when {
                rawVerdict.contains("BUY") -> "BUY"
                rawVerdict.contains("SKIP") -> "SKIP"
                else -> "WAIT"
            }
            ShouldIBuyResult(
                itemName = json.optString("itemName", itemDescription.ifBlank { "Scanned Item" }),
                category = GarmentCategory.fromLabel(json.optString("category")).label,
                colorName = json.optString("colorName", "Neutral"),
                colorHex = normalizeHex(json.optString("colorHex", "#33353A")),
                verdict = verdict,
                seasonalMatch = json.optString("seasonalMatch", "Aligned with ${profile.seasonPalette}"),
                duplicateWarning = json.optString("duplicateWarning", "No direct duplicate detected."),
                outfitMultiplierCount = json.optInt("outfitMultiplierCount", 4).coerceAtLeast(0),
                unlockedOutfits = json.optStringList("unlockedOutfits").take(4),
                projectedCpw = json.optString("projectedCpw", "${formatInr((priceInr / 20.0).coerceAtLeast(1.0))}/wear"),
                rationale = json.optString("rationale", "Evaluated honestly against your Style DNA and closet.")
            )
        } finally {
            if (scaled != null && scaled !== productPhoto) scaled.recycle()
        }
    }

    // -------------------------------------------------------------------------
    // 11. WARDROBE GAP ANALYZER
    // -------------------------------------------------------------------------
    suspend fun analyzeWardrobeGaps(
        profile: UserProfile,
        wardrobe: List<GarmentItem>,
        gender: StyleGender = StyleGender.UNISEX
    ): List<GapRecommendation> {
        val prompt = if (wardrobe.isEmpty()) {
            "The user is starting a fresh wardrobe from scratch. Recommend the Top 5 foundational capsule pieces with realistic Indian Rupee (INR) prices for their Style DNA: ${styleDnaContext(profile, gender)}"
        } else {
            "Be brutally honest about what is missing from this closet. Recommend the Top 5 highest-impact Multiplier Pieces to buy with realistic Indian Rupee (INR) prices. ${styleDnaContext(profile, gender)} Closet:${compactWardrobeJson(wardrobe)}"
        }
        val text = executeWithTimeout { gapAnalysisModel.generateContent(prompt).text }
        val arr = parseJsonObject(text).optJSONArray("recommendations") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GapRecommendation(
                name = o.optString("name", "Capsule Piece"),
                category = GarmentCategory.fromLabel(o.optString("category")).label,
                colorName = o.optString("colorName", "Neutral"),
                colorHex = normalizeHex(o.optString("colorHex", "#3E4F3C")),
                price = o.optDouble("price", 3500.0).let { if (it.isNaN()) 3500.0 else it },
                unlocks = o.optString("unlocks", "+6 outfits"),
                reason = o.optString("reason", "High cross-mix versatility.")
            )
        }
    }

    // -------------------------------------------------------------------------
    // 12. DECLUTTER & RESALE LISTING GENERATOR
    // -------------------------------------------------------------------------
    suspend fun generateResaleListing(garment: GarmentItem): ResaleListingResult {
        val prompt = "Write a luxury resale listing (priced in Indian Rupees ₹ INR) for: ${garment.brand} ${garment.name}, ${garment.category}, ${garment.fabric}, ${garment.colorName}, retail ${formatInr(garment.price)}, ${garment.wears} wears."
        val text = executeWithTimeout { resaleModel.generateContent(prompt).text }
        val json = parseJsonObject(text)
        return ResaleListingResult(
            garmentId = garment.id,
            estimatedResalePrice = json.optString("estimatedResalePrice", formatInr(garment.price * 0.45)),
            title = json.optString("title", "${garment.brand} ${garment.name}"),
            description = json.optString("description", "Timeless archival piece in excellent condition."),
            measurementsPrompt = json.optString("measurementsPrompt", "Pit-to-pit, shoulder width, total length."),
            hashtags = json.optStringList("hashtags")
        )
    }

    // -------------------------------------------------------------------------
    // 13. SMART CAPSULE TRIP PLANNER
    // -------------------------------------------------------------------------
    suspend fun generateCapsuleTripPlan(
        destination: String,
        days: Int,
        climate: String,
        activities: String,
        bagSize: String,
        profile: UserProfile,
        availableWardrobe: List<GarmentItem>
    ): Pair<List<String>, List<String>> {
        val prompt = "Build a $days-day $bagSize capsule for $destination ($climate; $activities). ${styleDnaContext(profile)} Wardrobe:${compactWardrobeJson(availableWardrobe)}"
        val text = executeWithTimeout { tripPlanModel.generateContent(prompt).text }
        val json = parseJsonObject(text)
        val validIds = availableWardrobe.map { it.id }.toSet()
        val packIds = json.optStringList("packingItemIds").filter { it in validIds }.distinct()
        val itineraryArr = json.optJSONArray("itinerary") ?: JSONArray()
        val lines = (0 until itineraryArr.length()).map { idx ->
            val d = itineraryArr.getJSONObject(idx)
            val dayNum = d.optInt("day", idx + 1)
            val occ = d.optString("occasion", "Day $dayNum").replace("|", "-")
            val ids = d.optStringList("itemIds").filter { it in validIds }.joinToString(",")
            val note = d.optString("note", "Capsule layering.").replace("|", "-")
            "Day $dayNum|$occ|$ids|$note"
        }
        return packIds to lines
    }

    // -------------------------------------------------------------------------
    // INTERNAL HELPERS
    // -------------------------------------------------------------------------
    private suspend fun executeWithTimeout(block: suspend () -> String?): String {
        try {
            val result = withTimeout(AI_TIMEOUT_MS) { block() }
            if (result.isNullOrBlank()) {
                throw IllegalStateException("AI returned an empty response. Please retry.")
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Firebase AI call failed", e)
            throw e
        }
    }

    private fun parseJsonObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JSONObject(trimmed.substring(start, end + 1))
            } else {
                throw IllegalStateException("Could not parse AI response.")
            }
        }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }

    private fun normalizeHex(raw: String?): String {
        val clean = (raw ?: "#141414").trim().removePrefix("#")
        return if (clean.matches(Regex("^[0-9A-Fa-f]{6}$"))) "#${clean.uppercase()}" else "#141414"
    }
}
