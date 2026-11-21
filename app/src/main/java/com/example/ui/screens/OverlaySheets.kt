package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AtelierViewModel
import com.google.android.gms.location.LocationServices
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleDnaSheet(
    viewModel: AtelierViewModel,
    profile: UserProfile,
    onDismiss: () -> Unit
) {
    val analyzing by viewModel.colorAnalysisLoading.collectAsState()
    val analysisError by viewModel.colorAnalysisError.collectAsState()
    val styleGender by viewModel.styleGender.collectAsState()
    val clipboard = LocalClipboardManager.current

    var selectedSeason by remember(profile.seasonPalette) { mutableStateOf(profile.seasonPalette) }
    var selectedArchetypes by remember(profile.archetypes) { mutableStateOf(profile.archetypes.toSet()) }
    var selectedSilhouette by remember(profile.silhouette) { mutableStateOf(profile.silhouette) }
    var heightCm by remember(profile.heightCm) { mutableFloatStateOf(profile.heightCm.toFloat().coerceIn(140f, 210f)) }
    var proportions by remember(profile.proportions) { mutableStateOf(profile.proportions) }

    val selfiePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.runSeasonalColorAnalysis(uri) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.saveVirtualModelPhoto(uri) }

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
                    eyebrow = "PERSONAL PROFILE",
                    title = "Style DNA & Color Analysis",
                    subtitle = "Every AI stylist recommendation strictly respects your gender, palette, and proportions"
                )
            }

            // Gender / Style Department selector
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("STYLE DEPARTMENT / GENDER", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StyleGender.entries.forEach { gender ->
                                FilterChip(
                                    selected = styleGender == gender,
                                    onClick = { viewModel.setStyleGender(gender) },
                                    label = { Text(gender.shortLabel, maxLines = 1) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dna_gender_${gender.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = styleGender.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Seasonal Color Analysis Card
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
                                Text("12-SEASON PALETTE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(profile.seasonPalette, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "${profile.undertone} · ${profile.contrastLevel} contrast · ${profile.hairEyeDepth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(profile.paletteSummary, style = MaterialTheme.typography.bodySmall)

                        Text("POWER COLORS (TAP TO COPY HEX)", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        ColorSwatchRow(
                            hexColors = profile.powerColors,
                            onSwatchClick = { hex ->
                                clipboard.setText(AnnotatedString(hex))
                                viewModel.showToast("Copied Power Color $hex")
                            }
                        )

                        Text("CORE NEUTRALS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        ColorSwatchRow(
                            hexColors = profile.neutralColors,
                            onSwatchClick = { hex ->
                                clipboard.setText(AnnotatedString(hex))
                                viewModel.showToast("Copied Neutral $hex")
                            }
                        )

                        Text("COLORS TO AVOID", style = MaterialTheme.typography.labelSmall, color = EditorialRose)
                        ColorSwatchRow(
                            hexColors = profile.avoidColors,
                            onSwatchClick = { hex ->
                                clipboard.setText(AnnotatedString(hex))
                                viewModel.showToast("Avoid swatch $hex copied")
                            }
                        )

                        if (analyzing) {
                            EditorialLoadingCard(
                                title = "Analyzing Undertone & Contrast",
                                subtitle = "Gemini Vision is determining your 12-Season Color Palette..."
                            )
                        }
                        if (!analysisError.isNullOrBlank()) {
                            EditorialErrorCard(message = analysisError!!)
                        }

                        Button(
                            onClick = {
                                selfiePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !analyzing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("run_color_analysis_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FaceRetouchingNatural, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Natural-Light Selfie for AI Analysis")
                        }
                    }
                }
            }

            // Manual 12-Season override
            item {
                Text("12-SEASON OVERRIDE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SEASON_PALETTES_12) { s ->
                        FilterChip(
                            selected = selectedSeason == s,
                            onClick = { selectedSeason = s },
                            label = { Text(s) }
                        )
                    }
                }
            }

            // Style Archetypes
            item {
                Text("STYLE ARCHETYPES", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(STYLE_ARCHETYPES) { arch ->
                        val active = arch in selectedArchetypes
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedArchetypes = if (active && selectedArchetypes.size > 1) {
                                    selectedArchetypes - arch
                                } else {
                                    selectedArchetypes + arch
                                }
                            },
                            label = { Text(arch) }
                        )
                    }
                }
            }

            // Silhouette Preference
            item {
                Text("SILHOUETTE PREFERENCE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SILHOUETTE_OPTIONS.forEach { sil ->
                        FilterChip(
                            selected = selectedSilhouette == sil,
                            onClick = { selectedSilhouette = sil },
                            label = { Text(sil) }
                        )
                    }
                }
            }

            // Height & Proportions
            item {
                Text("HEIGHT: ${heightCm.roundToInt()} cm", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = heightCm,
                    onValueChange = { heightCm = it },
                    valueRange = 145f..205f
                )
                OutlinedTextField(
                    value = proportions,
                    onValueChange = { proportions = it },
                    label = { Text("Proportions & Fit Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Saved Virtual Model Photo
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val hasPhoto = !profile.virtualModelImagePath.isNullOrBlank() &&
                            File(profile.virtualModelImagePath).exists()
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasPhoto) {
                                AsyncImage(
                                    model = File(profile.virtualModelImagePath!!),
                                    contentDescription = "Saved Virtual Model Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = Champagne)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Virtual Model Photo", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Stored locally once and reused across all Studio Virtual Try-Ons.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    modelPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("upload_virtual_model_button")
                            ) {
                                Text(if (hasPhoto) "Replace Photo" else "Upload Full-Body Photo")
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.updateStyleDna(
                            archetypes = selectedArchetypes.toList(),
                            silhouette = selectedSilhouette,
                            heightCm = heightCm.roundToInt().toDouble(),
                            proportions = proportions.trim(),
                            seasonPalette = selectedSeason,
                            monthlyBudget = profile.monthlyBudget
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("save_style_dna_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Style DNA Profile")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherControlSheet(
    viewModel: AtelierViewModel,
    weather: WeatherState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cityInput by remember { mutableStateOf("") }
    var numericTemp by remember(weather.tempC, weather.useFahrenheit) {
        mutableStateOf(weather.displayTempValue().toString())
    }

    fun requestGpsForecast() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                viewModel.fetchWeatherForCoords(loc.latitude, loc.longitude, "Local GPS")
            } else {
                viewModel.showToast("GPS location unavailable — search a city or use Manual controls")
            }
        }.addOnFailureListener {
            viewModel.showToast("Could not read GPS — search a city below")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestGpsForecast()
        } else {
            viewModel.showToast("Location denied — search a city or adjust weather manually")
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
                    eyebrow = "LIVE & MANUAL CLIMATE",
                    title = "Weather Styling Controls",
                    subtitle = "${weather.city} · ${weather.formattedTemp()} · ${weather.condition.label}"
                )
            }

            // Live Open-Meteo city search + GPS detect
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        placeholder = { Text("Search city (Paris, Tokyo, NYC)...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weather_city_input")
                    )
                    Button(
                        onClick = {
                            if (cityInput.isNotBlank()) {
                                viewModel.searchWeatherCity(cityInput.trim())
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("weather_search_city_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Sync")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                requestGpsForecast()
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("weather_detect_gps_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detect GPS")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetToLiveWeather() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Reset Live")
                    }
                }
            }

            // Manual °C / °F toggle + Temp slider & numeric field
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MANUAL OVERRIDE PANEL", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !weather.useFahrenheit,
                            onClick = { viewModel.setWeatherFahrenheit(false) },
                            label = { Text("°C") }
                        )
                        FilterChip(
                            selected = weather.useFahrenheit,
                            onClick = { viewModel.setWeatherFahrenheit(true) },
                            label = { Text("°F") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = weather.tempC.toFloat(),
                        onValueChange = { viewModel.setManualTempC(it.roundToInt()) },
                        valueRange = -15f..42f,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = numericTemp,
                        onValueChange = { raw ->
                            numericTemp = raw.filter { it.isDigit() || it == '-' }
                            val num = numericTemp.toIntOrNull()
                            if (num != null) {
                                val asC = if (weather.useFahrenheit) ((num - 32) * 5) / 9 else num
                                viewModel.setManualTempC(asC)
                            }
                        },
                        label = { Text(if (weather.useFahrenheit) "°F" else "°C") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(82.dp)
                    )
                }
            }

            // Wind & Humidity Sliders
            item {
                Text("Wind: ${weather.windKmh} km/h", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = weather.windKmh.toFloat(),
                    onValueChange = { viewModel.setManualWindHumidity(it.roundToInt(), weather.humidityPct) },
                    valueRange = 0f..80f
                )
                Text("Humidity: ${weather.humidityPct}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = weather.humidityPct.toFloat(),
                    onValueChange = { viewModel.setManualWindHumidity(weather.windKmh, it.roundToInt()) },
                    valueRange = 10f..100f
                )
            }

            // 6 Weather Condition Pills
            item {
                Text("CONDITION", style = MaterialTheme.typography.labelSmall, color = Champagne)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WeatherCondition.entries) { cond ->
                        FilterChip(
                            selected = weather.condition == cond,
                            onClick = { viewModel.setManualCondition(cond) },
                            leadingIcon = {
                                Icon(
                                    imageVector = weatherConditionIcon(cond),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(cond.label) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onDismiss()
                        viewModel.styleForCurrentWeather()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("weather_sheet_style_me_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Style Me for This Weather")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupSheet(
    viewModel: AtelierViewModel,
    darkMode: Boolean,
    userProfile: UserProfile,
    userEmail: String?,
    onOpenStyleDna: () -> Unit,
    onOpenWeatherControls: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val backupBusy by viewModel.backupBusy.collectAsState()
    val aiTone by viewModel.aiCritiqueTone.collectAsState()
    val styleGender by viewModel.styleGender.collectAsState()
    val voiceReadout by viewModel.voiceReadoutEnabled.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val confirmBeforeDelete by viewModel.confirmBeforeDelete.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val defaultCity by viewModel.defaultWeatherCity.collectAsState()
    val stylistState by viewModel.stylist.collectAsState()
    val exportCacheBytes by viewModel.exportCacheBytes.collectAsState()

    var cityInput by remember(defaultCity) { mutableStateOf(defaultCity) }
    var budgetInput by remember(userProfile.monthlyBudget) { mutableStateOf(userProfile.monthlyBudget.toInt().toString()) }

    LaunchedEffect(Unit) {
        viewModel.refreshExportCacheSize()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportClosetBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importClosetBackup(uri)
    }

    val cacheLabel = remember(exportCacheBytes) {
        when {
            exportCacheBytes <= 0L -> "0 KB"
            exportCacheBytes < 1024L * 1024L -> "${(exportCacheBytes / 1024L).coerceAtLeast(1L)} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", exportCacheBytes / (1024.0 * 1024.0))
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
            contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp)
        ) {
            item {
                EditorialSectionHeader(
                    eyebrow = "CRIEL SETTINGS",
                    title = "Settings",
                    subtitle = userEmail ?: "Signed in with Google"
                )
            }

            // 1. Account & Shortcuts
            item {
                SettingsGroupCard(title = "ACCOUNT & PROFILE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Champagne.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile.displayName.firstOrNull() ?: 'C').uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = ChampagneDark
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userProfile.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = userEmail ?: "Google Account Synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsActionRow(
                        icon = Icons.Rounded.ColorLens,
                        title = "Style DNA & Seasonal Palette",
                        subtitle = "${userProfile.seasonPalette} · ${userProfile.silhouette}",
                        onClick = {
                            onDismiss()
                            onOpenStyleDna()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsActionRow(
                        icon = Icons.Rounded.WbSunny,
                        title = "Climate & Forecast Controls",
                        subtitle = "${weather.city} · ${weather.formattedTemp()} ${weather.condition.label}",
                        onClick = {
                            onDismiss()
                            onOpenWeatherControls()
                        }
                    )
                }
            }

            // 2. Appearance & Interaction
            item {
                SettingsGroupCard(title = "APPEARANCE & INTERACTION") {
                    SettingsToggleRow(
                        title = "Obsidian Dark Mode",
                        subtitle = "Switch between Powder Blue Light and Deep Obsidian Dark",
                        checked = darkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        testTag = "settings_dark_mode_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Subtle tactile confirmation on actions",
                        checked = hapticsEnabled,
                        onCheckedChange = { viewModel.toggleHapticsEnabled() },
                        testTag = "settings_haptics_switch"
                    )
                }
            }

            // 3. AI Stylist Preferences
            item {
                SettingsGroupCard(title = "AI STYLIST PREFERENCES") {
                    Text("Style Department / Gender", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyleGender.entries.forEach { gender ->
                            FilterChip(
                                selected = styleGender == gender,
                                onClick = { viewModel.setStyleGender(gender) },
                                label = { Text(gender.shortLabel, maxLines = 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_gender_${gender.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = styleGender.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Text("Fit Critique Honesty", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiCritiqueTone.entries.forEach { tone ->
                            FilterChip(
                                selected = aiTone == tone,
                                onClick = { viewModel.setAiCritiqueTone(tone) },
                                label = { Text(tone.shortLabel, maxLines = 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_tone_${tone.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = aiTone.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsToggleRow(
                        title = "Voice Auto-Readout",
                        subtitle = "Read stylist replies aloud using device speech",
                        checked = voiceReadout,
                        onCheckedChange = { viewModel.toggleVoiceReadout() },
                        testTag = "settings_voice_readout_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsToggleRow(
                        title = "Include Unavailable Pieces",
                        subtitle = "Allow laundry, tailor & off-season items in AI fits",
                        checked = stylistState.includeUnavailableOverride,
                        onCheckedChange = { viewModel.toggleUnavailableOverride() },
                        testTag = "settings_include_unavailable_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    OutlinedButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("settings_clear_chat_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Stylist Chat History")
                    }
                }
            }

            // 4. Units, Currency & Location
            item {
                SettingsGroupCard(title = "UNITS, CURRENCY & CLIMATE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Currency", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "All wardrobe values, CPW & AI quotes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Champagne.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "₹ INR",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneDark,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsToggleRow(
                        title = "Use Fahrenheit (°F)",
                        subtitle = if (weather.useFahrenheit) "Showing temperatures in °F" else "Showing temperatures in °C",
                        checked = weather.useFahrenheit,
                        onCheckedChange = { viewModel.setWeatherFahrenheit(it) },
                        testTag = "settings_fahrenheit_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Text("Default Weather City", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            placeholder = { Text("e.g. Mumbai, Bengaluru, Delhi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.saveDefaultWeatherCity(cityInput) },
                            modifier = Modifier.heightIn(min = 50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // 5. Wardrobe & Safety
            item {
                SettingsGroupCard(title = "WARDROBE & SAFETY") {
                    Text("Monthly Fashion Budget (₹ INR)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Monthly Cap (₹)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val nextCap = budgetInput.toDoubleOrNull() ?: userProfile.monthlyBudget
                                viewModel.updateStyleDna(
                                    archetypes = userProfile.archetypes,
                                    silhouette = userProfile.silhouette,
                                    heightCm = userProfile.heightCm,
                                    proportions = userProfile.proportions,
                                    seasonPalette = userProfile.seasonPalette,
                                    monthlyBudget = nextCap
                                )
                            },
                            modifier = Modifier.heightIn(min = 50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Apply")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    SettingsToggleRow(
                        title = "Confirm Before Deleting",
                        subtitle = "Ask for confirmation before removing a wardrobe piece",
                        checked = confirmBeforeDelete,
                        onCheckedChange = { viewModel.toggleConfirmBeforeDelete() },
                        testTag = "settings_confirm_delete_switch"
                    )
                }
            }

            // 6. Backup & Local Storage
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("BACKUP & STORAGE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        Text(
                            "Export or restore your entire wardrobe, Style DNA, lookbooks, calendar, wishlist, trips, and embedded local garment photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { exportLauncher.launch("criel_closet_backup.json") },
                                enabled = !backupBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_backup_json_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                enabled = !backupBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("import_backup_json_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import JSON")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Temporary Export Cache", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Currently using $cacheLabel (safe to clear)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { viewModel.clearExportCache() },
                                modifier = Modifier.testTag("settings_clear_cache_button")
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // 7. About & Sign Out
            item {
                SettingsGroupCard(title = "ABOUT CRIEL") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Criel · Version 1.0", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Free-Tier AI · Cloud Sync Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EditorialEmerald.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Free Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = EditorialEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Champagne)
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Champagne, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

