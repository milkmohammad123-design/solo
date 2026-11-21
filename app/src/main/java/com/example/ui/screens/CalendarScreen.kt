package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AtelierViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun CalendarScreen(
    viewModel: AtelierViewModel,
    garments: List<GarmentItem>,
    savedLooks: List<SavedLook>,
    calendarEntries: List<CalendarEntry>,
    userProfile: UserProfile,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) }
    val analytics = remember(garments) { ColorTheoryEngine.computeClosetAnalytics(garments) }
    val garmentMap = remember(garments) { garments.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialSectionHeader(
            eyebrow = "WEAR LOG & INSIGHTS",
            title = "Calendar & Analytics",
            subtitle = "${calendarEntries.size} logged days · ${formatInr(analytics.totalValuation)} closet value"
        )

        EditorialPillTabs(
            tabs = listOf("Monthly Calendar", "Analytics & Budget"),
            selectedIndex = subTab,
            onSelect = { subTab = it }
        )

        if (subTab == 0) {
            MonthlyCalendarTab(
                viewModel = viewModel,
                garmentMap = garmentMap,
                savedLooks = savedLooks,
                calendarEntries = calendarEntries,
                modifier = Modifier.weight(1f)
            )
        } else {
            AnalyticsAndBudgetTab(
                viewModel = viewModel,
                analytics = analytics,
                userProfile = userProfile,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthlyCalendarTab(
    viewModel: AtelierViewModel,
    garmentMap: Map<String, GarmentItem>,
    savedLooks: List<SavedLook>,
    calendarEntries: List<CalendarEntry>,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { viewModel.todayDateKey() }
    var displayYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-based
    var selectedDateKey by remember { mutableStateOf(todayKey) }
    var showLogDialog by remember { mutableStateOf(false) }

    val entriesByDate = remember(calendarEntries) { calendarEntries.associateBy { it.dateKey } }
    val selectedEntry = entriesByDate[selectedDateKey]
    val selectedRepeatAlert = remember(selectedEntry, calendarEntries) {
        selectedEntry?.let {
            ColorTheoryEngine.findRepeatWearAlert(it.dateKey, it.itemIds, it.lookId, calendarEntries)
        }
    }

    val monthHeader = remember(displayYear, displayMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale.US).format(c.time)
    }

    val dayCells = remember(displayYear, displayMonth) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK) - 1 // 0..6
        val maxDays = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<String?>()
        repeat(firstDayOfWeek) { cells.add(null) }
        for (day in 1..maxDays) {
            cells.add(String.format(Locale.US, "%04d-%02d-%02d", displayYear, displayMonth + 1, day))
        }
        while (cells.size % 7 != 0) cells.add(null)
        cells
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // Month Grid Card
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
                        IconButton(
                            onClick = {
                                if (displayMonth == 0) {
                                    displayMonth = 11
                                    displayYear -= 1
                                } else displayMonth -= 1
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(monthHeader, style = MaterialTheme.typography.titleLarge)
                        IconButton(
                            onClick = {
                                if (displayMonth == 11) {
                                    displayMonth = 0
                                    displayYear += 1
                                } else displayMonth += 1
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        WEEKDAY_LABELS.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    dayCells.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { dateKey ->
                                if (dateKey == null) {
                                    Spacer(modifier = Modifier.weight(1f).height(44.dp))
                                } else {
                                    val dayNum = dateKey.takeLast(2).toIntOrNull() ?: 1
                                    val entry = entriesByDate[dateKey]
                                    val isSelected = dateKey == selectedDateKey
                                    val isToday = dateKey == todayKey
                                    val cellBg = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> Champagne.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    }
                                    val cellFg = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(cellBg)
                                            .clickable { selectedDateKey = dateKey },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = cellFg
                                        )
                                        if (entry != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (entry.worn) EditorialEmerald else Champagne)
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

        // Selected Date Detail Card
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
                        Column {
                            Text("SELECTED DATE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            Text(selectedDateKey, style = MaterialTheme.typography.titleLarge)
                        }
                        Button(
                            onClick = { showLogDialog = true },
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("log_outfit_for_date_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedEntry == null) "Log Outfit" else "Replace")
                        }
                    }

                    if (selectedEntry == null) {
                        Text(
                            "No outfit logged for $selectedDateKey yet. Schedule a look from your Lookbooks or Studio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (!selectedRepeatAlert.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EditorialAmber.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, EditorialAmber.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.WarningAmber, contentDescription = "Repeat wear alert", tint = EditorialAmber)
                                    Text(selectedRepeatAlert, style = MaterialTheme.typography.bodySmall, color = EditorialAmber)
                                }
                            }
                        }

                        Text(selectedEntry.lookTitle, style = MaterialTheme.typography.titleMedium)
                        val meta = listOfNotNull(
                            selectedEntry.occasion.takeIf { it.isNotBlank() },
                            selectedEntry.whoYouSaw.takeIf { it.isNotBlank() }?.let { "With: $it" }
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        val items = selectedEntry.itemIds.mapNotNull { garmentMap[it] }
                        if (items.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(items, key = { it.id }) { g ->
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(4.dp)
                                    ) {
                                        GarmentVisual(item = g, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!selectedEntry.worn) {
                                Button(
                                    onClick = { viewModel.markCalendarEntryWorn(selectedEntry) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Mark Worn (+CPW)")
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EditorialEmerald.copy(alpha = 0.16f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EditorialEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Worn & Logged", style = MaterialTheme.typography.labelLarge, color = EditorialEmerald)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.openOutfitInStudio(selectedEntry.lookTitle, selectedEntry.itemIds) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open Studio")
                            }
                            IconButton(onClick = { viewModel.deleteCalendarEntry(selectedEntry.dateKey) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear date", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogDialog) {
        val studioItems = remember { viewModel.resolveStudioItems().filter { !it.id.startsWith("wl:") } }
        var selectedLookId by remember { mutableStateOf<String?>(savedLooks.firstOrNull()?.id) }
        var useCurrentStudio by remember { mutableStateOf(savedLooks.isEmpty()) }
        var occasion by remember { mutableStateOf("Work / Gallery") }
        var whoYouSaw by remember { mutableStateOf("") }
        var markWorn by remember { mutableStateOf(selectedDateKey == todayKey) }

        val chosenLook = savedLooks.firstOrNull { it.id == selectedLookId }
        val candidateItemIds = if (useCurrentStudio || chosenLook == null) {
            studioItems.map { it.id }
        } else {
            chosenLook.itemIds
        }
        val liveAlert = remember(selectedDateKey, candidateItemIds, selectedLookId, calendarEntries) {
            ColorTheoryEngine.findRepeatWearAlert(
                selectedDateKey,
                candidateItemIds,
                if (useCurrentStudio) null else selectedLookId,
                calendarEntries
            )
        }

        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Log Outfit for $selectedDateKey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useCurrentStudio,
                            onClick = { useCurrentStudio = false },
                            label = { Text("Saved Look") }
                        )
                        FilterChip(
                            selected = useCurrentStudio,
                            onClick = { useCurrentStudio = true },
                            label = { Text("Current Studio") }
                        )
                    }

                    if (!useCurrentStudio && savedLooks.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(savedLooks, key = { it.id }) { lk ->
                                FilterChip(
                                    selected = selectedLookId == lk.id,
                                    onClick = { selectedLookId = lk.id },
                                    label = { Text(lk.title, maxLines = 1) }
                                )
                            }
                        }
                    }

                    if (!liveAlert.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EditorialAmber.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = liveAlert,
                                style = MaterialTheme.typography.bodySmall,
                                color = EditorialAmber,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = occasion,
                        onValueChange = { occasion = it },
                        label = { Text("Occasion") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = whoYouSaw,
                        onValueChange = { whoYouSaw = it },
                        label = { Text("Who You Saw (for Repeat-Wear Alerts)") },
                        placeholder = { Text("e.g. Design team, Elena & Marc") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = markWorn, onCheckedChange = { markWorn = it })
                        Text("Mark worn & increment CPW", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = if (useCurrentStudio || chosenLook == null) {
                            viewModel.studio.value.title
                        } else {
                            chosenLook.title
                        }
                        viewModel.logOutfitToDate(
                            dateKey = selectedDateKey,
                            lookTitle = title,
                            itemIds = candidateItemIds,
                            lookId = if (useCurrentStudio) null else chosenLook?.id,
                            occasion = occasion.trim(),
                            whoYouSaw = whoYouSaw.trim(),
                            markWorn = markWorn
                        )
                        showLogDialog = false
                    },
                    enabled = candidateItemIds.isNotEmpty()
                ) {
                    Text("Save Entry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AnalyticsAndBudgetTab(
    viewModel: AtelierViewModel,
    analytics: ClosetAnalyticsMetrics,
    userProfile: UserProfile,
    modifier: Modifier = Modifier
) {
    var budgetText by remember(userProfile.monthlyBudget) {
        mutableStateOf(userProfile.monthlyBudget.toInt().toString())
    }
    val budgetVal = userProfile.monthlyBudget.coerceAtLeast(1.0)
    val budgetRatio = (analytics.spentThisMonth / budgetVal).toFloat().coerceIn(0f, 1f)
    val overBudget = analytics.spentThisMonth > budgetVal

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // Valuation & CPW KPIs
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsKpiCard(
                    label = "CLOSET VALUATION",
                    value = formatInr(analytics.totalValuation),
                    sub = "Total archive investment",
                    modifier = Modifier.weight(1f)
                )
                AnalyticsKpiCard(
                    label = "AVG COST / WEAR",
                    value = formatInr(analytics.averageCpw),
                    sub = "Across worn pieces",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // MVP / Most Worn / Least Worn
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
                    Text("ARCHIVE STANDOUTS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    HighlightRow("MVP (Lowest CPW)", analytics.mvpItem?.let { "${it.name} (${it.formattedCpw()})" } ?: "Log wears to compute")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HighlightRow("Most Worn", analytics.mostWorn?.let { "${it.name} (${it.wears} wears)" } ?: "—")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    HighlightRow("Least Worn", analytics.leastWorn?.let { "${it.name} (${it.wears} wears)" } ?: "—")
                }
            }
        }

        // Monthly Shopping Budget Tracker
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (overBudget) EditorialRose else Champagne.copy(alpha = 0.45f)),
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
                        Text("MONTHLY SHOPPING BUDGET", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        Text(
                            text = "${formatInr(analytics.spentThisMonth)} / ${formatInr(userProfile.monthlyBudget)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (overBudget) EditorialRose else EditorialEmerald
                        )
                    }
                    LinearProgressIndicator(
                        progress = { budgetRatio },
                        color = if (overBudget) EditorialRose else Champagne,
                        trackColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { c -> c.isDigit() } },
                            label = { Text("Monthly Cap (₹ INR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val nextCap = budgetText.toDoubleOrNull() ?: userProfile.monthlyBudget
                                viewModel.updateStyleDna(
                                    archetypes = userProfile.archetypes,
                                    silhouette = userProfile.silhouette,
                                    heightCm = userProfile.heightCm,
                                    proportions = userProfile.proportions,
                                    seasonPalette = userProfile.seasonPalette,
                                    monthlyBudget = nextCap
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("save_budget_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Update")
                        }
                    }
                }
            }
        }

        // Category Distribution Chart
        item {
            val maxCat = (analytics.categoryDistribution.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
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
                    Text("CATEGORY DISTRIBUTION", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    analytics.categoryDistribution.forEach { (cat, count) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat, style = MaterialTheme.typography.labelLarge)
                                Text("$count pcs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCat.toFloat() },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }

        // Color Palette Distribution Chart
        item {
            val maxCol = (analytics.colorDistribution.maxOfOrNull { it.third } ?: 1).coerceAtLeast(1)
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
                    Text("CLOSET COLOR SPECTRUM", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    analytics.colorDistribution.take(8).forEach { (hex, name, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(105.dp)
                            )
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCol.toFloat() },
                                color = Champagne,
                                trackColor = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Text("$count", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Champagne)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HighlightRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
