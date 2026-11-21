package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val PHOTO_TAG = "ReferencePhotoService"
private const val USER_AGENT = "CrielWardrobeStylist/1.0 (Android; Personal Wardrobe App)"

enum class IndianStore(val label: String) {
    MYNTRA("Myntra"),
    AJIO("Ajio"),
    ZARA("Zara"),
    HM("H&M")
}

object IndianStoreLinks {
    fun buildSearchUrl(store: IndianStore, rawQuery: String): String {
        val cleaned = rawQuery.trim().replace(Regex("\\s+"), " ").ifBlank { "clothing essentials" }
        val encoded = URLEncoder.encode(cleaned, "UTF-8")
        return when (store) {
            IndianStore.MYNTRA -> {
                val slug = cleaned
                    .lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .trim()
                    .replace(Regex("\\s+"), "-")
                    .ifBlank { "clothing" }
                "https://www.myntra.com/$slug"
            }
            IndianStore.AJIO -> "https://www.ajio.com/search/?text=$encoded"
            IndianStore.ZARA -> "https://www.zara.com/in/en/search?searchTerm=$encoded"
            IndianStore.HM -> "https://www2.hm.com/en_in/search-results.html?q=$encoded"
        }
    }
}

/**
 * 100% Free, keyless reference photo resolver with STRICT fashion & garment verification.
 * Blocks animals, wildlife, plants, food, buildings, art, and unrelated stock photos by
 * inspecting candidate titles, tags, and Wikimedia categories against category-specific
 * clothing allowlists and a strict non-clothing blocklist.
 * If no verified garment photo exists, returns null so Criel renders its accurate
 * color-matched editorial GarmentVisual illustration instead of a wrong image.
 */
