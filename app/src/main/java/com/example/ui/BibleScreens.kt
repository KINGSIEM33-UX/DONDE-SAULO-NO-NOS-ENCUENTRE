package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.data.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleIndexScreen(
    onBack: () -> Unit,
    onChapterSelected: (String, String, String) -> Unit // bibleId, bookId, chapterId
) {
    var bibles by remember { mutableStateOf<List<BibleDto>>(emptyList()) }
    var selectedBible by remember { mutableStateOf<BibleDto?>(null) }
    var books by remember { mutableStateOf<List<BookDto>>(emptyList()) }
    var selectedBook by remember { mutableStateOf<BookDto?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterDto>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var expandedBibleSelector by remember { mutableStateOf(false) }

    // Predefined requested Bibles with known generic IDs
    val requestedBibles = listOf(
        BibleDto(id = "592420522e16049f-01", name = "Reina Valera 1960", abbreviation = "RVR1960", description = null, language = LanguageDto("spa", "Spanish")),
        BibleDto(id = "06125adad2d5898a-01", name = "Nueva Versión Internacional", abbreviation = "NVI", description = null, language = LanguageDto("spa", "Spanish")),
        BibleDto(id = "179568874c45066f-01", name = "Traducción en Lenguaje Actual", abbreviation = "TLA", description = null, language = LanguageDto("spa", "Spanish"))
    )

    LaunchedEffect(Unit) {
        try {
            val response = BibleClient.service.getBibles()
            val spanishBibles = response.data.filter { it.language?.id == "spa" }
            
            val mergedBibles = requestedBibles.toMutableList()
            for (sb in spanishBibles) {
                if (mergedBibles.none { it.abbreviation == sb.abbreviation }) {
                    mergedBibles.add(sb)
                }
            }
            bibles = mergedBibles
            selectedBible = bibles.firstOrNull { it.abbreviation == "RVR1960" } ?: bibles.firstOrNull()
            isLoading = false
        } catch (e: Exception) {
            bibles = requestedBibles
            selectedBible = requestedBibles.first()
            isLoading = false
        }
    }

    LaunchedEffect(selectedBible) {
        selectedBible?.let { bible ->
            isLoading = true
            errorMsg = null
            try {
                val response = BibleClient.service.getBooks(bible.id)
                books = response.data
                isLoading = false
            } catch (e: Exception) {
                errorMsg = "Error cargando libros: Verifica tu conexión y API Key."
                isLoading = false
            }
        }
    }
        
    LaunchedEffect(selectedBook) {
        selectedBook?.let { book ->
            try {
                val response = BibleClient.service.getChapters(selectedBible!!.id, book.id)
                chapters = response.data
            } catch (e: Exception) {
                // Ignore silent errors for chapters
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Índice de la Biblia", fontFamily = FontFamily.Serif)
                        if (selectedBible != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expandedBibleSelector = true }
                            ) {
                                Text(
                                    text = selectedBible?.name ?: "",
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
                                        text = { Text("${bible.abbreviation} - ${bible.name}") },
                                        onClick = { 
                                            selectedBible = bible
                                            selectedBook = null
                                            expandedBibleSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { 
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error) 
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Books list
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(books) { book ->
                        ListItem(
                            headlineContent = { Text(book.name, fontWeight = if (book == selectedBook) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.clickable { selectedBook = book }
                        )
                    }
                }
                
                Divider(modifier = Modifier.width(1.dp).fillMaxHeight())
                
                // Chapters list
                if (selectedBook != null) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(chapters) { chapter ->
                            ListItem(
                                headlineContent = { Text(chapter.reference ?: chapter.number) },
                                modifier = Modifier.clickable {
                                    onChapterSelected(selectedBible!!.id, selectedBook!!.id, chapter.id)
                                }
                            )
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("Selecciona un libro", style = MaterialTheme.typography.bodyMedium)
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
    var content by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("Cargando...") }
    var isLoading by remember { mutableStateOf(true) }
    var expandedBibleSelector by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf("chapter") } // "chapter" or "bible"
    var bibleSearchResults by remember { mutableStateOf<List<SearchVerseDto>?>(null) }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val availableVersions = remember { listOf(
        Pair("592420522e16049f-01", "RVR1960"),
        Pair("06125adad2d5898a-01", "NVI"),
        Pair("179568874c45066f-01", "TLA")
    ) }
    
    var currentBibleId by remember(bibleId) { mutableStateOf(bibleId ?: "592420522e16049f-01") }
    val currentBibleName = availableVersions.find { it.first == currentBibleId }?.second ?: "Versión"
    
    // Allow targetChapterId to change internally via search navigation
    var targetChapterId by remember(chapterId) { mutableStateOf(chapterId ?: "JHN.3") }

    val chapterNotes by viewModel.getNotesForChapter(targetChapterId).collectAsStateWithLifecycle(initialValue = emptyList())
    val rawChapterExplanation by viewModel.rawChapterExplanation.collectAsStateWithLifecycle()
    val isGeneratingRawExplanation by viewModel.isGeneratingRawExplanation.collectAsStateWithLifecycle()
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteVerseInput by remember { mutableStateOf("") }
    var noteTextInput by remember { mutableStateOf("") }

    LaunchedEffect(currentBibleId, targetChapterId) {
        isLoading = true
        isSearchActive = false
        searchQuery = ""
        bibleSearchResults = null
        viewModel.clearRawChapterExplanation()
        try {
            val response = BibleClient.service.getChapterContent(currentBibleId, targetChapterId)
            content = response.data.content
            title = response.data.reference ?: "Capítulo"
            viewModel.saveRecentChapter(currentBibleId, targetChapterId, title)
        } catch (e: Exception) {
            // Fallback for missing versions in free tier API.Bible
            if (e.message?.contains("404") == true || e.message?.contains("403") == true) {
                 try {
                     val fallbackResponse = BibleClient.service.getChapterContent("592420522e16049f-01", targetChapterId)
                     content = fallbackResponse.data.content
                     title = fallbackResponse.data.reference ?: "Capítulo"
                     viewModel.saveRecentChapter("592420522e16049f-01", targetChapterId, title)
                 } catch (fallbackEx: Exception) {
                     content = "Error de red: ${fallbackEx.message}\nVerifica tu conexión y clave de API."
                     title = "Error"
                 }
            } else {
                content = "Error de red: ${e.message}\nVerifica tu conexión y clave de API."
                title = "Error"
            }
        } finally {
            isLoading = false
        }
    }

    fun performBibleSearch(query: String) {
        if (query.isBlank()) return
        isSearchLoading = true
        searchError = null
        coroutineScope.launch {
            try {
                val response = BibleClient.service.searchBible(currentBibleId, query)
                bibleSearchResults = response.data.verses
            } catch (e: Exception) {
                searchError = "Error en la búsqueda. Revisa tu conexión."
            } finally {
                isSearchLoading = false
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!isSearchActive || searchScope == "chapter") {
                FloatingActionButton(
                    onClick = { showNoteDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Añadir Nota")
                }
            }
        },
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchScope == "bible") {
                                        performBibleSearch(searchQuery)
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                            bibleSearchResults = null
                        }) {
                            Icon(Icons.Default.ArrowBack, "Cerrar búsqueda")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Limpiar")
                            }
                        }
                        IconButton(onClick = { 
                            if (searchScope == "bible") {
                                performBibleSearch(searchQuery)
                            }
                        }) {
                            Icon(Icons.Default.Search, "Buscar")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { 
                        Column {
                            Text(title, fontFamily = FontFamily.Serif)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expandedBibleSelector = true }
                            ) {
                                Text(
                                    text = currentBibleName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar versión", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expandedBibleSelector,
                                onDismissRequest = { expandedBibleSelector = false }
                            ) {
                                availableVersions.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { 
                                            currentBibleId = id
                                            expandedBibleSelector = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Buscar")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (content == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text("No se pudo cargar el capítulo.", color = MaterialTheme.colorScheme.error) 
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isSearchActive) {
                    TabRow(
                        selectedTabIndex = if (searchScope == "chapter") 0 else 1,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = searchScope == "chapter",
                            onClick = { searchScope = "chapter" },
                            text = { Text("En este capítulo") }
                        )
                        Tab(
                            selected = searchScope == "bible",
                            onClick = { 
                                searchScope = "bible"
                                if (searchQuery.isNotBlank() && bibleSearchResults == null) {
                                    performBibleSearch(searchQuery)
                                }
                            },
                            text = { Text("Toda la Biblia") }
                        )
                    }
                }

                if (isSearchActive && searchScope == "bible") {
                    // Global Search Results
                    if (isSearchLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (searchError != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(searchError!!, color = MaterialTheme.colorScheme.error) }
                    } else if (bibleSearchResults?.isEmpty() == true) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No se encontraron resultados para '$searchQuery'") }
                    } else if (bibleSearchResults != null) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(bibleSearchResults!!) { result ->
                                val cleanSnippet = result.text.replace(Regex("<.*?>"), "")
                                ListItem(
                                    headlineContent = { Text(result.reference ?: "Versículo", fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text(cleanSnippet, maxLines = 3) },
                                    modifier = Modifier.clickable {
                                        targetChapterId = result.chapterId
                                        isSearchActive = false
                                    }
                                )
                                Divider()
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Escribe para buscar en toda la Biblia") }
                    }
                } else {
                    // Chapter text content
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        val cleanText = content?.replace(Regex("<.*?>"), " ")?.replace(Regex("\\s+"), " ") ?: ""
                        
                        val displayText = if (isSearchActive && searchQuery.isNotBlank()) {
                            val highlightColor = Color.Yellow.copy(alpha = 0.4f)
                            buildAnnotatedString {
                                val matchResults = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(cleanText).toList()
                                var currentIndex = 0
                                for (match in matchResults) {
                                    append(cleanText.substring(currentIndex, match.range.first))
                                    withStyle(style = SpanStyle(background = highlightColor)) {
                                        append(cleanText.substring(match.range))
                                    }
                                    currentIndex = match.range.last + 1
                                }
                                append(cleanText.substring(currentIndex))
                            }
                        } else {
                            AnnotatedString(cleanText)
                        }

                        Text(
                            text = displayText,
                            fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.explainRawChapter(title, cleanText) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !isGeneratingRawExplanation
                        ) {
                            if (isGeneratingRawExplanation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analizando capítulo con Gemini...")
                            } else {
                                Text("Explicar Capítulo con IA (Gemini)")
                            }
                        }

                        if (rawChapterExplanation != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Resumen Histórico y Espiritual (Gemini)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = rawChapterExplanation ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        
                        if (chapterNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Mis Notas (${chapterNotes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            chapterNotes.forEach { note ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Versículo: ${note.verseReference}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(onClick = { viewModel.deleteVerseNote(note.id) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = note.noteText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(48.dp)) // padding for FAB
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Añadir Nota") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteVerseInput,
                        onValueChange = { noteVerseInput = it },
                        label = { Text("Número de Versículo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteTextInput,
                        onValueChange = { noteTextInput = it },
                        label = { Text("Tu nota") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTextInput.isNotBlank()) {
                            viewModel.saveVerseNote(
                                sectionId = targetChapterId,
                                verseRef = noteVerseInput.ifBlank { "General" },
                                note = noteTextInput
                            )
                            noteVerseInput = ""
                            noteTextInput = ""
                            showNoteDialog = false
                        }
                    }
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
