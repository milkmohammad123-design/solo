package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AtelierViewModel
import java.io.File
import java.util.Locale

private val QUICK_PROMPTS = listOf(
    "Make a list of all basic things I need",
    "I need to get versatile everyday shoes",
    "Build a sharp everyday fit"
)

@Composable
fun StylistScreen(
    viewModel: AtelierViewModel,
    garments: List<GarmentItem>,
    voiceReadoutEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val stylistState by viewModel.stylist.collectAsState()
    val aiTone by viewModel.aiCritiqueTone.collectAsState()
    val styleGender by viewModel.styleGender.collectAsState()
    val lastSpokenReply by viewModel.lastSpokenReply.collectAsState()
    val context = LocalContext.current

    // Wire Android TextToSpeech cleanly for hands-free voice readout
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsReady = true
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    LaunchedEffect(lastSpokenReply, voiceReadoutEnabled, ttsReady) {
        val textToSpeak = lastSpokenReply
        if (!textToSpeak.isNullOrBlank() && voiceReadoutEnabled && ttsReady) {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "criel_reply")
            viewModel.clearSpokenReply()
        }
    }

    val garmentMap = remember(garments) { garments.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorialPillTabs(
            tabs = listOf("Chat", "Rate Fit", "Daily Looks"),
            selectedIndex = stylistState.subTab,
            onSelect = { viewModel.setStylistSubTab(it) }
        )

        when (stylistState.subTab) {
            0 -> StylistChatTab(
                viewModel = viewModel,
                state = stylistState,
                aiTone = aiTone,
                styleGender = styleGender,
                garmentMap = garmentMap,
                voiceReadoutEnabled = voiceReadoutEnabled,
                modifier = Modifier.weight(1f)
            )
            1 -> RateMyFitTab(
                viewModel = viewModel,
                state = stylistState,
                aiTone = aiTone,
                garmentMap = garmentMap,
                modifier = Modifier.weight(1f)
            )
            else -> DailyOotdTab(
                viewModel = viewModel,
                state = stylistState,
                garmentMap = garmentMap,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StylistChatTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.StylistSuiteState,
    aiTone: AiCritiqueTone,
    styleGender: StyleGender,
    garmentMap: Map<String, GarmentItem>,
    voiceReadoutEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var savedShoppingIds by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val photoToSend = pendingPhotoUri
                pendingPhotoUri = null
                viewModel.sendStylistMessage(spoken, photoToSend)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingPhotoUri = uri
        }
    }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Minimal, uncluttered top utility row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                item {
                    AssistChip(
                        onClick = { viewModel.requestBasicsChecklist() },
                        enabled = !state.chatLoading,
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = Champagne,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Basics List",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("stylist_basics_list_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = styleGender != StyleGender.UNISEX,
                        onClick = { showOptionsSheet = true },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = styleGender.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("stylist_gender_chip")
                    )
                }

                item {
                    FilterChip(
                        selected = state.selectedDressCode != null || state.includeUnavailableOverride,
                        onClick = { showOptionsSheet = true },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = state.selectedDressCode?.label ?: "Dress Code",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("stylist_options_chip")
                    )
                }

                item {
                    AssistChip(
                        onClick = { showOptionsSheet = true },
                        label = {
                            Text(
                                text = aiTone.shortLabel,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = Champagne,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier.testTag("stylist_tone_chip")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleVoiceReadout() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toggle_voice_readout_button")
                ) {
                    Icon(
                        imageVector = if (voiceReadoutEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = if (voiceReadoutEnabled) "Disable voice readout" else "Enable voice readout",
                        tint = if (voiceReadoutEnabled) Champagne else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.chatMessages.size > 1) {
                    IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("clear_stylist_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Chat transcript — generous breathing room
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(state.chatMessages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    msg = msg,
                    garmentMap = garmentMap,
                    savedShoppingIds = savedShoppingIds,
                    onOpenInStudio = { card -> viewModel.openOutfitInStudio(card.title, card.itemIds) },
                    onLogToday = { card ->
                        viewModel.logOutfitToDate(
                            dateKey = viewModel.todayDateKey(),
                            lookTitle = card.title,
                            itemIds = card.itemIds,
                            lookId = null,
                            occasion = state.selectedDressCode?.label ?: "Styled Today",
                            whoYouSaw = "",
                            markWorn = true
                        )
                    },
                    onSaveShoppingItem = { item, testInStudio ->
                        savedShoppingIds = savedShoppingIds + item.id
                        viewModel.saveShoppingItemToWishlist(item, testInStudio = testInStudio)
                    },
                    onSaveAllShoppingItems = { list ->
                        savedShoppingIds = savedShoppingIds + list.map { it.id }
                        viewModel.saveAllShoppingItemsToWishlist(list)
                    },
                    onStoreLinkError = { err -> viewModel.showToast(err) }
                )
            }

            if (state.chatLoading) {
                item {
                    EditorialLoadingCard(
                        title = "Criel Stylist Thinking",
                        subtitle = "Auditing your closet, building your list & finding reference photos..."
                    )
                }
            }
        }

        // Starter quick prompts only while chat is fresh so the screen stays uncluttered
        if (state.chatMessages.size <= 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QUICK_PROMPTS) { prompt ->
                    AssistChip(
                        onClick = { viewModel.sendStylistMessage(prompt) },
                        label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        // Pending photo attachment preview strip
        if (pendingPhotoUri != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = pendingPhotoUri,
                        contentDescription = "Attached photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Photo attached",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Add a question or tap Send for an honest verdict",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { pendingPhotoUri = null },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("stylist_remove_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove attached photo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Clean input bar: Photo Picker + Voice Mic + TextField + Send
        val canSend = (inputText.isNotBlank() || pendingPhotoUri != null) && !state.chatLoading
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("stylist_attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = "Attach photo of item or outfit",
                    tint = if (pendingPhotoUri != null) MaterialTheme.colorScheme.primary else Champagne
                )
            }

            FilledTonalIconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Criel Stylist...")
                        }
                        speechLauncher.launch(intent)
                    } catch (_: Exception) {
                        viewModel.showToast("Voice recognition is unavailable on this device")
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("stylist_voice_mic_button")
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice styling prompt", tint = Champagne)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = if (pendingPhotoUri != null) "Ask about this photo..." else "Ask what to get, send a photo...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stylist_chat_input")
            )

            IconButton(
                onClick = {
                    if (canSend) {
                        val textToSend = inputText
                        val photoToSend = pendingPhotoUri
                        inputText = ""
                        pendingPhotoUri = null
                        viewModel.sendStylistMessage(textToSend, photoToSend)
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("stylist_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send prompt",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EditorialSectionHeader(
                    eyebrow = "STYLIST OPTIONS",
                    title = "Gender, Dress Code & Honesty",
                    subtitle = "Tailor every outfit, basics list, and store link to your style"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    .testTag("stylist_gender_${gender.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = styleGender.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DRESS CODE MODE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DressCodeMode.entries) { mode ->
                            val selected = state.selectedDressCode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectDressCode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("dresscode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    if (state.selectedDressCode != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Champagne.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Champagne.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${state.selectedDressCode.label}: ${state.selectedDressCode.rules}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI HONESTY LEVEL", style = MaterialTheme.typography.labelSmall, color = Champagne)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiCritiqueTone.entries.forEach { tone ->
                            FilterChip(
                                selected = aiTone == tone,
                                onClick = { viewModel.setAiCritiqueTone(tone) },
                                label = { Text(tone.shortLabel, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = aiTone.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Unavailable Pieces", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (state.includeUnavailableOverride) "Using laundry, tailor & off-season items too" else "Only suggesting Available items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.includeUnavailableOverride,
                            onCheckedChange = { viewModel.toggleUnavailableOverride() },
                            modifier = Modifier.testTag("include_unavailable_switch")
                        )
                    }
                }

                Button(
                    onClick = { showOptionsSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done")
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: StylistChatMessage,
    garmentMap: Map<String, GarmentItem>,
    savedShoppingIds: Set<String>,
    onOpenInStudio: (StylistOutfitCard) -> Unit,
    onLogToday: (StylistOutfitCard) -> Unit,
    onSaveShoppingItem: (StylistShoppingItem, Boolean) -> Unit,
    onSaveAllShoppingItems: (List<StylistShoppingItem>) -> Unit,
    onStoreLinkError: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                bottomEnd = if (msg.fromUser) 4.dp else 18.dp
            ),
            color = if (msg.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (msg.fromUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!msg.attachedImagePath.isNullOrBlank() && File(msg.attachedImagePath).exists()) {
                    AsyncImage(
                        model = File(msg.attachedImagePath),
                        contentDescription = "Photo shared in chat",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                if (msg.shoppingList.isNotEmpty()) {
                    ShoppingRecommendationCard(
                        title = msg.shoppingListTitle,
                        items = msg.shoppingList,
                        savedShoppingIds = savedShoppingIds,
                        onSaveShoppingItem = onSaveShoppingItem,
                        onSaveAllShoppingItems = onSaveAllShoppingItems,
                        onStoreLinkError = onStoreLinkError
                    )
                }

                val outfit = msg.outfit
                if (outfit != null && outfit.itemIds.isNotEmpty()) {
                    val resolved = outfit.itemIds.mapNotNull { garmentMap[it] }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Champagne.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = outfit.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Champagne.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = "Score ${outfit.harmonyScore}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChampagneDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (resolved.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(resolved, key = { it.id }) { g ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(68.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(58.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(4.dp)
                                            ) {
                                                GarmentVisual(item = g, modifier = Modifier.fillMaxSize())
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = g.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (outfit.rationale.isNotBlank()) {
                                Text(
                                    text = outfit.rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (outfit.cohesionVerdict.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("HONEST PAIRING CHECK", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                        Text(outfit.cohesionVerdict, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            if (outfit.whatToGetNext.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("WHAT TO GET TO LEVEL IT UP", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                    outfit.whatToGetNext.forEach { suggestion ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Rounded.ShoppingBag,
                                                contentDescription = null,
                                                tint = ChampagneDark,
                                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                            )
                                            Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            if (outfit.dressCodeNote.isNotBlank()) {
                                Text(
                                    text = "Dress Code: ${outfit.dressCodeNote}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Champagne
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenInStudio(outfit) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .testTag("chat_open_studio_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open in Studio", maxLines = 1)
                                }
                                Button(
                                    onClick = { onLogToday(outfit) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .testTag("chat_log_today_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Log to Today", maxLines = 1)
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
private fun RateMyFitTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.StylistSuiteState,
    aiTone: AiCritiqueTone,
    garmentMap: Map<String, GarmentItem>,
    modifier: Modifier = Modifier
) {
    var occasion by remember { mutableStateOf("Smart Casual") }
    val mirrorPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.runRateMyFit(uri, occasion)
        }
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rate My Fit", style = MaterialTheme.typography.titleLarge)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Champagne.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = aiTone.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        "Upload a mirror selfie for a no-filter verdict on how well your pieces actually go together, 3 quick fixes, and exact pieces to buy (in ₹) to upgrade the fit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = occasion,
                        onValueChange = { occasion = it },
                        label = { Text("Occasion / Destination") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            mirrorPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !state.rateMyFitLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("rate_my_fit_upload_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Mirror Selfie & Critique")
                    }
                }
            }
        }

        if (state.rateMyFitLoading) {
            item {
                EditorialLoadingCard(
                    title = "Critiquing Your Fit",
                    subtitle = "Checking pairing cohesion, proportions, color clash, and upgrade pieces..."
                )
            }
        }

        if (!state.rateMyFitError.isNullOrBlank()) {
            item {
                EditorialErrorCard(message = state.rateMyFitError)
            }
        }

        val result = state.rateMyFitResult
        if (result != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!state.rateMyFitPhotoPath.isNullOrBlank() && File(state.rateMyFitPhotoPath).exists()) {
                            AsyncImage(
                                model = File(state.rateMyFitPhotoPath),
                                contentDescription = "Mirror check photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("HONEST FIT SCORE", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                Text(
                                    text = "${result.overallScore} / 100",
                                    style = MaterialTheme.typography.displaySmall
                                )
                            }
                            ScoreRing(score = result.overallScore, label = "Overall", ringSize = 66.dp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ScoreRing(score = result.proportionsScore, label = "Proportions", ringSize = 58.dp)
                            ScoreRing(score = result.colorHarmonyScore, label = "Harmony", ringSize = 58.dp)
                            ScoreRing(score = result.occasionMatchScore, label = "Occasion", ringSize = 58.dp)
                            ScoreRing(score = result.styleDnaScore, label = "Style DNA", ringSize = 58.dp)
                        }

                        Text(result.editorialVerdict, style = MaterialTheme.typography.bodyMedium)

                        if (result.cohesionBreakdown.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("HOW WELL THESE PIECES PAIR", style = MaterialTheme.typography.labelSmall, color = Champagne)
                                    Text(result.cohesionBreakdown, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Text("3 QUICK 10-SECOND FIXES", style = MaterialTheme.typography.labelSmall, color = Champagne)
                        result.quickTweaks.forEachIndexed { idx, tweak ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Champagne
                                    )
                                    Text(tweak, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (result.whatToGetToUpgrade.isNotEmpty()) {
                            Text("WHAT TO GET TO MAKE THIS FIT BETTER", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            result.whatToGetToUpgrade.forEach { upgradeItem ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Champagne.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.ShoppingBag,
                                            contentDescription = null,
                                            tint = ChampagneDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(upgradeItem, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        val swaps = result.suggestedSwapItemIds.mapNotNull { garmentMap[it] }
                        if (swaps.isNotEmpty()) {
                            Text("OWNED CLOSET SWAPS", style = MaterialTheme.typography.labelSmall, color = Champagne)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(swaps, key = { it.id }) { swapItem ->
                                    AssistChip(
                                        onClick = { viewModel.sendGarmentToStudioAndLock(swapItem) },
                                        label = { Text("Swap: ${swapItem.name}") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
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

@Composable
private fun DailyOotdTab(
    viewModel: AtelierViewModel,
    state: com.example.viewmodel.StylistSuiteState,
    garmentMap: Map<String, GarmentItem>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        if (state.ootdLoading) {
            item {
                EditorialLoadingCard(
                    title = "Curating 5 Daily Looks",
                    subtitle = "Weighting your loved pieces, Style DNA, and honest pairing scores..."
                )
            }
            return@LazyColumn
        }

        if (!state.ootdError.isNullOrBlank()) {
            item {
                EditorialErrorCard(message = state.ootdError)
            }
        }

        val currentCard = state.ootdDeck.getOrNull(state.ootdCurrentIndex)
        if (currentCard == null) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Style, contentDescription = null, tint = Champagne, modifier = Modifier.size(38.dp))
                        Text(
                            text = if (state.ootdDeck.isEmpty()) "Daily Look Deck" else "All 5 Looks Reviewed",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Generate 5 fresh daily outfits from your closet with honest pairing notes and upgrade tips.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.generateDailyOotdDeck() },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("generate_ootd_deck_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate 5 Fresh Looks")
                        }
                    }
                }
            }
        } else {
            item {
                val pieces = currentCard.itemIds.mapNotNull { garmentMap[it] }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Champagne.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LOOK ${state.ootdCurrentIndex + 1} OF ${state.ootdDeck.size} · ${currentCard.vibe.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Champagne
                                )
                                Text(currentCard.title, style = MaterialTheme.typography.headlineSmall)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Champagne.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${currentCard.harmonyScore}%",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneDark,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Garment mosaic
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pieces.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { piece ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                GarmentVisual(
                                                    item = piece,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(92.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = piece.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = piece.brand,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Text(
                            text = currentCard.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (currentCard.upgradeBuyTip.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Champagne.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Champagne.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.ShoppingBag,
                                        contentDescription = null,
                                        tint = ChampagneDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Upgrade to buy: ${currentCard.upgradeBuyTip}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.handleOotdSwipe("SKIP") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ootd_skip_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Skip")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.handleOotdSwipe("LOVE") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ootd_love_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = EditorialRose)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Love")
                            }
                            Button(
                                onClick = { viewModel.handleOotdSwipe("WEAR") },
                                modifier = Modifier
                                    .weight(1.15f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ootd_wear_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wear Today")
                            }
                        }
                    }
                }
            }
        }
    }
}
