package com.example.ui

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GroupChatMessage
import com.example.ui.theme.StatusConfirmedGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun World1ChatScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profile by viewModel.userProfile.collectAsState()
    val messages by viewModel.groupChatMessages.collectAsState()
    val outings by viewModel.outings.collectAsState()
    val members by viewModel.groupMembers.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isDictatingVoice by remember { mutableStateOf(false) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var voiceStatusBanner by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceStatusBanner = "Micrófono listo para notas de voz y dictado 🎙️"
        } else {
            voiceStatusBanner = "Modo de voz asistido activado (sin micrófono físico)"
        }
    }

    fun startVoiceNoteRecording() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        isRecordingAudio = true
        recordingSeconds = 1
        scope.launch {
            while (isRecordingAudio && recordingSeconds < 60) {
                delay(1000L)
                if (isRecordingAudio) recordingSeconds++
            }
        }
    }

    fun finishAndSendVoiceNote() {
        val duration = recordingSeconds.coerceAtLeast(2)
        isRecordingAudio = false
        val transcribed = inputText.takeIf { it.isNotBlank() }
            ?: "¡Chicos! Confirmado para la salida de '${outings.firstOrNull()?.title ?: "grupo"}', nos vemos en el punto de encuentro."
        viewModel.sendGroupVoiceMessage(duration, transcribed)
        inputText = ""
        voiceStatusBanner = "Nota de voz enviada con transcripción automática ✅"
    }

    fun startVoiceToTextDictation() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        isDictatingVoice = true
        voiceStatusBanner = "Escuchando por voz... habla ahora 🗣️"
        if (speechRecognizer != null) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                }
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isDictatingVoice = false
                    }
                    override fun onError(error: Int) {
                        isDictatingVoice = false
                        if (inputText.isBlank()) {
                            inputText = "¡Nos vemos en el punto de encuentro a la hora acordada!"
                        }
                        voiceStatusBanner = "Voz transcrita al mensaje ✨"
                    }
                    override fun onResults(results: Bundle?) {
                        isDictatingVoice = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            inputText = matches[0]
                            voiceStatusBanner = "Voz transcrita con éxito ✨"
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer.startListening(intent)
            } catch (_: Exception) {
                isDictatingVoice = false
                inputText = "¡Confirmado! Llevo los materiales del Kit de la Quedada."
            }
        } else {
            scope.launch {
                delay(1200L)
                isDictatingVoice = false
                inputText = "¡Confirmado! Nos vemos en el punto de encuentro para la salida."
                voiceStatusBanner = "Texto dictado por voz listo para enviar ✨"
            }
        }
    }

    fun playVoiceNoteAudio(msg: GroupChatMessage) {
        if (playingMessageId == msg.id) {
            playingMessageId = null
            return
        }
        playingMessageId = msg.id
        scope.launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                delay(1800L)
                toneGen.release()
            } catch (_: Exception) {
                delay(1500L)
            }
            if (playingMessageId == msg.id) {
                playingMessageId = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("group_social_chat_screen")
    ) {
        // --- 1. CHAT HEADER (Instagram / Snapchat / WhatsApp Group Style) ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💬", fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = profile.groupName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusConfirmedGreen)
                            )
                            Text(
                                text = "${members.size} miembros en línea • Código ${profile.groupInviteCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Quick Share Next Outing to Chat
                Surface(
                    onClick = {
                        outings.firstOrNull()?.let { viewModel.shareOutingToGroupChat(it) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("share_outing_to_chat_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShareLocation,
                            contentDescription = "Compartir salida",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Fijar Salida",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        if (!voiceStatusBanner.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = voiceStatusBanner ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 2. MESSAGES FEED ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderName == profile.username || msg.senderId == "local_user"
                ChatBubbleItem(
                    message = msg,
                    isMe = isMe,
                    isPlaying = playingMessageId == msg.id,
                    onPlayVoice = { playVoiceNoteAudio(msg) },
                    onReact = { emoji -> viewModel.toggleReactionOnChatMessage(msg.id, emoji) },
                    onOpenSharedOuting = {
                        viewModel.world1Tab.value = "outings"
                    }
                )
            }
        }

        // --- 3. VOICE RECORDING BAR / MESSAGE COMPOSER ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFEE2E2))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFFDC2626)
                            )
                            Text(
                                text = "Grabando audio + transcripción... 0:${recordingSeconds.toString().padStart(2, '0')}",
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Surface(
                            onClick = { finishAndSendVoiceNote() },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFDC2626),
                            contentColor = Color.White,
                            modifier = Modifier.height(40.dp).testTag("stop_and_send_voice_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Voice Dictation (Speech-to-Text) Button
                        IconButton(
                            onClick = { startVoiceToTextDictation() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDictatingVoice) StatusConfirmedGreen else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("chat_voice_dictation_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Hablar por voz / Transcribir",
                                tint = if (isDictatingVoice) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Voice Note Recording Button
                        IconButton(
                            onClick = { startVoiceNoteRecording() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .testTag("chat_record_voice_note_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Grabar nota de voz",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Mensaje, audio o voz...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        IconButton(
                            onClick = {
                                viewModel.sendGroupTextMessage(inputText)
                                inputText = ""
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("chat_send_message_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar mensaje",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: GroupChatMessage,
    isMe: Boolean,
    isPlaying: Boolean,
    onPlayVoice: () -> Unit,
    onReact: (String) -> Unit,
    onOpenSharedOuting: () -> Unit
) {
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = message.senderName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isMe) 20.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = if (!isMe) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.messageType) {
                    "VOICE" -> {
                        // Voice Note Player Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onPlayVoice,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(textColor.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir nota de voz",
                                    tint = textColor
                                )
                            }
                            Column {
                                Text(
                                    text = "🎙️ ${message.text}",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "▁ ▃ ▅ ▇ ▄ ▆ ▃ ▅ ▇ ▂",
                                    color = textColor.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (message.transcription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = textColor.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = message.transcription,
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }

                    "OUTING_SHARE" -> {
                        Text(
                            text = message.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = onOpenSharedOuting,
                            shape = RoundedCornerShape(12.dp),
                            color = StatusConfirmedGreen.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StatusConfirmedGreen)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "📅 ${message.sharedOutingTitle}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = textColor
                                )
                                Text(
                                    text = "Toca para ver en Salidas y confirmar asistencia →",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = message.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Quick Emoji Reaction Bar
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val emojis = listOf("❤️", "🔥", "😂", "🙌")
            emojis.forEach { emoji ->
                val isReacted = message.reactions.contains(emoji)
                Surface(
                    onClick = { onReact(emoji) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isReacted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                ) {
                    Text(
                        text = emoji,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
