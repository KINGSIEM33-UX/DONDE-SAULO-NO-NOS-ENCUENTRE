package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.ContextCompat
import com.example.data.GroupChatMessage
import com.example.data.MediaStorageHelper
import com.example.ui.theme.StatusConfirmedGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class PendingAudioAction {
    NONE,
    RECORD_VOICE_NOTE,
    DICTATE_SPEECH
}

@Composable
fun World1ChatScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val profile by viewModel.userProfile.collectAsState()
    val messages by viewModel.groupChatMessages.collectAsState()
    val outings by viewModel.outings.collectAsState()
    val members by viewModel.groupMembers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isDictatingVoice by remember { mutableStateOf(false) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var voiceStatusBanner by remember { mutableStateOf<String?>(null) }
    var pendingAudioAction by remember { mutableStateOf(PendingAudioAction.NONE) }

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun releaseRecorder(deleteFile: Boolean = false) {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        if (deleteFile) {
            currentRecordingFile?.delete()
            currentRecordingFile = null
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playingMessageId = null
        playbackProgress = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            releaseRecorder(deleteFile = true)
            stopPlayback()
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    // Auto-scroll to latest message when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun beginRealVoiceRecording() {
        stopPlayback()
        val outputFile = MediaStorageHelper.createVoiceNoteFile(context)
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            currentRecordingFile = outputFile
            isRecordingAudio = true
            recordingSeconds = 0
            voiceStatusBanner = "Grabando nota de voz real... 🎙️"

            scope.launch {
                while (isRecordingAudio && recordingSeconds < 120) {
                    delay(1000L)
                    if (isRecordingAudio) recordingSeconds++
                }
            }
        } catch (e: Exception) {
            releaseRecorder(deleteFile = true)
            isRecordingAudio = false
            voiceStatusBanner = "No se pudo iniciar la grabación de audio (${e.localizedMessage ?: "micrófono ocupado"})."
        }
    }

    fun cancelVoiceRecording() {
        isRecordingAudio = false
        releaseRecorder(deleteFile = true)
        voiceStatusBanner = "Grabación cancelada"
    }

    fun finishAndSendVoiceNote() {
        val duration = recordingSeconds.coerceAtLeast(1)
        val recordedFile = currentRecordingFile
        isRecordingAudio = false
        releaseRecorder(deleteFile = false)

        if (recordedFile == null || !recordedFile.exists() || recordedFile.length() == 0L) {
            voiceStatusBanner = "No se grabó audio válido. Intenta mantener pulsado unos segundos."
            return
        }

        val optionalCaption = inputText.trim()
        inputText = ""
        voiceStatusBanner = "Enviando nota de voz..."
        scope.launch {
            val savedAudioUrl = MediaStorageHelper.optionallyUploadVoiceNote(
                context = context,
                localFile = recordedFile,
                uid = currentUserId
            )
            viewModel.sendGroupVoiceMessage(
                durationSec = duration,
                transcriptionText = optionalCaption,
                audioUrl = savedAudioUrl
            )
            voiceStatusBanner = "Nota de voz enviada ✅"
        }
    }

    fun beginRealDictation() {
        if (speechRecognizer == null) {
            voiceStatusBanner = "El reconocimiento de voz no está disponible en este dispositivo."
            return
        }
        isDictatingVoice = true
        voiceStatusBanner = "Escuchando... habla ahora 🗣️"
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
                    voiceStatusBanner = "No se pudo transcribir el audio (código $error). Inténtalo de nuevo."
                }
                override fun onResults(results: Bundle?) {
                    isDictatingVoice = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        inputText = if (inputText.isBlank()) matches[0] else "$inputText ${matches[0]}"
                        voiceStatusBanner = "Dictado transcrito con éxito ✨"
                    } else {
                        voiceStatusBanner = "No se detectó voz."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        voiceStatusBanner = "Escuchando: \"$partial\""
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isDictatingVoice = false
            voiceStatusBanner = "Error al iniciar el dictado: ${e.localizedMessage ?: "desconocido"}"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAudioAction
        pendingAudioAction = PendingAudioAction.NONE
        if (granted) {
            when (action) {
                PendingAudioAction.RECORD_VOICE_NOTE -> beginRealVoiceRecording()
                PendingAudioAction.DICTATE_SPEECH -> beginRealDictation()
                PendingAudioAction.NONE -> voiceStatusBanner = "Permiso de micrófono concedido 🎙️"
            }
        } else {
            voiceStatusBanner = "Necesitas conceder permiso de micrófono para grabar notas de voz o dictar."
        }
    }

    fun requestVoiceNoteRecording() {
        if (hasRecordAudioPermission()) {
            beginRealVoiceRecording()
        } else {
            pendingAudioAction = PendingAudioAction.RECORD_VOICE_NOTE
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestDictation() {
        if (hasRecordAudioPermission()) {
            beginRealDictation()
        } else {
            pendingAudioAction = PendingAudioAction.DICTATE_SPEECH
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun playVoiceNoteAudio(msg: GroupChatMessage) {
        if (playingMessageId == msg.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        if (msg.audioUrl.isBlank()) {
            voiceStatusBanner = "Esta nota de voz de demostración solo tiene transcripción de texto."
            return
        }
        try {
            val player = MediaPlayer().apply {
                setDataSource(msg.audioUrl)
                setOnPreparedListener { mp ->
                    playingMessageId = msg.id
                    playbackProgress = 0f
                    mp.start()
                    scope.launch {
                        val totalMs = mp.duration.coerceAtLeast(1)
                        while (playingMessageId == msg.id) {
                            val currentPos = try { mp.currentPosition } catch (_: Exception) { break }
                            playbackProgress = (currentPos.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                            delay(100L)
                        }
                    }
                }
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    voiceStatusBanner = "No se pudo reproducir el archivo de audio."
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            stopPlayback()
            voiceStatusBanner = "Error al reproducir audio: ${e.localizedMessage ?: "archivo no encontrado"}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("group_social_chat_screen")
    ) {
        // --- 1. EDITORIAL CHAT HEADER ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    EditorialIconBadge(
                        icon = Icons.Default.ShareLocation,
                        size = 40.dp,
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = profile.groupName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(StatusConfirmedGreen)
                            )
                            Text(
                                text = "${members.size} miembros  •  ${profile.groupInviteCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                outings.firstOrNull()?.let { nextOuting ->
                    IconButton(
                        onClick = { viewModel.shareOutingToGroupChat(nextOuting) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShareLocation,
                            contentDescription = "Compartir próxima salida",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Status banner for voice/dictation feedback
        if (voiceStatusBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = voiceStatusBanner!!,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { voiceStatusBanner = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar aviso",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- 2. MESSAGES LIST ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no hay mensajes en este grupo.\n¡Envía un mensaje o nota de voz para empezar! 💬",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == (currentUserId ?: "local_user") || msg.senderName == profile.username
                ChatBubbleItem(
                    message = msg,
                    isMine = isMine,
                    isPlaying = playingMessageId == msg.id,
                    playbackProgress = if (playingMessageId == msg.id) playbackProgress else 0f,
                    onPlayVoice = { playVoiceNoteAudio(msg) },
                    onReact = { emoji -> viewModel.toggleReactionOnChatMessage(msg.id, emoji) },
                    onOpenSharedOuting = {
                        val found = outings.firstOrNull { it.id == msg.sharedOutingId }
                        if (found != null) {
                            viewModel.selectedOutingForDetail.value = found
                            viewModel.world1Tab.value = "map"
                        }
                    }
                )
            }
        }

        // --- 3. INPUT BAR + REAL VOICE RECORDER + SPEECH-TO-TEXT ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRecordingAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { cancelVoiceRecording() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancelar grabación",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    ) {
                        VoicePillWaveform(
                            isActive = true,
                            barColor = Color(0xFFDC2626),
                            barCount = 16,
                            modifier = Modifier.weight(1f).height(26.dp)
                        )
                        Text(
                            text = "0:${recordingSeconds.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )
                    }
                    Surface(
                        onClick = { finishAndSendVoiceNote() },
                        shape = RoundedCornerShape(20.dp),
                        color = StatusConfirmedGreen,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("stop_and_send_voice_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Enviar voz", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Dictation (Speech-to-Text)
                    IconButton(
                        onClick = { requestDictation() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDictatingVoice) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("dictate_voice_to_text_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Dictar mensaje por voz",
                            tint = if (isDictatingVoice) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Escribe al grupo...", fontSize = 14.sp) },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_text_input")
                    )

                    if (inputText.isBlank()) {
                        // Real Voice Note Recorder button
                        IconButton(
                            onClick = { requestVoiceNoteRecording() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                                .testTag("record_voice_note_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Grabar nota de voz",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    } else {
                        // Send text button (disabled when blank)
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendGroupTextMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_chat_message_btn")
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
    isMine: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onPlayVoice: () -> Unit,
    onReact: (String) -> Unit,
    onOpenSharedOuting: () -> Unit
) {
    val quickEmojis = listOf("❤️", "🔥", "😂", "👍")
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isMine) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor, contentColor = contentColor),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (message.messageType) {
                    "VOICE" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onPlayVoice,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Detener" else "Reproducir nota de voz",
                                    tint = contentColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nota de voz • 0:${message.audioDurationSec.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                VoicePillWaveform(
                                    isActive = isPlaying,
                                    barColor = contentColor,
                                    inactiveBarColor = contentColor.copy(alpha = 0.28f),
                                    barCount = 20,
                                    progress = if (isPlaying) playbackProgress else 0f,
                                    modifier = Modifier.fillMaxWidth().height(20.dp)
                                )
                            }
                        }
                        if (message.transcription.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = contentColor.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = message.transcription,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }

                    "OUTING_SHARE" -> {
                        Text(text = message.text, fontSize = 14.sp)
                        Surface(
                            onClick = onOpenSharedOuting,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "📍 Salida Compartida",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.sharedOutingTitle,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Toca para abrir en el mapa →",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    else -> {
                        Text(text = message.text, fontSize = 14.sp)
                    }
                }
            }
        }

        // Reaction pills & quick react row
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            message.reactions.forEach { emoji ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable { onReact(emoji) }
                ) {
                    Text(
                        text = emoji,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            quickEmojis.filterNot { message.reactions.contains(it) }.take(2).forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onReact(emoji) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
