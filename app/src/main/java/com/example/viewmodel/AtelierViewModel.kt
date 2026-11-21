package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val VM_TAG = "AtelierViewModel"

enum class PrimaryTab(val label: String) {
    WARDROBE("Wardrobe"),
    STYLIST("Stylist"),
    STUDIO("Studio"),
    CURATE("Curate"),
    CALENDAR("Calendar")
}

data class StudioState(
    val title: String = "Criel Signature Look",
    val mode: String = "Slots", // Slots, Collage, TryOn, Lookbooks
    val slots: Map<String, String?> = mapOf(
        "Outerwear" to null,
        "Tops" to null,
        "Bottoms" to null,
        "Footwear" to null,
        "Bags" to null,
        "Accessories" to null
    ),
    val locks: Map<String, Boolean> = GarmentCategory.allLabels.associateWith { false },
    val collagePieces: List<CollagePieceState> = emptyList(),
    val selectedCollageId: String? = null,
    val collageBackdrop: String = "Alabaster", // Alabaster, Linen, Obsidian, Grid
    val collageStickers: List<CollageTextSticker> = listOf(
        CollageTextSticker(text = "AUTUMN EDIT · 2026", xFraction = 0.08f, yFraction = 0.05f)
    ),
    val tryOnScene: String = "Studio", // Studio, Street, Office, Evening
    val tryOnLoading: Boolean = false,
    val tryOnImagePath: String? = null,
    val tryOnCritique: LookFitCritique? = null,
    val tryOnError: String? = null,
    val slotA: TryOnSlotSnapshot? = null,
    val slotB: TryOnSlotSnapshot? = null,
    val compareAB: Boolean = false,
    val aiCompleting: Boolean = false,
    val exportingLookbook: Boolean = false,
    val lastExportedFile: File? = null
)

data class StylistSuiteState(
    val subTab: Int = 0, // 0 = Chat & Voice, 1 = Rate My Fit, 2 = Daily OOTD
    val selectedDressCode: DressCodeMode? = null,
    val includeUnavailableOverride: Boolean = false,
    val chatMessages: List<StylistChatMessage> = listOf(
        StylistChatMessage(
            fromUser = false,
            text = "Welcome to Criel. Ask me what you need to get, send photos of items you're eyeing, build a full wardrobe basics list, or ask for an outfit — I'll give you the honest verdict in ₹ INR."
        )
    ),
    val chatLoading: Boolean = false,
    val rateMyFitLoading: Boolean = false,
    val rateMyFitPhotoPath: String? = null,
    val rateMyFitResult: RateMyFitResult? = null,
    val rateMyFitError: String? = null,
    val ootdDeck: List<OotdCardItem> = emptyList(),
    val ootdCurrentIndex: Int = 0,
    val ootdLoading: Boolean = false,
    val ootdError: String? = null
)

data class CurateState(
    val subTab: Int = 0, // 0 = Should I Buy, 1 = Gaps & Wishlist, 2 = Declutter/Resale, 3 = Pack My Bag
    val buyScanLoading: Boolean = false,
    val buyScanImagePath: String? = null,
    val buyScanResult: ShouldIBuyResult? = null,
    val buyScanError: String? = null,
    val gapsLoading: Boolean = false,
    val gapRecommendations: List<GapRecommendation> = emptyList(),
    val gapsError: String? = null,
    val resaleLoadingId: String? = null,
    val resaleListings: Map<String, ResaleListingResult> = emptyMap(),
    val resaleError: String? = null,
    val tripLoading: Boolean = false,
    val tripError: String? = null
)

data class IngestionState(
    val batchQueuePaths: List<String> = emptyList(),
    val activeOriginalPath: String? = null,
    val activeProcessedPath: String? = null,
    val cutoutThreshold: Int = 36,
    val cutoutSmoothing: Float = 0.25f,
    val aiProcessing: Boolean = false,
    val autoTagResult: AutoTagResult? = null,
    val extractedCandidates: List<ExtractedGarmentCandidate> = emptyList(),
    val extractorLoading: Boolean = false,
    val error: String? = null
)

