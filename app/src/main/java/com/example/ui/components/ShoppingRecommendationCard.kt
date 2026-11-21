package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.*
import com.example.ui.theme.*

private val PRIORITY_ORDER = listOf("Must-Have", "High Impact", "Nice-to-Have")

@Composable
fun ShoppingRecommendationCard(
    title: String,
    items: List<StylistShoppingItem>,
    savedShoppingIds: Set<String>,
    onSaveShoppingItem: (StylistShoppingItem, Boolean) -> Unit,
    onSaveAllShoppingItems: (List<StylistShoppingItem>) -> Unit,
    onStoreLinkError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var zoomedItem by remember { mutableStateOf<StylistShoppingItem?>(null) }

    val totalEstimate = remember(items) { items.sumOf { it.estimatedPriceInr } }
    val allSaved = remember(items, savedShoppingIds) { items.all { it.id in savedShoppingIds } }
    val grouped = remember(items) {
        val byTier = items.groupBy { it.priority }
        PRIORITY_ORDER.mapNotNull { tier ->
            val list = byTier[tier].orEmpty()
            if (list.isNotEmpty()) tier to list else null
        } + byTier.filterKeys { it !in PRIORITY_ORDER }.toList()
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card header: Title, item count, total INR budget, Save All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "ESSENTIALS & WHAT TO GET" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Champagne,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${items.size} pieces · Est. ${formatInr(totalEstimate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { onSaveAllShoppingItems(items) },
                    enabled = !allSaved,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("chat_save_all_wishlist_button")
                ) {
                    Icon(
                        imageVector = if (allSaved) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allSaved) "All Saved" else "Save All",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Priority-grouped sections (Must-Have -> High Impact -> Nice-to-Have)
            grouped.forEach { (priorityTier, tierItems) ->
                val tierSubtotal = tierItems.sumOf { it.estimatedPriceInr }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityTierHeader(
                        tier = priorityTier,
                        count = tierItems.size,
                        subtotalInr = tierSubtotal
                    )
                    tierItems.forEach { item ->
                        ShoppingItemEditorialRow(
                            item = item,
                            isSaved = item.id in savedShoppingIds,
                            onPhotoClick = { zoomedItem = item },
                            onOpenStore = { store ->
                                openIndianStoreSearch(context, store, item, onStoreLinkError)
                            },
                            onSaveWishlist = { onSaveShoppingItem(item, false) },
                            onTryInStudio = { onSaveShoppingItem(item, true) }
                        )
                    }
                }
            }
        }
    }

    val activeZoom = zoomedItem
    if (activeZoom != null) {
        ShoppingItemDetailDialog(
            item = activeZoom,
            isSaved = activeZoom.id in savedShoppingIds,
            onDismiss = { zoomedItem = null },
            onOpenStore = { store -> openIndianStoreSearch(context, store, activeZoom, onStoreLinkError) },
            onSaveWishlist = { onSaveShoppingItem(activeZoom, false) },
            onTryInStudio = {
                zoomedItem = null
                onSaveShoppingItem(activeZoom, true)
            }
        )
    }
}

@Composable
private fun PriorityTierHeader(
    tier: String,
    count: Int,
    subtotalInr: Double
) {
    val isMustHave = tier == "Must-Have"
    val isHighImpact = tier == "High Impact"
    val badgeBg = when {
        isMustHave -> Champagne.copy(alpha = 0.24f)
        isHighImpact -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surface
    }
    val badgeTextColor = when {
        isMustHave -> ChampagneDark
        isHighImpact -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = badgeBg,
            border = if (!isMustHave && !isHighImpact) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isMustHave -> Icons.Rounded.Star
                        isHighImpact -> Icons.Rounded.AutoAwesome
                        else -> Icons.Rounded.AddCircleOutline
                    },
                    contentDescription = null,
                    tint = badgeTextColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${tier.uppercase()} · $count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor
                )
            }
        }
        Text(
            text = "Est. ${formatInr(subtotalInr)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShoppingItemEditorialRow(
    item: StylistShoppingItem,
    isSaved: Boolean,
    onPhotoClick: () -> Unit,
    onOpenStore: (IndianStore) -> Unit,
    onSaveWishlist: () -> Unit,
    onTryInStudio: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Real reference photograph (tappable to zoom) with graceful fallback
                ReferenceGarmentPhoto(
                    item = item,
                    modifier = Modifier
                        .width(84.dp)
                        .height(104.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPhotoClick)
                        .testTag("shopping_item_photo")
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatInr(item.estimatedPriceInr),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChampagneDark
                        )
                    }

                    // Color dot + category + color name
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(item.colorHex))
                                .border(0.75.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Text(
                            text = "${item.category} · ${item.colorName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Fit & Fabric specification
                    if (item.fitAndFabric.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Fit & Fabric: ${item.fitAndFabric}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Honest reason why you need it
                    if (item.whyYouNeedIt.isNotBlank()) {
                        Text(
                            text = item.whyYouNeedIt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Indian Stores quick links (Myntra, Ajio, Zara, H&M)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SHOP LIVE IN INDIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(IndianStore.entries) { store ->
                        Surface(
                            onClick = { onOpenStore(store) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("store_link_${store.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = store.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Shop ${item.name} on ${store.label}",
                                    tint = ChampagneDark,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Wishlist & Studio actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveWishlist,
                    enabled = !isSaved,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("chat_save_item_wishlist_button")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "In Wishlist" else "Wishlist",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
                FilledTonalButton(
                    onClick = onTryInStudio,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("chat_test_item_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Checkroom,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Try in Studio",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceGarmentPhoto(
    item: StylistShoppingItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!item.referenceImageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.referenceImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Reference photo of ${item.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Champagne
                        )
                    }
                },
                error = {
                    GarmentVisual(
                        category = item.category,
                        colorHex = item.colorHex,
                        name = item.name,
                        localImagePath = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }
            )
        } else {
            GarmentVisual(
                category = item.category,
                colorHex = item.colorHex,
                name = item.name,
                localImagePath = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        }

        // Subtle zoom hint pill
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.ZoomIn,
                    contentDescription = "Inspect ${item.name}",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemDetailDialog(
    item: StylistShoppingItem,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onOpenStore: (IndianStore) -> Unit,
    onSaveWishlist: () -> Unit,
    onTryInStudio: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Champagne.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = item.priority.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneDark,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close reference preview")
                    }
                }

                ReferenceGarmentPhoto(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${item.category} · ${item.colorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatInr(item.estimatedPriceInr),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChampagneDark
                    )
                }

                if (item.fitAndFabric.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("WHAT TO LOOK FOR (FIT & FABRIC)", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text(item.fitAndFabric, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (item.whyYouNeedIt.isNotBlank()) {
                    Text(
                        text = item.whyYouNeedIt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BROWSE LIVE LISTINGS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IndianStore.entries.forEach { store ->
                            OutlinedButton(
                                onClick = { onOpenStore(store) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Text(store.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveWishlist,
                        enabled = !isSaved,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(if (isSaved) "In Wishlist" else "Add to Wishlist", maxLines = 1)
                    }
                    Button(
                        onClick = onTryInStudio,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Try in Studio", maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun openIndianStoreSearch(
    context: Context,
    store: IndianStore,
    item: StylistShoppingItem,
    onError: (String) -> Unit
) {
    val query = item.storeSearchQuery.ifBlank { "${item.colorName} ${item.name}" }
    val url = IndianStoreLinks.buildSearchUrl(store, query)
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("No browser installed to open ${store.label}")
    }
}
