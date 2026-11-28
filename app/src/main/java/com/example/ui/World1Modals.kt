package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.MediaStorageHelper
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusPlannedGray
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// 1. CAMERA, PHOTO/VIDEO ALBUM & ANIMATED INSTANTÁNEAS (STORIES) MODAL
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraAndStoriesModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stories by viewModel.stories.collectAsState()
    val outings by viewModel.outings.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val syncState by viewModel.socialSyncState.collectAsState()
    val storiesLoadState = syncState.storiesState

    var activeStoryIndex by remember { mutableIntStateOf(-1) }
    var showCreateStorySheet by remember { mutableStateOf(false) }
    var selectedOutingId by remember(outings) { mutableStateOf(outings.firstOrNull()?.id ?: "") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTargetIsStory by remember { mutableStateOf(false) }
    var draftStoryPhotoUri by rememberSaveable { mutableStateOf("") }
    var draftStoryExpectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var draftStoryExpectedGroupId by rememberSaveable { mutableStateOf("") }
    var draftStoryMediaFailed by rememberSaveable { mutableStateOf(false) }
    var pendingCameraExpectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraExpectedGroupId by rememberSaveable { mutableStateOf("") }
    var pendingAlbumExpectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAlbumExpectedGroupId by rememberSaveable { mutableStateOf("") }
    var pendingStoryPickerExpectedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStoryPickerExpectedGroupId by rememberSaveable { mutableStateOf("") }

    val selectedOuting = outings.firstOrNull { it.id == selectedOutingId } ?: outings.firstOrNull()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedOuting != null) {
            scope.launch {
                val expectedUid = pendingAlbumExpectedUid
                val expectedGroupId = pendingAlbumExpectedGroupId
                val persisted = MediaStorageHelper.persistAndOptionallyUploadImage(
                    context = context,
                    sourceUri = uri,
                    folder = "album",
                    uid = expectedUid
                )
                persisted.onSuccess { value ->
                    viewModel.addPhotoToOutingAlbum(selectedOuting.id, value, expectedUid, expectedGroupId)
                        .onSuccess { Toast.makeText(context, "Foto guardada en el álbum de '${selectedOuting.title}' 📸", Toast.LENGTH_SHORT).show() }
                        .onFailure { error -> Toast.makeText(context, "No se pudo guardar la foto: ${error.localizedMessage ?: "fallo al guardar"}", Toast.LENGTH_LONG).show() }
                }.onFailure { error ->
                    Toast.makeText(context, "No se pudo guardar la foto: ${error.localizedMessage ?: "fallo al subirla"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val storyGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val expectedUid = pendingStoryPickerExpectedUid
                val expectedGroupId = pendingStoryPickerExpectedGroupId
                val result = MediaStorageHelper.persistAndOptionallyUploadImage(
                    context = context,
                    sourceUri = uri,
                    folder = "stories",
                    uid = expectedUid
                )
                result.onSuccess { value ->
                    MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                    draftStoryPhotoUri = value
                    draftStoryExpectedUid = expectedUid
                    draftStoryExpectedGroupId = expectedGroupId
                    draftStoryMediaFailed = false
                }.onFailure { error ->
                    draftStoryMediaFailed = true
                    Toast.makeText(context, "No se pudo preparar la instantánea: ${error.localizedMessage ?: "fallo al subir la foto"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            scope.launch {
                val expectedUid = pendingCameraExpectedUid
                val expectedGroupId = pendingCameraExpectedGroupId
                val folder = if (cameraTargetIsStory) "stories" else "album"
                val persisted = MediaStorageHelper.persistAndOptionallyUploadImage(
                    context = context,
                    sourceUri = capturedUri,
                    folder = folder,
                    uid = expectedUid
                )
                persisted.onSuccess { value ->
                    if (cameraTargetIsStory) {
                        MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                        draftStoryPhotoUri = value
                        draftStoryExpectedUid = expectedUid
                        draftStoryExpectedGroupId = expectedGroupId
                        draftStoryMediaFailed = false
                        showCreateStorySheet = true
                    } else if (selectedOuting != null) {
                        viewModel.addPhotoToOutingAlbum(selectedOuting.id, value, expectedUid, expectedGroupId)
                            .onSuccess { Toast.makeText(context, "Captura guardada en '${selectedOuting.title}' 📸", Toast.LENGTH_SHORT).show() }
                            .onFailure { error -> Toast.makeText(context, "No se pudo guardar la captura: ${error.localizedMessage ?: "fallo al guardar"}", Toast.LENGTH_LONG).show() }
                    }
                }.onFailure { error ->
                    if (cameraTargetIsStory) draftStoryMediaFailed = true
                    Toast.makeText(context, "No se pudo guardar la captura: ${error.localizedMessage ?: "fallo al subirla"}", Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingCameraUri = null
    }

    fun launchRealCamera(forStory: Boolean) {
        cameraTargetIsStory = forStory
        try {
            val (_, uri) = MediaStorageHelper.createCameraCaptureFile(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir la cámara: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchRealCamera(cameraTargetIsStory)
        } else {
            Toast.makeText(context, "Concede permiso de cámara para hacer fotos", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCameraCapture(forStory: Boolean) {
        cameraTargetIsStory = forStory
        pendingCameraExpectedUid = currentUserId
        pendingCameraExpectedGroupId = viewModel.userProfile.value.groupId.ifBlank { viewModel.userProfile.value.groupInviteCode }
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            launchRealCamera(forStory)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = {
            MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("camera_and_stories_modal"),
            color = MaterialTheme.colorScheme.background
        ) {
            if (activeStoryIndex in stories.indices) {
                FullScreenStoryViewer(
                    stories = stories,
                    initialIndex = activeStoryIndex,
                    onLike = { storyId -> viewModel.likeInstantaneaStory(storyId) },
                    onClose = { activeStoryIndex = -1 }
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
                                    text = "Fotos reales, galería e historias del grupo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                                onDismiss()
                            },
                            modifier = Modifier.size(48.dp).testTag("close_camera_modal_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (syncState.isGuestDemo) {
                        EditorialDemoModeBanner(
                            detail = "Instantáneas y álbum de demostración locales (modo invitado)."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (storiesLoadState is com.example.data.CloudLoadState.Error) {
                        EditorialCloudErrorCard(
                            title = "No se pudieron cargar las instantáneas",
                            message = storiesLoadState.message,
                            onRetry = { viewModel.refreshCloudData() },
                            compact = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (storiesLoadState is com.example.data.CloudLoadState.Loading && stories.isEmpty()) {
                        EditorialCloudLoadingCard(label = "Cargando instantáneas desde la nube…")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // --- INSTANTÁNEAS (STORIES) SECTION ---
                    Text(
                        text = "Instantáneas del Grupo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                                        draftStoryPhotoUri = ""
                                        draftStoryExpectedUid = currentUserId
                                        draftStoryExpectedGroupId = viewModel.userProfile.value.groupId.ifBlank { viewModel.userProfile.value.groupInviteCode }
                                        draftStoryMediaFailed = false
                                        showCreateStorySheet = true
                                    }
                                    .testTag("create_new_instantanea_btn")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Nueva Instantánea",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Subir Historia", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        itemsIndexed(stories, key = { _, s -> s.id }) { idx, story ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(78.dp)
                                    .clickable { activeStoryIndex = idx }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 3.dp,
                                            brush = Brush.linearGradient(
                                                listOf(Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF8B5CF6))
                                            ),
                                            shape = CircleShape
                                        )
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                ) {
                                    if (MediaStorageHelper.isRenderableImageUri(story.mediaUri)) {
                                        AsyncImage(
                                            model = MediaStorageHelper.toCoilModel(story.mediaUri),
                                            contentDescription = story.caption,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(storyFilterBrush(story.filterStyle)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✨", fontSize = 24.sp)
                                        }
                                    }
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

                    // --- OUTING PHOTO ALBUM SECTION ---
                    Text(
                        text = "Álbum Compartido por Salida 📸",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(outings, key = { it.id }) { outing ->
                            val isSel = selectedOuting?.id == outing.id
                            Surface(
                                onClick = { selectedOutingId = outing.id },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = "${outing.title} (${outing.albumPhotos.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                pendingAlbumExpectedUid = currentUserId
                                pendingAlbumExpectedGroupId = viewModel.userProfile.value.groupId.ifBlank { viewModel.userProfile.value.groupInviteCode }
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = selectedOuting != null,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galería", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { requestCameraCapture(forStory = false) },
                            enabled = selectedOuting != null,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hacer Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val photos = selectedOuting?.albumPhotos.orEmpty()
                    if (photos.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Todavía no hay fotos en esta salida. Usa 'Hacer Foto' o 'Galería' para añadir recuerdos reales.",
                                modifier = Modifier.padding(20.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            photos.forEachIndexed { idx, photoUri ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                    modifier = Modifier.width(164.dp)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(130.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (MediaStorageHelper.isRenderableImageUri(photoUri)) {
                                                AsyncImage(
                                                    model = MediaStorageHelper.toCoilModel(photoUri),
                                                    contentDescription = "Recuerdo #${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(
                                                                    Color(0xFF6366F1),
                                                                    Color(0xFFEC4899)
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("📸", fontSize = 30.sp)
                                                        Text(
                                                            text = "Recuerdo #${idx + 1}",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        val saved = MediaStorageHelper.saveImageToGallery(context, photoUri)
                                                        Toast.makeText(
                                                            context,
                                                            if (saved) "Guardada en Galería (Pictures/Adora) ✅"
                                                            else "Solo las fotos reales pueden descargarse a la galería",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = "Descargar a galería", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    MediaStorageHelper.shareMediaOrText(
                                                        context = context,
                                                        uriOrPath = photoUri,
                                                        caption = "Recuerdo de '${selectedOuting?.title ?: "nuestra salida"}' en Adora 📸✨"
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Compartir foto", modifier = Modifier.size(18.dp))
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

    // Create Story Dialog (supports real camera/gallery photo + filter + caption)
    if (showCreateStorySheet) {
        var caption by rememberSaveable { mutableStateOf("") }
        var selectedFilter by rememberSaveable { mutableStateOf("NEON_VIBES") }
        val filters = listOf(
            "NEON_VIBES" to "Neón ✨",
            "GOLDEN_SUNSET" to "Atardecer 🌅",
            "CYBER_RETRO" to "Retro 📼"
        )

        AlertDialog(
            onDismissRequest = {
                MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                draftStoryPhotoUri = ""
                draftStoryExpectedUid = null
                draftStoryExpectedGroupId = ""
                draftStoryMediaFailed = false
                showCreateStorySheet = false
            },
            title = { Text("Nueva Instantánea ✨", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Live preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(storyFilterBrush(selectedFilter)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (MediaStorageHelper.isRenderableImageUri(draftStoryPhotoUri)) {
                            AsyncImage(
                                model = MediaStorageHelper.toCoilModel(draftStoryPhotoUri),
                                contentDescription = "Vista previa de instantánea",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("📸 Elige una foto o usa el fondo de filtro", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { requestCameraCapture(forStory = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cámara", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                pendingStoryPickerExpectedUid = currentUserId
                                pendingStoryPickerExpectedGroupId = viewModel.userProfile.value.groupId.ifBlank { viewModel.userProfile.value.groupInviteCode }
                                storyGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Galería", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Texto o pie de historia") },
                        placeholder = { Text("¡Momento épico con el grupo!") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        filters.forEach { (key, label) ->
                            val isSel = selectedFilter == key
                            Surface(
                                onClick = { selectedFilter = key },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !draftStoryMediaFailed,
                    onClick = {
                        val expectedUid = draftStoryExpectedUid
                        val expectedGroupId = draftStoryExpectedGroupId
                        scope.launch {
                            viewModel.publishInstantaneaStory(
                                caption = caption,
                                filterStyle = selectedFilter,
                                mediaUri = draftStoryPhotoUri.ifBlank { "story_${System.currentTimeMillis()}" },
                                outingTitle = selectedOuting?.title ?: "Quedada en grupo",
                                expectedUid = expectedUid,
                                expectedGroupId = expectedGroupId
                            ).onSuccess {
                                showCreateStorySheet = false
                                draftStoryPhotoUri = ""
                                draftStoryExpectedUid = null
                                draftStoryExpectedGroupId = ""
                                draftStoryMediaFailed = false
                                Toast.makeText(context, "Instantánea publicada ✨", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(context, "No se pudo publicar: ${error.localizedMessage ?: "fallo al guardar"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Publicar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    MediaStorageHelper.deleteOwnedLocalMedia(context, draftStoryPhotoUri)
                    draftStoryPhotoUri = ""
                    draftStoryExpectedUid = null
                    draftStoryExpectedGroupId = ""
                    draftStoryMediaFailed = false
                    showCreateStorySheet = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun storyFilterBrush(filterStyle: String): Brush {
    return when (filterStyle) {
        "WARM_GOLD", "GOLDEN_SUNSET" -> Brush.linearGradient(listOf(Color(0xFF2B2218), Color(0xFF5A4324), Color(0xFF191511)))
        "FOREST", "CYBER_RETRO" -> Brush.linearGradient(listOf(Color(0xFF10241D), Color(0xFF1E4436), Color(0xFF0C1512)))
        else -> Brush.linearGradient(listOf(Color(0xFF141417), Color(0xFF26231F), Color(0xFF09090B)))
    }
}

@Composable
private fun FullScreenStoryViewer(
    stories: List<OutingStory>,
    initialIndex: Int,
    onLike: (String) -> Unit,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex.coerceAtLeast(0))) }
    var progress by remember { mutableFloatStateOf(0f) }
    val story = stories.getOrNull(currentIndex) ?: return

    LaunchedEffect(currentIndex, stories.size) {
        progress = 0f
        val steps = 50
        for (i in 1..steps) {
            delay(100L) // 5 seconds per story
            progress = i.toFloat() / steps.toFloat()
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(storyFilterBrush(story.filterStyle))
    ) {
        // Real photo background if available
        if (MediaStorageHelper.isRenderableImageUri(story.mediaUri)) {
            AsyncImage(
                model = MediaStorageHelper.toCoilModel(story.mediaUri),
                contentDescription = story.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        // Left / Right tap zones for prev / next story
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (currentIndex > 0) currentIndex-- else progress = 0f
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
                    }
            )
        }

        // Top segmented animated progress bars + header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    val barProgress = when {
                        idx < currentIndex -> 1f
                        idx == currentIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = story.authorName,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "📍 ${story.outingTitle}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar historia", tint = Color.White)
                }
            }
        }

        // Center / Lower caption
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = story.caption,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentIndex + 1} / ${stories.size} • Toca izq/der para navegar",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    PulseHeartButton(
                        liked = story.likesCount > 0,
                        count = story.likesCount.toInt(),
                        onToggle = { onLike(story.id) },
                        activeColor = Color(0xFFEF4444),
                        inactiveColor = Color.White,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
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
    val syncState by viewModel.socialSyncState.collectAsState()
    val outingsLoadState = syncState.outingsState
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
                Text("Historial Completo de Salidas", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (syncState.isGuestDemo) {
                    EditorialDemoModeBanner(
                        detail = "Historial de demostración local (modo invitado)."
                    )
                }
                Text(
                    text = "Archivo histórico de todas las salidas por año, mes y día (${filtered.size} registros):",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filtrar por año, mes o nombre...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    outingsLoadState is com.example.data.CloudLoadState.Loading && filtered.isEmpty() -> {
                        EditorialCloudLoadingCard(label = "Cargando historial desde la nube…")
                    }
                    outingsLoadState is com.example.data.CloudLoadState.Error && filtered.isEmpty() -> {
                        EditorialCloudErrorCard(
                            title = "No se pudo cargar el historial",
                            message = outingsLoadState.message,
                            onRetry = { viewModel.refreshCloudData() }
                        )
                    }
                    com.example.data.SocialSessionPolicy.shouldShowEmptyState(outingsLoadState, filtered.size) -> {
                        EditorialCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (filterQuery.isBlank()) "Todavía no tienes salidas registradas." else "No hay coincidencias para \"$filterQuery\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (outingsLoadState is com.example.data.CloudLoadState.Error) {
                                item {
                                    EditorialCloudErrorCard(
                                        title = "Error al actualizar historial",
                                        message = outingsLoadState.message,
                                        onRetry = { viewModel.refreshCloudData() },
                                        compact = true
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { item ->
                                val stColor = when (item.outingStatus) {
                                    OutingStatus.CONFIRMED -> StatusConfirmedGreen
                                    OutingStatus.PLANNED -> StatusPlannedGray
                                    OutingStatus.CANCELLED -> StatusCancelledRed
                                }
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, stColor)
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
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${item.location}  •  ${item.budgetPerPerson.ifBlank { "Sin coste" }}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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
// 3. AGENTIC AI ASSISTANT MODAL
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
        "Crea salida Cena de Pizzas este viernes a las 21:00 llevando 15€ confirmada",
        "Pon como pensada (gris) la ruta de senderismo",
        "Marca que asistiré a la Cena & Noche de Bolera",
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
                            onClick = { if (!isBusy) viewModel.sendSocialAgentPrompt(qp) },
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
                            ThoughtLineIndicator(
                                label = "Analizando y preparando acción…",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
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
                            if (promptInput.isNotBlank()) {
                                viewModel.sendSocialAgentPrompt(promptInput)
                                promptInput = ""
                            }
                        },
                        enabled = !isBusy && promptInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isBusy && promptInput.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("social_agent_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar a IA",
                            tint = if (!isBusy && promptInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
// 4. SETTINGS MODAL (PERSISTED VIA DATASTORE)
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
                Text("Ajustes", style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = {
                        onDismiss()
                        viewModel.showSocialAgentModal.value = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("open_ai_agent_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Asistente IA",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
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
                Text("Apariencia", style = MaterialTheme.typography.labelLarge)
                RubberSegmentedBar(
                    options = listOf(
                        RubberSegmentOption(id = ThemeMode.SOCIAL_CLARO.name, label = "Marfil"),
                        RubberSegmentOption(id = ThemeMode.SOCIAL_OSCURO.name, label = "Obsidiana")
                    ),
                    selectedId = if (themeMode == ThemeMode.SOCIAL_OSCURO || themeMode == ThemeMode.VIGILIA) {
                        ThemeMode.SOCIAL_OSCURO.name
                    } else {
                        ThemeMode.SOCIAL_CLARO.name
                    },
                    onSelect = { selected ->
                        val next = if (selected == ThemeMode.SOCIAL_OSCURO.name) ThemeMode.SOCIAL_OSCURO else ThemeMode.SOCIAL_CLARO
                        viewModel.updateThemeMode(next)
                    },
                    height = 42.dp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Moneda para presupuestos", style = MaterialTheme.typography.labelLarge)
                RubberSegmentedBar(
                    options = currencies.map { RubberSegmentOption(id = it, label = it) },
                    selectedId = currency,
                    onSelect = { viewModel.updateDefaultCurrency(it) },
                    height = 40.dp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notificaciones de salidas", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = notifications,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Efectos de sonido en juegos", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = sounds,
                        onCheckedChange = { viewModel.setSoundEffectsEnabled(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = if (isLoggedIn) "Cuenta Google: $email" else "Cuenta Google: No conectada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.switchToWorld2Bible()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ir a Estudio Bíblico")
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
