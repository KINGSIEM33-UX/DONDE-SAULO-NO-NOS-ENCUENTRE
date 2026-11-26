package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.OutingStatus
import com.example.ui.theme.StatusConfirmedGreen
import kotlinx.coroutines.launch

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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsState()
    val outings by viewModel.outings.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val statusMsg by viewModel.authStatusMessage.collectAsState()
    val agentMessages by viewModel.socialAgentMessages.collectAsState()
    val isAgentBusy by viewModel.isSocialAgentBusy.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddCuriosityDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var quickAiPrompt by remember { mutableStateOf("") }

    // Attempt silent auto sign-in on first launch
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                viewModel.authStatusMessage.value = "Conectado con tu cuenta de Google"
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
                val persisted = com.example.data.MediaStorageHelper.persistAndOptionallyUploadImage(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("profile_screen_root")
    ) {
        // --- 1. TOP SOCIAL HEADER BAR (User handle + Portal + AI Agent + Settings Gear ⚙️) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.groupName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PORTAL TO WORLD 2: Bible with Sparkles Button (📖✨)
                Surface(
                    onClick = { viewModel.switchToWorld2Bible() },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("switch_to_world2_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Cambiar al Mundo Bíblico",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "Biblia ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }

                // AI AGENT BUTTON (🤖✨)
                IconButton(
                    onClick = { viewModel.showSocialAgentModal.value = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("open_ai_agent_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Agente IA de Salidas",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // SETTINGS GEAR ICON (⚙️)
                IconButton(
                    onClick = { viewModel.showSettingsModal.value = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("open_settings_gear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- 2. INSTAGRAM-STYLE USER PHOTO & PROFILE HEADER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // User Photo Avatar with Story-style Gradient Ring & Camera Badge
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("profile_avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFEC4899),
                                            Color(0xFF8B5CF6),
                                            Color(0xFF06B6D4)
                                        )
                                    )
                                )
                                .padding(3.5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("user_photo_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarVisualBadge(avatarKey = profile.avatarUrl, username = profile.username)
                        }

                        // Camera mini badge at bottom-right of user photo
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Cambiar foto de usuario",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Stats Row (Salidas, Confirmadas, Récord Juegos)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStatColumn(count = outings.size.toString(), label = "Salidas")
                        ProfileStatColumn(count = confirmedCount.toString(), label = "Confirmadas")
                        ProfileStatColumn(count = "${profile.gamesHighScore}", label = "Pts Juegos")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profile.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("edit_profile_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Editar perfil", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.world1Tab.value = "outings" },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quick_go_outings_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Ver Calendario", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. TARJETA INTEGRADA DEL AGENTE DE IA PARA CONSULTAS RÁPIDAS ---
        QuickAiAgentConsultationCard(
            quickPrompt = quickAiPrompt,
            onQuickPromptChange = { quickAiPrompt = it },
            isBusy = isAgentBusy,
            latestAiReply = agentMessages.lastOrNull { it.sender == "ai" }?.text,
            onSendQuickQuery = { query ->
                val cleanQuery = query.trim()
                if (cleanQuery.isNotEmpty()) {
                    viewModel.sendSocialAgentPrompt(cleanQuery)
                    quickAiPrompt = ""
                    focusManager.clearFocus()
                }
            },
            onOpenFullAgentModal = {
                viewModel.showSocialAgentModal.value = true
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. MIS GUSTOS Y CURIOSIDADES ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mis Gustos y Curiosidades ✨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lo que tu grupo debe saber de ti para las salidas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    IconButton(
                        onClick = { showAddCuriosityDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_curiosity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir gusto o curiosidad",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.curiosities.forEach { item ->
                        AssistChip(
                            onClick = { viewModel.removeCuriosity(item) },
                            label = {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar curiosidad",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. MI GRUPO DE SALIDAS & CÓDIGO DE INVITACIÓN ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Mi Grupo de Salidas",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = profile.groupName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Invite Code Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = profile.groupInviteCode,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Copy & Share Invite Code Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Código de Grupo", profile.groupInviteCode))
                            Toast.makeText(context, "Código ${profile.groupInviteCode} copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_group_code_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar código", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "¡Únete a nuestro grupo de salidas '${profile.groupName}' en Adora con el código: ${profile.groupInviteCode}!"
                                )
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Invitar amigos al grupo"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_group_code_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invitar amigos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Miembros del grupo (${groupMembers.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupMembers.forEach { member ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = member,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("create_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crear grupo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showJoinGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("join_group_code_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unirse con código", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 6. FIREBASE & GOOGLE SIGN-IN CLOUD SYNC CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) "Sincronización en la Nube Activa ☁️" else "Conecta tu Cuenta de Google ☁️",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoggedIn) StatusConfirmedGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLoggedIn) {
                                "Conectado como $userEmail • Tus salidas, grupo y chat se guardan en Firebase Firestore."
                            } else {
                                "Inicia sesión con Google para guardar y sincronizar tus grupos, calendario, mapa y chat en Firebase."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!statusMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMsg ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isLoggedIn) {
                    Button(
                        onClick = {
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    viewModel.authStatusMessage.value = "¡Sesión iniciada con Google y sincronizada con Firebase!"
                                },
                                onAuthError = { err ->
                                    viewModel.authStatusMessage.value = err
                                },
                                scope = scope
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Iniciar sesión con Google", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.handleSyncWithFirebase() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_cloud_now_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Sincronizar ahora", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                signOutWithCredentialManager(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {
                                        viewModel.handleLogout()
                                        viewModel.authStatusMessage.value = "Sesión cerrada"
                                    },
                                    scope = scope
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("google_sign_out_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cerrar sesión", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- DIALOGS ---
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(profile.username) }
        var bioInput by remember { mutableStateOf(profile.bio) }
        var selectedAvatar by remember { mutableStateOf(profile.avatarUrl) }
        val presets = listOf("avatar_neon", "avatar_sunset", "avatar_ocean", "avatar_forest")

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Editar Perfil Social", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        label = { Text("Biografía / Estado") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Estilo de Avatar:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (selectedAvatar == preset) 3.dp else 1.dp,
                                        color = if (selectedAvatar == preset) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatar = preset },
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarVisualBadge(avatarKey = preset, username = nameInput)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfileInfo(nameInput, bioInput, selectedAvatar)
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddCuriosityDialog) {
        var newItem by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCuriosityDialog = false },
            title = { Text("Añadir Gusto o Curiosidad", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = { Text("Ej: 🍣 Amo el sushi, 🎸 Toco guitarra...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCuriosity(newItem)
                        showAddCuriosityDialog = false
                    }
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCuriosityDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        var groupNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Crear Nuevo Grupo de Salidas", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = groupNameInput,
                    onValueChange = { groupNameInput = it },
                    label = { Text("Nombre del grupo") },
                    placeholder = { Text("Ej: Los Aventureros del Viernes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewOutingGroup(groupNameInput)
                        showCreateGroupDialog = false
                    }
                ) {
                    Text("Crear y Generar Código")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showJoinGroupDialog) {
        var codeInput by remember { mutableStateOf("") }
        var nameHint by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = { Text("Unirse a un Grupo con Código", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text("Código de invitación (ej. QUEDA-2026)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameHint,
                        onValueChange = { nameHint = it },
                        label = { Text("Nombre del grupo (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinOutingGroupByCode(codeInput, nameHint)
                        showJoinGroupDialog = false
                    }
                ) {
                    Text("Unirme al Grupo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAiAgentConsultationCard(
    quickPrompt: String,
    onQuickPromptChange: (String) -> Unit,
    isBusy: Boolean,
    latestAiReply: String?,
    onSendQuickQuery: (String) -> Unit,
    onOpenFullAgentModal: () -> Unit
) {
    val quickSuggestions = listOf(
        "📅 ¿Qué salidas tenemos pronto?",
        "💡 Sugiéreme un plan económico",
        "🟢 Confirmar próxima salida",
        "🎒 ¿Qué llevar a la quedada?"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_agent_quick_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6366F1),
                    Color(0xFFEC4899),
                    Color(0xFF06B6D4)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Agente de IA",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Agente de IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Consultas rápidas, planes y acciones en vivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenFullAgentModal,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_full_ai_agent_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Abrir Agente",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Suggestion Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickSuggestions.forEachIndexed { index, suggestion ->
                    SuggestionChip(
                        onClick = { onSendQuickQuery(suggestion) },
                        label = {
                            Text(
                                text = suggestion,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("ai_quick_suggestion_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Query Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickPrompt,
                    onValueChange = onQuickPromptChange,
                    placeholder = {
                        Text(
                            text = "Pregunta algo rápido o crea una salida...",
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { onSendQuickQuery(quickPrompt) }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_quick_query_input")
                )

                IconButton(
                    onClick = { onSendQuickQuery(quickPrompt) },
                    enabled = quickPrompt.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (quickPrompt.isNotBlank() && !isBusy) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("ai_quick_query_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar consulta rápida al Agente de IA",
                        tint = if (quickPrompt.isNotBlank() && !isBusy) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Loading bar when AI Agent is processing
            AnimatedVisibility(visible = isBusy) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "El Agente de IA está procesando tu consulta...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Inline Latest AI Response Preview
            if (!latestAiReply.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFullAgentModal() }
                        .testTag("ai_quick_response_box")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Última respuesta del Agente IA:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = latestAiReply,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AvatarVisualBadge(avatarKey: String, username: String) {
    val isCustomUri = avatarKey.startsWith("content://") ||
        avatarKey.startsWith("file://") ||
        avatarKey.startsWith("http://") ||
        avatarKey.startsWith("https://")

    if (isCustomUri) {
        AsyncImage(
            model = avatarKey,
            contentDescription = "Foto de perfil de $username",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
        return
    }

    val gradientColors = when {
        avatarKey.contains("sunset") -> listOf(Color(0xFFF97316), Color(0xFFEC4899))
        avatarKey.contains("ocean") -> listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
        avatarKey.contains("forest") -> listOf(Color(0xFF10B981), Color(0xFF059669))
        else -> listOf(Color(0xFF6366F1), Color(0xFFA855F7))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }
        Text(
            text = username.take(2).uppercase().ifBlank { "AQ" },
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp
        )
    }
}
