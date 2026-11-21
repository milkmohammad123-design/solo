package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.firebase.signOut
import com.example.ui.components.EditorialErrorCard
import com.example.ui.screens.*
import com.example.ui.theme.AtelierTheme
import com.example.ui.theme.Champagne
import com.example.viewmodel.AtelierViewModel
import com.example.viewmodel.PrimaryTab
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // CRITICAL: Configure App Check BEFORE setContent, ViewModels, or any Firebase SDK usage.
        configureAppCheck(this, intent)

        setContent {
            AtelierRootApp()
        }
    }
}

@Composable
private fun AtelierRootApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val uid = currentUser?.uid
    if (uid == null) {
        AtelierTheme(darkTheme = false) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        }
    } else {
        key(uid) {
            val context = LocalContext.current
            val vm: AtelierViewModel = viewModel(
                key = uid,
                factory = AtelierViewModel.provideFactory(context)
            )
            val darkMode by vm.darkMode.collectAsState()
            AtelierTheme(darkTheme = darkMode) {
                SignedInAtelierShell(
                    viewModel = vm,
                    userEmail = currentUser?.email,
                    darkMode = darkMode
                )
            }
        }
    }
}

private fun tabIcon(tab: PrimaryTab): ImageVector = when (tab) {
    PrimaryTab.WARDROBE -> Icons.Rounded.Checkroom
    PrimaryTab.STYLIST -> Icons.Rounded.AutoAwesome
    PrimaryTab.STUDIO -> Icons.Rounded.DesignServices
    PrimaryTab.CURATE -> Icons.Rounded.ShoppingBag
    PrimaryTab.CALENDAR -> Icons.Rounded.CalendarMonth
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInAtelierShell(
    viewModel: AtelierViewModel,
    userEmail: String?,
    darkMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val activeTab by viewModel.activeTab.collectAsState()
    val garments by viewModel.garments.collectAsState()
    val savedLooks by viewModel.savedLooks.collectAsState()
    val calendarEntries by viewModel.calendarEntries.collectAsState()
    val wishlist by viewModel.wishlist.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val voiceReadout by viewModel.voiceReadoutEnabled.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val firestoreError by viewModel.firestoreError.collectAsState()

    var showStyleDnaSheet by remember { mutableStateOf(false) }
    var showWeatherSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    // Back navigation returns to Wardrobe from secondary tabs
    BackHandler(enabled = activeTab != PrimaryTab.WARDROBE) {
        viewModel.selectTab(PrimaryTab.WARDROBE)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.criel_logo_refined_1795204460485),
                                contentDescription = "Criel logo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = "Criel",
                                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showStyleDnaSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_style_dna_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ColorLens,
                                contentDescription = "Style DNA & Color Analysis",
                                tint = Champagne
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("topbar_theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (darkMode) "Switch to Powder Blue Light Mode" else "Switch to Deep Obsidian Dark Mode"
                            )
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        PrimaryTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab),
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Champagne,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = Champagne.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Champagne,
                                    indicatorColor = Champagne.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (!firestoreError.isNullOrBlank()) {
                        EditorialErrorCard(
                            message = firestoreError!!,
                            onDismiss = { viewModel.dismissError() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (activeTab) {
                            PrimaryTab.WARDROBE -> WardrobeScreen(
                                viewModel = viewModel,
                                garments = garments,
                                savedLooks = savedLooks,
                                weather = weather,
                                onOpenWeatherControls = { showWeatherSheet = true }
                            )
                            PrimaryTab.STYLIST -> StylistScreen(
                                viewModel = viewModel,
                                garments = garments,
                                voiceReadoutEnabled = voiceReadout
                            )
                            PrimaryTab.STUDIO -> StudioScreen(
                                viewModel = viewModel,
                                garments = garments,
                                savedLooks = savedLooks,
                                wishlist = wishlist,
                                userProfile = userProfile,
                                onOpenStyleDna = { showStyleDnaSheet = true }
                            )
                            PrimaryTab.CURATE -> CurateScreen(
                                viewModel = viewModel,
                                garments = garments,
                                wishlist = wishlist,
                                trips = trips
                            )
                            PrimaryTab.CALENDAR -> CalendarScreen(
                                viewModel = viewModel,
                                garments = garments,
                                savedLooks = savedLooks,
                                calendarEntries = calendarEntries,
                                userProfile = userProfile
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStyleDnaSheet) {
        StyleDnaSheet(
            viewModel = viewModel,
            profile = userProfile,
            onDismiss = { showStyleDnaSheet = false }
        )
    }

    if (showWeatherSheet) {
        WeatherControlSheet(
            viewModel = viewModel,
            weather = weather,
            onDismiss = { showWeatherSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsBackupSheet(
            viewModel = viewModel,
            darkMode = darkMode,
            userProfile = userProfile,
            userEmail = userEmail,
            onOpenStyleDna = { showStyleDnaSheet = true },
            onOpenWeatherControls = { showWeatherSheet = true },
            onSignOut = {
                signOut(credentialManager = credentialManager, scope = scope)
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