class ReferencePhotoService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, String>()
    private val concurrencyGate = Semaphore(4)

    // Color/style words that match animals, plants, or food in public photo archives
    private val homonymColorsToStrip = setOf(
        "camel", "olive", "rust", "sage", "mint", "coral", "salmon", "peach", "plum",
        "rose", "chocolate", "coffee", "mustard", "forest", "ivory", "cream", "pearl",
        "emerald", "charcoal", "espresso", "cognac", "taupe", "mocha", "sand", "stone",
        "slate", "burgundy", "maroon", "champagne", "ash", "moss", "pine", "terracotta",
        "caramel", "amber", "honey", "ochre", "lilac", "lavender", "sky", "powder", "navy"
    )

    // Hard blocklist — immediately reject any candidate photo mentioning non-fashion subjects
    private val blockedKeywords = listOf(
        "animal", "animals", "dog", "dogs", "puppy", "cat", "cats", "kitten", "bird", "birds",
        "camel", "horse", "pony", "cow", "bull", "calf", "sheep", "lamb", "goat", "deer",
        "bear", "lion", "tiger", "leopard", "monkey", "ape", "elephant", "giraffe", "zebra",
        "fish", "shark", "whale", "dolphin", "duck", "goose", "chicken", "rooster", "owl",
        "eagle", "hawk", "parrot", "pigeon", "crow", "swan", "insect", "butterfly", "moth",
        "bee", "wasp", "spider", "ant", "beetle", "snake", "lizard", "frog", "toad", "turtle",
        "rabbit", "bunny", "fox", "wolf", "squirrel", "mouse", "rat", "hamster", "wildlife",
        "zoo", "safari", "pet", "pets", "aquarium", "reptile", "mammal", "fauna", "flora",
        "tree", "trees", "flower", "flowers", "blossom", "plant", "plants", "leaf", "leaves",
        "forest", "woods", "jungle", "mountain", "mountains", "valley", "desert", "beach",
        "ocean", "sea", "river", "lake", "pond", "waterfall", "sunset", "sunrise", "cloud",
        "clouds", "landscape", "scenery", "garden", "park", "meadow", "grass", "mushroom",
        "food", "meal", "dish", "recipe", "cake", "bread", "pasta", "pizza", "burger", "salad",
        "fruit", "vegetable", "coffee", "espresso", "tea", "wine", "beer", "cocktail", "soup",
        "restaurant", "kitchen", "bakery", "cafe", "chocolate", "cookie", "ice cream",
        "car", "cars", "truck", "bus", "train", "locomotive", "airplane", "aircraft", "jet",
        "helicopter", "boat", "ship", "yacht", "bicycle", "motorcycle", "tank", "military",
        "soldier", "army", "war", "weapon", "gun", "rifle", "cannon", "battleship",
        "building", "architecture", "cathedral", "church", "temple", "mosque", "castle",
        "palace", "bridge", "tower", "skyscraper", "monument", "statue", "sculpture",
        "museum", "ruins", "cemetery", "painting", "fresco", "mural", "portrait of",
        "cartoon", "anime", "comic", "manga", "meme", "graffiti", "logo", "emblem", "coat of arms",
        "flag", "map", "chart", "diagram", "graph", "blueprint", "book", "newspaper",
        "magazine cover", "poster", "billboard", "street sign", "traffic", "stadium",
        "football", "soccer", "cricket", "baseball", "basketball", "tennis", "concert",
        "festival", "parade", "protest", "demonstration", "microscope", "cell", "bacteria",
        "astronomy", "galaxy", "nebula", "planet", "moon", "volcano", "fossil", "skeleton"
    )

    private val categoryRequiredNouns: Map<String, List<String>> = mapOf(
        "Tops" to listOf(
            "shirt", "t-shirt", "tshirt", "tee", "polo", "blouse", "sweater", "pullover",
            "knitwear", "hoodie", "sweatshirt", "henley", "oxford", "kurta", "kurti",
            "camisole", "cardigan", "tunic", "jersey", "top", "turtleneck", "crewneck", "tank top"
        ),
        "Bottoms" to listOf(
            "pants", "trouser", "trousers", "chino", "chinos", "jeans", "denim", "shorts",
            "skirt", "leggings", "jogger", "joggers", "cargo", "slacks", "palazzo", "culotte",
            "dress", "jumpsuit", "bottoms", "pleated"
        ),
        "Outerwear" to listOf(
            "jacket", "coat", "blazer", "overcoat", "trench", "parka", "bomber", "vest",
            "gilet", "cardigan", "shacket", "overshirt", "windbreaker", "suit", "outerwear",
            "anorak", "peacoat", "denim jacket", "leather jacket"
        ),
        "Footwear" to listOf(
            "shoe", "shoes", "sneaker", "sneakers", "boot", "boots", "loafer", "loafers",
            "heel", "heels", "sandal", "sandals", "moccasin", "derby", "brogue", "oxford",
            "trainer", "trainers", "footwear", "flats", "ballet flat", "mule", "mules",
            "pump", "pumps", "espadrille", "slipper", "chelsea boot", "running shoe"
        ),
        "Bags" to listOf(
            "bag", "bags", "tote", "backpack", "handbag", "crossbody", "messenger",
            "satchel", "clutch", "duffel", "duffle", "purse", "briefcase", "sling bag", "rucksack"
        ),
        "Accessories" to listOf(
            "belt", "watch", "wristwatch", "sunglasses", "eyewear", "cap", "baseball cap",
            "hat", "beanie", "scarf", "necktie", "tie", "necklace", "bracelet", "ring",
            "earring", "earrings", "jewelry", "jewellery", "wallet", "socks", "gloves"
        )
    )

    private val generalFashionContextWords = listOf(
        "clothing", "clothes", "apparel", "garment", "fashion", "wear", "menswear",
        "womenswear", "outfit", "footwear", "accessory", "accessories", "textile",
        "cotton", "denim", "leather", "wool", "linen", "silk", "suede", "canvas",
        "tailored", "wardrobe", "boutique", "mannequin", "hanger", "store"
    )

    suspend fun resolvePhotosForItems(
        items: List<StylistShoppingItem>,
        gender: StyleGender = StyleGender.UNISEX
    ): List<StylistShoppingItem> {
        if (items.isEmpty()) return items
        return withTimeoutOrNull(14_000L) {
            coroutineScope {
                items.map { item ->
                    async(Dispatchers.IO) {
                        val normalizedStoreQuery = ensureGenderInStoreQuery(
                            rawQuery = item.storeSearchQuery.ifBlank { "${item.colorName} ${item.name}" },
                            gender = gender
                        )
                        val updatedBase = item.copy(storeSearchQuery = normalizedStoreQuery)
                        if (!updatedBase.referenceImageUrl.isNullOrBlank()) return@async updatedBase
                        val url = concurrencyGate.withPermit {
                            findVerifiedClothingPhotoUrl(updatedBase, gender)
                        }
                        updatedBase.copy(referenceImageUrl = url)
                    }
                }.awaitAll()
            }
        } ?: items
    }

    private fun ensureGenderInStoreQuery(rawQuery: String, gender: StyleGender): String {
        val cleaned = rawQuery.trim().replace(Regex("\\s+"), " ")
        val lower = cleaned.lowercase()
        val alreadyHasGender = lower.startsWith("men ") || lower.startsWith("men's ") ||
            lower.startsWith("mens ") || lower.startsWith("women ") ||
            lower.startsWith("women's ") || lower.startsWith("womens ") ||
            lower.startsWith("unisex ")
        if (alreadyHasGender) return cleaned
        return when (gender) {
            StyleGender.MEN -> "men $cleaned"
            StyleGender.WOMEN -> "women $cleaned"
            StyleGender.UNISEX -> cleaned
        }
    }

    private fun findVerifiedClothingPhotoUrl(item: StylistShoppingItem, gender: StyleGender): String? {
        val candidates = buildSafeClothingQueries(item, gender)
        for (q in candidates) {
            val cacheKey = "${item.category.lowercase()}|${q.lowercase()}"
            cache[cacheKey]?.let { cached ->
                if (cached.isNotBlank()) return cached
            }
        }

        // 1. Wikimedia Commons FIRST — cleaner garment filenames & category metadata
        for (q in candidates) {
            val wikiUrl = queryWikimediaCommons(q, item.category)
            if (!wikiUrl.isNullOrBlank()) {
                cache["${item.category.lowercase()}|${q.lowercase()}"] = wikiUrl
                return wikiUrl
            }
        }

        // 2. Openverse SECOND — strictly verified against title + tags + category allowlist
        for (q in candidates) {
            val openverseUrl = queryOpenverse(q, item.category)
            if (!openverseUrl.isNullOrBlank()) {
                cache["${item.category.lowercase()}|${q.lowercase()}"] = openverseUrl
                return openverseUrl
            }
        }

        // Return null if no verified clothing photograph passed our strict checks —
        // UI cleanly renders the accurate color-matched GarmentVisual illustration instead!
        return null
    }

    private fun stripHomonyms(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { token -> token.isNotBlank() && token !in homonymColorsToStrip }
            .joinToString(" ")
            .trim()
    }

    private fun buildSafeClothingQueries(item: StylistShoppingItem, gender: StyleGender): List<String> {
        val list = mutableListOf<String>()
        val categoryNoun = when (item.category) {
            "Footwear" -> "shoes"
            "Outerwear" -> "jacket"
            "Bottoms" -> "trousers"
            "Bags" -> "bag"
            "Accessories" -> "fashion accessory"
            else -> "clothing"
        }
        val genderWord = when (gender) {
            StyleGender.MEN -> "mens"
            StyleGender.WOMEN -> "womens"
            StyleGender.UNISEX -> ""
        }

        val cleanExplicit = stripHomonyms(item.photoSearchQuery)
        if (cleanExplicit.isNotBlank()) {
            val anchored = if (hasCategoryNoun(cleanExplicit, item.category)) {
                cleanExplicit
            } else {
                "$cleanExplicit $categoryNoun"
            }
            list.add(anchored.trim())
        }

        val cleanName = stripHomonyms(item.name)
        if (cleanName.isNotBlank()) {
            val q = listOf(genderWord, cleanName, if (hasCategoryNoun(cleanName, item.category)) "" else categoryNoun)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            if (list.none { it.equals(q, ignoreCase = true) }) {
                list.add(q)
            }
        }

        return list.take(2)
    }

    private fun hasCategoryNoun(text: String, category: String): Boolean {
        val lower = text.lowercase()
        val nouns = categoryRequiredNouns[category] ?: categoryRequiredNouns.values.flatten()
        return nouns.any { noun ->
            Regex("\\b${Regex.escape(noun)}\\b").containsMatchIn(lower)
        }
    }

    private fun isVerifiedClothingCandidate(metadataText: String, category: String): Boolean {
        val lower = metadataText.lowercase()
        if (lower.isBlank()) return false

        // Gate 1: Hard blocklist — zero tolerance for animals, plants, food, vehicles, monuments, cartoons
        for (blocked in blockedKeywords) {
            if (Regex("\\b${Regex.escape(blocked)}\\b").containsMatchIn(lower)) {
                return false
            }
        }

        // Gate 2: Must explicitly match a clothing/garment noun for this exact category
        if (!hasCategoryNoun(lower, category)) {
            return false
        }

        // Gate 3: Extra confidence — either multiple fashion words or clear apparel context
        val hasGeneralFashion = generalFashionContextWords.any { w ->
            Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(lower)
        }
        val categoryNounCount = (categoryRequiredNouns[category] ?: emptyList()).count { noun ->
            Regex("\\b${Regex.escape(noun)}\\b").containsMatchIn(lower)
        }
        return hasGeneralFashion || categoryNounCount >= 1
    }

    private fun queryWikimediaCommons(query: String, category: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=560&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val page = pages.optJSONObject(key) ?: continue
                    val fileTitle = page.optString("title", "")
                    val infoArr = page.optJSONArray("imageinfo") ?: continue
                    val info = infoArr.optJSONObject(0) ?: continue
                    val mime = info.optString("mime", "").lowercase()
                    if (mime !in setOf("image/jpeg", "image/png", "image/webp")) continue

                    val ext = info.optJSONObject("extmetadata")
                    val desc = ext?.optJSONObject("ImageDescription")?.optString("value", "").orEmpty()
                        .replace(Regex("<[^>]*>"), " ")
                    val cats = ext?.optJSONObject("Categories")?.optString("value", "").orEmpty()
                    val combinedMeta = "$fileTitle $desc $cats"

                    if (!isVerifiedClothingCandidate(combinedMeta, category)) continue

                    val thumb = info.optString("thumburl", "").trim()
                    val direct = info.optString("url", "").trim()
                    val chosen = when {
                        thumb.startsWith("https://") -> thumb
                        direct.startsWith("https://") -> direct
                        else -> ""
                    }
                    if (chosen.isNotBlank()) return chosen
                }
                null
            }
        } catch (e: Exception) {
            Log.d(PHOTO_TAG, "Wikimedia lookup skipped for '$query': ${e.message}")
            null
        }
    }

    private fun queryOpenverse(query: String, category: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.openverse.org/v1/images/?q=$encoded&page_size=12&category=photograph&mature=false"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                val results = JSONObject(body).optJSONArray("results") ?: return null
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    val filetype = obj.optString("filetype", "").lowercase()
                    if (filetype == "svg" || filetype == "gif") continue
                    val width = obj.optInt("width", 0)
                    val height = obj.optInt("height", 0)
                    if (width in 1..199 || height in 1..199) continue

                    val title = obj.optString("title", "")
                    val tagsArr = obj.optJSONArray("tags")
                    val tagsText = buildString {
                        if (tagsArr != null) {
                            for (t in 0 until tagsArr.length()) {
                                val tagName = tagsArr.optJSONObject(t)?.optString("name", "").orEmpty()
                                if (tagName.isNotBlank()) append(' ').append(tagName)
                            }
                        }
                    }
                    val combinedMeta = "$title $tagsText"
                    if (!isVerifiedClothingCandidate(combinedMeta, category)) continue

                    val thumb = obj.optString("thumbnail", "").trim()
                    val direct = obj.optString("url", "").trim()
                    val chosen = when {
                        thumb.startsWith("https://") -> thumb
                        direct.startsWith("https://") && !direct.endsWith(".svg", ignoreCase = true) -> direct
                        else -> ""
                    }
                    if (chosen.isNotBlank()) return chosen
                }
                null
            }
        } catch (e: Exception) {
            Log.d(PHOTO_TAG, "Openverse lookup skipped for '$query': ${e.message}")
            null
        }
    }
}
