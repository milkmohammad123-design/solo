package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AtelierViewModel
import java.io.File

@Composable
fun CurateScreen(
    viewModel: AtelierViewModel,
    garments: List<GarmentItem>,
    wishlist: List<WishlistItem>,
    trips: List<TripPlan>,
    modifier: Modifier = Modifier
) {
    val curate by viewModel.curate.collectAsState()
    val analytics = remember(garments) { ColorTheoryEngine.computeClosetAnalytics(garments) }
    val garmentMap = remember(garments) { garments.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialSectionHeader(
            eyebrow = "SMART SHOPPING & CAPSULES",
            title = "Curate",
            subtitle = "Purchase intelligence, wardrobe gaps, resale listings & trip packing"
        )

        EditorialPillTabs(
            tabs = listOf("Should I Buy?", "Gaps & Wishlist", "Declutter", "Pack My Bag"),
            selectedIndex = curate.subTab,
            onSelect = { viewModel.setCurateSubTab(it) }
        )

        when (curate.subTab) {
            0 -> ShouldIBuyTab(viewModel = viewModel, state = curate, modifier = Modifier.weight(1f))
            1 -> GapsAndWishlistTab(
                viewModel = viewModel,
                state = curate,
                analytics = analytics,
                wishlist = wishlist,
                modifier = Modifier.weight(1f)
            )
            2 -> DeclutterTab(
                viewModel = viewModel,
                state = curate,
                staleItems = analytics.staleOrUnwornItems,
                modifier = Modifier.weight(1f)
            )
            else -> PackMyBagTab(
                viewModel = viewModel,
                state = curate,
                trips = trips,
                garmentMap = garmentMap,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShouldIBuyTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.CurateState,
    modifier: Modifier = Modifier
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("260") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedUri = uri }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("\"Should I Buy This?\" Scanner", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Upload a product photo or describe a prospective purchase. Gemini checks for duplicates, calculates Outfit Multiplier combos, projected CPW, and Seasonal Palette match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("buy_scan_photo_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedUri != null) "Change Photo" else "Product Photo")
                        }
                        if (selectedUri != null) {
                            AsyncImage(
                                model = selectedUri,
                                contentDescription = "Selected product",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Item Description") },
                        placeholder = { Text("e.g. Deep olive merino knit polo, relaxed drape") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("buy_scan_description_input")
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Retail Price (₹ INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (selectedUri == null && description.isBlank()) {
                                viewModel.showToast("Add a photo or description first")
                                return@Button
                            }
                            viewModel.scanProspectivePurchase(
                                photoUri = selectedUri,
                                description = description.trim(),
                                priceInr = priceText.toDoubleOrNull() ?: 3500.0
                            )
                        },
                        enabled = !state.buyScanLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("run_buy_scan_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Against My Closet")
                    }
                }
            }
        }

        if (state.buyScanLoading) {
            item {
                EditorialLoadingCard(
                    title = "Evaluating Purchase Value",
                    subtitle = "Checking duplicates, outfit multiplier combos, and seasonal harmony..."
                )
            }
        }

        if (!state.buyScanError.isNullOrBlank()) {
            item { EditorialErrorCard(message = state.buyScanError) }
        }

        val res = state.buyScanResult
        if (res != null) {
            item {
                val (badgeBg, badgeFg) = when (res.verdict.uppercase()) {
                    "BUY" -> EditorialEmerald.copy(alpha = 0.16f) to EditorialEmerald
                    "SKIP" -> EditorialRose.copy(alpha = 0.16f) to EditorialRose
                    else -> EditorialAmber.copy(alpha = 0.18f) to EditorialAmber
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${res.category.uppercase()} · ${res.colorName.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(res.itemName, style = MaterialTheme.typography.headlineSmall)
                            }
                            Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                                Text(
                                    text = res.verdict.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill("MULTIPLIER", "+${res.outfitMultiplierCount} outfits", Modifier.weight(1f))
                            InfoPill("PROJECTED CPW", res.projectedCpw, Modifier.weight(1f))
                        }

                        Text("Seasonal Match: ${res.seasonalMatch}", style = MaterialTheme.typography.labelLarge, color = Champagne)
                        Text("Duplicate Check: ${res.duplicateWarning}", style = MaterialTheme.typography.bodySmall)
                        Text(res.rationale, style = MaterialTheme.typography.bodyMedium)

                        if (res.unlockedOutfits.isNotEmpty()) {
                            Text("UNLOCKED CLOSET COMBINATIONS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            res.unlockedOutfits.forEach { combo ->
                                Text("• $combo", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveScanResultToWishlist(
                                        result = res,
                                        price = priceText.toDoubleOrNull() ?: 200.0,
                                        testNow = false
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_save_wishlist_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Save Wishlist")
                            }
                            Button(
                                onClick = {
                                    viewModel.saveScanResultToWishlist(
                                        result = res,
                                        price = priceText.toDoubleOrNull() ?: 200.0,
                                        testNow = true
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_test_studio_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Test in Studio")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapsAndWishlistTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.CurateState,
    analytics: ClosetAnalyticsMetrics,
    wishlist: List<WishlistItem>,
    modifier: Modifier = Modifier
) {
    var showAddWishlistDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // Versatility & Neutral-to-Accent Meter
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WARDROBE GAP ANALYZER", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text("Versatility Score: ${analytics.versatilityScore}/100", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Neutrals ${analytics.neutralPercent}% · Accents ${analytics.accentPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ScoreRing(score = analytics.versatilityScore, label = "Versatility", ringSize = 62.dp)
                    }

                    LinearProgressIndicator(
                        progress = { (analytics.neutralPercent / 100f).coerceIn(0f, 1f) },
                        color = Champagne,
                        trackColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )

                    Button(
                        onClick = { viewModel.runWardrobeGapAnalysis() },
                        enabled = !state.gapsLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("find_multiplier_pieces_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Find Top 5 Multiplier Pieces")
                    }
                }
            }
        }

        if (state.gapsLoading) {
            item {
                EditorialLoadingCard(
                    title = "Auditing Wardrobe Matrix",
                    subtitle = "Computing the 5 missing pieces with highest outfit-multiplication ROI..."
                )
            }
        }

        if (!state.gapsError.isNullOrBlank()) {
            item { EditorialErrorCard(message = state.gapsError) }
        }

        if (state.gapRecommendations.isNotEmpty()) {
            item {
                Text("TOP 5 MULTIPLIER RECOMMENDATIONS", style = MaterialTheme.typography.labelSmall, color = Champagne)
            }
            items(state.gapRecommendations) { rec ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rec.category.uppercase()} · ${rec.colorName.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(rec.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${formatInr(rec.price)} · ${rec.unlocks}",
                                style = MaterialTheme.typography.labelLarge,
                                color = EditorialEmerald
                            )
                        }
                        Text(rec.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.addGapRecommendationToWishlist(rec, testInStudio = false) },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Wishlist") }
                            Button(
                                onClick = { viewModel.addGapRecommendationToWishlist(rec, testInStudio = true) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Test in Studio") }
                        }
                    }
                }
            }
        }

        // Wishlist Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CURATED WISHLIST (${wishlist.size})", style = MaterialTheme.typography.labelSmall, color = Champagne)
                TextButton(
                    onClick = { showAddWishlistDialog = true },
                    modifier = Modifier.testTag("add_wishlist_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item")
                }
            }
        }

        items(wishlist, key = { it.id }) { item ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GarmentVisual(
                            category = item.category,
                            colorHex = item.colorHex,
                            name = item.name,
                            localImagePath = item.localImagePath,
                            modifier = Modifier
                                .size(62.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.brand.uppercase()} · ${item.category.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${formatInr(item.price)} ${if (item.unlocks.isNotBlank()) "· ${item.unlocks}" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EditorialEmerald
                            )
                        }
                        IconButton(onClick = { viewModel.deleteWishlistItem(item.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove wishlist item")
                        }
                    }
                    if (item.reason.isNotBlank()) {
                        Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.testWishlistItemInStudio(item) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Test in Studio") }
                        Button(
                            onClick = { viewModel.markWishlistBought(item) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Mark Bought") }
                    }
                }
            }
        }
    }

    if (showAddWishlistDialog) {
        var wName by remember { mutableStateOf("") }
        var wCategory by remember { mutableStateOf("Tops") }
        var wColorName by remember { mutableStateOf("Espresso") }
        var wPrice by remember { mutableStateOf("3500") }
        var wReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddWishlistDialog = false },
            title = { Text("Add to Wishlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = wName, onValueChange = { wName = it }, label = { Text("Piece Name") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(GarmentCategory.allLabels) { cat ->
                            FilterChip(selected = wCategory == cat, onClick = { wCategory = cat }, label = { Text(cat) })
                        }
                    }
                    OutlinedTextField(value = wColorName, onValueChange = { wColorName = it }, label = { Text("Color") }, singleLine = true)
                    OutlinedTextField(
                        value = wPrice,
                        onValueChange = { wPrice = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Price (₹ INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(value = wReason, onValueChange = { wReason = it }, label = { Text("Why this piece?") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (wName.isNotBlank()) {
                        viewModel.addManualWishlistItem(
                            name = wName.trim(),
                            category = wCategory,
                            colorName = wColorName.trim(),
                            colorHex = "#4A3728",
                            price = wPrice.toDoubleOrNull() ?: 0.0,
                            reason = wReason.trim()
                        )
                        showAddWishlistDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWishlistDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeclutterTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.CurateState,
    staleItems: List<GarmentItem>,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DECLUTTER & RESALE ASSISTANT", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    Text("${staleItems.size} Unworn or 90+ Day Candidates", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Pieces with zero wears or unworn for 90+ days are flagged below. Generate copyable luxury resale listings with estimated resale valuation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!state.resaleError.isNullOrBlank()) {
            item { EditorialErrorCard(message = state.resaleError) }
        }

        if (staleItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, EditorialEmerald.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, tint = EditorialEmerald, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Zero Closet Deadweight", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Every piece in your wardrobe has been actively worn within the last 90 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(staleItems, key = { it.id }) { item ->
                val listing = state.resaleListings[item.id]
                val busy = state.resaleLoadingId == item.id

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GarmentVisual(
                                item = item,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.brand.uppercase(), style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Retail ${formatInr(item.price)} · ${item.wears} wears",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EditorialAmber
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.generateResaleListing(item) },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .testTag("draft_resale_${item.id}"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Drafting Resale Listing...")
                            } else {
                                Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (listing == null) "Draft Resale Listing" else "Regenerate Listing")
                            }
                        }

                        if (listing != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "EST. RESALE: ${listing.estimatedResalePrice}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = EditorialEmerald
                                        )
                                        TextButton(
                                            onClick = {
                                                val fullCopy = buildString {
                                                    appendLine(listing.title)
                                                    appendLine("Price: ${listing.estimatedResalePrice}")
                                                    appendLine()
                                                    appendLine(listing.description)
                                                    appendLine()
                                                    appendLine("Measurements: ${listing.measurementsPrompt}")
                                                    appendLine(listing.hashtags.joinToString(" "))
                                                }
                                                clipboard.setText(AnnotatedString(fullCopy))
                                                viewModel.showToast("Resale listing copied to clipboard")
                                            },
                                            modifier = Modifier.testTag("copy_resale_${item.id}")
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy")
                                        }
                                    }
                                    Text(listing.title, style = MaterialTheme.typography.titleSmall)
                                    Text(listing.description, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Measurements to add: ${listing.measurementsPrompt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (listing.hashtags.isNotEmpty()) {
                                        Text(
                                            listing.hashtags.joinToString(" "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Champagne
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackMyBagTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.CurateState,
    trips: List<TripPlan>,
    garmentMap: Map<String, GarmentItem>,
    modifier: Modifier = Modifier
) {
    var destination by remember { mutableStateOf("Paris") }
    var daysText by remember { mutableStateOf("4") }
    var climate by remember { mutableStateOf("Cool & Crisp 13°C, Light Rain") }
    var activities by remember { mutableStateOf("Art galleries, client lunch, evening bistro") }
    var bagSize by remember { mutableStateOf("Carry-on") }

    val activeTrip = trips.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Smart Capsule Trip Planner", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            label = { Text("Destination") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("trip_destination_input")
                        )
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter { c -> c.isDigit() } },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    OutlinedTextField(
                        value = climate,
                        onValueChange = { climate = it },
                        label = { Text("Climate & Forecast") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = activities,
                        onValueChange = { activities = it },
                        label = { Text("Activities & Dress Codes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Carry-on", "Checked").forEach { size ->
                            FilterChip(
                                selected = bagSize == size,
                                onClick = { bagSize = size },
                                label = { Text(size) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.generateTripPlan(
                                destination = destination.trim().ifBlank { "Weekend City" },
                                days = (daysText.toIntOrNull() ?: 4).coerceIn(1, 21),
                                climate = climate.trim(),
                                activities = activities.trim(),
                                bagSize = bagSize
                            )
                        },
                        enabled = !state.tripLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("generate_trip_capsule_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Luggage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pack My Capsule Bag")
                    }
                }
            }
        }

        if (state.tripLoading) {
            item {
                EditorialLoadingCard(
                    title = "Building Cross-Mixing Capsule",
                    subtitle = "Minimizing luggage weight while maximizing daily outfits..."
                )
            }
        }

        if (!state.tripError.isNullOrBlank()) {
            item { EditorialErrorCard(message = state.tripError) }
        }

        if (activeTrip != null) {
            item {
                val packedCount = activeTrip.checkedItemIds.size
                val totalPack = activeTrip.packingItemIds.size.coerceAtLeast(1)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${activeTrip.bagSize.uppercase()} CAPSULE · ${activeTrip.days} DAYS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Champagne
                                )
                                Text(activeTrip.destination, style = MaterialTheme.typography.headlineSmall)
                            }
                            Text(
                                "$packedCount / ${activeTrip.packingItemIds.size} packed",
                                style = MaterialTheme.typography.labelLarge,
                                color = EditorialEmerald
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (packedCount.toFloat() / totalPack.toFloat()).coerceIn(0f, 1f) },
                            color = Champagne,
                            trackColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )

                        Text("CHECKABLE PACKING MATRIX", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        activeTrip.packingItemIds.forEach { id ->
                            val g = garmentMap[id]
                            if (g != null) {
                                val checked = id in activeTrip.checkedItemIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.toggleTripPackedItem(activeTrip, id) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { viewModel.toggleTripPackedItem(activeTrip, id) }
                                    )
                                    GarmentVisual(
                                        item = g,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${g.category} · ${g.colorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Text("DAY-BY-DAY OUTFIT ITINERARY", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        activeTrip.itineraryLines.forEachIndexed { idx, raw ->
                            val parts = raw.split("|")
                            val dayLabel = parts.getOrNull(0)?.ifBlank { "Day ${idx + 1}" } ?: "Day ${idx + 1}"
                            val occ = parts.getOrNull(1) ?: activeTrip.destination
                            val ids = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                            val note = parts.getOrNull(3) ?: ""
                            val dayGarments = ids.mapNotNull { garmentMap[it] }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("$dayLabel · $occ", style = MaterialTheme.typography.titleSmall)
                                    if (dayGarments.isNotEmpty()) {
                                        Text(
                                            dayGarments.joinToString(" + ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ChampagneDark
                                        )
                                    }
                                    if (note.isNotBlank()) {
                                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.saveTripItineraryToCalendar(activeTrip) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_trip_to_calendar_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Itinerary to Calendar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
