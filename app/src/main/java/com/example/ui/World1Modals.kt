package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusPlannedGray
import com.example.ui.theme.ThemeMode

// ============================================================================
// 1. CAMERA, PHOTO/VIDEO ALBUM & INSTAGRAM-STYLE INSTANTÁNEAS (STORIES) MODAL
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraAndStoriesModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val stories by viewModel.stories.collectAsState()
    val outings by viewModel.outings.collectAsState()

    var activeStoryViewer by remember { mutableStateOf<OutingStory?>(null) }
    var showCreateStorySheet by remember { mutableStateOf(false) }
    var selectedOutingId by remember { mutableStateOf(outings.firstOrNull()?.id ?: "") }

    val selectedOuting = outings.firstOrNull { it.id == selectedOutingId } ?: outings.firstOrNull()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedOuting != null) {
            viewModel.addPhotoToOutingAlbum(selectedOuting.id, uri.toString())
            Toast.makeText(context, "Foto/Video guardado en el álbum de la salida 📸", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("camera_and_stories_modal"),
            color = MaterialTheme.colorScheme.background
        ) {
            if (activeStoryViewer != null) {
                val st = stories.firstOrNull { it.id == activeStoryViewer?.id } ?: activeStoryViewer!!
                FullScreenStoryViewer(
                    story = st,
                    onLike = { viewModel.likeInstantaneaStory(st.id) },
                    onClose = { activeStoryViewer = null }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Cámara, Álbum e Instantáneas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Fotos, videos e historias en vivo de las salidas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp).testTag("close_camera_modal_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- INSTANTÁNEAS (STORIES) SECTION ---
                    Text(
                        text = "Instantáneas del Grupo ✨ (Estilo Instagram)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Add New Instantánea Tile
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { showCreateStorySheet = true }
                                    .testTag("create_new_instantanea_btn")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFFEC4899), Color(0xFF6366F1))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Subir instantánea",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+ Instantánea",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Existing Stories
                        items(stories, key = { it.id }) { story ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(78.dp)
                                    .clickable { activeStoryViewer = story }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFFF59E0B),
                                                    Color(0xFFEC4899),
                                                    Color(0xFF8B5CF6)
                                                )
                                            )
                                        )
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            story.filterStyle.contains("SUNSET") -> "🌅"
                                            story.filterStyle.contains("RETRO") -> "🍕"
                                            else -> "🎳"
                                        },
                                        fontSize = 28.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = story.authorName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- SHARED OUTING PHOTO/VIDEO ALBUM ---
                    Text(
                        text = "Álbum de Recuerdos de la Salida 📸",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Cualquier miembro puede subir fotos o videos, verlos, descargarlos o compartirlos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Outing Selector for Album
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        outings.forEach { outing ->
                            val isSel = outing.id == selectedOuting?.id
                            Surface(
                                onClick = { selectedOutingId = outing.id },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = "${outing.title} (${outing.albumPhotos.size})",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons: Tomar Instantánea / Subir de Galería
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Subir Foto/Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                selectedOuting?.let {
                                    viewModel.addPhotoToOutingAlbum(
                                        it.id,
                                        "capture_${System.currentTimeMillis() % 1000}"
                                    )
                                    Toast.makeText(context, "¡Captura instantánea montada en el álbum! 📸", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Captura Rápida", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val photos = selectedOuting?.albumPhotos.orEmpty()
                    if (photos.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aún no hay fotos en esta salida. ¡Sube la primera foto o video!",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        photos.forEachIndexed { idx, photoKey ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    when (idx % 3) {
                                                        0 -> listOf(Color(0xFF4F46E5), Color(0xFFEC4899))
                                                        1 -> listOf(Color(0xFF059669), Color(0xFF0284C7))
                                                        else -> listOf(Color(0xFFF59E0B), Color(0xFFDC2626))
                                                    }
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📸 Recuerdo #${idx + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                            Text(
                                                text = selectedOuting?.title ?: "",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Subido al álbum del grupo ($photoKey)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    Toast.makeText(
                                                        context,
                                                        "Foto descargada en la galería del dispositivo ✅",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = "Descargar foto")
                                            }
                                            IconButton(
                                                onClick = {
                                                    val send = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "📸 Recuerdo de nuestra salida '${selectedOuting?.title}' en Adora"
                                                        )
                                                    }
                                                    context.startActivity(Intent.createChooser(send, "Compartir recuerdo"))
                                                }
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Compartir foto")
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
    }

    if (showCreateStorySheet) {
        var caption by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf("NEON_VIBES") }
        val filters = listOf("NEON_VIBES", "GOLDEN_SUNSET", "CYBER_RETRO")

        AlertDialog(
            onDismissRequest = { showCreateStorySheet = false },
            title = { Text("Crear Instantánea ✨", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Texto sobre la instantánea") },
                        placeholder = { Text("Ej: ¡Rumbo a la quedada! 🔥") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Filtro Visual:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        filters.forEach { f ->
                            Surface(
                                onClick = { filter = f },
                                shape = RoundedCornerShape(10.dp),
                                color = if (filter == f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (filter == f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = f.replace("_", " "),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.publishInstantaneaStory(
                            caption = caption,
                            filterStyle = filter,
                            mediaUri = "story_${System.currentTimeMillis()}",
                            outingTitle = selectedOuting?.title ?: "Salida en grupo"
                        )
                        showCreateStorySheet = false
                    }
                ) {
                    Text("Publicar Instantánea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateStorySheet = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FullScreenStoryViewer(
    story: OutingStory,
    onLike: () -> Unit,
    onClose: () -> Unit
) {
    val gradient = when {
        story.filterStyle.contains("SUNSET") -> listOf(Color(0xFFF97316), Color(0xFFDB2777), Color(0xFF4F46E5))
        story.filterStyle.contains("RETRO") -> listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF9333EA))
        else -> listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF10B981))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            LinearProgressIndicator(
                progress = { 0.75f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(story.authorName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("📍 ${story.outingTitle} • ${story.filterStyle}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar historia", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✨📸✨", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = story.caption,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Visible para todo el grupo",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            Button(
                onClick = onLike,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${story.likesCount} Me gusta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 2. HISTORIAL DE SALIDAS MODAL (ALL YEARS, MONTHS & DAYS)
// ============================================================================
@Composable
fun OutingHistoryModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val outings by viewModel.outings.collectAsState()
    var filterQuery by remember { mutableStateOf("") }

    val filtered = remember(outings, filterQuery) {
        outings.filter {
            filterQuery.isBlank() ||
                it.title.contains(filterQuery, ignoreCase = true) ||
                it.location.contains(filterQuery, ignoreCase = true) ||
                it.dateIso.contains(filterQuery, ignoreCase = true)
        }.sortedByDescending { it.dateIso + " " + it.time }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Historial Completo de Salidas", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Archivo histórico de todas las salidas por año, mes y día (${filtered.size} registros):",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filtrar por año (ej. 2025, 2026), mes o nombre...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val stColor = when (item.outingStatus) {
                            OutingStatus.CONFIRMED -> StatusConfirmedGreen
                            OutingStatus.PLANNED -> StatusPlannedGray
                            OutingStatus.CANCELLED -> StatusCancelledRed
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, stColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${item.dateIso} (${item.time}) • ${item.outingStatus.label}",
                                    color = stColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "📍 ${item.location} | 💶 ${item.budgetPerPerson}",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar Historial")
            }
        }
    )
}

// ============================================================================
// 3. AGENTIC AI ASSISTANT MODAL (ACCESSIBLE FROM PERFIL, SALIDAS & AJUSTES)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialAgentModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val messages by viewModel.socialAgentMessages.collectAsState()
    val isBusy by viewModel.isSocialAgentBusy.collectAsState()
    var promptInput by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Crea salida Cena de Pizzas el martes 10 de noviembre a las 21:00 llevando 15€ confirmada",
        "Pon como pensada (gris) la salida del sábado",
        "Marca que asistiré a la próxima salida",
        "¿Qué materiales llevamos en el Kit de la Quedada?"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("Agente IA de Salidas ✨", fontWeight = FontWeight.Black)
                    Text(
                        "Con habilidades para agendar en el calendario y mapa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Acciones rápidas con 1 toque:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.forEach { qp ->
                        Surface(
                            onClick = { viewModel.sendSocialAgentPrompt(qp) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = qp.take(38) + "...",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.sender == "user"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg.text,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (isBusy) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Ejecutando acción con Gemini...", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text("Pídeme crear o editar una salida...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("social_agent_input")
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendSocialAgentPrompt(promptInput)
                            promptInput = ""
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("social_agent_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar a IA",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

// ============================================================================
// 4. SETTINGS MODAL (OPENED VIA GEAR ICON ⚙️ IN MAIN PROFILE SCREEN)
// ============================================================================
@Composable
fun World1SettingsModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()
    val notifications by viewModel.notificationsEnabled.collectAsState()
    val sounds by viewModel.soundEffectsEnabled.collectAsState()
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val email by viewModel.userEmail.collectAsState()

    val currencies = listOf("€", "$", "MXN", "COP")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ajustes Generales ⚙️", fontWeight = FontWeight.Black)
                // AI Agent Button inside Settings as requested!
                IconButton(
                    onClick = {
                        onDismiss()
                        viewModel.showSocialAgentModal.value = true
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("open_ai_agent_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Abrir Agente IA desde Ajustes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Light / Dark Mode Toggle
                Text("Apariencia Red Social:", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.updateThemeMode(ThemeMode.SOCIAL_CLARO) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Modo Claro",
                            fontWeight = if (themeMode == ThemeMode.SOCIAL_CLARO) FontWeight.Black else FontWeight.Medium
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.updateThemeMode(ThemeMode.SOCIAL_OSCURO) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Modo Oscuro",
                            fontWeight = if (themeMode == ThemeMode.SOCIAL_OSCURO) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider()

                // 2. Default Currency for Outing Budgets
                Text("Moneda por defecto para Presupuestos:", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { curr ->
                        val isSel = currency == curr
                        Surface(
                            onClick = { viewModel.defaultCurrency.value = curr },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(curr, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 3. Notifications & Sounds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Avisos de nuevas salidas y chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = notifications,
                        onCheckedChange = { viewModel.notificationsEnabled.value = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Efectos de sonido en Mini-Juegos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = sounds,
                        onCheckedChange = { viewModel.soundEffectsEnabled.value = it }
                    )
                }

                HorizontalDivider()

                // 4. Cloud Status
                Text(
                    text = if (isLoggedIn) "☁️ Cuenta Google: $email" else "☁️ Cuenta Google: No conectada",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // 5. Quick Portal to World 2
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.switchToWorld2Bible()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ir al Mundo 2 (Biblia y Estudio)")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}
