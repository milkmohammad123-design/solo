package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AtelierViewModel
import java.io.File
import kotlin.math.roundToInt

private val STUDIO_MODES = listOf("Slots", "Collage", "TryOn", "Lookbooks")
private val STUDIO_MODE_LABELS = listOf("Slots", "Collage", "Try-On", "Lookbooks")
private val COLLAGE_BACKDROPS = listOf("Alabaster", "Linen", "Obsidian", "Grid")
private val TRYON_SCENES = listOf("Studio", "Street", "Office", "Evening")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: AtelierViewModel,
    garments: List<GarmentItem>,
    savedLooks: List<SavedLook>,
    wishlist: List<WishlistItem>,
    userProfile: UserProfile,
    onOpenStyleDna: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studio by viewModel.studio.collectAsState()
    val context = LocalContext.current

    var pickerCategory by remember { mutableStateOf<String?>(null) }
    var showSaveLookDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val garmentMap = remember(garments) { garments.associateBy { it.id } }
    val wishlistMap = remember(wishlist) { wishlist.associateBy { "wl:${it.id}" } }

    val resolvedItems = remember(studio.slots, garments, wishlist) {
        GarmentCategory.allLabels.mapNotNull { cat ->
            val id = studio.slots[cat] ?: return@mapNotNull null
            garmentMap[id] ?: wishlistMap[id]?.let { w ->
                GarmentItem(
                    id = "wl:${w.id}",
                    name = "${w.name} (Wishlist)",
                    brand = w.brand,
                    category = w.category,
                    colorName = w.colorName,
                    colorHex = w.colorHex,
                    price = w.price,
                    localImagePath = w.localImagePath
                )
            }
        }
    }
    val harmony = remember(resolvedItems, userProfile) {
        ColorTheoryEngine.evaluateOutfitHarmony(resolvedItems, userProfile)
    }

    val modeIndex = STUDIO_MODES.indexOf(studio.mode).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialSectionHeader(
            eyebrow = "OUTFIT STUDIO PRO",
            title = "Studio",
            subtitle = "${resolvedItems.size} active layers · ${harmony.scheme} (${harmony.score}%)"
        )

        EditorialPillTabs(
            tabs = STUDIO_MODE_LABELS,
            selectedIndex = modeIndex,
            onSelect = { idx -> viewModel.setStudioMode(STUDIO_MODES[idx]) }
        )

        // Action strip for Slots / Collage / TryOn
        if (studio.mode != "Lookbooks") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.shuffleUnlockedStudioSlots() },
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("studio_shuffle_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { viewModel.aiCompleteStudioLook() },
                        enabled = !studio.aiCompleting,
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("studio_ai_complete_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (studio.aiCompleting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Complete")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showExportSheet = true },
                        enabled = !studio.exportingLookbook,
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("studio_export_png_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PNG")
                    }
                }
                item {
                    Button(
                        onClick = { showSaveLookDialog = true },
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("studio_save_look_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Look")
                    }
                }
            }
        }

        when (studio.mode) {
            "Slots" -> SlotsModeView(
                studio = studio,
                resolvedItems = resolvedItems,
                harmony = harmony,
                garmentMap = garmentMap,
                wishlistMap = wishlistMap,
                onPickSlot = { pickerCategory = it },
                onToggleLock = { viewModel.toggleStudioLock(it) },
                onClearSlot = { viewModel.assignStudioSlot(it, null) },
                modifier = Modifier.weight(1f)
            )
            "Collage" -> CollageModeView(
                studio = studio,
                garmentMap = garmentMap,
                wishlistMap = wishlistMap,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            "TryOn" -> TryOnModeView(
                studio = studio,
                userProfile = userProfile,
                viewModel = viewModel,
                onOpenStyleDna = onOpenStyleDna,
                modifier = Modifier.weight(1f)
            )
            else -> LookbooksModeView(
                savedLooks = savedLooks,
                garmentMap = garmentMap,
                onLoadLook = { look -> viewModel.openOutfitInStudio(look.title, look.itemIds) },
                onLogToday = { look ->
                    viewModel.logOutfitToDate(
                        dateKey = viewModel.todayDateKey(),
                        lookTitle = look.title,
                        itemIds = look.itemIds,
                        lookId = look.id,
                        occasion = look.occasion,
                        whoYouSaw = "",
                        markWorn = true
                    )
                },
                onDeleteLook = { viewModel.deleteSavedLook(it.id) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Slot Garment Picker Sheet
    if (pickerCategory != null) {
        val cat = pickerCategory!!
        val ownedInCat = garments.filter { it.category == cat }
        val wishlistInCat = wishlist.filter { it.category == cat }
        ModalBottomSheet(
            onDismissRequest = { pickerCategory = null },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    EditorialSectionHeader(
                        eyebrow = "SELECT LAYER",
                        title = cat,
                        subtitle = "${ownedInCat.size} owned · ${wishlistInCat.size} wishlist"
                    )
                }
                items(ownedInCat, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.assignStudioSlot(cat, item.id)
                                pickerCategory = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GarmentVisual(
                                item = item,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${item.brand} · ${item.colorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            GarmentStatusPill(status = item.statusEnum())
                        }
                    }
                }
                if (wishlistInCat.isNotEmpty()) {
                    item {
                        Text("TEST FROM WISHLIST", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    }
                    items(wishlistInCat, key = { "wl_${it.id}" }) { wl ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.assignStudioSlot(cat, "wl:${wl.id}")
                                    pickerCategory = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GarmentVisual(
                                    category = wl.category,
                                    colorHex = wl.colorHex,
                                    name = wl.name,
                                    localImagePath = wl.localImagePath,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${wl.name} (Wishlist)", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${wl.brand} · ${formatInr(wl.price)}",
                                        style = MaterialTheme.typography.bodySmall,
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

    // Export Multi-Template PNG Sheet
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorialSectionHeader(
                    eyebrow = "HIGH-RES EXPORTER",
                    title = "Export Lookbook PNG",
                    subtitle = "Saved to Pictures/Criel and ready to share"
                )
                LookbookExportTemplate.entries.forEach { template ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportSheet = false
                                viewModel.exportLookbook(template) { file ->
                                    runCatching {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share Lookbook PNG"))
                                    }
                                }
                            }
                            .testTag("export_template_${template.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(template.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    template.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Rounded.Download, contentDescription = "Export ${template.label}", tint = Champagne)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Save Look Dialog
    if (showSaveLookDialog) {
        var lookTitle by remember { mutableStateOf(studio.title) }
        var occasion by remember { mutableStateOf("Smart Casual") }
        var collectionName by remember { mutableStateOf("Autumn Work") }
        var scheduleToday by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSaveLookDialog = false },
            title = { Text("Save to Lookbook") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lookTitle,
                        onValueChange = { lookTitle = it },
                        label = { Text("Look Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = occasion,
                        onValueChange = { occasion = it },
                        label = { Text("Occasion") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        label = { Text("Collection Folder") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = scheduleToday, onCheckedChange = { scheduleToday = it })
                        Text("Also log to Today's Calendar", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setStudioTitle(lookTitle)
                    viewModel.saveCurrentStudioLook(
                        title = lookTitle,
                        occasion = occasion,
                        collectionName = collectionName,
                        assignDateKey = if (scheduleToday) viewModel.todayDateKey() else null
                    )
                    showSaveLookDialog = false
                }) {
                    Text("Save Look")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveLookDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SlotsModeView(
    studio: com.example.viewmodel.StudioState,
    resolvedItems: List<GarmentItem>,
    harmony: ColorHarmonyAnalysis,
    garmentMap: Map<String, GarmentItem>,
    wishlistMap: Map<String, WishlistItem>,
    onPickSlot: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onClearSlot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // Live Color Harmony & Proportion Meter
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
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
                            Text("LIVE HARMONY & PROPORTION", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text("${harmony.scheme} · ${harmony.score}/100", style = MaterialTheme.typography.titleLarge)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Champagne.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = harmony.seasonMatchBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (harmony.score / 100f).coerceIn(0f, 1f) },
                        color = Champagne,
                        trackColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )

                    Text(harmony.note, style = MaterialTheme.typography.bodySmall)
                    Text(
                        harmony.proportionNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (resolvedItems.isNotEmpty()) {
                        ColorSwatchRow(hexColors = resolvedItems.map { it.colorHex }, swatchSize = 24.dp)
                    }
                }
            }
        }

        // 6 Category Layer Slots (2-up grid inside LazyColumn)
        items(GarmentCategory.allLabels.chunked(2)) { rowCats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCats.forEach { cat ->
                    val assignedId = studio.slots[cat]
                    val item = assignedId?.let { id ->
                        garmentMap[id] ?: wishlistMap[id]?.let { w ->
                            GarmentItem(
                                id = id,
                                name = "${w.name} (Wishlist)",
                                brand = w.brand,
                                category = w.category,
                                colorName = w.colorName,
                                colorHex = w.colorHex,
                                localImagePath = w.localImagePath
                            )
                        }
                    }
                    val locked = studio.locks[cat] == true
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (locked) 1.5.dp else 1.dp,
                            color = if (locked) Champagne else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onPickSlot(cat) }
                            .testTag("studio_slot_${cat.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Row {
                                    IconButton(
                                        onClick = { onToggleLock(cat) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                            contentDescription = "Lock $cat slot",
                                            tint = if (locked) Champagne else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (item != null) {
                                        IconButton(
                                            onClick = { onClearSlot(cat) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear $cat slot",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(106.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item != null) {
                                    GarmentVisual(item = item, modifier = Modifier.fillMaxSize().padding(6.dp))
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Champagne)
                                        Text(
                                            "Add $cat",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = item?.name ?: "Empty Slot",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageModeView(
    studio: com.example.viewmodel.StudioState,
    garmentMap: Map<String, GarmentItem>,
    wishlistMap: Map<String, WishlistItem>,
    viewModel: AtelierViewModel,
    modifier: Modifier = Modifier
) {
    var stickerInput by remember { mutableStateOf("") }
    val selectedPiece = studio.collagePieces.firstOrNull { it.id == studio.selectedCollageId }
    val density = LocalDensity.current

    val canvasBg = when (studio.collageBackdrop) {
        "Linen" -> Color(0xFFEAF2FA)
        "Obsidian" -> Obsidian
        "Grid" -> Alabaster
        else -> Alabaster
    }
    val stickerFg = if (studio.collageBackdrop == "Obsidian") Alabaster else Obsidian

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // Backdrop selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(COLLAGE_BACKDROPS) { bg ->
                        FilterChip(
                            selected = studio.collageBackdrop == bg,
                            onClick = { viewModel.setCollageBackdrop(bg) },
                            label = { Text(bg) }
                        )
                    }
                }
                TextButton(onClick = { viewModel.syncCollageFromSlots() }) {
                    Text("Reset")
                }
            }
        }

        // Interactive Freeform Moodboard Canvas
        item {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(370.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(canvasBg)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .testTag("collage_canvas")
            ) {
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }

                if (studio.collageBackdrop == "Grid") {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 28.dp.toPx()
                        var x = step
                        while (x < size.width) {
                            drawLine(Color(0xFFD2E3F4), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = step
                        while (y < size.height) {
                            drawLine(Color(0xFFD2E3F4), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }
                }

                studio.collagePieces.sortedBy { it.zIndex }.forEach { piece ->
                    val item = garmentMap[piece.itemId] ?: wishlistMap[piece.itemId]?.let { w ->
                        GarmentItem(
                            id = piece.itemId,
                            name = w.name,
                            category = w.category,
                            colorHex = w.colorHex,
                            localImagePath = w.localImagePath
                        )
                    }
                    if (item != null) {
                        val isSel = piece.id == studio.selectedCollageId
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (piece.xFraction * widthPx).roundToInt(),
                                        (piece.yFraction * heightPx).roundToInt()
                                    )
                                }
                                .zIndex(piece.zIndex)
                                .size((118.dp * piece.scale).coerceIn(72.dp, 200.dp))
                                .graphicsLayer {
                                    rotationZ = piece.rotationDeg
                                    scaleX = if (piece.flippedHorizontal) -1f else 1f
                                }
                                .then(
                                    if (isSel) Modifier.border(2.dp, Champagne, RoundedCornerShape(14.dp))
                                    else Modifier
                                )
                                .pointerInput(piece.id, widthPx, heightPx) {
                                    detectDragGestures(
                                        onDragStart = { viewModel.selectCollagePiece(piece.id) }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        if (widthPx > 0f && heightPx > 0f) {
                                            viewModel.moveCollagePiece(
                                                id = piece.id,
                                                dxFraction = dragAmount.x / widthPx,
                                                dyFraction = dragAmount.y / heightPx
                                            )
                                        }
                                    }
                                }
                                .clickable { viewModel.selectCollagePiece(piece.id) }
                        ) {
                            GarmentVisual(item = item, modifier = Modifier.fillMaxSize().padding(4.dp))
                        }
                    }
                }

                // Text stickers
                studio.collageStickers.forEach { sticker ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Champagne.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Champagne.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (sticker.xFraction * widthPx).roundToInt(),
                                    (sticker.yFraction * heightPx).roundToInt()
                                )
                            }
                            .clickable { viewModel.removeCollageSticker(sticker.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = sticker.text,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                                color = stickerFg
                            )
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove sticker",
                                tint = stickerFg,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Transform controls for selected piece
        if (selectedPiece != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("SELECTED LAYER CONTROLS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        Text("Scale: ${(selectedPiece.scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = selectedPiece.scale,
                            onValueChange = { sc ->
                                viewModel.updateSelectedCollagePiece { it.copy(scale = sc) }
                            },
                            valueRange = 0.65f..1.65f
                        )
                        Text("Rotation: ${selectedPiece.rotationDeg.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = selectedPiece.rotationDeg,
                            onValueChange = { deg ->
                                viewModel.updateSelectedCollagePiece { it.copy(rotationDeg = deg) }
                            },
                            valueRange = -45f..45f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateSelectedCollagePiece { it.copy(flippedHorizontal = !it.flippedHorizontal) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Flip H")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateSelectedCollagePiece { it.copy(zIndex = it.zIndex + 1f) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Front")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateSelectedCollagePiece { it.copy(zIndex = (it.zIndex - 1f).coerceAtLeast(0f)) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                        }
                    }
                }
            }
        }

        // Add Text Sticker
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stickerInput,
                    onValueChange = { stickerInput = it },
                    placeholder = { Text("Add editorial sticker (e.g. PARIS CAPSULE)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.addCollageSticker(stickerInput)
                        stickerInput = ""
                    },
                    enabled = stickerInput.isNotBlank(),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun TryOnModeView(
    studio: com.example.viewmodel.StudioState,
    userProfile: UserProfile,
    viewModel: AtelierViewModel,
    onOpenStyleDna: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVirtualModel = !userProfile.virtualModelImagePath.isNullOrBlank() &&
        File(userProfile.virtualModelImagePath).exists()

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
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VIRTUAL MODEL & LOOKBOARD (FREE)", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text(
                                text = if (hasVirtualModel) "Saved Full-Body Model Active" else "Editorial Runway Lookboard",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        TextButton(onClick = onOpenStyleDna) {
                            Text(if (hasVirtualModel) "Change Photo" else "Upload Model Photo")
                        }
                    }

                    Text("SCENE BACKDROP", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TRYON_SCENES) { scene ->
                            FilterChip(
                                selected = studio.tryOnScene == scene,
                                onClick = { viewModel.setTryOnScene(scene) },
                                label = { Text(scene) }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.generateVirtualTryOn() },
                        enabled = !studio.tryOnLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("generate_tryon_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Look Preview & AI Fit Check")
                    }
                }
            }
        }

        if (studio.tryOnLoading) {
            item {
                EditorialLoadingCard(
                    title = "Evaluating Fit, Drape & Scene",
                    subtitle = "Composing your ${studio.tryOnScene} lookboard and running free-tier Gemini Fit Analysis..."
                )
            }
        }

        if (!studio.tryOnError.isNullOrBlank()) {
            item {
                EditorialErrorCard(message = studio.tryOnError)
            }
        }

        if (!studio.tryOnImagePath.isNullOrBlank() && File(studio.tryOnImagePath).exists()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = File(studio.tryOnImagePath),
                            contentDescription = "Editorial Look Preview Board",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.saveTryOnToComparisonSlot(isSlotA = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_tryon_a_button")
                            ) {
                                Text("Pin to Look A")
                            }
                            OutlinedButton(
                                onClick = { viewModel.saveTryOnToComparisonSlot(isSlotA = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_tryon_b_button")
                            ) {
                                Text("Pin to Look B")
                            }
                        }
                    }
                }
            }
        }

        val critique = studio.tryOnCritique
        if (critique != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI FIT & DRAPE PREVIEW", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(critique.headline, style = MaterialTheme.typography.titleLarge)
                            }
                            ScoreRing(score = critique.fitScore, label = "Fit Score", ringSize = 58.dp)
                        }
                        Text(critique.silhouetteAndDrape, style = MaterialTheme.typography.bodyMedium)
                        if (critique.pairingHonesty.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("HONEST PAIRING CHECK", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                    Text(critique.pairingHonesty, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            critique.sceneFitNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (critique.stylingTips.isNotEmpty()) {
                            Text("STYLING ADJUSTMENTS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            critique.stylingTips.forEachIndexed { idx, tip ->
                                Text("${idx + 1}. $tip", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (critique.whatToGetToUpgrade.isNotEmpty()) {
                            Text("WHAT TO GET TO LEVEL UP THIS FIT", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            critique.whatToGetToUpgrade.forEach { pick ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Champagne.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.ShoppingBag,
                                            contentDescription = null,
                                            tint = ChampagneDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(pick, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Side-by-Side A/B Comparison
        if (studio.slotA != null || studio.slotB != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SIDE-BY-SIDE A/B COMPARISON", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            if (studio.slotA != null && studio.slotB != null) {
                                TextButton(onClick = { viewModel.toggleABComparison() }) {
                                    Text(if (studio.compareAB) "Hide Split" else "Compare Side-by-Side")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TryOnCompareTile(snapshot = studio.slotA, placeholder = "Pin Look A", modifier = Modifier.weight(1f))
                            TryOnCompareTile(snapshot = studio.slotB, placeholder = "Pin Look B", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TryOnCompareTile(
    snapshot: TryOnSlotSnapshot?,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (snapshot != null && File(snapshot.imagePath).exists()) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AsyncImage(
                    model = File(snapshot.imagePath),
                    contentDescription = snapshot.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Text(snapshot.label, style = MaterialTheme.typography.labelSmall, color = Champagne)
                Text(snapshot.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LookbooksModeView(
    savedLooks: List<SavedLook>,
    garmentMap: Map<String, GarmentItem>,
    onLoadLook: (SavedLook) -> Unit,
    onLogToday: (SavedLook) -> Unit,
    onDeleteLook: (SavedLook) -> Unit,
    modifier: Modifier = Modifier
) {
    val allCollections = remember(savedLooks) {
        (DEFAULT_COLLECTIONS + savedLooks.map { it.collectionName }).distinct()
    }
    var selectedCollection by remember { mutableStateOf("All Looks") }
    val filteredLooks = remember(savedLooks, selectedCollection) {
        if (selectedCollection == "All Looks") savedLooks
        else savedLooks.filter { it.collectionName.equals(selectedCollection, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCollections) { col ->
                    FilterChip(
                        selected = selectedCollection == col,
                        onClick = { selectedCollection = col },
                        label = { Text(col) }
                    )
                }
            }
        }

        if (filteredLooks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No looks in \"$selectedCollection\" yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Build an outfit in Slots mode and tap Save Look.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredLooks, key = { it.id }) { look ->
                val lookItems = look.itemIds.mapNotNull { garmentMap[it] }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${look.collectionName.uppercase()} · ${look.occasion.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Champagne
                                )
                                Text(look.title, style = MaterialTheme.typography.titleLarge)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Champagne.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${look.harmonyScore}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lookItems, key = { it.id }) { item ->
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(5.dp)
                                ) {
                                    GarmentVisual(item = item, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }

                        if (look.notes.isNotBlank()) {
                            Text(
                                look.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onLoadLook(look) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Load in Studio")
                            }
                            Button(
                                onClick = { onLogToday(look) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Log Today")
                            }
                            IconButton(
                                onClick = { onDeleteLook(look) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete look",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
