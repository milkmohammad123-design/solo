package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.atelierPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "atelier_prefs")

class AtelierRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    val imageStore: LocalImageStore
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        imageStore = LocalImageStore(context.applicationContext)
    )

    private val auth = Firebase.auth
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val useFahrenheitKey = booleanPreferencesKey("use_fahrenheit")
    private val voiceReadoutKey = booleanPreferencesKey("voice_readout")
    private val aiCritiqueToneKey = stringPreferencesKey("ai_critique_tone")
    private val styleGenderKey = stringPreferencesKey("style_gender")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val confirmDeleteKey = booleanPreferencesKey("confirm_before_delete")
    private val defaultWeatherCityKey = stringPreferencesKey("default_weather_city")

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // =========================================================================
    // LIGHTWEIGHT PREFERENCES (DataStore)
    // =========================================================================
    val darkModeFlow: Flow<Boolean> = context.atelierPrefsDataStore.data.map { it[darkModeKey] ?: false }
    val useFahrenheitFlow: Flow<Boolean> = context.atelierPrefsDataStore.data.map { it[useFahrenheitKey] ?: false }
    val voiceReadoutFlow: Flow<Boolean> = context.atelierPrefsDataStore.data.map { it[voiceReadoutKey] ?: false }
    val aiCritiqueToneFlow: Flow<AiCritiqueTone> = context.atelierPrefsDataStore.data.map {
        AiCritiqueTone.safeValueOf(it[aiCritiqueToneKey])
    }
    val styleGenderFlow: Flow<StyleGender> = context.atelierPrefsDataStore.data.map {
        StyleGender.safeValueOf(it[styleGenderKey])
    }
    val hapticsEnabledFlow: Flow<Boolean> = context.atelierPrefsDataStore.data.map { it[hapticsEnabledKey] ?: true }
    val confirmBeforeDeleteFlow: Flow<Boolean> = context.atelierPrefsDataStore.data.map { it[confirmDeleteKey] ?: true }
    val defaultWeatherCityFlow: Flow<String> = context.atelierPrefsDataStore.data.map { it[defaultWeatherCityKey] ?: "" }

    suspend fun setDarkModePref(enabled: Boolean) {
        context.atelierPrefsDataStore.edit { it[darkModeKey] = enabled }
    }

    suspend fun setUseFahrenheitPref(enabled: Boolean) {
        context.atelierPrefsDataStore.edit { it[useFahrenheitKey] = enabled }
    }

    suspend fun setVoiceReadoutPref(enabled: Boolean) {
        context.atelierPrefsDataStore.edit { it[voiceReadoutKey] = enabled }
    }

    suspend fun setAiCritiqueTonePref(tone: AiCritiqueTone) {
        context.atelierPrefsDataStore.edit { it[aiCritiqueToneKey] = tone.name }
    }

    suspend fun setStyleGenderPref(gender: StyleGender) {
        context.atelierPrefsDataStore.edit { it[styleGenderKey] = gender.name }
    }

    suspend fun setHapticsEnabledPref(enabled: Boolean) {
        context.atelierPrefsDataStore.edit { it[hapticsEnabledKey] = enabled }
    }

    suspend fun setConfirmBeforeDeletePref(enabled: Boolean) {
        context.atelierPrefsDataStore.edit { it[confirmDeleteKey] = enabled }
    }

    suspend fun setDefaultWeatherCityPref(city: String) {
        context.atelierPrefsDataStore.edit { it[defaultWeatherCityKey] = city.trim() }
    }

    // =========================================================================
    // REAL-TIME FIRESTORE OBSERVERS (ServerTimestampBehavior.ESTIMATE)
    // =========================================================================
    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { err -> handleFlowError(err, OperationType.GET, path, uid) }
        )
    }

    fun observeGarments(): Flow<List<GarmentItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/garments"
        emitAll(
            db.collection("users").document(uid).collection("garments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(GarmentItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    fun observeLooks(): Flow<List<SavedLook>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/looks"
        emitAll(
            db.collection("users").document(uid).collection("looks")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SavedLook::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    fun observeCalendarEntries(): Flow<List<CalendarEntry>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/calendar"
        emitAll(
            db.collection("users").document(uid).collection("calendar")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CalendarEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.dateKey }
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    fun observeWishlist(): Flow<List<WishlistItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/wishlist"
        emitAll(
            db.collection("users").document(uid).collection("wishlist")
                .snapshots()
                .map { snap ->
                    snap.toObjects(WishlistItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    fun observeTrips(): Flow<List<TripPlan>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/trips"
        emitAll(
            db.collection("users").document(uid).collection("trips")
                .snapshots()
                .map { snap ->
                    snap.toObjects(TripPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    fun observeOotdFeedback(): Flow<List<OotdFeedback>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/ootd_feedback"
        emitAll(
            db.collection("users").document(uid).collection("ootd_feedback")
                .snapshots()
                .map { snap ->
                    snap.toObjects(OotdFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
        )
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, op, path)
        }
        throw error
    }

    // =========================================================================
    // INITIALIZATION (Create Profile Only — Keep Wardrobe Clean & Empty)
    // =========================================================================
    suspend fun ensureStarterArchiveSeeded(): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            path = userDocRef.path
            val profileSnap = userDocRef.get().await()
            if (!profileSnap.exists()) {
                val defaultProfile = UserProfile(
                    userId = uid,
                    displayName = auth.currentUser?.displayName?.take(80) ?: "Criel Member"
                )
                userDocRef.set(profileCreatePayload(uid, defaultProfile)).await()
            } else {
                // Clean up any previously auto-seeded starter items so the wardrobe stays empty
                // while preserving any real pieces the user added themselves.
                val garmentsRef = userDocRef.collection("garments")
                val starterIds = StarterArchive.starterGarments(uid).map { it.first }
                val starterLookIds = StarterArchive.starterLooks(uid).map { it.first }
                val starterWlIds = StarterArchive.starterWishlist(uid).map { it.first }
                val existingGarments = garmentsRef.get().await().documents.map { it.id }.toSet()
                val existingLooks = userDocRef.collection("looks").get().await().documents.map { it.id }.toSet()
                val existingWl = userDocRef.collection("wishlist").get().await().documents.map { it.id }.toSet()
                val toDeleteG = starterIds.filter { it in existingGarments }
                val toDeleteL = starterLookIds.filter { it in existingLooks }
                val toDeleteW = starterWlIds.filter { it in existingWl }
                if (toDeleteG.isNotEmpty() || toDeleteL.isNotEmpty() || toDeleteW.isNotEmpty()) {
                    val batch = db.batch()
                    toDeleteG.forEach { batch.delete(garmentsRef.document(it)) }
                    toDeleteL.forEach { batch.delete(userDocRef.collection("looks").document(it)) }
                    toDeleteW.forEach { batch.delete(userDocRef.collection("wishlist").document(it)) }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // PROFILE / STYLE DNA MUTATIONS
    // =========================================================================
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        var path = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(profileCreatePayload(uid, profile)).await()
            } else {
                val updates = mapOf<String, Any?>(
                    "displayName" to profile.displayName.take(100),
                    "seasonPalette" to profile.seasonPalette.ifBlank { "Deep Autumn" }.take(60),
                    "undertone" to profile.undertone.take(40),
                    "contrastLevel" to profile.contrastLevel.take(40),
                    "hairEyeDepth" to profile.hairEyeDepth.take(80),
                    "paletteSummary" to profile.paletteSummary.take(800),
                    "powerColors" to profile.powerColors.take(12),
                    "neutralColors" to profile.neutralColors.take(12),
                    "avoidColors" to profile.avoidColors.take(12),
                    "archetypes" to profile.archetypes.take(10),
                    "silhouette" to profile.silhouette.ifBlank { "Tailored" }.take(40),
                    "heightCm" to profile.heightCm.coerceIn(100.0, 250.0),
                    "proportions" to profile.proportions.take(80),
                    "monthlyBudget" to profile.monthlyBudget.coerceIn(0.0, 1_000_000.0),
                    "virtualModelImagePath" to profile.virtualModelImagePath?.take(500),
                    "darkMode" to profile.darkMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // GARMENT MUTATIONS
    // =========================================================================
    suspend fun addGarment(garment: GarmentItem): Result<String> {
        var path = "users/unauthenticated/garments"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("garments").document()
            path = docRef.path
            docRef.set(garmentCreatePayload(uid, garment)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGarment(garment: GarmentItem): Result<Unit> {
        var path = "users/unauthenticated/garments/${garment.id}"
        return try {
            val uid = requireUserId()
            require(garment.id.isNotBlank()) { "Garment ID required for update" }
            val docRef = db.collection("users").document(uid).collection("garments").document(garment.id)
            path = docRef.path
            val updates = mapOf<String, Any?>(
                "name" to garment.name.ifBlank { "Garment" }.take(120),
                "brand" to garment.brand.take(80),
                "category" to GarmentCategory.fromLabel(garment.category).label,
                "subCategory" to garment.subCategory.take(60),
                "colorName" to garment.colorName.ifBlank { "Neutral" }.take(60),
                "colorHex" to garment.colorHex.ifBlank { "#141414" }.take(9),
                "fabric" to garment.fabric.take(80),
                "season" to garment.season.ifBlank { "All-Season" }.take(40),
                "formality" to garment.formality.coerceIn(1, 5),
                "price" to garment.price.coerceIn(0.0, 1_000_000.0),
                "wears" to garment.wears.coerceIn(0, 10_000),
                "status" to GarmentStatus.safeValueOf(garment.status).name,
                "careInstructions" to garment.careInstructions.take(300),
                "careNotes" to garment.careNotes.take(500),
                "tags" to garment.tags.take(15),
                "localImagePath" to garment.localImagePath?.take(500),
                "lastWornEpochMs" to garment.lastWornEpochMs.coerceAtLeast(0L),
                "purchasedEpochMs" to garment.purchasedEpochMs.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logGarmentWear(garmentId: String, currentWears: Int): Result<Unit> {
        var path = "users/unauthenticated/garments/$garmentId"
        return try {
            val uid = requireUserId()
            require(garmentId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("garments").document(garmentId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "wears" to (currentWears + 1).coerceAtMost(10_000),
                    "lastWornEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGarmentStatus(garmentId: String, status: GarmentStatus): Result<Unit> {
        var path = "users/unauthenticated/garments/$garmentId"
        return try {
            val uid = requireUserId()
            require(garmentId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("garments").document(garmentId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGarment(garment: GarmentItem): Result<Unit> {
        var path = "users/unauthenticated/garments/${garment.id}"
        return try {
            val uid = requireUserId()
            require(garment.id.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("garments").document(garment.id)
            path = docRef.path
            docRef.delete().await()
            imageStore.deleteFile(garment.localImagePath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // LOOKS & COLLECTIONS MUTATIONS
    // =========================================================================
    suspend fun saveLook(look: SavedLook): Result<String> {
        var path = "users/unauthenticated/looks"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("looks").document()
            path = docRef.path
            docRef.set(lookCreatePayload(uid, look)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLook(lookId: String): Result<Unit> {
        var path = "users/unauthenticated/looks/$lookId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("looks").document(lookId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // CALENDAR MUTATIONS
    // =========================================================================
    suspend fun saveCalendarEntry(entry: CalendarEntry): Result<Unit> {
        val safeDateKey = entry.dateKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "-")
        var path = "users/unauthenticated/calendar/$safeDateKey"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("calendar").document(safeDateKey)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "dateKey" to safeDateKey,
                    "lookId" to entry.lookId?.take(128),
                    "lookTitle" to entry.lookTitle.ifBlank { "Curated Look" }.take(120),
                    "itemIds" to entry.itemIds.take(12),
                    "occasion" to entry.occasion.take(80),
                    "whoYouSaw" to entry.whoYouSaw.take(120),
                    "worn" to entry.worn,
                    "notes" to entry.notes.take(400),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mapOf<String, Any?>(
                    "lookId" to entry.lookId?.take(128),
                    "lookTitle" to entry.lookTitle.ifBlank { "Curated Look" }.take(120),
                    "itemIds" to entry.itemIds.take(12),
                    "occasion" to entry.occasion.take(80),
                    "whoYouSaw" to entry.whoYouSaw.take(120),
                    "worn" to entry.worn,
                    "notes" to entry.notes.take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCalendarEntry(dateKey: String): Result<Unit> {
        var path = "users/unauthenticated/calendar/$dateKey"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("calendar").document(dateKey)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // WISHLIST, TRIPS & OOTD FEEDBACK MUTATIONS
    // =========================================================================
    suspend fun addWishlistItem(item: WishlistItem): Result<String> {
        var path = "users/unauthenticated/wishlist"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("wishlist").document()
            path = docRef.path
            docRef.set(wishlistCreatePayload(uid, item)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteWishlistItem(wishlistId: String): Result<Unit> {
        var path = "users/unauthenticated/wishlist/$wishlistId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("wishlist").document(wishlistId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTripPlan(trip: TripPlan): Result<String> {
        var path = "users/unauthenticated/trips"
        return try {
            val uid = requireUserId()
            val col = db.collection("users").document(uid).collection("trips")
            if (trip.id.isNotBlank()) {
                val docRef = col.document(trip.id)
                path = docRef.path
                val updates = mapOf<String, Any?>(
                    "destination" to trip.destination.ifBlank { "Trip" }.take(120),
                    "days" to trip.days.coerceIn(1, 30),
                    "climate" to trip.climate.ifBlank { "Temperate" }.take(60),
                    "activities" to trip.activities.take(200),
                    "bagSize" to trip.bagSize.ifBlank { "Carry-on" }.take(40),
                    "packingItemIds" to trip.packingItemIds.take(30),
                    "checkedItemIds" to trip.checkedItemIds.take(30),
                    "itineraryLines" to trip.itineraryLines.take(30),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
                Result.success(trip.id)
            } else {
                val docRef = col.document()
                path = docRef.path
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "destination" to trip.destination.ifBlank { "Trip" }.take(120),
                    "days" to trip.days.coerceIn(1, 30),
                    "climate" to trip.climate.ifBlank { "Temperate" }.take(60),
                    "activities" to trip.activities.take(200),
                    "bagSize" to trip.bagSize.ifBlank { "Carry-on" }.take(40),
                    "packingItemIds" to trip.packingItemIds.take(30),
                    "checkedItemIds" to trip.checkedItemIds.take(30),
                    "itineraryLines" to trip.itineraryLines.take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                Result.success(docRef.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun logOotdFeedback(title: String, action: String, itemIds: List<String>): Result<Unit> {
        var path = "users/unauthenticated/ootd_feedback"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("ootd_feedback").document()
            path = docRef.path
            val safeAction = if (action in listOf("LOVE", "SKIP", "WEAR")) action else "LOVE"
            val payload = mapOf(
                "userId" to uid,
                "title" to title.take(120),
                "action" to safeAction,
                "itemIds" to itemIds.take(12),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 1-CLICK FULL CLOSET BACKUP EXPORT / IMPORT (.json)
    // =========================================================================
    suspend fun exportFullBackupToUri(
        uri: Uri,
        profile: UserProfile,
        garments: List<GarmentItem>,
        looks: List<SavedLook>,
        calendar: List<CalendarEntry>,
        wishlist: List<WishlistItem>,
        trips: List<TripPlan>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("profile", JSONObject().apply {
                    put("displayName", profile.displayName)
                    put("seasonPalette", profile.seasonPalette)
                    put("undertone", profile.undertone)
                    put("contrastLevel", profile.contrastLevel)
                    put("hairEyeDepth", profile.hairEyeDepth)
                    put("paletteSummary", profile.paletteSummary)
                    put("powerColors", JSONArray(profile.powerColors))
                    put("neutralColors", JSONArray(profile.neutralColors))
                    put("avoidColors", JSONArray(profile.avoidColors))
                    put("archetypes", JSONArray(profile.archetypes))
                    put("silhouette", profile.silhouette)
                    put("heightCm", profile.heightCm)
                    put("proportions", profile.proportions)
                    put("monthlyBudget", profile.monthlyBudget)
                    put("darkMode", profile.darkMode)
                    imageStore.encodeImageToBase64(profile.virtualModelImagePath)?.let { put("virtualModelBase64", it) }
                })
                val garmentsArr = JSONArray()
                garments.forEach { g ->
                    garmentsArr.put(JSONObject().apply {
                        put("name", g.name)
                        put("brand", g.brand)
                        put("category", g.category)
                        put("subCategory", g.subCategory)
                        put("colorName", g.colorName)
                        put("colorHex", g.colorHex)
                        put("fabric", g.fabric)
                        put("season", g.season)
                        put("formality", g.formality)
                        put("price", g.price)
                        put("wears", g.wears)
                        put("status", g.status)
                        put("careInstructions", g.careInstructions)
                        put("careNotes", g.careNotes)
                        put("tags", JSONArray(g.tags))
                        put("lastWornEpochMs", g.lastWornEpochMs)
                        put("purchasedEpochMs", g.purchasedEpochMs)
                        imageStore.encodeImageToBase64(g.localImagePath)?.let { put("imageBase64", it) }
                    })
                }
                put("garments", garmentsArr)

                val looksArr = JSONArray()
                looks.forEach { l ->
                    looksArr.put(JSONObject().apply {
                        put("title", l.title)
                        put("occasion", l.occasion)
                        put("collectionName", l.collectionName)
                        put("harmonyScore", l.harmonyScore)
                        put("itemIds", JSONArray(l.itemIds))
                        put("notes", l.notes)
                    })
                }
                put("looks", looksArr)

                val calArr = JSONArray()
                calendar.forEach { c ->
                    calArr.put(JSONObject().apply {
                        put("dateKey", c.dateKey)
                        put("lookTitle", c.lookTitle)
                        put("itemIds", JSONArray(c.itemIds))
                        put("occasion", c.occasion)
                        put("whoYouSaw", c.whoYouSaw)
                        put("worn", c.worn)
                        put("notes", c.notes)
                    })
                }
                put("calendar", calArr)

                val wlArr = JSONArray()
                wishlist.forEach { w ->
                    wlArr.put(JSONObject().apply {
                        put("name", w.name)
                        put("brand", w.brand)
                        put("category", w.category)
                        put("colorName", w.colorName)
                        put("colorHex", w.colorHex)
                        put("price", w.price)
                        put("reason", w.reason)
                        put("unlocks", w.unlocks)
                        put("verdict", w.verdict)
                    })
                }
                put("wishlist", wlArr)

                val tripsArr = JSONArray()
                trips.forEach { t ->
                    tripsArr.put(JSONObject().apply {
                        put("destination", t.destination)
                        put("days", t.days)
                        put("climate", t.climate)
                        put("activities", t.activities)
                        put("bagSize", t.bagSize)
                        put("packingItemIds", JSONArray(t.packingItemIds))
                        put("checkedItemIds", JSONArray(t.checkedItemIds))
                        put("itineraryLines", JSONArray(t.itineraryLines))
                    })
                }
                put("trips", tripsArr)
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open backup destination"))

            Result.success(garments.size + looks.size + wishlist.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFullBackupFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read backup file"))
            val root = JSONObject(raw)
            var importedCount = 0

            root.optJSONObject("profile")?.let { p ->
                val vmPath = p.optString("virtualModelBase64").takeIf { it.isNotBlank() }?.let {
                    imageStore.decodeBase64ToFile(it, "vmodel")
                }
                val profile = UserProfile(
                    userId = uid,
                    displayName = p.optString("displayName", "Criel Member"),
                    seasonPalette = p.optString("seasonPalette", "Deep Autumn"),
                    undertone = p.optString("undertone", "Warm Neutral"),
                    contrastLevel = p.optString("contrastLevel", "Medium-High"),
                    hairEyeDepth = p.optString("hairEyeDepth", ""),
                    paletteSummary = p.optString("paletteSummary", ""),
                    powerColors = p.optStringArray("powerColors"),
                    neutralColors = p.optStringArray("neutralColors"),
                    avoidColors = p.optStringArray("avoidColors"),
                    archetypes = p.optStringArray("archetypes"),
                    silhouette = p.optString("silhouette", "Tailored"),
                    heightCm = p.optDouble("heightCm", 172.0),
                    proportions = p.optString("proportions", ""),
                    monthlyBudget = p.optDouble("monthlyBudget", 600.0),
                    virtualModelImagePath = vmPath,
                    darkMode = p.optBoolean("darkMode", false)
                )
                updateUserProfile(profile)
            }

            val garmentsArr = root.optJSONArray("garments") ?: JSONArray()
            for (i in 0 until garmentsArr.length()) {
                val g = garmentsArr.getJSONObject(i)
                val imgPath = g.optString("imageBase64").takeIf { it.isNotBlank() }?.let {
                    imageStore.decodeBase64ToFile(it, "garment")
                }
                val item = GarmentItem(
                    userId = uid,
                    name = g.optString("name", "Imported Piece"),
                    brand = g.optString("brand", "Archive"),
                    category = GarmentCategory.fromLabel(g.optString("category")).label,
                    subCategory = g.optString("subCategory", ""),
                    colorName = g.optString("colorName", "Neutral"),
                    colorHex = g.optString("colorHex", "#141414"),
                    fabric = g.optString("fabric", "Cotton"),
                    season = g.optString("season", "All-Season"),
                    formality = g.optInt("formality", 3),
                    price = g.optDouble("price", 0.0),
                    wears = g.optInt("wears", 0),
                    status = GarmentStatus.safeValueOf(g.optString("status")).name,
                    careInstructions = g.optString("careInstructions", ""),
                    careNotes = g.optString("careNotes", ""),
                    tags = g.optStringArray("tags"),
                    localImagePath = imgPath,
                    lastWornEpochMs = g.optLong("lastWornEpochMs", 0L),
                    purchasedEpochMs = g.optLong("purchasedEpochMs", 0L)
                )
                if (addGarment(item).isSuccess) importedCount++
            }

            val wlArr = root.optJSONArray("wishlist") ?: JSONArray()
            for (i in 0 until wlArr.length()) {
                val w = wlArr.getJSONObject(i)
                val item = WishlistItem(
                    userId = uid,
                    name = w.optString("name", "Wishlist Piece"),
                    brand = w.optString("brand", "Wishlist"),
                    category = GarmentCategory.fromLabel(w.optString("category")).label,
                    colorName = w.optString("colorName", "Neutral"),
                    colorHex = w.optString("colorHex", "#4A3728"),
                    price = w.optDouble("price", 0.0),
                    reason = w.optString("reason", ""),
                    unlocks = w.optString("unlocks", ""),
                    verdict = w.optString("verdict", "")
                )
                if (addWishlistItem(item).isSuccess) importedCount++
            }

            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // PAYLOAD BUILDERS (Strictly aligned with firestore.rules — NO "id" field!)
    // =========================================================================
    private fun profileCreatePayload(uid: String, p: UserProfile): Map<String, Any> = mapOf<String, Any?>(
        "userId" to uid,
        "displayName" to p.displayName.take(100),
        "seasonPalette" to p.seasonPalette.ifBlank { "Deep Autumn" }.take(60),
        "undertone" to p.undertone.take(40),
        "contrastLevel" to p.contrastLevel.take(40),
        "hairEyeDepth" to p.hairEyeDepth.take(80),
        "paletteSummary" to p.paletteSummary.take(800),
        "powerColors" to p.powerColors.take(12),
        "neutralColors" to p.neutralColors.take(12),
        "avoidColors" to p.avoidColors.take(12),
        "archetypes" to p.archetypes.take(10),
        "silhouette" to p.silhouette.ifBlank { "Tailored" }.take(40),
        "heightCm" to p.heightCm.coerceIn(100.0, 250.0),
        "proportions" to p.proportions.take(80),
        "monthlyBudget" to p.monthlyBudget.coerceIn(0.0, 1_000_000.0),
        "virtualModelImagePath" to p.virtualModelImagePath?.take(500),
        "darkMode" to p.darkMode,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    private fun garmentCreatePayload(uid: String, g: GarmentItem): Map<String, Any> = mapOf<String, Any?>(
        "userId" to uid,
        "name" to g.name.ifBlank { "Garment" }.take(120),
        "brand" to g.brand.take(80),
        "category" to GarmentCategory.fromLabel(g.category).label,
        "subCategory" to g.subCategory.take(60),
        "colorName" to g.colorName.ifBlank { "Neutral" }.take(60),
        "colorHex" to g.colorHex.ifBlank { "#141414" }.take(9),
        "fabric" to g.fabric.take(80),
        "season" to g.season.ifBlank { "All-Season" }.take(40),
        "formality" to g.formality.coerceIn(1, 5),
        "price" to g.price.coerceIn(0.0, 1_000_000.0),
        "wears" to g.wears.coerceIn(0, 10_000),
        "status" to GarmentStatus.safeValueOf(g.status).name,
        "careInstructions" to g.careInstructions.take(300),
        "careNotes" to g.careNotes.take(500),
        "tags" to g.tags.take(15),
        "localImagePath" to g.localImagePath?.take(500),
        "lastWornEpochMs" to g.lastWornEpochMs.coerceAtLeast(0L),
        "purchasedEpochMs" to g.purchasedEpochMs.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    private fun lookCreatePayload(uid: String, l: SavedLook): Map<String, Any> = mapOf<String, Any?>(
        "userId" to uid,
        "title" to l.title.ifBlank { "Editorial Look" }.take(120),
        "occasion" to l.occasion.ifBlank { "Editorial" }.take(80),
        "collectionName" to l.collectionName.ifBlank { "Autumn Work" }.take(60),
        "harmonyScore" to l.harmonyScore.coerceIn(0, 100),
        "itemIds" to l.itemIds.take(12),
        "notes" to l.notes.take(500),
        "localTryOnImagePath" to l.localTryOnImagePath?.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    private fun wishlistCreatePayload(uid: String, w: WishlistItem): Map<String, Any> = mapOf<String, Any?>(
        "userId" to uid,
        "name" to w.name.ifBlank { "Wishlist Piece" }.take(120),
        "brand" to w.brand.take(80),
        "category" to GarmentCategory.fromLabel(w.category).label,
        "colorName" to w.colorName.ifBlank { "Neutral" }.take(60),
        "colorHex" to w.colorHex.ifBlank { "#4A3728" }.take(9),
        "price" to w.price.coerceIn(0.0, 1_000_000.0),
        "reason" to w.reason.take(400),
        "unlocks" to w.unlocks.take(60),
        "verdict" to w.verdict.take(20),
        "localImagePath" to w.localImagePath?.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>

    private fun JSONObject.optStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
    }
}
