package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BibleClient
import com.example.data.BibleDto
import com.example.data.BookDto
import com.example.data.ChapterDto
import com.example.data.ParsedVerse
import com.example.data.ResolvedChapter
import com.example.data.SearchVerseDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleIndexScreen(
    preferredVersion: String = "RVR1960",
    onBack: () -> Unit,
    onChapterSelected: (String, String, String) -> Unit
) {
    var bibles by remember { mutableStateOf<List<BibleDto>>(emptyList()) }
    var selectedBible by remember { mutableStateOf<BibleDto?>(null) }
    var books by remember { mutableStateOf<List<BookDto>>(emptyList()) }
    var selectedBook by remember { mutableStateOf<BookDto?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterDto>>(emptyList()) }

    var isLoadingBooks by remember { mutableStateOf(true) }
    var isLoadingChapters by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var chaptersError by remember { mutableStateOf<String?>(null) }
    var expandedBibleSelector by remember { mutableStateOf(false) }
    var retryCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryCounter) {
        isLoadingBooks = true
        errorMsg = null
        try {
            val fetched = BibleClient.fetchBibles()
            bibles = fetched
            selectedBible = fetched.firstOrNull {
                it.abbreviation.equals(preferredVersion, ignoreCase = true) ||
                    it.id.equals(preferredVersion, ignoreCase = true)
            } ?: fetched.firstOrNull()
        } catch (e: Exception) {
            errorMsg = "No se pudieron cargar las traducciones: ${e.localizedMessage}"
            isLoadingBooks = false
        }
    }

    LaunchedEffect(selectedBible, retryCounter) {
        selectedBible?.let { bible ->
            isLoadingBooks = true
            errorMsg = null
            chapters = emptyList()
            selectedBook = null
            try {
                books = BibleClient.fetchBooks(bible.id)
                isLoadingBooks = false
            } catch (e: Exception) {
                errorMsg = "Error cargando libros: ${e.localizedMessage ?: "sin conexión"}"
                isLoadingBooks = false
            }
        }
    }

    LaunchedEffect(selectedBook, selectedBible) {
        val bible = selectedBible ?: return@LaunchedEffect
        val book = selectedBook ?: return@LaunchedEffect
        isLoadingChapters = true
        chaptersError = null
        chapters = emptyList()
        try {
            chapters = BibleClient.fetchChapters(bible.id, book.id)
        } catch (e: Exception) {
            chaptersError = "No se pudieron cargar los capítulos de ${book.name}"
        } finally {
            isLoadingChapters = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Índice de la Biblia", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        selectedBible?.let { activeBible ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expandedBibleSelector = true }
                            ) {
                                Text(
                                    text = "${activeBible.abbreviation ?: activeBible.id} — ${activeBible.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar versión", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expandedBibleSelector,
                                onDismissRequest = { expandedBibleSelector = false }
                            ) {
                                bibles.forEach { bible ->
                                    DropdownMenuItem(
                                        text = { Text("${bible.abbreviation ?: bible.id} — ${bible.name}") },
                                        onClick = {
                                            selectedBible = bible
                                            expandedBibleSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoadingBooks -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { retryCounter++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                }
            }
            else -> {
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(books, key = { it.id }) { book ->
                            val isSel = book.id == selectedBook?.id
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = book.name,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedBook = book }
                            )
                        }
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight())

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            selectedBook == null -> {
                                Text(
                                    text = "Selecciona un libro",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            isLoadingChapters -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            chaptersError != null -> {
                                Text(
                                    text = chaptersError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                                )
                            }
                            else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(chapters, key = { it.id }) { chapter ->
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = chapter.reference ?: "Capítulo ${chapter.number}",
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            modifier = Modifier.clickable {
                                                val bId = selectedBible?.id ?: preferredVersion
                                                val bkId = selectedBook?.id ?: "JHN"
                                                onChapterSelected(bId, bkId, chapter.id)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleReaderScreen(
    bibleId: String?,
    chapterId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val preferredVersion by viewModel.bibleVersion.collectAsStateWithLifecycle()
    val textSizeSp by viewModel.textSizeSp.collectAsStateWithLifecycle()
    val textPaddingDp by viewModel.textPaddingDp.collectAsStateWithLifecycle()
    val favoriteVerses by viewModel.favoriteVerses.collectAsStateWithLifecycle()

    val availableVersions = remember { BibleClient.availableSpanishBibles() }

    var currentBibleId by remember(bibleId, preferredVersion) {
        mutableStateOf(bibleId ?: BibleClient.toBollsTranslation(preferredVersion))
    }
    var targetChapterId by remember(chapterId) {
        mutableStateOf(chapterId ?: "JHN.3")
    }
    var retryCounter by remember { mutableIntStateOf(0) }

    var resolvedChapter by remember { mutableStateOf<ResolvedChapter?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedBibleSelector by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf("chapter") } // "chapter" or "bible"
    var bibleSearchResults by remember { mutableStateOf<List<SearchVerseDto>?>(null) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val chapterNotes by viewModel.getNotesForChapter(targetChapterId).collectAsStateWithLifecycle(initialValue = emptyList())
    val rawChapterExplanation by viewModel.rawChapterExplanation.collectAsStateWithLifecycle()
    val isGeneratingRawExplanation by viewModel.isGeneratingRawExplanation.collectAsStateWithLifecycle()

    var showNoteDialog by remember { mutableStateOf(false) }
    var noteVerseInput by remember { mutableStateOf("") }
    var noteTextInput by remember { mutableStateOf("") }
    var selectedVerseForActions by remember { mutableStateOf<ParsedVerse?>(null) }

    val currentBibleLabel = availableVersions.firstOrNull {
        it.id.equals(currentBibleId, ignoreCase = true) ||
            it.abbreviation.equals(currentBibleId, ignoreCase = true)
    }?.abbreviation ?: currentBibleId

    LaunchedEffect(currentBibleId, targetChapterId, retryCounter) {
        isLoading = true
        loadError = null
        bibleSearchResults = null
        viewModel.clearRawChapterExplanation()
        try {
            val chapter = BibleClient.fetchChapter(currentBibleId, targetChapterId)
            resolvedChapter = chapter
            viewModel.saveRecentChapter(chapter.bibleId, chapter.chapterId, chapter.reference)
        } catch (e: Exception) {
            loadError = "No se pudo cargar el capítulo (${e.localizedMessage ?: "verifica tu conexión a internet"})."
            resolvedChapter = null
        } finally {
            isLoading = false
        }
    }

    fun executeGlobalSearch() {
        val q = searchQuery.trim()
        if (q.isBlank()) return
        coroutineScope.launch {
            isSearchLoading = true
            searchError = null
            try {
                bibleSearchResults = BibleClient.searchVerses(currentBibleId, q)
            } catch (e: Exception) {
                searchError = "Error al buscar en la Biblia: ${e.localizedMessage}"
            } finally {
                isSearchLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(if (searchScope == "chapter") "Buscar en capítulo..." else "Buscar en toda la Biblia...")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { if (searchScope == "bible") executeGlobalSearch() }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; bibleSearchResults = null }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(
                                text = resolvedChapter?.reference ?: "Lector Bíblico",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expandedBibleSelector = true }
                            ) {
                                Text(
                                    text = currentBibleLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar versión", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expandedBibleSelector,
                                onDismissRequest = { expandedBibleSelector = false }
                            ) {
                                availableVersions.forEach { bible ->
                                    DropdownMenuItem(
                                        text = { Text("${bible.abbreviation} — ${bible.name}") },
                                        onClick = {
                                            currentBibleId = bible.id
                                            viewModel.setBibleVersion(bible.abbreviation ?: bible.id)
                                            expandedBibleSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                                bibleSearchResults = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        IconButton(
                            onClick = {
                                noteVerseInput = resolvedChapter?.reference ?: targetChapterId
                                noteTextInput = ""
                                showNoteDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Añadir nota")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { searchScope = "chapter"; bibleSearchResults = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "En este capítulo",
                            fontWeight = if (searchScope == "chapter") FontWeight.Black else FontWeight.Normal
                        )
                    }
                    FilledTonalButton(
                        onClick = { searchScope = "bible"; if (searchQuery.isNotBlank()) executeGlobalSearch() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Toda la Biblia",
                            fontWeight = if (searchScope == "bible") FontWeight.Black else FontWeight.Normal
                        )
                    }
                }
            }

            if (isSearchActive && searchScope == "bible") {
                when {
                    isSearchLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    searchError != null -> Text(searchError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    bibleSearchResults != null -> {
                        val results = bibleSearchResults.orEmpty()
                        if (results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sin coincidencias para '$searchQuery'.")
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(results, key = { it.id }) { hit ->
                                    Card(
                                        onClick = {
                                            targetChapterId = hit.chapterId
                                            isSearchActive = false
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = hit.reference ?: hit.chapterId,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(hit.text, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (isSearchLoading || searchError != null || bibleSearchResults != null) {
                    return@Column
                }
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { retryCounter++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                    }
                }
                resolvedChapter != null -> {
                    val chapter = resolvedChapter!!
                    val fullPlainText = remember(chapter) {
                        chapter.verses.joinToString("\n") { "${it.number}. ${it.text}" }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = textPaddingDp.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // AI Chapter Study Banner
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Explicación con IA", fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.explainRawChapter(chapter.reference, fullPlainText) },
                                            enabled = !isGeneratingRawExplanation
                                        ) {
                                            Text(if (isGeneratingRawExplanation) "Analizando..." else "Explicar", fontSize = 12.sp)
                                        }
                                    }
                                    if (isGeneratingRawExplanation) {
                                        ThoughtLineIndicator(
                                            label = "Analizando contexto histórico y teológico…"
                                        )
                                    } else if (!rawChapterExplanation.isNullOrBlank()) {
                                        Text(
                                            text = rawChapterExplanation!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = (textSizeSp * 1.35f).sp
                                        )
                                    }
                                }
                            }
                        }

                        // Verses List (tappable for Favorite / Note / Share)
                        items(chapter.verses, key = { it.number }) { verse ->
                            val verseRef = "${chapter.reference}:${verse.number}"
                            val isFav = favoriteVerses.any { it.verseReference == verseRef }
                            val matchesChapterFilter = !isSearchActive ||
                                searchQuery.isBlank() ||
                                verse.text.contains(searchQuery, ignoreCase = true)

                            if (matchesChapterFilter) {
                                Surface(
                                    onClick = { selectedVerseForActions = verse },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isFav) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${verse.number}",
                                            fontSize = (textSizeSp * 0.72f).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.width(28.dp).padding(top = 3.dp)
                                        )
                                        Text(
                                            text = highlightQuery(verse.text, if (isSearchActive) searchQuery else ""),
                                            fontSize = textSizeSp.sp,
                                            lineHeight = (textSizeSp * 1.5f).sp,
                                            fontFamily = FontFamily.Serif,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PulseHeartButton(
                                            liked = isFav,
                                            onToggle = {
                                                val existing = favoriteVerses.firstOrNull { it.verseReference == verseRef }
                                                if (existing != null) {
                                                    viewModel.deleteFavoriteVerse(existing.id)
                                                } else {
                                                    viewModel.saveFavoriteVerse(targetChapterId, verseRef, verse.text)
                                                }
                                            },
                                            activeColor = Color(0xFFB91C1C),
                                            inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                            iconSize = 16.dp
                                        )
                                    }
                                }
                            }
                        }

                        // Chapter Notes Section
                        if (chapterNotes.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Mis Notas del Capítulo (${chapterNotes.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(chapterNotes, key = { it.id }) { note ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.verseReference, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            Text(note.noteText, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        IconButton(onClick = { viewModel.deleteVerseNote(note.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar nota", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Chapter Prev / Next Navigation Bar
                    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (chapter.chapterNumber > 1) {
                                        targetChapterId = "${chapter.bookId}.${chapter.chapterNumber - 1}"
                                    }
                                },
                                enabled = chapter.chapterNumber > 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                                Text("Anterior", fontSize = 12.sp)
                            }
                            Text(
                                text = "Cap. ${chapter.chapterNumber} / ${chapter.totalChapters}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = {
                                    if (chapter.chapterNumber < chapter.totalChapters) {
                                        targetChapterId = "${chapter.bookId}.${chapter.chapterNumber + 1}"
                                    }
                                },
                                enabled = chapter.chapterNumber < chapter.totalChapters
                            ) {
                                Text("Siguiente", fontSize = 12.sp)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    // Verse Action Dialog (Favorite, Note, Share)
    selectedVerseForActions?.let { verse ->
        val chapterRef = resolvedChapter?.reference ?: targetChapterId
        val verseRef = "$chapterRef:${verse.number}"
        val existingFav = favoriteVerses.firstOrNull { it.verseReference == verseRef }

        AlertDialog(
            onDismissRequest = { selectedVerseForActions = null },
            title = { Text(verseRef, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("«${verse.text}»", fontFamily = FontFamily.Serif)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                if (existingFav != null) {
                                    viewModel.deleteFavoriteVerse(existingFav.id)
                                    Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveFavoriteVerse(targetChapterId, verseRef, verse.text)
                                    Toast.makeText(context, "Guardado en Favoritos ❤️", Toast.LENGTH_SHORT).show()
                                }
                                selectedVerseForActions = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (existingFav != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (existingFav != null) "Quitar" else "Favorito", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.shareVerse(context, verse.text, verseRef)
                                selectedVerseForActions = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            noteVerseInput = verseRef
                            noteTextInput = ""
                            selectedVerseForActions = null
                            showNoteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Escribir nota personal")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedVerseForActions = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Add Note Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Nueva Nota de Estudio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteVerseInput,
                        onValueChange = { noteVerseInput = it },
                        label = { Text("Referencia (ej. Juan 3:16)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteTextInput,
                        onValueChange = { noteTextInput = it },
                        label = { Text("Tu reflexión o apunte") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTextInput.isNotBlank()) {
                            viewModel.saveVerseNote(
                                sectionId = targetChapterId,
                                verseRef = noteVerseInput.ifBlank { resolvedChapter?.reference ?: targetChapterId },
                                note = noteTextInput
                            )
                            showNoteDialog = false
                            Toast.makeText(context, "Nota guardada (+10 XP) 📝", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = noteTextInput.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun highlightQuery(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var start = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    while (start < text.length) {
        val index = lowerText.indexOf(lowerQuery, start)
        if (index == -1) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, index))
        withStyle(SpanStyle(background = Color(0xFFFFEB3B), color = Color.Black, fontWeight = FontWeight.Bold)) {
            append(text.substring(index, index + query.length))
        }
        start = index + query.length
    }
}
