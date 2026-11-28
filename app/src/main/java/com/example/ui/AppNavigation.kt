package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import com.example.ui.theme.*
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.CredentialManager
import com.example.data.BibleSection
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val currentWorld by viewModel.currentWorld.collectAsStateWithLifecycle()
    val currentWorld1Tab by viewModel.world1Tab.collectAsStateWithLifecycle()
    val showWorld1SettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val showSocialAgentModal by viewModel.showSocialAgentModal.collectAsStateWithLifecycle()
    val showCameraModal by viewModel.showCameraStoriesModal.collectAsStateWithLifecycle()
    val showHistoryModal by viewModel.showOutingHistoryModal.collectAsStateWithLifecycle()

    if (currentWorld == AppWorld.WORLD_1_SOCIAL) {
        World1MainContainer(
            viewModel = viewModel,
            currentTab = currentWorld1Tab,
            onTabSelected = { viewModel.world1Tab.value = it }
        )

        if (showWorld1SettingsModal) {
            World1SettingsModal(
                viewModel = viewModel,
                onDismiss = { viewModel.showSettingsModal.value = false }
            )
        }

        if (showSocialAgentModal) {
            SocialAgentModal(
                viewModel = viewModel,
                onDismiss = { viewModel.showSocialAgentModal.value = false }
            )
        }

        if (showCameraModal) {
            CameraAndStoriesModal(
                viewModel = viewModel,
                onDismiss = { viewModel.showCameraStoriesModal.value = false }
            )
        }

        if (showHistoryModal) {
            OutingHistoryModal(
                viewModel = viewModel,
                onDismiss = { viewModel.showOutingHistoryModal.value = false }
            )
        }
        return
    }

    // Back handler in World 2 returns to World 1 when on root tab
    val sections by viewModel.sectionsState.collectAsState()
    val selectedSection by viewModel.selectedSection.collectAsState()
    val themeModeState by viewModel.themeMode.collectAsState()
    val textSizeState by viewModel.textSizeSp.collectAsState()
    val textPaddingState by viewModel.textPaddingDp.collectAsState()
    val pointsState by viewModel.studyPoints.collectAsState()
    val streakState by viewModel.streakDays.collectAsState()
    val loginState by viewModel.isUserLoggedIn.collectAsState()
    val emailState by viewModel.userEmail.collectAsState()

    val activeTab by viewModel.world2ActiveTab.collectAsState()
    val preferredBibleVersion by viewModel.bibleVersion.collectAsState()

    var currentBibleId by remember { mutableStateOf<String?>(null) }
    var currentChapterId by remember { mutableStateOf<String?>(null) }

    BackHandler {
        when {
            selectedSection != null -> viewModel.selectSection(null)
            activeTab == "bible_reader" || activeTab == "bible_index" || activeTab == "favorites" -> {
                viewModel.setWorld2ActiveTab("library")
            }
            else -> viewModel.switchToWorld1Social()
        }
    }

    val isDarkWorld2 = themeModeState == ThemeMode.SOCIAL_OSCURO || themeModeState == ThemeMode.VIGILIA
    val hasOwnTopBar = activeTab == "bible_reader" || activeTab == "bible_index"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        Scaffold(
            topBar = {
                if (!hasOwnTopBar) {
                    val searchQuery by viewModel.searchTerm.collectAsState()
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Portal back to World 1 (Salidas)
                            Surface(
                                onClick = { viewModel.switchToWorld1Social() },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.testTag("switch_to_world1_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver al Mundo 1 de Salidas",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Salidas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.setQuery(it)
                                    if (it.isNotEmpty() && activeTab != "library") {
                                        viewModel.setWorld2ActiveTab("library")
                                    }
                                    if (it.isNotEmpty() && selectedSection != null) {
                                        viewModel.selectSection(null)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                                placeholder = { Text("Buscar estudios y versículos...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("global_search_input"),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                )
                            )

                            if (activeTab == "library") {
                                IconButton(onClick = { viewModel.setWorld2ActiveTab("favorites") }) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Mis Favoritos",
                                        tint = Color.Red,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            } else if (activeTab == "favorites") {
                                IconButton(onClick = { viewModel.setWorld2ActiveTab("library") }) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Volver a Estudio",
                                        tint = Color.Red,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.setWorld2ActiveTab("chat") }) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Adora IA",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    TabBottomBar(
                        activeTab = activeTab,
                        onTabSelected = { viewModel.setWorld2ActiveTab(it) }
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            val bgColor = MaterialTheme.colorScheme.background
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    TabNavigationRail(
                        activeTab = activeTab,
                        onTabSelected = { viewModel.setWorld2ActiveTab(it) }
                    )
                    Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (activeTab) {
                        "calendar" -> CalendarScreen(viewModel = viewModel)
                        "library" -> LibraryScreen(
                            sections = sections,
                            selectedSection = selectedSection,
                            viewModel = viewModel,
                            textSize = textSizeState,
                            textPadding = textPaddingState,
                            onNavigateToBibleReader = { bId, cId ->
                                currentBibleId = bId
                                currentChapterId = cId
                                viewModel.setWorld2ActiveTab("bible_reader")
                            },
                            onNavigateToBibleIndex = {
                                viewModel.setWorld2ActiveTab("bible_index")
                            }
                        )
                        "bible_reader" -> BibleReaderScreen(
                            bibleId = currentBibleId,
                            chapterId = currentChapterId,
                            viewModel = viewModel,
                            onBack = { viewModel.setWorld2ActiveTab("library") }
                        )
                        "bible_index" -> BibleIndexScreen(
                            preferredVersion = preferredBibleVersion,
                            onBack = { viewModel.setWorld2ActiveTab("library") },
                            onChapterSelected = { bId, _, cId ->
                                currentBibleId = bId
                                currentChapterId = cId
                                viewModel.setWorld2ActiveTab("bible_reader")
                            }
                        )
                        "favorites" -> FavoritesScreen(viewModel = viewModel)
                        "chat" -> GeminiChatScreen(viewModel = viewModel)
                        "trivia" -> TriviaAchievementsScreen(
                            viewModel = viewModel,
                            points = pointsState,
                            streak = streakState
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            themeMode = themeModeState,
                            textSize = textSizeState,
                            textPadding = textPaddingState,
                            userLoggedIn = loginState,
                            userEmail = emailState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun World1MainContainer(
    viewModel: MainViewModel,
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    if (currentTab != "profile") {
        BackHandler {
            onTabSelected("profile")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        Scaffold(
            bottomBar = {
                if (!isWideScreen) {
                    World1BottomBar(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    World1NavigationRail(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected
                    )
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentTab) {
                        "profile" -> ProfileScreen(viewModel = viewModel)
                        "feed" -> GroupFeedScreen(viewModel = viewModel)
                        "outings" -> World1OutingsScreen(viewModel = viewModel)
                        "map" -> World1MapScreen(viewModel = viewModel)
                        "chat" -> World1ChatScreen(viewModel = viewModel)
                        "games" -> World1GamesScreen(viewModel = viewModel)
                        else -> ProfileScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun World1BottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.75.dp)
        NavigationBar(
            modifier = Modifier
                .testTag("world1_bottom_nav_bar")
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            val items = listOf(
                Triple("profile", "Inicio", Icons.Default.Person),
                Triple("feed", "Feed", Icons.Default.DynamicFeed),
                Triple("outings", "Salidas", Icons.Default.DateRange),
                Triple("map", "Mapa", Icons.Default.Explore),
                Triple("chat", "Chat", Icons.Default.ChatBubble),
                Triple("games", "Juegos", Icons.Default.SportsEsports)
            )
            val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            items.forEach { (tab, label, icon) ->
                val selected = currentTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    colors = navColors,
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(21.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("world1_tab_$tab")
                )
            }
        }
    }
}

@Composable
fun World1NavigationRail(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        val items = listOf(
            Triple("profile", "Inicio", Icons.Default.Person),
            Triple("feed", "Feed", Icons.Default.DynamicFeed),
            Triple("outings", "Salidas", Icons.Default.DateRange),
            Triple("map", "Mapa", Icons.Default.Explore),
            Triple("chat", "Chat", Icons.Default.ChatBubble),
            Triple("games", "Juegos", Icons.Default.SportsEsports)
        )
        items.forEach { (tab, label, icon) ->
            val selected = currentTab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// --------------------------------------------------------------------------
// Navigation Components (Responsive Adaptation)
// --------------------------------------------------------------------------

@Composable
fun TabBottomBar(activeTab: String, onTabSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.75.dp)
        NavigationBar(
            modifier = Modifier
                .testTag("bottom_nav_bar")
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NavigationBarItem(
                selected = activeTab == "calendar",
                onClick = { onTabSelected("calendar") },
                colors = navColors,
                icon = { Icon(Icons.Default.DateRange, contentDescription = "Agenda", modifier = Modifier.size(21.dp)) },
                label = { Text("Agenda", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("tab_calendar")
            )
            NavigationBarItem(
                selected = activeTab == "library",
                onClick = { onTabSelected("library") },
                colors = navColors,
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Estudio", modifier = Modifier.size(21.dp)) },
                label = { Text("Estudio", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("tab_library")
            )
            NavigationBarItem(
                selected = activeTab == "trivia",
                onClick = { onTabSelected("trivia") },
                colors = navColors,
                icon = { Icon(Icons.Default.Star, contentDescription = "Conocimiento", modifier = Modifier.size(21.dp)) },
                label = { Text("Conocimiento", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("tab_trivia")
            )
            NavigationBarItem(
                selected = activeTab == "settings",
                onClick = { onTabSelected("settings") },
                colors = navColors,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes", modifier = Modifier.size(21.dp)) },
                label = { Text("Ajustes", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("tab_settings")
            )
        }
    }
}

@Composable
fun TabNavigationRail(activeTab: String, onTabSelected: (String) -> Unit) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().testTag("side_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onTabSelected("settings") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "User info profile",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    ) {
        NavigationRailItem(
            selected = activeTab == "calendar",
            onClick = { onTabSelected("calendar") },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar icon") },
            label = { Text("Calendario", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.testTag("tab_calendar_rail")
        )
        NavigationRailItem(
            selected = activeTab == "library",
            onClick = { onTabSelected("library") },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Study Library icon") },
            label = { Text("Estudio", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.testTag("tab_library_rail")
        )
        NavigationRailItem(
            selected = activeTab == "trivia",
            onClick = { onTabSelected("trivia") },
            icon = { Icon(Icons.Default.Star, contentDescription = "Trivia exercises icon") },
            label = { Text("Conocimiento", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.testTag("tab_trivia_rail")
        )
        NavigationRailItem(
            selected = activeTab == "settings",
            onClick = { onTabSelected("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Configurations icon") },
            label = { Text("Ajustes", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            modifier = Modifier.testTag("tab_settings_rail")
        )
    }
}

// --------------------------------------------------------------------------
// 1. Library Screens (Continuous Scripture Reader + Detail Modal)
// --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    sections: List<BibleSection>,
    selectedSection: BibleSection?,
    viewModel: MainViewModel,
    textSize: Float,
    textPadding: Float,
    onNavigateToBibleReader: (String?, String?) -> Unit,
    onNavigateToBibleIndex: () -> Unit
) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val searchQuery by viewModel.searchTerm.collectAsState()
    val recentChapters by viewModel.recentChapters.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = selectedSection,
        label = "LibraryReaderTransition",
        transitionSpec = {
            if (targetState != null) {
                // Moving to reader pane (slide in from right)
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut())
            } else {
                // Moving back to library feed (slide in from left)
                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut())
            }
        }
    ) { currentSection ->
        if (currentSection != null) {
            // Continuous dedicated responsive scripture reader
            ReaderPane(
                section = currentSection,
                viewModel = viewModel,
                textSize = textSize,
                textPadding = textPadding
            )
        } else {
            // Study Feed Grid / List
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scrollState)) {
            // Editorial Header (React Bits SplitTextReveal)
            SplitTextReveal(
                text = "Estudio Bíblico",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
            )
            Text(
                text = "«Se fue renovando a imagen de su Creador»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Daily Verse Component (Spotlight Card + Shiny Editorial label)
            val dailyVerses = listOf(
                Pair("«Porque yo sé los pensamientos que tengo acerca de vosotros, dice Jehová, pensamientos de paz, y no de mal, para daros el fin que esperáis.»", "Jeremías 29:11"),
                Pair("«Todo lo puedo en Cristo que me fortalece.»", "Filipenses 4:13"),
                Pair("«Lámpara es a mis pies tu palabra, y lumbrera a mi camino.»", "Salmos 119:105"),
                Pair("«Confiad en Jehová perpetuamente, porque en Jehová el Señor está la fortaleza de los siglos.»", "Isaías 26:4"),
                Pair("«Mas los que esperan a Jehová tendrán nuevas fuerzas; levantarán alas como águilas; correrán, y no se cansarán; caminarán, y no se fatigarán.»", "Isaías 40:31"),
                Pair("«Jehová es mi pastor; nada me faltará.»", "Salmos 23:1"),
                Pair("«No temas, porque yo estoy contigo; no desmayes, porque yo soy tu Dios que te esfuerzo; siempre te ayudaré, siempre te sustentaré con la diestra de mi justicia.»", "Isaías 41:10")
            )
            val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            val dailyVerse = remember(dayOfYear) { dailyVerses[dayOfYear % dailyVerses.size] }

            SpotlightEditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Versículo del Día",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ShinyEditorialText(
                                text = "VERSÍCULO DEL DÍA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp
                                ),
                                baseColor = MaterialTheme.colorScheme.secondary,
                                shineColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val context = LocalContext.current
                        IconButton(
                            onClick = { viewModel.shareVerse(context, dailyVerse.first, dailyVerse.second) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir Versículo del Día",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = dailyVerse.first,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "— ${dailyVerse.second}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                    )
                }
            }

            val allSections by viewModel.allSectionsUnfiltered.collectAsStateWithLifecycle()
            val favoriteVerses by viewModel.favoriteVerses.collectAsStateWithLifecycle()
            val readSectionsCount = allSections.count { it.isRead }
            val totalSectionsCount = allSections.size.coerceAtLeast(1)
            val studyPercent = ((readSectionsCount * 100f) / totalSectionsCount).coerceIn(0f, 100f)
            val recentUniqueChapters = recentChapters.distinctBy { it.chapterId }.size
            val lastRecent = recentChapters.firstOrNull()

            // Spiritual Journey Progress Tracker (Real Room Stats)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mi Viaje Espiritual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val stats = listOf(
                        Triple("Estudios completados", studyPercent / 100f, "$readSectionsCount/$totalSectionsCount (${studyPercent.toInt()}%)"),
                        Triple("Capítulos explorados", (recentUniqueChapters / 20f).coerceIn(0f, 1f), "$recentUniqueChapters"),
                        Triple("Versículos favoritos", (favoriteVerses.size / 15f).coerceIn(0f, 1f), "${favoriteVerses.size}")
                    )
                    stats.forEach { (statName, progress, displayValue) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(0.42f)
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(0.35f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.weight(0.23f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Bible Dashboard Flotante (Spotlight Card — Real last-read chapter)
            SpotlightEditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                cornerRadius = 18.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Biblia",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                onNavigateToBibleReader(lastRecent?.bibleId, lastRecent?.chapterId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (lastRecent != null) "Continuar" else "Leer")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (lastRecent != null) {
                            "Último capítulo donde te quedaste: ${lastRecent.reference}"
                        } else {
                            "Empieza tu lectura hoy en Juan 3 o abre el índice completo"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Índice button
            Button(
                onClick = { onNavigateToBibleIndex() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(vertical = 6.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Índice Completo de la Biblia (66 libros)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }

            // Curated Study Sections + React Bits RubberSegmentedBar Filter
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (searchQuery.isBlank()) "Estudios Guiados y Devocionales" else "Resultados para \"$searchQuery\" (${sections.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val categories = listOf("Todos", "Tabernáculo", "Sacerdotes", "Devocional")
            RubberSegmentedBar(
                options = categories.map { RubberSegmentOption(id = it, label = it) },
                selectedId = categories.firstOrNull { it.equals(activeCategory, ignoreCase = true) } ?: "Todos",
                onSelect = { viewModel.selectCategory(it) },
                height = 38.dp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (sections.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "No se encontraron estudios locales para \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { onNavigateToBibleReader(null, null) }) {
                            Text("Abrir Lector Bíblico para buscar en toda la Biblia")
                        }
                    }
                }
            } else {
                sections.forEachIndexed { idx, sec ->
                    AnimatedStaggeredItem(index = idx) {
                        BibleSectionCard(
                            item = sec,
                            onClick = { viewModel.selectSection(sec) },
                            viewModel = viewModel
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Recientes Section
            if (recentChapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Capítulos Recientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                recentChapters.forEach { recent ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onNavigateToBibleReader(recent.bibleId, recent.chapterId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Reciente", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = recent.reference,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }
}

@Composable
fun BibleSectionCard(item: BibleSection, onClick: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    SpotlightEditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bible_item_card_${item.id}"),
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.category.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    // Bookmark trigger
                    IconButton(
                        onClick = { viewModel.toggleBookmark(item.id, item.isBookmarked) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("bookmark_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Save bookmark icon",
                            tint = if (item.isBookmarked) Color(0xFFD4AF37) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Share trigger
                    IconButton(
                        onClick = { viewModel.shareVerse(context, item.content, item.title) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share verses text",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = item.passageReference,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reading state flag (React Bits SpringCheckToggle)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpringCheckToggle(
                        checked = item.isRead,
                        onToggle = { viewModel.toggleRead(item.id, item.isRead) },
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        size = 22.dp,
                        modifier = Modifier.testTag("read_button_${item.id}")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (item.isRead) "Completado" else "Marcar leído (+50 XP)",
                        fontSize = 11.sp,
                        fontWeight = if (item.isRead) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "Continuar leyendo →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ReaderPane(
    section: BibleSection,
    viewModel: MainViewModel,
    textSize: Float,
    textPadding: Float
) {
    val context = LocalContext.current
    var annotationText by remember(section.id) { mutableStateOf(section.notes) }
    val verseNotes by viewModel.verseNotes.collectAsStateWithLifecycle()
    var newVerseRef by remember { mutableStateOf("") }
    var newVerseNoteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_pane")
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Reader Header Navbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { viewModel.selectSection(null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("back_to_library_btn").height(44.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("← Biblioteca", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }

            Row {
                IconButton(onClick = { viewModel.toggleBookmark(section.id, section.isBookmarked) }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Save reading bookmark",
                        tint = if (section.isBookmarked) Color(0xFFD4AF37) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = { viewModel.scheduleCalendarEvent(context, section) }) {
                    Icon(Icons.Default.DateRange, "Schedule study to Calendar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.shareVerse(context, section.content, section.title) }) {
                    Icon(Icons.Default.Share, "Share passage", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = textPadding.dp)
        ) {
            item {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = section.passageReference,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful Scripture block quote with subtle left golden border decoration
                val currentVersion by viewModel.bibleVersion.collectAsStateWithLifecycle()
                val contentToDisplay = when (currentVersion) {
                    "NTV" -> if (section.contentNTV.isNotBlank()) section.contentNTV else section.content
                    "NVI" -> if (section.contentNVI.isNotBlank()) section.contentNVI else section.content
                    "TLA" -> if (section.contentTLA.isNotBlank()) section.contentTLA else section.content
                    else -> section.content
                }

                val accentBorderColor = MaterialTheme.colorScheme.secondary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = accentBorderColor,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 10f
                            )
                        }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    AnimatedContent(
                        targetState = Pair(currentVersion, contentToDisplay),
                        transitionSpec = {
                            (fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                             androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300), initialOffsetY = { 30 })).togetherWith(
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + 
                                androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(300), targetOffsetY = { -30 })
                            )
                        },
                        label = "BibleVersionTransition"
                    ) { (version, content) ->
                        Column {
                            Text(
                                text = "Versión: $version",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "« $content »",
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.5).sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val chapterExplanation by viewModel.chapterExplanation.collectAsStateWithLifecycle()
                val isGeneratingExplanation by viewModel.isGeneratingExplanation.collectAsStateWithLifecycle()

                Button(
                    onClick = { viewModel.explainChapter(section) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isGeneratingExplanation
                ) {
                    if (isGeneratingExplanation) {
                        ThoughtLineIndicator(
                            label = "Analizando capítulo…",
                            accentColor = MaterialTheme.colorScheme.onPrimary,
                            textColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explicar con IA")
                    }
                }

                AnimatedVisibility(visible = chapterExplanation != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = "AI", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resumen Histórico y Contextual (Gemini)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = chapterExplanation ?: "",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Reflexión Teológica e Histórica",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Serif
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = section.details,
                    fontSize = (textSize - 2).sp,
                    lineHeight = ((textSize - 2) * 1.5).sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(20.dp))

                // Chapter-Specific Notes
                Text(
                    text = "Mis Notas (Capítulo)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var chapterNote by remember(section.id) { mutableStateOf(section.notes) }
                OutlinedTextField(
                    value = chapterNote,
                    onValueChange = { chapterNote = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Escribe tus reflexiones sobre este capítulo...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveAnnotation(section.id, chapterNote) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Guardar Nota del Capítulo")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(20.dp))

                // Verse-Specific Notes
                Text(
                    text = "Anotaciones de Versículos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Existing Notes List
                if (verseNotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        verseNotes.forEach { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = note.verseReference,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Row {
                                            IconButton(
                                                onClick = { viewModel.shareVerseNote(context, note.verseReference, note.noteText, section.title) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Compartir nota",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { viewModel.deleteVerseNote(note.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar nota",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.noteText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Add New Note Inputs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newVerseRef,
                        onValueChange = { newVerseRef = it },
                        modifier = Modifier.weight(0.3f),
                        placeholder = { Text("Verso", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    OutlinedTextField(
                        value = newVerseNoteText,
                        onValueChange = { newVerseNoteText = it },
                        modifier = Modifier.weight(0.7f),
                        placeholder = { Text("Tu reflexión personal...", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newVerseRef.isNotBlank() && newVerseNoteText.isNotBlank()) {
                            viewModel.saveVerseNote(section.id, newVerseRef, newVerseNoteText)
                            newVerseRef = ""
                            newVerseNoteText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir Nota al Versículo")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(20.dp))

                // Favorite Verse Section
                Text(
                    text = "Marcar Versículo Favorito",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                var favVerseRef by remember { mutableStateOf("") }
                var favVerseText by remember { mutableStateOf("") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = favVerseRef,
                        onValueChange = { favVerseRef = it },
                        modifier = Modifier.weight(0.3f),
                        placeholder = { Text("Ej. Juan 3:16", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    OutlinedTextField(
                        value = favVerseText,
                        onValueChange = { favVerseText = it },
                        modifier = Modifier.weight(0.7f),
                        placeholder = { Text("Texto del versículo...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (favVerseRef.isNotBlank() && favVerseText.isNotBlank()) {
                            viewModel.saveFavoriteVerse(section.id, favVerseRef, favVerseText)
                            favVerseRef = ""
                            favVerseText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar en Mis Favoritos")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(20.dp))

                // Verse-Specific Annotations
                Text(
                    text = "Anotaciones por Versículo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                verseNotes.forEach { verseNote ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ref: ${verseNote.verseReference}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Borrar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { viewModel.deleteVerseNote(verseNote.id) }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = verseNote.noteText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newVerseRef,
                        onValueChange = { newVerseRef = it },
                        modifier = Modifier.weight(0.3f),
                        placeholder = { Text("v. Ej: 3", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    OutlinedTextField(
                        value = newVerseNoteText,
                        onValueChange = { newVerseNoteText = it },
                        modifier = Modifier.weight(0.7f),
                        placeholder = { Text("Nota de este versículo...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newVerseRef.isNotBlank() && newVerseNoteText.isNotBlank()) {
                            viewModel.saveVerseNote(section.id, newVerseRef, newVerseNoteText)
                            newVerseRef = ""
                            newVerseNoteText = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Añadir Nota al Versículo (+10 XP)")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(20.dp))

                // Annotations section
                Text(
                    text = "Reflexión General del Capítulo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Escribe tus reflexiones generales, preguntas o rezos sobre este pasaje completo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = annotationText,
                    onValueChange = { annotationText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("annotation_input"),
                    placeholder = { Text("Escribe para guardar...", fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.saveAnnotation(section.id, annotationText) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_notes_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Guardar Apuntes (+20 XP)")
                    }

                    // Read toggle inside details screen
                    OutlinedButton(
                        onClick = { viewModel.toggleRead(section.id, section.isRead) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm read chapter",
                            tint = if (section.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (section.isRead) "Completado" else "Completar Capítulo")
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

// --------------------------------------------------------------------------
// 2. Chat Screen (Dialogue with Apostle Paul via Gemini 3.1 Pro Thinking Mode)
// --------------------------------------------------------------------------

@Composable
fun GeminiChatScreen(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    val isBusy by viewModel.isGeneratingResponse.collectAsState()
    var inputQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_panel")
    ) {
        // Chat Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Adora IA",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = "Adora IA • Estudio Bíblico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Preguntas, pasajes, agenda y explicaciones con Gemini",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Chat Message Feed
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                val isAi = msg.sender != "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = if (isAi) 4.dp else 24.dp,
                                    bottomEnd = if (isAi) 24.dp else 4.dp
                                )
                            )
                            .background(
                                if (isAi) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
                            )
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = if (isAi) 4.dp else 24.dp,
                                    bottomEnd = if (isAi) 24.dp else 4.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isAi) "ADORA IA" else "TÚ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (isAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAi) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            if (isBusy) {
                item {
                    ThoughtLineIndicator(
                        label = "Adora IA está preparando tu respuesta…",
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        // Chat Input box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = { Text("Pregúntale a Saulo sobre su caminar teológico...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("chat_input_text")
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                enabled = !isBusy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputQuery.isNotBlank() && !isBusy) {
                            viewModel.sendChatMessage(inputQuery)
                            inputQuery = ""
                            focusManager.clearFocus()
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputQuery.isNotBlank() && !isBusy) {
                        viewModel.sendChatMessage(inputQuery)
                        inputQuery = ""
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (inputQuery.isNotBlank() && !isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    .testTag("chat_send_button"),
                enabled = inputQuery.isNotBlank() && !isBusy
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message icons",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// --------------------------------------------------------------------------
// 3. Wisdom Challenges & Gamified Achievements Pane
// --------------------------------------------------------------------------

@Composable
fun TriviaAchievementsScreen(viewModel: MainViewModel, points: Int, streak: Int) {
    val currentTrivia by viewModel.currentTrivia.collectAsState()
    val triviaAnsweredState by viewModel.triviaAnsweredStatus.collectAsState()
    val achievements by viewModel.unlockedAchievements.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trivia_panel")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header (React Bits SplitTextReveal)
        item {
            SplitTextReveal(
                text = "Conocimiento y Desafíos",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Completa cuestionarios diarios, lee epístolas y desbloquea insignias.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stats Box (React Bits SpotlightEditorialCard + CountUpText)
        item {
            SpotlightEditorialCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Puntos Espirituales", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CountUpText(
                            targetValue = points,
                            suffix = " XP",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Racha de Estudio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CountUpText(
                            targetValue = streak,
                            suffix = " días",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Trivia Question Box
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("TRIVIA DIARIA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Recibe +100 XP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentTrivia.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    currentTrivia.options.forEachIndexed { index, option ->
                        val isAnswered = triviaAnsweredState != null
                        val isSelected = triviaAnsweredState == index
                        val isCorrect = index == currentTrivia.correctIndex

                        Box(
                            modifier = Modifier
                                .testTag("quiz_option_${currentTrivia.id}_$index")
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    when {
                                        isSelected && isCorrect -> Color(0xFFE8F5E9)
                                        isSelected && !isCorrect -> Color(0xFFFFEBEE)
                                        isAnswered && isCorrect -> Color(0xFFE8F5E9) // show right answer
                                        else -> MaterialTheme.colorScheme.surface
                                      }
                                )
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        isSelected && isCorrect -> Color(0xFF4CAF50)
                                        isSelected && !isCorrect -> Color(0xFFF44336)
                                        isAnswered && isCorrect -> Color(0xFF4CAF50)
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable(enabled = !isAnswered) {
                                    viewModel.submitTriviaAnswer(index)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isAnswered && isCorrect -> Color(0xFF2E7D32)
                                    isSelected && !isCorrect -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }

                    if (triviaAnsweredState != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentTrivia.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.nextTrivia() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Siguiente Desafío")
                        }
                    }
                }
            }
        }

        // Achievements Button (Clash Royale Style)
        item {
            var showBadgesDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { showBadgesDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mis Insignias", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }

            if (showBadgesDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showBadgesDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = "Mis Logros",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Clash Royale Style Achievements List
                            val allAchievements = listOf(
                                Triple("Novato Espiritual", "Consigue 100 puntos de XP en trivias.", Pair(points, 100)),
                                Triple("Llamas de Fe", "Mantén una racha de estudio de 3 días.", Pair(streak, 3)),
                                Triple("Erudito Bíblico", "Consigue 500 puntos de XP.", Pair(points, 500)),
                                Triple("Lector Constante", "Mantén una racha de estudio de 7 días.", Pair(streak, 7)),
                                Triple("Apóstol", "Consigue 1000 puntos de XP.", Pair(points, 1000)),
                                Triple("Fuego Inextinguible", "Mantén una racha de 30 días.", Pair(streak, 30))
                            )

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                items(allAchievements) { (title, desc, progressPair) ->
                                    val current = progressPair.first
                                    val target = progressPair.second
                                    val isCompleted = current >= target
                                    
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, if (isCompleted) Color(0xFFD4AF37) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isCompleted) Color(0xFFD4AF37) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isCompleted) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                                                } else {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(title, fontWeight = FontWeight.Bold, color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), lineHeight = 14.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = if (isCompleted) Color(0xFFD4AF37) else MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                                )
                                                Text(
                                                    "${current.coerceAtMost(target)} / $target",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showBadgesDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cerrar")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --------------------------------------------------------------------------
// 4. Settings & Reading Personalization Configuration
// --------------------------------------------------------------------------

@Suppress("DEPRECATION")
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    textSize: Float,
    textPadding: Float,
    userLoggedIn: Boolean,
    userEmail: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_panel")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App settings header
        item {
            Text(
                text = "Personalización y Sistemas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Calibra tu espacio de lectura según tu preferencia visual y sincroniza tus bitácoras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // Theme selector
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vibe del Lector (Colores)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = themeMode == mode
                            Box(
                                modifier = Modifier
                                    .testTag("theme_selector_${mode.name}")
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                                    .clickable { viewModel.updateThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SOCIAL_CLARO -> "Social"
                                        ThemeMode.SOCIAL_OSCURO -> "Noche"
                                        ThemeMode.PERGAMINO -> "Pergamino"
                                        ThemeMode.MINIMALISTA -> "Zen"
                                        ThemeMode.VIGILIA -> "Vigilia"
                                    },
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Font & Padding resizers
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tamaño de la Fuente",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${textSize.toInt()} sp",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = textSize,
                        onValueChange = { viewModel.updateTextSize(it) },
                        valueRange = 14f..26f,
                        steps = 5,
                        modifier = Modifier.testTag("font_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Márgenes del Lector",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${textPadding.toInt()} dp",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = textPadding,
                        onValueChange = { viewModel.updateTextPadding(it) },
                        valueRange = 8f..32f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "«Lámpara es a mis pies tu palabra...»",
                        fontSize = textSize.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = (textPadding / 2f).dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bible Version selector
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Versión de la Biblia",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val currentVersion by viewModel.bibleVersion.collectAsStateWithLifecycle()
                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentVersion,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "▼",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            listOf("RVR1960", "NTV", "NVI", "LBLA", "DHH").forEach { v ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(v, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.setBibleVersion(v)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Firebase Cloud syncing profile
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sincronizador Firebase & Google Auth",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Resguarda tu perfil, grupo de salidas y preferencias en la nube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (analyticsEnabled) "Rendimiento y Métricas: Activo" else "Modo de Privacidad: Activo",
                            fontWeight = FontWeight.Bold,
                            color = if (analyticsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        androidx.compose.material3.Switch(
                            checked = analyticsEnabled,
                            onCheckedChange = { viewModel.toggleAnalytics(it) },
                            modifier = Modifier.testTag("analytics_switch")
                        )
                    }

                    if (userLoggedIn) {
                        Text(
                            text = "Conectado como: $userEmail",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.handleSyncWithFirebase() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sincronizar Cloud")
                            }
                            OutlinedButton(
                                onClick = {
                                    signOutWithCredentialManager(
                                        credentialManager = credentialManager,
                                        onSignOutComplete = { viewModel.handleLogout() },
                                        scope = coroutineScope
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Desconectar")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = { viewModel.handleSyncWithFirebase() },
                                    onAuthError = { err ->
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                    },
                                    scope = coroutineScope
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("google_sync_btn")
                        ) {
                            Text("Iniciar Sesión con Google Auth")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    val favoriteVerses by viewModel.favoriteVerses.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Mis Favoritos",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "«Tus pasajes guardados en el corazón»",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (favoriteVerses.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 300.dp)) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Sin favoritos", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aún no tienes versículos favoritos guardados.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteVerses, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.verseReference,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row {
                                    val context = LocalContext.current
                                    IconButton(onClick = { viewModel.shareVerse(context, item.verseText, item.verseReference) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Share, contentDescription = "Compartir favorito", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(onClick = { viewModel.deleteFavoriteVerse(item.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Favorite, contentDescription = "Eliminar de favoritos", tint = Color.Red, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "«${item.verseText}»",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
