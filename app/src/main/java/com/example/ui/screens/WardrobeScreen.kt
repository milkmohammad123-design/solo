package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import kotlin.math.roundToInt

private val COLOR_SWATCH_PRESETS = listOf(
    "#141414" to "Obsidian",
    "#FAF8F5" to "Alabaster",
    "#33353A" to "Charcoal",
    "#CFC5B4" to "Stone",
    "#B8956A" to "Camel",
    "#3B2B23" to "Espresso",
    "#8B5A3C" to "Cognac",
    "#1F2B44" to "Indigo",
    "#2E4034" to "Forest",
    "#6B2D2C" to "Burgundy",
    "#6E8296" to "Slate"
)

private val SEASON_OPTIONS = listOf("All-Season", "Fall/Winter", "Spring/Summer")
private val COLOR_FAMILIES = listOf("All Colors", "Black/Charcoal", "White/Ivory", "Grey/Stone", "Earth/Camel", "Blue/Indigo", "Olive/Green", "Accent")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    viewModel: AtelierViewModel,
    garments: List<GarmentItem>,
    savedLooks: List<SavedLook>,
    weather: WeatherState,
    onOpenWeatherControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ingestion by viewModel.ingestion.collectAsState()
    val confirmBeforeDelete by viewModel.confirmBeforeDelete.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSeason by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") }
    var selectedColorFamily by remember { mutableStateOf("All Colors") }

    var showAddSheet by remember { mutableStateOf(false) }
    var inspectedGarmentId by remember { mutableStateOf<String?>(null) }

    val inspectedGarment = remember(garments, inspectedGarmentId) {
        garments.firstOrNull { it.id == inspectedGarmentId }
    }

    val filteredGarments = remember(
        garments, searchQuery, selectedCategory, selectedSeason, selectedStatus, selectedColorFamily
    ) {
        val q = searchQuery.trim().lowercase()
        garments.filter { item ->
            val catOk = selectedCategory == "All" || item.category == selectedCategory
            val seasonOk = selectedSeason == "All" || item.season.equals(selectedSeason, ignoreCase = true)
            val statusOk = selectedStatus == "All" || item.statusEnum().label == selectedStatus
            val colorOk = selectedColorFamily == "All Colors" ||
                ColorTheoryEngine.getColorFamily(item.colorHex) == selectedColorFamily
            val queryOk = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.brand.lowercase().contains(q) ||
                item.colorName.lowercase().contains(q) ||
                item.fabric.lowercase().contains(q) ||
                item.tags.any { it.lowercase().contains(q) }
            catOk && seasonOk && statusOk && colorOk && queryOk
        }
    }

    val totalValue = remember(garments) { garments.sumOf { it.price }.toInt() }
    val availableCount = remember(garments) { garments.count { it.statusEnum() == GarmentStatus.AVAILABLE } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EditorialWeatherBar(
                    weather = weather,
                    onOpenControls = onOpenWeatherControls,
                    onStyleForWeather = { viewModel.styleForCurrentWeather() }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                EditorialSectionHeader(
                    eyebrow = "DIGITAL ARCHIVE",
                    title = "Wardrobe",
                    subtitle = "${garments.size} pieces · $availableCount available · ${formatInr(totalValue)} archive",
                    trailing = {
                        Button(
                            onClick = { showAddSheet = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_garment_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add piece",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Piece", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }

            // Search bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search garment, designer, fabric, tag...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = "Search wardrobe")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wardrobe_search_input")
                )
            }

            // Category filter pills
            item(span = { GridItemSpan(maxLineSpan) }) {
                val categories = listOf("All") + GarmentCategory.allLabels
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val active = selectedCategory == cat
                        FilterChip(
                            selected = active,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("filter_cat_${cat.lowercase()}")
                        )
                    }
                }
            }

            // Secondary filters: Status, Season, Color Family
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All") + GarmentStatus.entries.map { it.label }) { st ->
                        val active = selectedStatus == st
                        AssistChip(
                            onClick = { selectedStatus = if (active) "All" else st },
                            label = {
                                Text(
                                    text = if (st == "All") "All Status" else st,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (active) Champagne.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (active) Champagne else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    items(listOf("All") + SEASON_OPTIONS) { season ->
                        val active = selectedSeason == season
                        AssistChip(
                            onClick = { selectedSeason = if (active) "All" else season },
                            label = {
                                Text(
                                    text = if (season == "All") "All Seasons" else season,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (active) Champagne.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (active) Champagne else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    items(COLOR_FAMILIES) { fam ->
                        val active = selectedColorFamily == fam
                        AssistChip(
                            onClick = { selectedColorFamily = if (active) "All Colors" else fam },
                            label = { Text(fam, style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (active) Champagne.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (active) Champagne else MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            if (filteredGarments.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checkroom,
                                contentDescription = null,
                                tint = Champagne,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (garments.isEmpty()) "Your Wardrobe Is Empty" else "No Matching Pieces",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (garments.isEmpty()) {
                                    "Start your archive by uploading garment photos with Auto-Tag AI or extracting an outfit from a mirror selfie."
                                } else {
                                    "Adjust your filters or add a new garment using Batch Upload or Mirror Extraction."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (garments.isEmpty()) {
                                Button(
                                    onClick = { showAddSheet = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Your First Piece")
                                }
                            } else {
                                TextButton(onClick = {
                                    searchQuery = ""
                                    selectedCategory = "All"
                                    selectedSeason = "All"
                                    selectedStatus = "All"
                                    selectedColorFamily = "All Colors"
                                }) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredGarments, key = { it.id }) { item ->
                    WardrobeGarmentCard(
                        item = item,
                        onClick = { inspectedGarmentId = item.id },
                        onQuickWear = { viewModel.logGarmentWear(item) }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddSheet) {
        AddGarmentProSheet(
            ingestion = ingestion,
            viewModel = viewModel,
            onDismiss = {
                showAddSheet = false
                viewModel.resetIngestion()
            }
        )
    }

    if (inspectedGarment != null) {
        GarmentDetailSheet(
            item = inspectedGarment,
            linkedLooks = savedLooks.filter { inspectedGarment.id in it.itemIds },
            confirmBeforeDelete = confirmBeforeDelete,
            onDismiss = { inspectedGarmentId = null },
            onLogWear = { viewModel.logGarmentWear(inspectedGarment) },
            onStatusChange = { status -> viewModel.updateGarmentStatus(inspectedGarment, status) },
            onSendToStudio = {
                inspectedGarmentId = null
                viewModel.sendGarmentToStudioAndLock(inspectedGarment)
            },
            onSaveEdits = { updated -> viewModel.updateGarmentDetails(updated) },
            onDelete = {
                inspectedGarmentId = null
                viewModel.deleteGarment(inspectedGarment)
            }
        )
    }
}

@Composable
private fun WardrobeGarmentCard(
    item: GarmentItem,
    onClick: () -> Unit,
    onQuickWear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("garment_card_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(10.dp)
            ) {
                GarmentVisual(
                    item = item,
                    modifier = Modifier.fillMaxSize()
                )
                GarmentStatusPill(
                    status = item.statusEnum(),
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(item.colorHex))
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                )
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Champagne,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.category} · ${item.fabric}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.formattedCpw(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${item.wears} wears",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onQuickWear,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("quick_wear_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddTask,
                            contentDescription = "Log wear for ${item.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGarmentProSheet(
    ingestion: com.example.viewmodel.IngestionState,
    viewModel: AtelierViewModel,
    onDismiss: () -> Unit
) {
    var sheetMode by remember { mutableIntStateOf(0) } // 0 = Single/Batch, 1 = Full-Body Extractor
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tops") }
    var subCategory by remember { mutableStateOf("") }
    var colorName by remember { mutableStateOf("Obsidian Black") }
    var colorHex by remember { mutableStateOf("#141414") }
    var fabric by remember { mutableStateOf("Wool") }
    var season by remember { mutableStateOf("All-Season") }
    var formality by remember { mutableFloatStateOf(3f) }
    var priceText by remember { mutableStateOf("180") }
    var careInstructions by remember { mutableStateOf("Dry clean or cold gentle wash") }
    var tagsText by remember { mutableStateOf("editorial, versatile") }
    var cutoutThreshold by remember { mutableFloatStateOf(ingestion.cutoutThreshold.toFloat()) }
    var cutoutSmoothing by remember { mutableFloatStateOf(ingestion.cutoutSmoothing) }

    // Apply Gemini Vision Auto-Tag results automatically when they arrive
    LaunchedEffect(ingestion.autoTagResult) {
        val r = ingestion.autoTagResult ?: return@LaunchedEffect
        name = r.name
        brand = r.brand
        category = r.category
        subCategory = r.subCategory
        colorName = r.colorName
        colorHex = r.colorHex
        fabric = r.fabric
        season = r.season
        formality = r.formality.toFloat()
        if (r.price > 0) priceText = r.price.toInt().toString()
        careInstructions = r.careInstructions
        if (r.tags.isNotEmpty()) tagsText = r.tags.joinToString(", ")
    }

    val batchPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.enqueueBatchPhotos(uris)
        }
    }

    val extractorPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractFullBodyOutfit(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                EditorialSectionHeader(
                    eyebrow = "PRO INGESTION",
                    title = "Add to Archive",
                    subtitle = "Batch photo queue, studio cutout, AI flat-lay & mirror outfit extractor"
                )
            }

            item {
                EditorialPillTabs(
                    tabs = listOf("Batch & Studio Upload", "Mirror Outfit Extractor"),
                    selectedIndex = sheetMode,
                    onSelect = { sheetMode = it }
                )
            }

            if (!ingestion.error.isNullOrBlank()) {
                item {
                    EditorialErrorCard(message = ingestion.error)
                }
            }

            if (sheetMode == 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (ingestion.batchQueuePaths.isNotEmpty()) {
                                        "Active Photo (${ingestion.batchQueuePaths.size} more in queue)"
                                    } else {
                                        "Garment Photo Studio"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(
                                    onClick = {
                                        batchPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("pick_batch_photos_button")
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select Photos")
                                }
                            }

                            val previewPath = ingestion.activeProcessedPath ?: ingestion.activeOriginalPath
                            if (previewPath != null && File(previewPath).exists()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = File(previewPath),
                                        contentDescription = "Garment photo preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (ingestion.aiProcessing) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = Champagne)
                                        }
                                    }
                                }

                                // Studio Cutout Controls
                                Text(
                                    text = "Studio Cutout Threshold: ${cutoutThreshold.roundToInt()} · Edge Smooth: ${(cutoutSmoothing * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = cutoutThreshold,
                                    onValueChange = { cutoutThreshold = it },
                                    valueRange = 12f..85f
                                )
                                Slider(
                                    value = cutoutSmoothing,
                                    onValueChange = { cutoutSmoothing = it },
                                    valueRange = 0f..0.75f
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.applyCutoutToActivePhoto(
                                                cutoutThreshold.roundToInt(),
                                                cutoutSmoothing
                                            )
                                        },
                                        enabled = !ingestion.aiProcessing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("apply_cutout_button")
                                    ) {
                                        Text("Studio Cutout")
                                    }
                                    TextButton(
                                        onClick = { viewModel.restoreOriginalIngestionPhoto() },
                                        enabled = !ingestion.aiProcessing
                                    ) {
                                        Text("Restore")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { viewModel.generateAiFlatLayForActivePhoto() },
                                        enabled = !ingestion.aiProcessing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ai_flatlay_button")
                                    ) {
                                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Studio Flat-Lay")
                                    }
                                    Button(
                                        onClick = { viewModel.runVisionAutoTagOnActivePhoto() },
                                        enabled = !ingestion.aiProcessing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("vision_autotag_button")
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Auto-Tag AI")
                                    }
                                }
                            } else {
                                Text(
                                    text = "Pick 1 to 8 garment photos at once for batch processing, or enter garment details manually below (a bespoke editorial canvas illustration is generated automatically).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Metadata Form
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Garment Name") },
                        placeholder = { Text("e.g. Tailored Cashmere Overcoat") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("garment_name_input")
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Brand / House") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Price (₹ INR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                }

                item {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GarmentCategory.allLabels) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                item {
                    Text("Color Swatch ($colorName · $colorHex)", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(COLOR_SWATCH_PRESETS) { (hex, label) ->
                            val selected = colorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Champagne else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        colorHex = hex
                                        colorName = label
                                    }
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = fabric,
                            onValueChange = { fabric = it },
                            label = { Text("Fabric / Material") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = subCategory,
                            onValueChange = { subCategory = it },
                            label = { Text("Sub-Category") },
                            placeholder = { Text("Blazer, Knit...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text("Season", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SEASON_OPTIONS.forEach { s ->
                            FilterChip(
                                selected = season == s,
                                onClick = { season = s },
                                label = { Text(s) }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Formality Level: ${formality.roundToInt()} / 5",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = formality,
                        onValueChange = { formality = it },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                item {
                    OutlinedTextField(
                        value = careInstructions,
                        onValueChange = { careInstructions = it },
                        label = { Text("Care & Washing Instructions") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Style Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                viewModel.showToast("Please enter a garment name")
                                return@Button
                            }
                            val hadMoreInQueue = ingestion.batchQueuePaths.isNotEmpty()
                            viewModel.saveIngestedGarment(
                                GarmentItem(
                                    name = name.trim(),
                                    brand = brand.trim().ifBlank { "Archive" },
                                    category = category,
                                    subCategory = subCategory.trim(),
                                    colorName = colorName.trim(),
                                    colorHex = colorHex,
                                    fabric = fabric.trim().ifBlank { "Cotton" },
                                    season = season,
                                    formality = formality.roundToInt(),
                                    price = priceText.toDoubleOrNull() ?: 0.0,
                                    wears = 0,
                                    status = GarmentStatus.AVAILABLE.name,
                                    careInstructions = careInstructions.trim(),
                                    tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                )
                            )
                            if (hadMoreInQueue) {
                                name = ""
                                brand = ""
                                subCategory = ""
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("save_garment_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (ingestion.batchQueuePaths.isNotEmpty()) {
                                "Save & Advance Queue (${ingestion.batchQueuePaths.size} left)"
                            } else {
                                "Archive Piece to Wardrobe"
                            }
                        )
                    }
                }
            } else {
                // Full-Body Outfit Extractor mode
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Full-Body Mirror Outfit Extractor", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Upload a full-body mirror selfie. Gemini Vision detects every visible garment, crops each bounding region, and prepares tagged items for 1-click import.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    extractorPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !ingestion.extractorLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("pick_mirror_extractor_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Mirror Selfie to Extract")
                            }
                        }
                    }
                }

                if (ingestion.extractorLoading) {
                    item {
                        EditorialLoadingCard(
                            title = "Detecting & Cropping Garments",
                            subtitle = "Gemini Vision is identifying outerwear, tops, bottoms, shoes, and bags..."
                        )
                    }
                }

                if (ingestion.extractedCandidates.isNotEmpty()) {
                    items(ingestion.extractedCandidates, key = { it.id }) { candidate ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (candidate.selected) Champagne else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleExtractedCandidate(candidate.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = candidate.selected,
                                    onCheckedChange = { viewModel.toggleExtractedCandidate(candidate.id) }
                                )
                                GarmentVisual(
                                    category = candidate.category,
                                    colorHex = candidate.colorHex,
                                    name = candidate.name,
                                    localImagePath = candidate.croppedImagePath,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${candidate.category} · ${candidate.colorName} · ${candidate.fabric}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Est. ${formatInr(candidate.estimatedPrice)} · Formality ${candidate.formality}/5",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Champagne
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val selectedCount = ingestion.extractedCandidates.count { it.selected }
                        Button(
                            onClick = {
                                viewModel.importSelectedExtractedGarments()
                                onDismiss()
                            },
                            enabled = selectedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("import_extracted_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Import $selectedCount Selected Garments")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarmentDetailSheet(
    item: GarmentItem,
    linkedLooks: List<SavedLook>,
    confirmBeforeDelete: Boolean,
    onDismiss: () -> Unit,
    onLogWear: () -> Unit,
    onStatusChange: (GarmentStatus) -> Unit,
    onSendToStudio: () -> Unit,
    onSaveEdits: (GarmentItem) -> Unit,
    onDelete: () -> Unit
) {
    var editName by remember(item.id) { mutableStateOf(item.name) }
    var editBrand by remember(item.id) { mutableStateOf(item.brand) }
    var editFabric by remember(item.id) { mutableStateOf(item.fabric) }
    var editPrice by remember(item.id) { mutableStateOf(item.price.toInt().toString()) }
    var editCareInstructions by remember(item.id) { mutableStateOf(item.careInstructions) }
    var editCareNotes by remember(item.id) { mutableStateOf(item.careNotes) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        GarmentVisual(item = item, modifier = Modifier.fillMaxSize())
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.brand.uppercase(), style = MaterialTheme.typography.labelSmall, color = Champagne)
                        Text(item.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${item.category} · ${item.colorName} · ${item.season}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GarmentStatusPill(status = item.statusEnum())
                    }
                }
            }

            // KPI row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(label = "COST / WEAR", value = item.formattedCpw(), modifier = Modifier.weight(1f))
                    MetricBox(label = "PRICE", value = formatInr(item.price), modifier = Modifier.weight(1f))
                    MetricBox(label = "WEARS", value = "${item.wears}", modifier = Modifier.weight(0.8f))
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLogWear,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("detail_log_wear_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+1 Wear")
                    }
                    OutlinedButton(
                        onClick = onSendToStudio,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("detail_send_studio_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.DesignServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Style in Studio")
                    }
                }
            }

            // Laundry & Availability Status selector
            item {
                Text("LAUNDRY & AVAILABILITY STATUS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GarmentStatus.entries) { status ->
                        val active = item.statusEnum() == status
                        FilterChip(
                            selected = active,
                            onClick = { onStatusChange(status) },
                            label = { Text(status.label) },
                            modifier = Modifier.testTag("status_chip_${status.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = "Items not marked Available are automatically excluded from AI outfit recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Outfits containing this piece
            item {
                Text(
                    text = "APPEARS IN ${linkedLooks.size} SAVED LOOKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Champagne
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (linkedLooks.isEmpty()) {
                    Text(
                        text = "Not yet saved in a Lookbook — tap Style in Studio to build an outfit around it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(linkedLooks) { look ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(look.title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "${look.collectionName} · Harmony ${look.harmonyScore}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Editable metadata & Care/Washing notes
            item {
                Text("EDIT METADATA & CARE LOG", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = editBrand,
                            onValueChange = { editBrand = it },
                            label = { Text("Brand") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = { editPrice = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Price (₹ INR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                    OutlinedTextField(
                        value = editFabric,
                        onValueChange = { editFabric = it },
                        label = { Text("Fabric") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCareInstructions,
                        onValueChange = { editCareInstructions = it },
                        label = { Text("Care Instructions") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCareNotes,
                        onValueChange = { editCareNotes = it },
                        label = { Text("Care & Washing Log Notes") },
                        placeholder = { Text("e.g. Dry cleaned Oct 14, hemmed 1cm at tailor") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveEdits(
                                    item.copy(
                                        name = editName.trim().ifBlank { item.name },
                                        brand = editBrand.trim().ifBlank { item.brand },
                                        fabric = editFabric.trim().ifBlank { item.fabric },
                                        price = editPrice.toDoubleOrNull() ?: item.price,
                                        careInstructions = editCareInstructions.trim(),
                                        careNotes = editCareNotes.trim()
                                    )
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_garment_edits_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save Changes")
                        }
                        OutlinedButton(
                            onClick = {
                                if (confirmBeforeDelete) confirmDelete = true else onDelete()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("delete_garment_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete piece")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove Piece?") },
            text = { Text("Remove \"${item.name}\" from your archive permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
