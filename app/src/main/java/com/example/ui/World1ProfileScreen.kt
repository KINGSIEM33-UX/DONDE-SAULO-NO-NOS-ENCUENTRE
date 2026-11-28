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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.GroupInvite
import com.example.data.GroupInviteCrypto
import com.example.data.GroupRole
import com.example.data.MediaStorageHelper
import com.example.data.OutingStatus
import com.example.ui.theme.StatusConfirmedGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun World1ProfileScreen(viewModel: MainViewModel) {
    ProfileScreen(viewModel = viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsState()
    val outings by viewModel.outings.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val activeInvites by viewModel.activeGroupInvites.collectAsState()
    val currentRole by viewModel.currentGroupRole.collectAsState()
    val inviteFeedback by viewModel.inviteActionFeedback.collectAsState()
    val isInviteBusy by viewModel.isInviteActionBusy.collectAsState()
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val statusMsg by viewModel.authStatusMessage.collectAsState()
    val isAgentBusy by viewModel.isSocialAgentBusy.collectAsState()
    val syncState by viewModel.socialSyncState.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddCuriosityDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var showCreateInviteDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var quickAiPrompt by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                viewModel.authStatusMessage.value = "Sesión de Google activa"
            },
            onUnauthenticated = {},
            scope = scope
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val persisted = MediaStorageHelper.persistAndOptionallyUploadImage(
                    context = context,
                    sourceUri = uri,
                    folder = "avatars",
                    uid = viewModel.currentUserId.value
                )
                viewModel.updateProfileInfo(profile.username, profile.bio, persisted)
            }
        }
    }

    val confirmedCount = outings.count { it.outingStatus == OutingStatus.CONFIRMED }
    val hasActiveGroup = profile.groupId.isNotBlank() || profile.groupInviteCode.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("profile_screen_root"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- 1. REFINED EDITORIAL TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShinyEditorialText(
                text = "Adora",
                style = MaterialTheme.typography.headlineMedium,
                baseColor = MaterialTheme.colorScheme.onBackground,
                shineColor = MaterialTheme.colorScheme.secondary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorialWorldTogglePill(
                    label = "Biblia",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = { viewModel.switchToWorld2Bible() },
                    modifier = Modifier.testTag("switch_to_world2_bible_button")
                )

                IconButton(
                    onClick = { viewModel.showSocialAgentModal.value = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .testTag("open_ai_agent_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Asistente IA",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.showSettingsModal.value = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .testTag("open_settings_gear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Honest Guest Demo or Cloud Error indicator
        if (syncState.isGuestDemo) {
            EditorialDemoModeBanner(
                detail = "Estás viendo datos de demostración locales. Inicia sesión con Google para empezar con tu cuenta real."
            )
        } else {
            val profileErr = syncState.profileState as? com.example.data.CloudLoadState.Error
            val groupErr = syncState.groupState as? com.example.data.CloudLoadState.Error
            val activeErr = profileErr ?: groupErr
            if (activeErr != null) {
                EditorialCloudErrorCard(
                    title = "Error al cargar desde la nube",
                    message = activeErr.message,
                    onRetry = { viewModel.refreshCloudData() },
                    compact = true
                )
            }
        }

        // --- 2. IDENTITY & METRICS CARD (React Bits Spotlight Card + CountUp) ---
        AnimatedStaggeredItem(index = 0) {
            SpotlightEditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (MediaStorageHelper.isRenderableImageUri(profile.avatarUrl)) {
                                    AsyncImage(
                                        model = MediaStorageHelper.toCoilModel(profile.avatarUrl),
                                        contentDescription = "Avatar de perfil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = profile.username.ifBlank { "U" }.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = FontFamily.Serif,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Cambiar foto",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "@${profile.username.ifBlank { "usuario" }}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (hasActiveGroup) profile.groupName.ifBlank { "Grupo activo" } else "Sin grupo activo",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasActiveGroup) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = profile.bio.ifBlank { "Toca el lápiz para añadir tu biografía." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar perfil",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileMetricColumn(value = outings.size, label = "Salidas", modifier = Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileMetricColumn(value = confirmedCount, label = "Confirmadas", modifier = Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileMetricColumn(value = profile.gamesHighScore.toInt(), label = "Puntos", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // --- 3. QUIET INLINE AI ASSISTANT BAR (NOTION / LINEAR STYLE) ---
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Asistente de Salidas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = { viewModel.showSocialAgentModal.value = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Abrir chat", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickAiPrompt,
                        onValueChange = { quickAiPrompt = it },
                        placeholder = { Text("Ej. Crea una cena el viernes a las 21:00...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (quickAiPrompt.isNotBlank() && !isAgentBusy) {
                                    viewModel.sendSocialAgentPrompt(quickAiPrompt)
                                    quickAiPrompt = ""
                                    focusManager.clearFocus()
                                    viewModel.showSocialAgentModal.value = true
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (quickAiPrompt.isNotBlank() && !isAgentBusy) {
                                viewModel.sendSocialAgentPrompt(quickAiPrompt)
                                quickAiPrompt = ""
                                focusManager.clearFocus()
                                viewModel.showSocialAgentModal.value = true
                            }
                        },
                        enabled = quickAiPrompt.isNotBlank() && !isAgentBusy,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (quickAiPrompt.isNotBlank() && !isAgentBusy) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        if (isAgentBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (quickAiPrompt.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 4. ACTIVE GROUP & REVOCABLE HIGH-ENTROPY INVITES ---
        EditorialSectionLabel(text = "Grupo activo")
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (hasActiveGroup) {
                    val roleLabel = when (currentRole) {
                        GroupRole.OWNER -> "Propietario"
                        GroupRole.ADMIN -> "Admin"
                        GroupRole.MEMBER -> "Miembro"
                        null -> "Miembro"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            EditorialIconBadge(icon = Icons.Default.Groups)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = profile.groupName.ifBlank { "Grupo activo" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${groupMembers.size} miembros  •  $roleLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentRole == GroupRole.MEMBER || currentRole == GroupRole.ADMIN) {
                            IconButton(
                                onClick = { showLeaveGroupDialog = true },
                                enabled = !isInviteBusy
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Salir del grupo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (groupMembers.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            groupMembers.forEach { member ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = member,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Revocable high-entropy invites section (OWNER / ADMIN only)
                    if (currentRole?.canManageInvites == true) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Invitaciones privadas",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tokens de 160 bits con caducidad y revocación",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showCreateInviteDialog = true },
                                enabled = !isInviteBusy,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Invitar", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        val nowMs = System.currentTimeMillis()
                        val usableInvites = activeInvites.filter { it.isActiveAt(nowMs) }
                        val inactiveCount = activeInvites.size - usableInvites.size

                        if (usableInvites.isEmpty()) {
                            Text(
                                text = "No hay invitaciones activas ahora mismo. Pulsa «Invitar» para generar un enlace revocable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                usableInvites.take(4).forEach { invite ->
                                    GroupInviteRow(
                                        invite = invite,
                                        enabled = !isInviteBusy,
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(invite.token))
                                            Toast.makeText(context, "Token de invitación copiado", Toast.LENGTH_SHORT).show()
                                        },
                                        onShare = {
                                            val shareText = buildString {
                                                append("Únete a nuestro grupo '${profile.groupName.ifBlank { invite.groupNamePreview }}' en Adora.\n")
                                                append("Abre Perfil → Unirse y pega esta invitación privada:\n")
                                                append(invite.token)
                                            }
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Compartir invitación privada"))
                                        },
                                        onRevoke = { viewModel.revokeGroupInvite(invite.token) }
                                    )
                                }
                            }
                        }

                        if (inactiveCount > 0) {
                            Text(
                                text = "$inactiveCount invitación(es) anteriores revocadas, agotadas o caducadas.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Solo el propietario o administradores pueden emitir o revocar invitaciones de este grupo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditorialIconBadge(icon = Icons.Default.Groups)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Aún no perteneces a un grupo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Crea un grupo propio o únete pegando una invitación privada (INV-…) para compartir salidas, mapa y chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isInviteBusy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = inviteFeedback ?: "Procesando solicitud segura…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else if (!inviteFeedback.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = inviteFeedback!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearInviteActionFeedback() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar aviso",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCreateGroupDialog = true },
                        enabled = !isInviteBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear grupo", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { showJoinGroupDialog = true },
                        enabled = !isInviteBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unirse", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // --- 5. INTERESTS & TASTES ---
        EditorialSectionLabel(
            text = "Intereses y preferencias",
            trailing = {
                TextButton(
                    onClick = { showAddCuriosityDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            if (profile.curiosities.isEmpty()) {
                Text(
                    text = "Aún no has añadido intereses. Pulsa «Añadir» para personalizar tu perfil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.curiosities.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.removeCuriosity(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 6. CLOUD ACCOUNT & SYNC ---
        EditorialSectionLabel(text = "Sincronización en la nube")
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorialIconBadge(
                        icon = if (isLoggedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        iconTint = if (isLoggedIn) StatusConfirmedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) "Cuenta sincronizada" else "Modo invitado (demo local)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLoggedIn) userEmail else "Conecta Google para cargar tus datos reales y sincronizar grupos en tiempo real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!statusMsg.isNullOrBlank()) {
                    Text(
                        text = statusMsg!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (isLoggedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.refreshCloudData() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Actualizar", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                signOutWithCredentialManager(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = { viewModel.handleLogout() },
                                    scope = scope
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Cerrar sesión", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { viewModel.refreshCloudData() },
                                onAuthError = { err ->
                                    viewModel.authStatusMessage.value = err
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                },
                                scope = scope
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Continuar con Google", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // --- DIALOGS ---
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(profile.username) }
        var bioInput by remember { mutableStateOf(profile.bio) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Editar perfil", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nombre de usuario") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Biografía") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfileInfo(nameInput, bioInput, profile.avatarUrl)
                        showEditProfileDialog = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddCuriosityDialog) {
        var itemText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCuriosityDialog = false },
            title = { Text("Añadir interés", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = itemText,
                    onValueChange = { itemText = it },
                    label = { Text("Ej. Cine clásico, Montaña, Café") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCuriosity(itemText)
                        showAddCuriosityDialog = false
                    },
                    enabled = itemText.isNotBlank()
                ) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCuriosityDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Crear nuevo grupo", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Nombre del grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewOutingGroup(groupName)
                        showCreateGroupDialog = false
                    },
                    enabled = groupName.isNotBlank()
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showJoinGroupDialog) {
        var tokenInput by remember { mutableStateOf("") }
        val isLegacyTyped = GroupInviteCrypto.isLegacyGroupCode(tokenInput)
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = { Text("Unirse con invitación", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pega el token privado (INV-…) o el mensaje completo que te compartió el propietario del grupo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = GroupInviteCrypto.normalizeInviteToken(it) },
                        label = { Text("Token de invitación (INV-…)") },
                        maxLines = 3,
                        isError = isLegacyTyped,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isLegacyTyped) {
                        Text(
                            text = "Los códigos antiguos de 4 dígitos (ej. QUEDA-2026) ya no permiten autounirse por seguridad. Pide al propietario una invitación nueva.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinOutingGroupByCode(tokenInput)
                        showJoinGroupDialog = false
                    },
                    enabled = tokenInput.isNotBlank() && !isLegacyTyped
                ) { Text("Unirse") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCreateInviteDialog) {
        var selectedMultiUse by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateInviteDialog = false },
            title = { Text("Crear invitación privada", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cada invitación genera un token independiente de 160 bits. Podrás revocarla en cualquier momento sin afectar al grupo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = { selectedMultiUse = false },
                        shape = RoundedCornerShape(12.dp),
                        color = if (!selectedMultiUse) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (!selectedMultiUse) 1.5.dp else 0.75.dp,
                            color = if (!selectedMultiUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "1 uso  •  Caduca en 24 horas (Recomendado)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ideal para invitar a una sola persona de forma privada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        onClick = { selectedMultiUse = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedMultiUse) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selectedMultiUse) 1.5.dp else 0.75.dp,
                            color = if (selectedMultiUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Hasta 10 usos  •  Caduca en 7 días",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Útil para compartir con varios amigos a la vez; revocable al instante.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maxUses = if (selectedMultiUse) 10 else 1
                        val hours = if (selectedMultiUse) 24 * 7 else 24
                        viewModel.generateGroupInvite(maxUses = maxUses, expiresInHours = hours) { created ->
                            clipboard.setText(AnnotatedString(created.token))
                            Toast.makeText(context, "Invitación creada y copiada al portapapeles", Toast.LENGTH_SHORT).show()
                        }
                        showCreateInviteDialog = false
                    }
                ) { Text("Generar y copiar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInviteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showLeaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupDialog = false },
            title = { Text("Salir del grupo", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Dejarás de ver las salidas, mensajes e instantáneas de '${profile.groupName.ifBlank { "este grupo" }}'. Para volver necesitarás una nueva invitación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.leaveCurrentGroup()
                        showLeaveGroupDialog = false
                    }
                ) { Text("Salir del grupo") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun GroupInviteRow(
    invite: GroupInvite,
    enabled: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit
) {
    val previewToken = if (invite.token.length > 16) {
        "${invite.token.take(12)}…${invite.token.takeLast(4)}"
    } else {
        invite.token
    }
    val expiryLabel = remember(invite.expiresAt) {
        val date = invite.expiresAt?.toDate()
        if (date != null) {
            val fmt = SimpleDateFormat("dd MMM · HH:mm", Locale("es", "ES"))
            "Caduca ${fmt.format(date)}"
        } else {
            "Activa"
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = previewToken,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${invite.useCount}/${invite.maxUses} usos  •  $expiryLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar invitación", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onShare, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir invitación", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRevoke, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Revocar invitación",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMetricColumn(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CountUpText(
            targetValue = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