class AtelierViewModel(
    private val repository: AtelierRepository,
    private val aiService: AiStylistService,
    private val weatherService: WeatherService
) : ViewModel() {

    private val _activeTab = MutableStateFlow(PrimaryTab.WARDROBE)
    val activeTab: StateFlow<PrimaryTab> = _activeTab.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _firestoreError = MutableStateFlow<String?>(null)
    val firestoreError: StateFlow<String?> = _firestoreError.asStateFlow()

    val darkMode: StateFlow<Boolean> = repository.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val voiceReadoutEnabled: StateFlow<Boolean> = repository.voiceReadoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val aiCritiqueTone: StateFlow<AiCritiqueTone> = repository.aiCritiqueToneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiCritiqueTone.BRUTALLY_HONEST)

    val styleGender: StateFlow<StyleGender> = repository.styleGenderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StyleGender.UNISEX)

    val hapticsEnabled: StateFlow<Boolean> = repository.hapticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val confirmBeforeDelete: StateFlow<Boolean> = repository.confirmBeforeDeleteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val defaultWeatherCity: StateFlow<String> = repository.defaultWeatherCityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _exportCacheBytes = MutableStateFlow(0L)
    val exportCacheBytes: StateFlow<Long> = _exportCacheBytes.asStateFlow()

    // Real-time Firestore collections with two-tier CancellationException / Firestore error unwrapping
    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile()
        .map { it ?: UserProfile() }
        .catch { err -> handleStreamError(err, "Failed to sync Style DNA") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserProfile())

    val garments: StateFlow<List<GarmentItem>> = repository.observeGarments()
        .catch { err -> handleStreamError(err, "Failed to load wardrobe") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val savedLooks: StateFlow<List<SavedLook>> = repository.observeLooks()
        .catch { err -> handleStreamError(err, "Failed to load looks") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val calendarEntries: StateFlow<List<CalendarEntry>> = repository.observeCalendarEntries()
        .catch { err -> handleStreamError(err, "Failed to load calendar") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wishlist: StateFlow<List<WishlistItem>> = repository.observeWishlist()
        .catch { err -> handleStreamError(err, "Failed to load wishlist") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val trips: StateFlow<List<TripPlan>> = repository.observeTrips()
        .catch { err -> handleStreamError(err, "Failed to load trip plans") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val ootdFeedback: StateFlow<List<OotdFeedback>> = repository.observeOotdFeedback()
        .catch { err -> handleStreamError(err, "Failed to load preferences") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _studio = MutableStateFlow(StudioState())
    val studio: StateFlow<StudioState> = _studio.asStateFlow()

    private val _stylist = MutableStateFlow(StylistSuiteState())
    val stylist: StateFlow<StylistSuiteState> = _stylist.asStateFlow()

    private val _curate = MutableStateFlow(CurateState())
    val curate: StateFlow<CurateState> = _curate.asStateFlow()

    private val _ingestion = MutableStateFlow(IngestionState())
    val ingestion: StateFlow<IngestionState> = _ingestion.asStateFlow()

    private val _colorAnalysisLoading = MutableStateFlow(false)
    val colorAnalysisLoading: StateFlow<Boolean> = _colorAnalysisLoading.asStateFlow()

    private val _colorAnalysisError = MutableStateFlow<String?>(null)
    val colorAnalysisError: StateFlow<String?> = _colorAnalysisError.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _lastSpokenReply = MutableStateFlow<String?>(null)
    val lastSpokenReply: StateFlow<String?> = _lastSpokenReply.asStateFlow()

    private var studioInitialized = false

    init {
        viewModelScope.launch {
            repository.ensureStarterArchiveSeeded().onFailure { e ->
                _firestoreError.value = e.localizedMessage ?: "Unable to initialize cloud wardrobe."
            }
        }
        viewModelScope.launch {
            repository.useFahrenheitFlow.collect { f ->
                _weather.update { it.copy(useFahrenheit = f) }
            }
        }
        viewModelScope.launch {
            val savedCity = repository.defaultWeatherCityFlow.firstOrNull().orEmpty()
            if (savedCity.isNotBlank()) {
                searchWeatherCity(savedCity)
            }
        }
        refreshExportCacheSize()
        // Populate initial studio slots once garments arrive
        viewModelScope.launch {
            garments.collect { list ->
                if (!studioInitialized && list.isNotEmpty()) {
                    studioInitialized = true
                    seedDefaultStudioSlots(list)
                }
            }
        }
    }

    private suspend fun <T> FlowCollector<T>.handleStreamError(error: Throwable, fallbackMsg: String) {
        val firestoreErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreErr == null && error is CancellationException) throw error
        val root = firestoreErr ?: error
        _firestoreError.value = root.localizedMessage ?: fallbackMsg
    }

    fun selectTab(tab: PrimaryTab) {
        _activeTab.value = tab
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun dismissError() {
        _firestoreError.value = null
    }

    fun clearSpokenReply() {
        _lastSpokenReply.value = null
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !darkMode.value
            repository.setDarkModePref(next)
            repository.updateUserProfile(userProfile.value.copy(darkMode = next))
        }
    }

    fun toggleVoiceReadout() {
        viewModelScope.launch {
            repository.setVoiceReadoutPref(!voiceReadoutEnabled.value)
        }
    }

    fun setAiCritiqueTone(tone: AiCritiqueTone) {
        viewModelScope.launch {
            repository.setAiCritiqueTonePref(tone)
            showToast("AI Honesty set to ${tone.label}")
        }
    }

    fun setStyleGender(gender: StyleGender) {
        viewModelScope.launch {
            repository.setStyleGenderPref(gender)
            showToast("Styling tailored for ${gender.label}")
        }
    }

    fun toggleHapticsEnabled() {
        viewModelScope.launch {
            repository.setHapticsEnabledPref(!hapticsEnabled.value)
        }
    }

    fun toggleConfirmBeforeDelete() {
        viewModelScope.launch {
            repository.setConfirmBeforeDeletePref(!confirmBeforeDelete.value)
        }
    }

    fun saveDefaultWeatherCity(city: String) {
        val trimmed = city.trim()
        viewModelScope.launch {
            repository.setDefaultWeatherCityPref(trimmed)
            if (trimmed.isNotBlank()) {
                searchWeatherCity(trimmed)
            } else {
                showToast("Cleared default weather city")
            }
        }
    }

    fun clearChatHistory() {
        _stylist.update {
            it.copy(
                chatMessages = listOf(
                    StylistChatMessage(
                        fromUser = false,
                        text = "Fresh slate. Ask what you need to buy, send a photo of an item, tap Basics List for essentials, or ask for a fit."
                    )
                )
            )
        }
        showToast("Stylist chat cleared")
    }

    fun refreshExportCacheSize() {
        viewModelScope.launch {
            _exportCacheBytes.value = repository.imageStore.getExportCacheSizeBytes()
        }
    }

    fun clearExportCache() {
        viewModelScope.launch {
            val count = repository.imageStore.clearExportCache()
            _exportCacheBytes.value = repository.imageStore.getExportCacheSizeBytes()
            showToast(if (count > 0) "Cleared $count temporary export file(s)" else "Cache is already clean")
        }
    }

    // =========================================================================
    // 1. WEATHER (LIVE OPEN-METEO + MANUAL CONTROL PANEL)
    // =========================================================================
    fun fetchWeatherForCoords(lat: Double, lon: Double, label: String = "Your Location") {
        viewModelScope.launch {
            _weather.update { it.copy(loading = true) }
            weatherService.fetchWeatherByCoords(lat, lon, label)
                .onSuccess { snap ->
                    _weather.update {
                        it.copy(
                            city = snap.city,
                            tempC = snap.tempC,
                            condition = snap.condition,
                            windKmh = snap.windKmh,
                            humidityPct = snap.humidityPct,
                            manualOverride = false,
                            loading = false,
                            liveSnapshot = snap
                        )
                    }
                    showToast("Live weather loaded: ${snap.city}")
                }
                .onFailure { e ->
                    _weather.update { it.copy(loading = false) }
                    showToast(e.localizedMessage ?: "Live weather unavailable — use Manual controls")
                }
        }
    }

    fun searchWeatherCity(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _weather.update { it.copy(loading = true) }
            weatherService.searchCityAndFetchWeather(query)
                .onSuccess { snap ->
                    _weather.update {
                        it.copy(
                            city = snap.city,
                            tempC = snap.tempC,
                            condition = snap.condition,
                            windKmh = snap.windKmh,
                            humidityPct = snap.humidityPct,
                            manualOverride = false,
                            loading = false,
                            liveSnapshot = snap
                        )
                    }
                    showToast("Weather synced for ${snap.city}")
                }
                .onFailure { e ->
                    _weather.update { it.copy(loading = false) }
                    showToast(e.localizedMessage ?: "City not found")
                }
        }
    }

    fun setWeatherFahrenheit(useF: Boolean) {
        viewModelScope.launch {
            repository.setUseFahrenheitPref(useF)
            _weather.update { it.copy(useFahrenheit = useF) }
        }
    }

    fun setManualTempC(tempC: Int) {
        _weather.update { it.copy(tempC = tempC.coerceIn(-20, 48), manualOverride = true) }
    }

    fun setManualWindHumidity(windKmh: Int, humidityPct: Int) {
        _weather.update {
            it.copy(
                windKmh = windKmh.coerceIn(0, 100),
                humidityPct = humidityPct.coerceIn(0, 100),
                manualOverride = true
            )
        }
    }

    fun setManualCondition(condition: WeatherCondition) {
        _weather.update { it.copy(condition = condition, manualOverride = true) }
    }

    fun resetToLiveWeather() {
        val snap = _weather.value.liveSnapshot
        if (snap != null) {
            _weather.update {
                it.copy(
                    city = snap.city,
                    tempC = snap.tempC,
                    condition = snap.condition,
                    windKmh = snap.windKmh,
                    humidityPct = snap.humidityPct,
                    manualOverride = false
                )
            }
            showToast("Restored live weather for ${snap.city}")
        } else {
            searchWeatherCity("New York")
        }
    }

    fun styleForCurrentWeather() {
        val w = _weather.value
        _activeTab.value = PrimaryTab.STYLIST
        _stylist.update { it.copy(subTab = 0) }
        sendStylistMessage(
            "Style me a complete outfit from my available closet for ${w.formattedTemp()}, ${w.condition.label} weather (Wind ${w.windKmh} km/h, Humidity ${w.humidityPct}%). Explain why each layer fits today's climate and my Style DNA."
        )
    }

    // =========================================================================
    // 2. STYLE DNA, SEASONAL COLOR ANALYSIS & VIRTUAL MODEL PHOTO
    // =========================================================================
    fun runSeasonalColorAnalysis(selfieUri: Uri) {
        viewModelScope.launch {
            _colorAnalysisLoading.value = true
            _colorAnalysisError.value = null
            try {
                val bmp = repository.imageStore.bitmapFromUri(selfieUri, 840)
                    ?: throw IllegalStateException("Could not decode selfie photo.")
                val result = aiService.analyzeSeasonalColor(bmp)
                bmp.recycle()
                val updated = userProfile.value.copy(
                    seasonPalette = result.seasonPalette,
                    undertone = result.undertone,
                    contrastLevel = result.contrastLevel,
                    hairEyeDepth = result.hairEyeDepth,
                    paletteSummary = result.paletteSummary,
                    powerColors = result.powerColors,
                    neutralColors = result.neutralColors,
                    avoidColors = result.avoidColors
                )
                repository.updateUserProfile(updated)
                showToast("Assigned ${result.seasonPalette} palette!")
            } catch (e: TimeoutCancellationException) {
                _colorAnalysisError.value = "Color analysis timed out. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Seasonal color analysis failed", e)
                _colorAnalysisError.value = e.localizedMessage ?: "Seasonal color analysis failed."
            } finally {
                _colorAnalysisLoading.value = false
            }
        }
    }

    fun saveVirtualModelPhoto(uri: Uri) {
        viewModelScope.launch {
            val savedPath = repository.imageStore.importFromUri(uri, prefix = "virtual_model", maxDimension = 1024)
            if (savedPath != null) {
                repository.imageStore.deleteFile(userProfile.value.virtualModelImagePath)
                repository.updateUserProfile(userProfile.value.copy(virtualModelImagePath = savedPath))
                showToast("Virtual Model Photo saved for all future Try-Ons")
            } else {
                showToast("Unable to save photo")
            }
        }
    }

    fun updateStyleDna(
        archetypes: List<String>,
        silhouette: String,
        heightCm: Double,
        proportions: String,
        seasonPalette: String,
        monthlyBudget: Double
    ) {
        viewModelScope.launch {
            val updated = userProfile.value.copy(
                archetypes = archetypes.ifEmpty { listOf("Minimalist") },
                silhouette = silhouette,
                heightCm = heightCm,
                proportions = proportions,
                seasonPalette = seasonPalette,
                monthlyBudget = monthlyBudget
            )
            repository.updateUserProfile(updated)
                .onSuccess { showToast("Style DNA saved") }
                .onFailure { showToast(it.localizedMessage ?: "Save failed") }
        }
    }

    // =========================================================================
    // 3. SMART DIGITAL WARDROBE & PRO INGESTION
    // =========================================================================
    fun enqueueBatchPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val imported = uris.mapNotNull { uri ->
                repository.imageStore.importFromUri(uri, prefix = "garment", maxDimension = 960)
            }
            if (imported.isEmpty()) {
                showToast("No photos could be loaded")
                return@launch
            }
            val first = imported.first()
            val rest = imported.drop(1)
            _ingestion.update {
                it.copy(
                    activeOriginalPath = first,
                    activeProcessedPath = first,
                    batchQueuePaths = it.batchQueuePaths + rest,
                    autoTagResult = null,
                    error = null
                )
            }
            if (imported.size > 1) {
                showToast("Loaded ${imported.size} garments into ingestion queue")
            }
        }
    }

    fun applyCutoutToActivePhoto(threshold: Int, smoothing: Float) {
        val origPath = _ingestion.value.activeOriginalPath ?: return
        viewModelScope.launch {
            _ingestion.update { it.copy(cutoutThreshold = threshold, cutoutSmoothing = smoothing, aiProcessing = true, error = null) }
            try {
                val src = repository.imageStore.loadBitmap(origPath, 720)
                    ?: throw IllegalStateException("Original photo missing")
                val cut = repository.imageStore.applyStudioCutout(src, threshold, smoothing)
                src.recycle()
                val newPath = repository.imageStore.saveBitmap(cut, prefix = "cutout", asPng = true)
                cut.recycle()
                _ingestion.update { it.copy(activeProcessedPath = newPath, aiProcessing = false) }
                showToast("Studio Cutout applied")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ingestion.update { it.copy(aiProcessing = false, error = e.localizedMessage) }
            }
        }
    }

    fun restoreOriginalIngestionPhoto() {
        _ingestion.update { it.copy(activeProcessedPath = it.activeOriginalPath) }
    }

    fun generateAiFlatLayForActivePhoto() {
        val srcPath = _ingestion.value.activeOriginalPath ?: _ingestion.value.activeProcessedPath ?: return
        val threshold = _ingestion.value.cutoutThreshold
        val smoothing = _ingestion.value.cutoutSmoothing
        viewModelScope.launch {
            _ingestion.update { it.copy(aiProcessing = true, error = null) }
            try {
                val bmp = repository.imageStore.loadBitmap(srcPath, 840)
                    ?: throw IllegalStateException("Unable to load garment photo")
                val flatLay = repository.imageStore.createOnDeviceStudioFlatLay(bmp, threshold, smoothing)
                bmp.recycle()
                val saved = repository.imageStore.saveBitmap(flatLay, prefix = "flatlay", asPng = true)
                flatLay.recycle()
                _ingestion.update { it.copy(activeProcessedPath = saved, aiProcessing = false) }
                showToast("On-device Studio Flat-Lay applied (unlimited & free)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ingestion.update { it.copy(aiProcessing = false, error = e.localizedMessage ?: "Studio Flat-Lay failed") }
            }
        }
    }

    fun runVisionAutoTagOnActivePhoto() {
        val srcPath = _ingestion.value.activeProcessedPath ?: _ingestion.value.activeOriginalPath ?: return
        viewModelScope.launch {
            _ingestion.update { it.copy(aiProcessing = true, autoTagResult = null, error = null) }
            try {
                val bmp = repository.imageStore.loadBitmap(srcPath, 512)
                    ?: throw IllegalStateException("Unable to load garment photo")
                val tags = aiService.autoTagGarment(bmp)
                bmp.recycle()
                _ingestion.update { it.copy(autoTagResult = tags, aiProcessing = false) }
                showToast("Auto-tagged: ${tags.name}")
            } catch (e: TimeoutCancellationException) {
                _ingestion.update { it.copy(aiProcessing = false, error = "Auto-tag timed out. Please retry.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ingestion.update { it.copy(aiProcessing = false, error = e.localizedMessage ?: "Auto-tag failed") }
            }
        }
    }

    fun extractFullBodyOutfit(mirrorSelfieUri: Uri) {
        viewModelScope.launch {
            _ingestion.update { it.copy(extractorLoading = true, extractedCandidates = emptyList(), error = null) }
            try {
                val bmp = repository.imageStore.bitmapFromUri(mirrorSelfieUri, 1024)
                    ?: throw IllegalStateException("Unable to load mirror selfie")
                val rawCandidates = aiService.extractOutfitFromMirrorSelfie(bmp)
                val cropped = rawCandidates.map { c ->
                    val cropPath = repository.imageStore.cropNormalizedBox(bmp, c.ymin, c.xmin, c.ymax, c.xmax, "extracted")
                    c.copy(croppedImagePath = cropPath)
                }
                bmp.recycle()
                _ingestion.update { it.copy(extractedCandidates = cropped, extractorLoading = false) }
                showToast("Detected ${cropped.size} garments from mirror selfie!")
            } catch (e: TimeoutCancellationException) {
                _ingestion.update { it.copy(extractorLoading = false, error = "Outfit extraction timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ingestion.update { it.copy(extractorLoading = false, error = e.localizedMessage ?: "Extraction failed") }
            }
        }
    }

    fun toggleExtractedCandidate(id: String) {
        _ingestion.update { state ->
            state.copy(
                extractedCandidates = state.extractedCandidates.map {
                    if (it.id == id) it.copy(selected = !it.selected) else it
                }
            )
        }
    }

    fun importSelectedExtractedGarments() {
        val selected = _ingestion.value.extractedCandidates.filter { it.selected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            selected.forEach { c ->
                val item = GarmentItem(
                    name = c.name,
                    brand = "Archive",
                    category = c.category,
                    subCategory = c.subCategory,
                    colorName = c.colorName,
                    colorHex = c.colorHex,
                    fabric = c.fabric,
                    season = "All-Season",
                    formality = c.formality,
                    price = c.estimatedPrice,
                    wears = 1,
                    status = GarmentStatus.AVAILABLE.name,
                    localImagePath = c.croppedImagePath,
                    lastWornEpochMs = System.currentTimeMillis(),
                    purchasedEpochMs = System.currentTimeMillis()
                )
                if (repository.addGarment(item).isSuccess) added++
            }
            _ingestion.update { it.copy(extractedCandidates = emptyList()) }
            showToast("Imported $added extracted garments into Wardrobe!")
        }
    }

    fun saveIngestedGarment(garment: GarmentItem) {
        viewModelScope.launch {
            val withImg = garment.copy(
                localImagePath = _ingestion.value.activeProcessedPath ?: garment.localImagePath,
                purchasedEpochMs = System.currentTimeMillis()
            )
            repository.addGarment(withImg)
                .onSuccess {
                    // Advance batch queue if more photos remain
                    val remaining = _ingestion.value.batchQueuePaths
                    if (remaining.isNotEmpty()) {
                        val next = remaining.first()
                        _ingestion.update {
                            it.copy(
                                activeOriginalPath = next,
                                activeProcessedPath = next,
                                batchQueuePaths = remaining.drop(1),
                                autoTagResult = null,
                                error = null
                            )
                        }
                        showToast("Saved \"${garment.name}\" — next queued photo ready (${remaining.size} left)")
                    } else {
                        resetIngestion()
                        showToast("Archived \"${garment.name}\"")
                    }
                }
                .onFailure { showToast(it.localizedMessage ?: "Failed to save garment") }
        }
    }

    fun resetIngestion() {
        _ingestion.value = IngestionState()
    }

    fun logGarmentWear(item: GarmentItem) {
        viewModelScope.launch {
            repository.logGarmentWear(item.id, item.wears)
                .onSuccess { showToast("+1 wear logged for ${item.name}") }
                .onFailure { showToast(it.localizedMessage ?: "Update failed") }
        }
    }

    fun updateGarmentStatus(item: GarmentItem, status: GarmentStatus) {
        viewModelScope.launch {
            repository.updateGarmentStatus(item.id, status)
                .onSuccess { showToast("${item.name} marked ${status.label}") }
                .onFailure { showToast(it.localizedMessage ?: "Status update failed") }
        }
    }

    fun updateGarmentDetails(item: GarmentItem) {
        viewModelScope.launch {
            repository.updateGarment(item)
                .onSuccess { showToast("Updated \"${item.name}\"") }
                .onFailure { showToast(it.localizedMessage ?: "Update failed") }
        }
    }

    fun deleteGarment(item: GarmentItem) {
        viewModelScope.launch {
            repository.deleteGarment(item)
                .onSuccess { showToast("Removed \"${item.name}\"") }
                .onFailure { showToast(it.localizedMessage ?: "Delete failed") }
        }
    }

    // =========================================================================
    // 4. AI STYLIST SUITE (CHAT, VOICE, DRESS CODE, RATE MY FIT, DAILY OOTD)
    // =========================================================================
    private fun stylingEligibleWardrobe(): List<GarmentItem> {
        val all = garments.value
        if (_stylist.value.includeUnavailableOverride) return all
        val available = all.filter { it.statusEnum() == GarmentStatus.AVAILABLE }
        return available.ifEmpty { all }
    }

    private fun lovedAndSkippedIds(): Pair<Set<String>, Set<String>> {
        val fb = ootdFeedback.value
        val loved = fb.filter { it.action == "LOVE" || it.action == "WEAR" }.flatMap { it.itemIds }.toSet()
        val skipped = fb.filter { it.action == "SKIP" }.flatMap { it.itemIds }.toSet()
        return loved to skipped
    }

    fun setStylistSubTab(tab: Int) {
        _stylist.update { it.copy(subTab = tab) }
        if (tab == 2 && _stylist.value.ootdDeck.isEmpty() && !_stylist.value.ootdLoading) {
            generateDailyOotdDeck()
        }
    }

    fun selectDressCode(mode: DressCodeMode?) {
        _stylist.update { it.copy(selectedDressCode = if (it.selectedDressCode == mode) null else mode) }
    }

    fun toggleUnavailableOverride() {
        _stylist.update { it.copy(includeUnavailableOverride = !it.includeUnavailableOverride) }
    }

    fun sendStylistMessage(text: String, photoUri: Uri? = null, forceShoppingAdvisor: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && photoUri == null) return

        val effectivePrompt = trimmed.ifBlank {
            "Be brutally honest about this photo — is it worth getting or keeping, does it suit my Style DNA and closet, and what pieces should I get to pair with it?"
        }
        val displayUserText = trimmed.ifBlank { "Sent a photo — give me your honest take on this." }
        val priorHistory = _stylist.value.chatMessages.takeLast(5)

        viewModelScope.launch {
            var savedPhotoPath: String? = null
            var photoBitmap: android.graphics.Bitmap? = null
            try {
                if (photoUri != null) {
                    savedPhotoPath = repository.imageStore.importFromUri(
                        uri = photoUri,
                        prefix = "chat_photo",
                        maxDimension = 840
                    )
                    photoBitmap = repository.imageStore.loadBitmap(savedPhotoPath, 640)
                }

                val userMsg = StylistChatMessage(
                    fromUser = true,
                    text = displayUserText,
                    attachedImagePath = savedPhotoPath
                )
                _stylist.update { it.copy(chatMessages = it.chatMessages + userMsg, chatLoading = true) }

                val (loved, skipped) = lovedAndSkippedIds()
                val reply = aiService.chatWithStylist(
                    userMessage = effectivePrompt,
                    profile = userProfile.value,
                    availableWardrobe = stylingEligibleWardrobe(),
                    weather = _weather.value,
                    dressCode = _stylist.value.selectedDressCode,
                    lovedItemIds = loved,
                    skippedItemIds = skipped,
                    tone = aiCritiqueTone.value,
                    gender = styleGender.value,
                    attachedPhoto = photoBitmap,
                    recentHistory = priorHistory,
                    forceShoppingAdvisor = forceShoppingAdvisor
                )
                _stylist.update { it.copy(chatMessages = it.chatMessages + reply, chatLoading = false) }
                if (voiceReadoutEnabled.value) {
                    _lastSpokenReply.value = reply.text
                }
            } catch (e: TimeoutCancellationException) {
                val errMsg = StylistChatMessage(fromUser = false, text = "Stylist request timed out after 50 seconds. Please retry.")
                _stylist.update { it.copy(chatMessages = it.chatMessages + errMsg, chatLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errMsg = StylistChatMessage(fromUser = false, text = "Stylist error: ${e.localizedMessage ?: "Unable to connect to Gemini."}")
                _stylist.update { it.copy(chatMessages = it.chatMessages + errMsg, chatLoading = false) }
            } finally {
                photoBitmap?.recycle()
            }
        }
    }

    fun requestBasicsChecklist() {
        if (_stylist.value.chatLoading) return
        sendStylistMessage(
            text = "Create a complete, brutally honest list of all the basic wardrobe essentials I need to get based on my Style DNA and what's missing from my closet. Group across Must-Have, High Impact, and Nice-to-Have with fit/fabric details and realistic ₹ INR prices.",
            photoUri = null,
            forceShoppingAdvisor = true
        )
    }

    fun saveShoppingItemToWishlist(item: StylistShoppingItem, testInStudio: Boolean = false) {
        viewModelScope.launch {
            val fullNotes = buildString {
                if (item.fitAndFabric.isNotBlank()) {
                    append(item.fitAndFabric)
                    if (item.whyYouNeedIt.isNotBlank()) append(" • ")
                }
                append(item.whyYouNeedIt)
            }.trim()
            val wl = WishlistItem(
                name = item.name,
                brand = item.priority,
                category = item.category,
                colorName = item.colorName,
                colorHex = item.colorHex,
                price = item.estimatedPriceInr,
                reason = fullNotes,
                unlocks = if (item.priority == "Must-Have") "6+ everyday outfits" else "4+ outfit combos",
                verdict = "BUY"
            )
            repository.addWishlistItem(wl)
                .onSuccess { id ->
                    showToast("Saved \"${item.name}\" to Wishlist")
                    if (testInStudio) testWishlistItemInStudio(wl.copy(id = id))
                }
                .onFailure { showToast(it.localizedMessage ?: "Could not save to Wishlist") }
        }
    }

    fun saveAllShoppingItemsToWishlist(items: List<StylistShoppingItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var savedCount = 0
            items.forEach { item ->
                val fullNotes = buildString {
                    if (item.fitAndFabric.isNotBlank()) {
                        append(item.fitAndFabric)
                        if (item.whyYouNeedIt.isNotBlank()) append(" • ")
                    }
                    append(item.whyYouNeedIt)
                }.trim()
                val wl = WishlistItem(
                    name = item.name,
                    brand = item.priority,
                    category = item.category,
                    colorName = item.colorName,
                    colorHex = item.colorHex,
                    price = item.estimatedPriceInr,
                    reason = fullNotes,
                    unlocks = if (item.priority == "Must-Have") "6+ everyday outfits" else "4+ outfit combos",
                    verdict = "BUY"
                )
                if (repository.addWishlistItem(wl).isSuccess) {
                    savedCount++
                }
            }
            if (savedCount > 0) {
                showToast("Saved $savedCount essentials to Wishlist")
            } else {
                showToast("Could not save items to Wishlist")
            }
        }
    }

    fun runRateMyFit(photoUri: Uri, occasion: String) {
        viewModelScope.launch {
            _stylist.update { it.copy(rateMyFitLoading = true, rateMyFitError = null, rateMyFitResult = null) }
            try {
                val savedPath = repository.imageStore.importFromUri(photoUri, prefix = "mirror_check", maxDimension = 960)
                val bmp = repository.imageStore.loadBitmap(savedPath, 960)
                    ?: throw IllegalStateException("Could not load mirror selfie.")
                val res = aiService.rateMyFit(
                    mirrorSelfie = bmp,
                    occasion = occasion.ifBlank { "Smart Casual" },
                    profile = userProfile.value,
                    availableWardrobe = stylingEligibleWardrobe(),
                    tone = aiCritiqueTone.value,
                    gender = styleGender.value
                )
                bmp.recycle()
                _stylist.update {
                    it.copy(
                        rateMyFitLoading = false,
                        rateMyFitPhotoPath = savedPath,
                        rateMyFitResult = res
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _stylist.update { it.copy(rateMyFitLoading = false, rateMyFitError = "Mirror Check timed out after 60s.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stylist.update { it.copy(rateMyFitLoading = false, rateMyFitError = e.localizedMessage ?: "Rate My Fit failed.") }
            }
        }
    }

    fun generateDailyOotdDeck() {
        val eligible = stylingEligibleWardrobe()
        if (eligible.isEmpty()) {
            _stylist.update { it.copy(ootdError = "Add a few pieces to your Wardrobe first to generate Daily OOTD looks.") }
            return
        }
        viewModelScope.launch {
            _stylist.update { it.copy(ootdLoading = true, ootdError = null) }
            try {
                val (loved, skipped) = lovedAndSkippedIds()
                val deck = aiService.generateDailyOotdDeck(
                    profile = userProfile.value,
                    availableWardrobe = eligible,
                    weather = _weather.value,
                    lovedItemIds = loved,
                    skippedItemIds = skipped,
                    tone = aiCritiqueTone.value,
                    gender = styleGender.value
                )
                _stylist.update { it.copy(ootdDeck = deck, ootdCurrentIndex = 0, ootdLoading = false) }
            } catch (e: TimeoutCancellationException) {
                _stylist.update { it.copy(ootdLoading = false, ootdError = "OOTD generation timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stylist.update { it.copy(ootdLoading = false, ootdError = e.localizedMessage ?: "Could not generate OOTD deck.") }
            }
        }
    }

    fun handleOotdSwipe(action: String) {
        val deck = _stylist.value.ootdDeck
        val idx = _stylist.value.ootdCurrentIndex
        val card = deck.getOrNull(idx) ?: return
        viewModelScope.launch {
            repository.logOotdFeedback(card.title, action, card.itemIds)
            when (action) {
                "LOVE" -> {
                    repository.saveLook(
                        SavedLook(
                            title = card.title,
                            occasion = card.vibe,
                            collectionName = "333 Capsule",
                            harmonyScore = card.harmonyScore,
                            itemIds = card.itemIds,
                            notes = card.rationale
                        )
                    )
                    showToast("Loved & saved \"${card.title}\"")
                }
                "WEAR" -> {
                    logOutfitToDate(
                        dateKey = todayDateKey(),
                        lookTitle = card.title,
                        itemIds = card.itemIds,
                        lookId = null,
                        occasion = card.vibe,
                        whoYouSaw = "",
                        markWorn = true
                    )
                    showToast("Logged \"${card.title}\" for Today!")
                }
                else -> showToast("Skipped — weighting future looks")
            }
            _stylist.update { it.copy(ootdCurrentIndex = idx + 1) }
        }
    }

    // =========================================================================
    // 5. OUTFIT STUDIO PRO (SLOTS, COLLAGE, TRY-ON + A/B, EXPORTER, COLLECTIONS)
    // =========================================================================
    private fun seedDefaultStudioSlots(list: List<GarmentItem>) {
        val available = list.filter { it.statusEnum() == GarmentStatus.AVAILABLE }.ifEmpty { list }
        val slots = GarmentCategory.allLabels.associateWith { cat ->
            available.firstOrNull { it.category == cat }?.id
        }
        _studio.update { it.copy(slots = slots) }
        syncCollageFromSlots()
    }

    fun setStudioMode(mode: String) {
        if (mode == "Collage" && _studio.value.collagePieces.isEmpty()) {
            syncCollageFromSlots()
        }
        _studio.update { it.copy(mode = mode) }
    }

    fun setStudioTitle(title: String) {
        _studio.update { it.copy(title = title) }
    }

    fun assignStudioSlot(category: String, itemId: String?) {
        _studio.update { s ->
            s.copy(
                slots = s.slots.toMutableMap().apply { put(category, itemId) },
                tryOnImagePath = null,
                tryOnCritique = null,
                tryOnError = null
            )
        }
        syncCollageFromSlots()
    }

    fun toggleStudioLock(category: String) {
        _studio.update { s ->
            val current = s.locks[category] ?: false
            s.copy(locks = s.locks.toMutableMap().apply { put(category, !current) })
        }
    }

    fun shuffleUnlockedStudioSlots() {
        val available = stylingEligibleWardrobe()
        if (available.isEmpty()) {
            showToast("Add pieces to your Wardrobe first")
            return
        }
        val current = _studio.value
        val updatedSlots = current.slots.toMutableMap()
        GarmentCategory.allLabels.forEach { cat ->
            if (current.locks[cat] == true) return@forEach
            val pool = available.filter { it.category == cat }
            if (pool.isNotEmpty()) {
                updatedSlots[cat] = pool.random().id
            }
        }
        _studio.update { it.copy(slots = updatedSlots, tryOnImagePath = null, tryOnError = null) }
        syncCollageFromSlots()
        showToast("Shuffled unlocked layers")
    }

    fun aiCompleteStudioLook() {
        val available = stylingEligibleWardrobe()
        if (available.isEmpty()) {
            showToast("Add pieces to your Wardrobe first")
            return
        }
        viewModelScope.launch {
            _studio.update { it.copy(aiCompleting = true) }
            try {
                val allMap = garments.value.associateBy { it.id }
                val lockedMap = _studio.value.locks
                    .filterValues { it }
                    .keys
                    .mapNotNull { cat -> _studio.value.slots[cat]?.let { id -> allMap[id]?.let { cat to it } } }
                    .toMap()
                val (title, resultSlots) = aiService.completeStudioLook(
                    lockedSlots = lockedMap,
                    availableWardrobe = stylingEligibleWardrobe(),
                    profile = userProfile.value,
                    weather = _weather.value,
                    gender = styleGender.value
                )
                val merged = _studio.value.slots.toMutableMap()
                GarmentCategory.allLabels.forEach { cat ->
                    if (_studio.value.locks[cat] != true) {
                        merged[cat] = resultSlots[cat]
                    }
                }
                _studio.update { it.copy(title = title, slots = merged, aiCompleting = false, tryOnImagePath = null) }
                syncCollageFromSlots()
                showToast("AI styled \"$title\"")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studio.update { it.copy(aiCompleting = false) }
                showToast(e.localizedMessage ?: "AI Complete failed")
            }
        }
    }

    fun openOutfitInStudio(title: String, itemIds: List<String>) {
        val allMap = garments.value.associateBy { it.id }
        val newSlots = GarmentCategory.allLabels.associateWith { null as String? }.toMutableMap()
        itemIds.forEach { id ->
            val g = allMap[id] ?: return@forEach
            if (newSlots.containsKey(g.category)) newSlots[g.category] = g.id
        }
        _studio.update {
            it.copy(
                title = title,
                mode = "Slots",
                slots = newSlots,
                locks = GarmentCategory.allLabels.associateWith { false },
                tryOnImagePath = null
            )
        }
        syncCollageFromSlots()
        _activeTab.value = PrimaryTab.STUDIO
    }

    fun sendGarmentToStudioAndLock(item: GarmentItem) {
        _studio.update { s ->
            s.copy(
                title = "Styled Around ${item.name.take(26)}",
                mode = "Slots",
                slots = s.slots.toMutableMap().apply { put(item.category, item.id) },
                locks = s.locks.toMutableMap().apply { put(item.category, true) },
                tryOnImagePath = null
            )
        }
        syncCollageFromSlots()
        _activeTab.value = PrimaryTab.STUDIO
        showToast("Locked \"${item.name}\" in Studio")
    }

    fun testWishlistItemInStudio(wl: WishlistItem) {
        // Add temporary preview item by matching wishlist item id; Studio resolves both garments and wishlist
        _studio.update { s ->
            s.copy(
                title = "Testing: ${wl.name.take(24)}",
                mode = "Slots",
                slots = s.slots.toMutableMap().apply { put(wl.category, "wl:${wl.id}") },
                locks = s.locks.toMutableMap().apply { put(wl.category, true) },
                tryOnImagePath = null
            )
        }
        syncCollageFromSlots()
        _activeTab.value = PrimaryTab.STUDIO
        showToast("Testing \"${wl.name}\" with your wardrobe")
    }

    fun resolveStudioItems(): List<GarmentItem> {
        val garmentMap = garments.value.associateBy { it.id }
        val wlMap = wishlist.value.associateBy { "wl:${it.id}" }
        return GarmentCategory.allLabels.mapNotNull { cat ->
            val id = _studio.value.slots[cat] ?: return@mapNotNull null
            garmentMap[id] ?: wlMap[id]?.let { w ->
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

    fun syncCollageFromSlots() {
        val defaults = mapOf(
            "Outerwear" to Pair(0.08f, 0.14f),
            "Tops" to Pair(0.50f, 0.14f),
            "Bottoms" to Pair(0.30f, 0.48f),
            "Footwear" to Pair(0.62f, 0.66f),
            "Bags" to Pair(0.06f, 0.62f),
            "Accessories" to Pair(0.64f, 0.36f)
        )
        val pieces = GarmentCategory.allLabels.mapIndexedNotNull { idx, cat ->
            val itemId = _studio.value.slots[cat] ?: return@mapIndexedNotNull null
            val (x, y) = defaults[cat] ?: Pair(0.3f, 0.3f)
            CollagePieceState(
                id = "col_$cat",
                itemId = itemId,
                xFraction = x,
                yFraction = y,
                scale = if (cat == "Outerwear" || cat == "Bottoms") 1.12f else 0.96f,
                rotationDeg = if (idx % 2 == 0) -3f else 3f,
                zIndex = (idx + 1).toFloat()
            )
        }
        _studio.update {
            it.copy(
                collagePieces = pieces,
                selectedCollageId = pieces.firstOrNull()?.id
            )
        }
    }

    fun selectCollagePiece(id: String) {
        _studio.update { it.copy(selectedCollageId = id) }
    }

    fun moveCollagePiece(id: String, dxFraction: Float, dyFraction: Float) {
        _studio.update { s ->
            s.copy(
                collagePieces = s.collagePieces.map { p ->
                    if (p.id == id) p.copy(
                        xFraction = (p.xFraction + dxFraction).coerceIn(-0.08f, 0.78f),
                        yFraction = (p.yFraction + dyFraction).coerceIn(-0.04f, 0.78f)
                    ) else p
                }
            )
        }
    }

    fun updateSelectedCollagePiece(transform: (CollagePieceState) -> CollagePieceState) {
        val sel = _studio.value.selectedCollageId ?: return
        _studio.update { s ->
            s.copy(collagePieces = s.collagePieces.map { if (it.id == sel) transform(it) else it })
        }
    }

    fun setCollageBackdrop(backdrop: String) {
        _studio.update { it.copy(collageBackdrop = backdrop) }
    }

    fun addCollageSticker(text: String) {
        if (text.isBlank()) return
        _studio.update { s ->
            s.copy(
                collageStickers = s.collageStickers + CollageTextSticker(
                    text = text.trim().uppercase(),
                    xFraction = 0.12f,
                    yFraction = 0.82f
                )
            )
        }
    }

    fun removeCollageSticker(id: String) {
        _studio.update { s -> s.copy(collageStickers = s.collageStickers.filterNot { it.id == id }) }
    }

    fun setTryOnScene(scene: String) {
        _studio.update { it.copy(tryOnScene = scene) }
    }

    fun generateVirtualTryOn() {
        val items = resolveStudioItems()
        if (items.isEmpty()) {
            showToast("Select at least one garment first")
            return
        }
        viewModelScope.launch {
            _studio.update { it.copy(tryOnLoading = true, tryOnError = null, tryOnCritique = null) }
            try {
                val harmony = ColorTheoryEngine.evaluateOutfitHarmony(items, userProfile.value)
                // 1. Instant, unlimited on-device Editorial Look Preview Board (Virtual Model + Garments)
                val board = repository.imageStore.createEditorialLookBoard(
                    title = _studio.value.title,
                    sceneBackdrop = _studio.value.tryOnScene,
                    harmony = harmony,
                    items = items,
                    virtualModelPath = userProfile.value.virtualModelImagePath
                )
                val savedPath = repository.imageStore.saveBitmap(board, prefix = "lookboard", asPng = false)
                board.recycle()
                _studio.update { it.copy(tryOnImagePath = savedPath) }

                // 2. Free-tier Gemini 3.5 Flash-Lite fit, drape & scene styling critique
                val vmBmp = repository.imageStore.loadBitmap(userProfile.value.virtualModelImagePath, 512)
                val critique = aiService.generateLookFitCritique(
                    items = items,
                    sceneBackdrop = _studio.value.tryOnScene,
                    profile = userProfile.value,
                    virtualModelPhoto = vmBmp,
                    tone = aiCritiqueTone.value,
                    gender = styleGender.value
                )
                vmBmp?.recycle()
                _studio.update { it.copy(tryOnLoading = false, tryOnCritique = critique) }
                showToast("Look Preview & AI Fit Check ready!")
            } catch (e: TimeoutCancellationException) {
                _studio.update { it.copy(tryOnLoading = false, tryOnError = "AI Fit Critique timed out, but your Lookboard Preview is ready above.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Look Preview AI critique failed", e)
                _studio.update { it.copy(tryOnLoading = false, tryOnError = e.localizedMessage ?: "AI Fit Critique failed.") }
            }
        }
    }

    fun saveTryOnToComparisonSlot(isSlotA: Boolean) {
        val path = _studio.value.tryOnImagePath ?: return
        val items = resolveStudioItems()
        val snap = TryOnSlotSnapshot(
            label = if (isSlotA) "Look A" else "Look B",
            title = _studio.value.title,
            scene = _studio.value.tryOnScene,
            itemIds = items.map { it.id },
            imagePath = path
        )
        _studio.update {
            if (isSlotA) it.copy(slotA = snap) else it.copy(slotB = snap, compareAB = it.slotA != null)
        }
        showToast("Pinned to ${snap.label} for A/B comparison")
    }

    fun toggleABComparison() {
        _studio.update { it.copy(compareAB = !it.compareAB) }
    }

    fun exportLookbook(template: LookbookExportTemplate, onExportReady: (File) -> Unit) {
        val items = resolveStudioItems()
        if (items.isEmpty()) {
            showToast("Add garments to Studio before exporting")
            return
        }
        viewModelScope.launch {
            _studio.update { it.copy(exportingLookbook = true) }
            try {
                val harmony = ColorTheoryEngine.evaluateOutfitHarmony(items, userProfile.value)
                val w = _weather.value
                val file = repository.imageStore.exportLookbookPng(
                    template = template,
                    title = _studio.value.title,
                    weatherLabel = "${w.formattedTemp()} ${w.condition.label}",
                    harmony = harmony,
                    items = items,
                    tryOnImagePath = _studio.value.tryOnImagePath
                )
                _studio.update { it.copy(exportingLookbook = false, lastExportedFile = file) }
                refreshExportCacheSize()
                showToast("Saved ${template.label} to Pictures/Criel")
                onExportReady(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studio.update { it.copy(exportingLookbook = false) }
                showToast(e.localizedMessage ?: "Export failed")
            }
        }
    }

    fun saveCurrentStudioLook(title: String, occasion: String, collectionName: String, assignDateKey: String?) {
        val realItemIds = _studio.value.slots.values.filterNotNull().filter { !it.startsWith("wl:") }
        if (realItemIds.isEmpty()) {
            showToast("Add owned garments before saving")
            return
        }
        val items = resolveStudioItems()
        val harmony = ColorTheoryEngine.evaluateOutfitHarmony(items, userProfile.value)
        viewModelScope.launch {
            val look = SavedLook(
                title = title.ifBlank { _studio.value.title },
                occasion = occasion.ifBlank { "Editorial" },
                collectionName = collectionName.ifBlank { "Autumn Work" },
                harmonyScore = harmony.score,
                itemIds = realItemIds,
                localTryOnImagePath = _studio.value.tryOnImagePath
            )
            repository.saveLook(look)
                .onSuccess { lookId ->
                    if (!assignDateKey.isNullOrBlank()) {
                        logOutfitToDate(assignDateKey, look.title, realItemIds, lookId, look.occasion, "", false)
                    }
                    showToast("Saved \"${look.title}\" to $collectionName")
                }
                .onFailure { showToast(it.localizedMessage ?: "Save failed") }
        }
    }

    fun deleteSavedLook(lookId: String) {
        viewModelScope.launch {
            repository.deleteLook(lookId).onSuccess { showToast("Look removed") }
        }
    }

    // =========================================================================
    // 6. SMART SHOPPING: "SHOULD I BUY THIS?", GAPS, WISHLIST, RESALE, TRIPS
    // =========================================================================
    fun setCurateSubTab(tab: Int) {
        _curate.update { it.copy(subTab = tab) }
    }

    fun scanProspectivePurchase(photoUri: Uri?, description: String, priceInr: Double) {
        viewModelScope.launch {
            _curate.update { it.copy(buyScanLoading = true, buyScanError = null, buyScanResult = null) }
            try {
                val savedPath = photoUri?.let { repository.imageStore.importFromUri(it, "buyscan", 840) }
                val bmp = savedPath?.let { repository.imageStore.loadBitmap(it, 840) }
                val result = aiService.evaluateProspectivePurchase(
                    productPhoto = bmp,
                    itemDescription = description,
                    priceInr = priceInr,
                    profile = userProfile.value,
                    wardrobe = garments.value,
                    tone = aiCritiqueTone.value,
                    gender = styleGender.value
                )
                bmp?.recycle()
                _curate.update {
                    it.copy(
                        buyScanLoading = false,
                        buyScanImagePath = savedPath,
                        buyScanResult = result
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _curate.update { it.copy(buyScanLoading = false, buyScanError = "Purchase scan timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _curate.update { it.copy(buyScanLoading = false, buyScanError = e.localizedMessage ?: "Scan failed.") }
            }
        }
    }

    fun saveScanResultToWishlist(result: ShouldIBuyResult, price: Double, testNow: Boolean) {
        viewModelScope.launch {
            val wl = WishlistItem(
                name = result.itemName,
                brand = "Scanned Pick",
                category = result.category,
                colorName = result.colorName,
                colorHex = result.colorHex,
                price = price,
                reason = result.rationale,
                unlocks = "+${result.outfitMultiplierCount} outfits",
                verdict = result.verdict,
                localImagePath = _curate.value.buyScanImagePath
            )
            repository.addWishlistItem(wl)
                .onSuccess { id ->
                    showToast("Added \"${wl.name}\" to Wishlist")
                    if (testNow) testWishlistItemInStudio(wl.copy(id = id))
                }
        }
    }

    fun runWardrobeGapAnalysis() {
        viewModelScope.launch {
            _curate.update { it.copy(gapsLoading = true, gapsError = null) }
            try {
                val recs = aiService.analyzeWardrobeGaps(userProfile.value, garments.value, styleGender.value)
                _curate.update { it.copy(gapsLoading = false, gapRecommendations = recs) }
                showToast("Identified top ${recs.size} Multiplier Pieces")
            } catch (e: TimeoutCancellationException) {
                _curate.update { it.copy(gapsLoading = false, gapsError = "Gap audit timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _curate.update { it.copy(gapsLoading = false, gapsError = e.localizedMessage ?: "Gap analysis failed.") }
            }
        }
    }

    fun addGapRecommendationToWishlist(rec: GapRecommendation, testInStudio: Boolean = false) {
        viewModelScope.launch {
            val wl = WishlistItem(
                name = rec.name,
                brand = "Multiplier Pick",
                category = rec.category,
                colorName = rec.colorName,
                colorHex = rec.colorHex,
                price = rec.price,
                reason = rec.reason,
                unlocks = rec.unlocks,
                verdict = "BUY"
            )
            repository.addWishlistItem(wl).onSuccess { id ->
                showToast("Saved \"${rec.name}\" to Wishlist")
                if (testInStudio) testWishlistItemInStudio(wl.copy(id = id))
            }
        }
    }

    fun addManualWishlistItem(name: String, category: String, colorName: String, colorHex: String, price: Double, reason: String) {
        viewModelScope.launch {
            repository.addWishlistItem(
                WishlistItem(
                    name = name,
                    category = category,
                    colorName = colorName,
                    colorHex = colorHex,
                    price = price,
                    reason = reason
                )
            ).onSuccess { showToast("Added \"$name\" to Wishlist") }
        }
    }

    fun markWishlistBought(item: WishlistItem) {
        viewModelScope.launch {
            val newGarment = GarmentItem(
                name = item.name,
                brand = item.brand.ifBlank { "Archive" },
                category = item.category,
                colorName = item.colorName,
                colorHex = item.colorHex,
                price = item.price,
                wears = 0,
                status = GarmentStatus.AVAILABLE.name,
                localImagePath = item.localImagePath,
                tags = listOf("new-arrival"),
                purchasedEpochMs = System.currentTimeMillis()
            )
            repository.addGarment(newGarment).onSuccess {
                repository.deleteWishlistItem(item.id)
                showToast("Moved \"${item.name}\" into Wardrobe!")
            }
        }
    }

    fun deleteWishlistItem(id: String) {
        viewModelScope.launch { repository.deleteWishlistItem(id) }
    }

    fun generateResaleListing(garment: GarmentItem) {
        viewModelScope.launch {
            _curate.update { it.copy(resaleLoadingId = garment.id, resaleError = null) }
            try {
                val listing = aiService.generateResaleListing(garment)
                _curate.update {
                    it.copy(
                        resaleLoadingId = null,
                        resaleListings = it.resaleListings + (garment.id to listing)
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _curate.update { it.copy(resaleLoadingId = null, resaleError = "Listing generation timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _curate.update { it.copy(resaleLoadingId = null, resaleError = e.localizedMessage ?: "Listing generation failed.") }
            }
        }
    }

    // =========================================================================
    // 7. SMART CAPSULE TRIP PLANNER ("PACK MY BAG")
    // =========================================================================
    fun generateTripPlan(destination: String, days: Int, climate: String, activities: String, bagSize: String) {
        viewModelScope.launch {
            _curate.update { it.copy(tripLoading = true, tripError = null) }
            try {
                val (packIds, itineraryLines) = aiService.generateCapsuleTripPlan(
                    destination = destination,
                    days = days.coerceIn(1, 21),
                    climate = climate,
                    activities = activities,
                    bagSize = bagSize,
                    profile = userProfile.value,
                    availableWardrobe = stylingEligibleWardrobe()
                )
                val plan = TripPlan(
                    destination = destination,
                    days = days.coerceIn(1, 21),
                    climate = climate,
                    activities = activities,
                    bagSize = bagSize,
                    packingItemIds = packIds,
                    checkedItemIds = emptyList(),
                    itineraryLines = itineraryLines
                )
                repository.saveTripPlan(plan)
                _curate.update { it.copy(tripLoading = false) }
                showToast("Packed ${packIds.size}-piece capsule for $destination!")
            } catch (e: TimeoutCancellationException) {
                _curate.update { it.copy(tripLoading = false, tripError = "Trip packing timed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _curate.update { it.copy(tripLoading = false, tripError = e.localizedMessage ?: "Trip packing failed.") }
            }
        }
    }

    fun toggleTripPackedItem(trip: TripPlan, itemId: String) {
        viewModelScope.launch {
            val updatedChecked = if (itemId in trip.checkedItemIds) {
                trip.checkedItemIds - itemId
            } else {
                trip.checkedItemIds + itemId
            }
            repository.saveTripPlan(trip.copy(checkedItemIds = updatedChecked))
        }
    }

    fun saveTripItineraryToCalendar(trip: TripPlan) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            var saved = 0
            trip.itineraryLines.forEachIndexed { idx, raw ->
                val parts = raw.split("|")
                val occasion = parts.getOrNull(1)?.ifBlank { trip.destination } ?: trip.destination
                val ids = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                val note = parts.getOrNull(3) ?: ""
                val dateKey = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val entry = CalendarEntry(
                    dateKey = dateKey,
                    lookTitle = "${trip.destination} · Day ${idx + 1}",
                    itemIds = ids,
                    occasion = occasion,
                    whoYouSaw = "Trip: ${trip.destination}",
                    worn = false,
                    notes = note
                )
                if (repository.saveCalendarEntry(entry).isSuccess) saved++
            }
            showToast("Scheduled $saved trip days to Calendar!")
        }
    }

    // =========================================================================
    // 8. FULL MONTHLY CALENDAR & REPEAT-WEAR ALERTS
    // =========================================================================
    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun logOutfitToDate(
        dateKey: String,
        lookTitle: String,
        itemIds: List<String>,
        lookId: String?,
        occasion: String,
        whoYouSaw: String,
        markWorn: Boolean
    ) {
        viewModelScope.launch {
            val alert = ColorTheoryEngine.findRepeatWearAlert(dateKey, itemIds, lookId, calendarEntries.value)
            val entry = CalendarEntry(
                dateKey = dateKey,
                lookId = lookId,
                lookTitle = lookTitle,
                itemIds = itemIds,
                occasion = occasion,
                whoYouSaw = whoYouSaw,
                worn = markWorn
            )
            repository.saveCalendarEntry(entry).onSuccess {
                if (markWorn) {
                    val map = garments.value.associateBy { it.id }
                    itemIds.forEach { id -> map[id]?.let { repository.logGarmentWear(it.id, it.wears) } }
                }
                showToast(alert ?: "Logged \"$lookTitle\" for $dateKey")
            }
        }
    }

    fun markCalendarEntryWorn(entry: CalendarEntry) {
        if (entry.worn) return
        viewModelScope.launch {
            repository.saveCalendarEntry(entry.copy(worn = true)).onSuccess {
                val map = garments.value.associateBy { it.id }
                entry.itemIds.forEach { id -> map[id]?.let { repository.logGarmentWear(it.id, it.wears) } }
                showToast("Marked worn & updated CPW for ${entry.itemIds.size} pieces")
            }
        }
    }

    fun deleteCalendarEntry(dateKey: String) {
        viewModelScope.launch {
            repository.deleteCalendarEntry(dateKey).onSuccess { showToast("Cleared $dateKey") }
        }
    }

    // =========================================================================
    // 9. 1-CLICK FULL CLOSET BACKUP EXPORT / IMPORT (.json)
    // =========================================================================
    fun exportClosetBackup(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            repository.exportFullBackupToUri(
                uri = uri,
                profile = userProfile.value,
                garments = garments.value,
                looks = savedLooks.value,
                calendar = calendarEntries.value,
                wishlist = wishlist.value,
                trips = trips.value
            ).onSuccess { count ->
                showToast("Exported full backup ($count records + photos)")
            }.onFailure { e ->
                showToast(e.localizedMessage ?: "Backup export failed")
            }
            _backupBusy.value = false
        }
    }

    fun importClosetBackup(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            repository.importFullBackupFromUri(uri)
                .onSuccess { count -> showToast("Imported $count items from backup!") }
                .onFailure { e -> showToast(e.localizedMessage ?: "Backup import failed") }
            _backupBusy.value = false
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AtelierViewModel(
                    repository = AtelierRepository(context.applicationContext),
                    aiService = AiStylistService(),
                    weatherService = WeatherService()
                )
            }
        }
    }
}
