package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BibleSection
import com.example.data.FavoriteVerse
import com.example.data.GeminiClient
import com.example.data.GroupChatMessage
import com.example.data.NotificationHelper
import com.example.data.Outing
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.data.RecentChapter
import com.example.data.Repository
import com.example.data.RsvpChoice
import com.example.data.SocialGroup
import com.example.data.SocialRepository
import com.example.data.SoundEffectsHelper
import com.example.data.StudyEvent
import com.example.data.UserProfile
import com.example.data.VerseNote
import com.example.data.todayIsoDate
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class ChatMessage(
    val sender: String, // "user", "ai", "saulo"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TriviaQuestion(
    val id: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

enum class AppWorld {
    WORLD_1_SOCIAL,
    WORLD_2_BIBLE
}

class MainViewModel(
    private val repository: Repository,
    private val socialRepository: SocialRepository? = repository.firestore?.let { SocialRepository(it) }
) : ViewModel() {

    private fun dateOffsetIso(daysOffset: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysOffset) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    // --- Two Worlds Switcher State ---
    val currentWorld = MutableStateFlow(AppWorld.WORLD_1_SOCIAL)
    val world1Tab = MutableStateFlow("profile") // "profile", "outings", "map", "chat", "games"
    val world2ActiveTab = MutableStateFlow("library")
    val showSettingsModal = MutableStateFlow(false)
    val showSocialAgentModal = MutableStateFlow(false)
    val showCameraStoriesModal = MutableStateFlow(false)
    val showOutingHistoryModal = MutableStateFlow(false)
    val defaultCurrency = MutableStateFlow("€")
    val notificationsEnabled = MutableStateFlow(true)
    val soundEffectsEnabled = MutableStateFlow(true)

    // --- Theme State (Persisted via DataStore) ---
    val themeMode = MutableStateFlow(ThemeMode.SOCIAL_CLARO)

    // --- Auth & Cloud Session State ---
    val currentUserId = MutableStateFlow<String?>(null)
    val isUserLoggedIn = MutableStateFlow(false)
    val userEmail = MutableStateFlow("")
    val authStatusMessage = MutableStateFlow<String?>(null)
    val activeSocialGroup = MutableStateFlow<SocialGroup?>(null)

    private var profileJob: Job? = null
    private var groupJob: Job? = null
    private var outingsJob: Job? = null
    private var chatJob: Job? = null
    private var storiesJob: Job? = null
    private var observedGroupId: String? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        currentUserId.value = user?.uid
        isUserLoggedIn.value = user != null
        userEmail.value = user?.email ?: ""
        if (user != null) {
            userProfile.value = userProfile.value.copy(
                userId = user.uid,
                username = user.displayName?.takeIf { it.isNotBlank() } ?: userProfile.value.username
            )
            startCloudObservers(user.uid)
        } else {
            stopCloudObservers()
            activeSocialGroup.value = null
            observedGroupId = null
        }
    }

    // --- World 1: User Profile, Tastes/Curiosities & Group ---
    private val defaultGuestProfile = UserProfile(
        userId = "local_user",
        username = "alex_salidas",
        bio = "Organizando planes, rutas y encuentros con el grupo.",
        avatarUrl = "avatar_obsidian",
        curiosities = listOf(
            "Cocina artesanal",
            "Senderismo de montaña",
            "Bolera y ocio nocturno",
            "Fotografía urbana",
            "Juegos de mesa"
        ),
        groupId = "QUEDA-2026",
        groupInviteCode = "QUEDA-2026",
        groupName = "Los Inseparables",
        gamesHighScore = 340L
    )

    val userProfile = MutableStateFlow(defaultGuestProfile)

    private val defaultGuestMembers = listOf(
        "Alex (Tú)",
        "Sofía",
        "Mateo",
        "Valeria",
        "Lucas",
        "Elena"
    )
    val groupMembers = MutableStateFlow(defaultGuestMembers)

    // --- World 1: Outings (Salidas y Quedadas) with dynamic dates relative to today ---
    private val defaultOutings = listOf(
        Outing(
            id = "outing_upcoming_confirmed",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Cena & Noche de Bolera",
            location = "Ozone Bowling & Burger Plaza",
            meetingPoint = "Fuente de la Plaza Mayor (Entrada Norte)",
            dateIso = dateOffsetIso(2),
            time = "20:30",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "18 €",
            kitItems = "Calcetines para bolera, carnet joven y batería portátil",
            additionalInfo = "Reserva confirmada para la pista 4. Se recomienda llegar 10 minutos antes.",
            placePhotoUrl = "preset_bowling",
            destLat = 40.4168,
            destLng = -3.7038,
            meetLat = 40.4155,
            meetLng = -3.7074,
            rsvpYesCount = 5L,
            rsvpMaybeCount = 1L,
            rsvpNoCount = 0L,
            myRsvp = RsvpChoice.YES.name,
            albumPhotos = listOf("memory_bowling_1", "memory_burger_2", "memory_selfie_3")
        ),
        Outing(
            id = "outing_upcoming_planned",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Ruta de Senderismo y Picnic",
            location = "Mirador del Valle Verde",
            meetingPoint = "Estación Central - Andén 2",
            dateIso = dateOffsetIso(6),
            time = "09:30",
            status = OutingStatus.PLANNED.name,
            budgetPerPerson = "12 €",
            kitItems = "Mochila, 1.5L de agua, gorra, bocadillo y manta de picnic",
            additionalInfo = "Pendiente de confirmar según la previsión meteorológica del jueves.",
            placePhotoUrl = "preset_mountain",
            destLat = 40.4240,
            destLng = -3.6880,
            meetLat = 40.4205,
            meetLng = -3.6930,
            rsvpYesCount = 3L,
            rsvpMaybeCount = 2L,
            rsvpNoCount = 0L,
            myRsvp = RsvpChoice.MAYBE.name,
            albumPhotos = listOf("memory_hiking_1")
        ),
        Outing(
            id = "outing_upcoming_cancelled",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Cine al Aire Libre",
            location = "Terraza Parque del Sur",
            meetingPoint = "Puerta de Alcalá",
            dateIso = dateOffsetIso(11),
            time = "21:00",
            status = OutingStatus.CANCELLED.name,
            budgetPerPerson = "10 €",
            kitItems = "Chaqueta abrigada",
            additionalInfo = "Cancelada por aviso de lluvia intensa. Se reprogramará el próximo mes.",
            placePhotoUrl = "preset_cinema",
            destLat = 40.4110,
            destLng = -3.6980,
            meetLat = 40.4199,
            meetLng = -3.6887,
            rsvpYesCount = 1L,
            rsvpMaybeCount = 0L,
            rsvpNoCount = 4L,
            myRsvp = RsvpChoice.NO.name,
            albumPhotos = emptyList()
        ),
        Outing(
            id = "outing_upcoming_escape",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Tarde de Escape Room",
            location = "Enigma Escape Center",
            meetingPoint = "Cafetería Sol (Esquina Reloj)",
            dateIso = dateOffsetIso(16),
            time = "18:00",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "22 €",
            kitItems = "Ropa cómoda",
            additionalInfo = "Nivel difícil: El Misterio del Faraón (60 minutos).",
            placePhotoUrl = "preset_escape",
            destLat = 40.4215,
            destLng = -3.7055,
            meetLat = 40.4170,
            meetLng = -3.7035,
            rsvpYesCount = 6L,
            rsvpMaybeCount = 0L,
            rsvpNoCount = 0L,
            myRsvp = RsvpChoice.YES.name,
            albumPhotos = listOf("memory_escape_1", "memory_icecream_2")
        ),
        Outing(
            id = "outing_history_past",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Encuentro en el Lago",
            location = "Lago Azul Club",
            meetingPoint = "Parking Norte",
            dateIso = dateOffsetIso(-45),
            time = "13:00",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "25 €",
            kitItems = "Toalla y protector solar",
            additionalInfo = "Jornada completa de verano con todo el grupo.",
            placePhotoUrl = "preset_beach",
            destLat = 40.4085,
            destLng = -3.7120,
            meetLat = 40.4100,
            meetLng = -3.7100,
            rsvpYesCount = 6L,
            rsvpMaybeCount = 0L,
            rsvpNoCount = 0L,
            myRsvp = RsvpChoice.YES.name,
            albumPhotos = listOf("memory_bbq_1", "memory_lake_2")
        )
    )

    val outings = MutableStateFlow<List<Outing>>(defaultOutings)
    val selectedOutingForDetail = MutableStateFlow<Outing?>(null)

    // --- World 1: Group Social Chat Messages (Guest Mode Demo) ---
    private val defaultGroupChat = listOf(
        GroupChatMessage(
            id = "msg_1",
            senderId = "sofia_1",
            senderName = "Sofía",
            groupId = "QUEDA-2026",
            text = "Ya dejé confirmada la Cena y Noche de Bolera en el calendario del grupo.",
            messageType = "TEXT",
            reactions = listOf("👍")
        ),
        GroupChatMessage(
            id = "msg_2",
            senderId = "mateo_2",
            senderName = "Mateo",
            groupId = "QUEDA-2026",
            text = "Nota de voz (0:08)",
            messageType = "VOICE",
            audioDurationSec = 8L,
            transcription = "Perfecto, yo llevo batería portátil y nos vemos en el punto de encuentro a las 20:30.",
            reactions = listOf("👍")
        ),
        GroupChatMessage(
            id = "msg_3",
            senderId = "valeria_3",
            senderName = "Valeria",
            groupId = "QUEDA-2026",
            text = "He compartido la ficha actualizada de nuestra próxima salida.",
            messageType = "OUTING_SHARE",
            sharedOutingId = "outing_upcoming_confirmed",
            sharedOutingTitle = "Cena & Noche de Bolera (${dateOffsetIso(2)} • 18 €)",
            reactions = emptyList()
        )
    )
    val groupChatMessages = MutableStateFlow<List<GroupChatMessage>>(defaultGroupChat)

    // --- World 1: Instantáneas (Stories) (Guest Mode Demo) ---
    private val defaultStories = listOf(
        OutingStory(
            id = "story_1",
            authorId = "valeria_3",
            authorName = "Valeria",
            groupId = "QUEDA-2026",
            caption = "Preparando la noche de bolera con el equipo.",
            filterStyle = "OBSIDIAN",
            mediaUri = "story_bowling_neon",
            outingTitle = "Cena & Noche de Bolera",
            likesCount = 14L
        ),
        OutingStory(
            id = "story_2",
            authorId = "sofia_1",
            authorName = "Sofía",
            groupId = "QUEDA-2026",
            caption = "Vistas del Mirador para la ruta del sábado.",
            filterStyle = "WARM_GOLD",
            mediaUri = "story_mountain_sunset",
            outingTitle = "Ruta de Senderismo y Picnic",
            likesCount = 9L
        ),
        OutingStory(
            id = "story_3",
            authorId = "mateo_2",
            authorName = "Mateo",
            groupId = "QUEDA-2026",
            caption = "Listos para el Escape Room del fin de semana.",
            filterStyle = "FOREST",
            mediaUri = "story_pizza_party",
            outingTitle = "Tarde de Escape Room",
            likesCount = 19L
        )
    )
    val stories = MutableStateFlow<List<OutingStory>>(defaultStories)

    // --- World 1: Agentic AI Assistant Chat ---
    val socialAgentMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                "ai",
                "Soy tu asistente de Adora. Puedo crear salidas en el calendario y mapa, actualizar su estado, confirmar tu asistencia o preparar el kit del grupo. ¿Qué necesitas organizar?"
            )
        )
    )
    val isSocialAgentBusy = MutableStateFlow(false)

    // --- World 2: Bible & Spiritual Study State ---
    val searchTerm = MutableStateFlow("")
    val activeCategory = MutableStateFlow("Todos")

    val favoriteVerses: StateFlow<List<FavoriteVerse>> = repository.allFavoriteVerses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChapters: StateFlow<List<RecentChapter>> = repository.recentChapters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studyEvents: StateFlow<List<StudyEvent>> = repository.studyEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sectionsState: StateFlow<List<BibleSection>> = combine(
        repository.allSections,
        searchTerm,
        activeCategory
    ) { sections, query, cat ->
        sections.filter { section ->
            val matchesCategory = cat == "Todos" || section.category.equals(cat, ignoreCase = true)
            val matchesSearch = query.isEmpty() ||
                section.title.contains(query, ignoreCase = true) ||
                section.content.contains(query, ignoreCase = true) ||
                section.contentNTV.contains(query, ignoreCase = true) ||
                section.contentNVI.contains(query, ignoreCase = true) ||
                section.contentTLA.contains(query, ignoreCase = true) ||
                section.passageReference.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSectionsUnfiltered: StateFlow<List<BibleSection>> = repository.allSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedSection = MutableStateFlow<BibleSection?>(null)
    val textSizeSp = MutableStateFlow(18f)
    val textPaddingDp = MutableStateFlow(16f)
    val studyPoints = MutableStateFlow(repository.getStudyPoints())
    val streakDays = MutableStateFlow(repository.getStreakDays())
    val unlockedAchievements = MutableStateFlow<List<String>>(emptyList())
    val analyticsEnabled = MutableStateFlow(repository.isAnalyticsEnabled())
    val bibleVersion = MutableStateFlow("RVR1960")

    val chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                "saulo",
                "Bienvenido al Mundo Bíblico de estudio y reflexión espiritual. Pregúntame sobre cualquier pasaje, pídemelo en el lector o agenda una sesión de estudio."
            )
        )
    )
    val isGeneratingResponse = MutableStateFlow(false)

    private val triviaPool = listOf(
        TriviaQuestion(
            id = 1,
            question = "¿Quién fue el primer hombre creado según el libro de Génesis?",
            options = listOf("Abraham", "Adán", "Noé"),
            correctIndex = 1,
            explanation = "¡Exacto! Según Génesis 2:7, Dios formó a Adán del polvo de la tierra."
        ),
        TriviaQuestion(
            id = 2,
            question = "¿Qué turno sacerdotal pertenecía a Hupa según la división de suertes en 1 Crónicas 24?",
            options = listOf("El décimo turno", "El decimatercer turno", "El decimoquinto turno"),
            correctIndex = 1,
            explanation = "¡Exacto! Según 1 Crónicas 24:13, la decimatercera suerte tocó a Hupa, y la decimacuarta a Jesebeab."
        ),
        TriviaQuestion(
            id = 3,
            question = "¿De qué metal estaba recubierta la rejilla del altar de las ofrendas del Tabernáculo?",
            options = listOf("Oro de Ofir", "Plata purificada", "Bronce sacrificado"),
            correctIndex = 2,
            explanation = "¡Correcto! En Éxodo 35, se detalla que la rejilla del altar de las ofrendas quemadas estaba elaborada en bronce."
        ),
        TriviaQuestion(
            id = 4,
            question = "En cuanto a la Ley y el celo religioso pre-conversión, ¿cómo se describía a sí mismo Saulo de Tarso?",
            options = listOf("Fariseo de celo intachable y perseguidor", "Un líder saduceo conciliador", "Escriba humilde del desierto"),
            correctIndex = 0,
            explanation = "¡Preciso! En Filipenses 3, declara: «en cuanto al celo, perseguidor de la iglesia; en cuanto a la justicia que la Ley exige, intachable»."
        ),
        TriviaQuestion(
            id = 5,
            question = "¿Cuál es el último libro de la Biblia?",
            options = listOf("Apocalipsis", "Judas", "Malaquías"),
            correctIndex = 0,
            explanation = "¡Correcto! Apocalipsis es el último libro y trata sobre las revelaciones proféticas."
        ),
        TriviaQuestion(
            id = 6,
            question = "¿En qué camino tuvo Saulo de Tarso su encuentro transformador con Jesús?",
            options = listOf("Camino a Jericó", "Camino a Damasco", "Camino a Emaús"),
            correctIndex = 1,
            explanation = "¡Exacto! En Hechos 9, Saulo iba camino a Damasco cuando una luz del cielo lo rodeó."
        ),
        TriviaQuestion(
            id = 7,
            question = "¿Qué salmo comienza con «Jehová es mi pastor; nada me faltará»?",
            options = listOf("Salmo 23", "Salmo 91", "Salmo 119"),
            correctIndex = 0,
            explanation = "¡Así es! El Salmo 23 de David es uno de los cantos de confianza más conocidos de las Escrituras."
        )
    )

    val currentTrivia = MutableStateFlow(triviaPool[0])
    val triviaAnsweredStatus = MutableStateFlow<Int?>(null)

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(GeminiClient.MODEL_NAME)
    }

    var chapterExplanation = MutableStateFlow<String?>(null)
    var isGeneratingExplanation = MutableStateFlow(false)
    var rawChapterExplanation = MutableStateFlow<String?>(null)
    var isGeneratingRawExplanation = MutableStateFlow(false)

    init {
        updateAchievements()
        // Restore persisted user preferences from DataStore
        viewModelScope.launch {
            repository.preferences.settingsFlow
                .catch { e -> Log.w("MainViewModel", "Preferences flow error: ${e.message}") }
                .collect { prefs ->
                    themeMode.value = prefs.themeMode
                    defaultCurrency.value = prefs.defaultCurrency
                    notificationsEnabled.value = prefs.notificationsEnabled
                    soundEffectsEnabled.value = prefs.soundEffectsEnabled
                    textSizeSp.value = prefs.textSizeSp
                    textPaddingDp.value = prefs.textPaddingDp
                    bibleVersion.value = prefs.bibleVersion
                    world2ActiveTab.value = prefs.world2ActiveTab
                }
        }
        try {
            if (repository.isFirebaseAvailable) {
                FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "FirebaseAuth listener registration skipped: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (repository.isFirebaseAvailable) {
                FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
            }
        } catch (_: Exception) {
        }
        stopCloudObservers()
    }

    fun playFeedbackSound(success: Boolean = true) {
        if (success) {
            SoundEffectsHelper.playSuccess(soundEffectsEnabled.value)
        } else {
            SoundEffectsHelper.playTap(soundEffectsEnabled.value)
        }
    }

    // --- Cloud Realtime Observers (Multi-User Group Scoped) ---
    private fun startCloudObservers(uid: String) {
        val repo = socialRepository ?: return
        stopCloudObservers()

        profileJob = viewModelScope.launch {
            repo.observeUserProfile(uid)
                .catch { e ->
                    Log.w("MainViewModel", "Profile observer error: ${e.message}")
                    authStatusMessage.value = "Error al leer perfil en la nube: ${e.localizedMessage ?: "sin conexión"}"
                }
                .collect { remoteProfile ->
                    if (remoteProfile != null) {
                        userProfile.value = remoteProfile
                        val targetGroup = remoteProfile.groupId.ifBlank { remoteProfile.groupInviteCode.ifBlank { "QUEDA-2026" } }
                        if (observedGroupId != targetGroup) {
                            startGroupScopedObservers(uid, targetGroup, remoteProfile)
                        }
                    } else {
                        val initialProfile = userProfile.value.copy(userId = uid)
                        repo.saveUserProfile(initialProfile, uid).onFailure { err ->
                            authStatusMessage.value = "No se pudo guardar tu perfil: ${err.localizedMessage}"
                        }
                        startGroupScopedObservers(uid, initialProfile.groupId, initialProfile)
                    }
                }
        }
    }

    private fun startGroupScopedObservers(uid: String, groupId: String, profile: UserProfile) {
        val repo = socialRepository ?: return
        observedGroupId = groupId
        groupJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()

        // Ensure user is registered in /groups/{groupId} so Firestore rules grant shared group access
        viewModelScope.launch {
            repo.createOrEnsureGroup(
                groupId = groupId,
                groupName = profile.groupName,
                creatorUid = uid,
                creatorName = profile.username
            ).onFailure { err ->
                Log.w("MainViewModel", "Could not ensure group doc: ${err.message}")
            }
        }

        groupJob = viewModelScope.launch {
            repo.observeGroup(groupId)
                .catch { e -> Log.w("MainViewModel", "Group observer error: ${e.message}") }
                .collect { remoteGroup ->
                    activeSocialGroup.value = remoteGroup
                    if (remoteGroup != null) {
                        val realMembers = remoteGroup.memberIds.map { memberUid ->
                            val name = remoteGroup.memberNames[memberUid]?.ifBlank { "Miembro" } ?: "Miembro"
                            if (memberUid == uid) "$name (Tú) 👑" else name
                        }.ifEmpty { listOf("${profile.username} (Tú) 👑") }
                        groupMembers.value = realMembers
                        if (remoteGroup.name.isNotBlank() && userProfile.value.groupName != remoteGroup.name) {
                            userProfile.value = userProfile.value.copy(groupName = remoteGroup.name)
                        }
                    }
                }
        }

        outingsJob = viewModelScope.launch {
            repo.observeGroupOutings(groupId)
                .catch { e ->
                    Log.w("MainViewModel", "Group outings observer error: ${e.message}")
                    // Fallback to owner-scoped query if group doc is still propagating
                    repo.observeOutings(uid)
                        .catch { inner -> Log.w("MainViewModel", "Owner outings fallback error: ${inner.message}") }
                        .collect { fallbackList -> outings.value = fallbackList }
                }
                .collect { remoteOutings ->
                    if (remoteOutings.isNotEmpty()) {
                        outings.value = remoteOutings
                    } else {
                        val alreadySeeded = repository.preferences.hasSeededCloudForUser(uid)
                        if (!alreadySeeded) {
                            repository.preferences.markCloudSeededForUser(uid)
                            defaultOutings.forEach { seed ->
                                repo.createOuting(
                                    seed.copy(ownerId = uid, groupId = groupId),
                                    uid
                                )
                            }
                        } else {
                            outings.value = emptyList()
                        }
                    }
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeGroupChatMessages(groupId)
                .catch { e -> Log.w("MainViewModel", "Chat observer error: ${e.message}") }
                .collect { remoteMessages ->
                    groupChatMessages.value = remoteMessages
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeGroupStories(groupId)
                .catch { e -> Log.w("MainViewModel", "Stories observer error: ${e.message}") }
                .collect { remoteStories ->
                    stories.value = remoteStories
                }
        }
    }

    private fun stopCloudObservers() {
        profileJob?.cancel()
        groupJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        profileJob = null
        groupJob = null
        outingsJob = null
        chatJob = null
        storiesJob = null
    }

    // --- World Switching & Navigation ---
    fun switchToWorld2Bible() {
        currentWorld.value = AppWorld.WORLD_2_BIBLE
    }

    fun switchToWorld1Social() {
        currentWorld.value = AppWorld.WORLD_1_SOCIAL
        if (themeMode.value != ThemeMode.SOCIAL_CLARO && themeMode.value != ThemeMode.SOCIAL_OSCURO) {
            updateThemeMode(ThemeMode.SOCIAL_CLARO)
        }
    }

    fun setWorld2ActiveTab(tab: String) {
        world2ActiveTab.value = tab
        viewModelScope.launch { repository.preferences.setWorld2ActiveTab(tab) }
    }

    fun toggleDarkLightSocialTheme() {
        val next = if (themeMode.value == ThemeMode.SOCIAL_OSCURO) {
            ThemeMode.SOCIAL_CLARO
        } else {
            ThemeMode.SOCIAL_OSCURO
        }
        updateThemeMode(next)
    }

    fun updateDefaultCurrency(currency: String) {
        defaultCurrency.value = currency
        viewModelScope.launch { repository.preferences.setDefaultCurrency(currency) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
        viewModelScope.launch { repository.preferences.setNotificationsEnabled(enabled) }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        soundEffectsEnabled.value = enabled
        viewModelScope.launch { repository.preferences.setSoundEffectsEnabled(enabled) }
    }

    // --- World 1: Profile, Curiosities & Group Actions ---
    fun updateProfileInfo(newUsername: String, newBio: String, newAvatar: String) {
        val updated = userProfile.value.copy(
            username = newUsername.ifBlank { userProfile.value.username },
            bio = newBio,
            avatarUrl = newAvatar
        )
        userProfile.value = updated
        persistProfileIfAuthenticated(updated)
    }

    fun addCuriosity(item: String) {
        val trimmed = item.trim()
        if (trimmed.isBlank()) return
        val current = userProfile.value.curiosities.toMutableList()
        if (!current.contains(trimmed) && current.size < 20) {
            current.add(0, trimmed)
            val updated = userProfile.value.copy(curiosities = current)
            userProfile.value = updated
            persistProfileIfAuthenticated(updated)
        }
    }

    fun removeCuriosity(item: String) {
        val current = userProfile.value.curiosities.toMutableList()
        current.remove(item)
        val updated = userProfile.value.copy(curiosities = current)
        userProfile.value = updated
        persistProfileIfAuthenticated(updated)
    }

    fun createNewOutingGroup(groupNameInput: String) {
        val cleanName = groupNameInput.trim().ifBlank { "Grupo de Salidas" }
        val code = "QUEDA-" + (1000..9999).random()
        val updated = userProfile.value.copy(
            groupName = cleanName,
            groupInviteCode = code,
            groupId = code
        )
        userProfile.value = updated

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.createOrEnsureGroup(code, cleanName, uid, updated.username)
                    .onSuccess {
                        repo.saveUserProfile(updated, uid)
                        startGroupScopedObservers(uid, code, updated)
                        authStatusMessage.value = "Grupo '$cleanName' creado en la nube con código $code"
                    }
                    .onFailure { err ->
                        authStatusMessage.value = "Error al crear grupo en la nube: ${err.localizedMessage}"
                    }
            }
        } else {
            groupMembers.value = listOf("${updated.username} (Tú) 👑")
            authStatusMessage.value = "Grupo '$cleanName' creado con código $code (modo local)"
        }
    }

    fun joinOutingGroupByCode(inviteCodeInput: String, groupNameHint: String = "") {
        val cleanCode = inviteCodeInput.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
        if (cleanCode.isBlank()) {
            authStatusMessage.value = "Introduce un código de invitación válido"
            return
        }
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.joinGroupByCode(
                    inviteCode = cleanCode,
                    userId = uid,
                    displayName = userProfile.value.username,
                    fallbackName = groupNameHint.trim()
                ).onSuccess { joinedGroup ->
                    val updated = userProfile.value.copy(
                        groupId = joinedGroup.id,
                        groupInviteCode = joinedGroup.inviteCode,
                        groupName = joinedGroup.name
                    )
                    userProfile.value = updated
                    repo.saveUserProfile(updated, uid)
                    startGroupScopedObservers(uid, joinedGroup.id, updated)
                    authStatusMessage.value = "¡Te has unido al grupo '${joinedGroup.name}' ($cleanCode)!"
                }.onFailure { err ->
                    authStatusMessage.value = "No se pudo unir al grupo $cleanCode: ${err.localizedMessage}"
                }
            }
        } else {
            val displayGroup = groupNameHint.trim().ifBlank { "Grupo $cleanCode" }
            val updated = userProfile.value.copy(
                groupId = cleanCode,
                groupInviteCode = cleanCode,
                groupName = displayGroup
            )
            userProfile.value = updated
            if (!groupMembers.value.any { it.contains(cleanCode) }) {
                groupMembers.value = groupMembers.value + "Miembro ($cleanCode) 🎉"
            }
            authStatusMessage.value = "¡Te has unido al grupo con el código $cleanCode (modo local)!"
        }
    }

    fun addMiniGameScore(points: Int) {
        if (points <= 0) return
        val newScore = userProfile.value.gamesHighScore + points
        val updated = userProfile.value.copy(gamesHighScore = newScore)
        userProfile.value = updated
        playFeedbackSound(true)
        persistProfileIfAuthenticated(updated)
    }

    private fun persistProfileIfAuthenticated(profile: UserProfile) {
        val uid = currentUserId.value ?: return
        val repo = socialRepository ?: return
        viewModelScope.launch {
            repo.saveUserProfile(profile, uid).onFailure { err ->
                authStatusMessage.value = "Error al guardar perfil: ${err.localizedMessage}"
            }
        }
    }

    // --- World 1: Outing CRUD, Calendar & Map Actions ---
    fun saveOrUpdateOuting(outing: Outing) {
        val existingList = outings.value.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == outing.id && outing.id.isNotBlank() }
        val activeGroupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode }
        val finalOuting = if (outing.id.isBlank()) {
            outing.copy(
                id = "outing_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                ownerId = currentUserId.value ?: "local_user",
                groupId = activeGroupId
            )
        } else {
            outing.copy(groupId = outing.groupId.ifBlank { activeGroupId })
        }

        val isNew = existingIdx < 0
        if (existingIdx >= 0) {
            existingList[existingIdx] = finalOuting
        } else {
            existingList.add(finalOuting)
        }
        outings.value = existingList.sortedBy { it.dateIso + " " + it.time }

        if (isNew || finalOuting.outingStatus == OutingStatus.CONFIRMED) {
            NotificationHelper.showOutingNotification(
                context = repository.appContext,
                enabled = notificationsEnabled.value,
                title = if (isNew) "Nueva salida: ${finalOuting.title}" else "Salida actualizada: ${finalOuting.title}",
                body = "${finalOuting.dateIso} a las ${finalOuting.time} • ${finalOuting.location}"
            )
        }

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.upsertOuting(finalOuting, uid).onFailure { err ->
                    authStatusMessage.value = "No se pudo sincronizar '${finalOuting.title}': ${err.localizedMessage}"
                }
            }
        }
    }

    fun updateOutingStatusColor(outingId: String, newStatus: OutingStatus) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        saveOrUpdateOuting(target.copy(status = newStatus.name))
    }

    fun updateOutingRsvp(outingId: String, choice: RsvpChoice) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        val uid = currentUserId.value ?: "local_user"

        val updatedMap = target.rsvpByUser.toMutableMap()
        if (choice == RsvpChoice.NONE) {
            updatedMap.remove(uid)
        } else {
            updatedMap[uid] = choice.name
        }

        val updated: Outing = if (target.rsvpByUser.isNotEmpty() || currentUserId.value != null) {
            val yes = updatedMap.values.count { it == RsvpChoice.YES.name }.toLong()
            val maybe = updatedMap.values.count { it == RsvpChoice.MAYBE.name }.toLong()
            val no = updatedMap.values.count { it == RsvpChoice.NO.name }.toLong()
            target.copy(
                myRsvp = choice.name,
                rsvpByUser = updatedMap,
                rsvpYesCount = yes,
                rsvpMaybeCount = maybe,
                rsvpNoCount = no
            )
        } else {
            val prevChoice = target.rsvpChoice
            var yes = target.rsvpYesCount
            var maybe = target.rsvpMaybeCount
            var no = target.rsvpNoCount
            if (prevChoice == RsvpChoice.YES && yes > 0) yes--
            if (prevChoice == RsvpChoice.MAYBE && maybe > 0) maybe--
            if (prevChoice == RsvpChoice.NO && no > 0) no--
            when (choice) {
                RsvpChoice.YES -> yes++
                RsvpChoice.MAYBE -> maybe++
                RsvpChoice.NO -> no++
                RsvpChoice.NONE -> {}
            }
            target.copy(
                myRsvp = choice.name,
                rsvpByUser = updatedMap,
                rsvpYesCount = yes,
                rsvpMaybeCount = maybe,
                rsvpNoCount = no
            )
        }

        val currentList = outings.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == outingId }
        if (idx >= 0) {
            currentList[idx] = updated
            outings.value = currentList
        }
        if (selectedOutingForDetail.value?.id == outingId) {
            selectedOutingForDetail.value = updated
        }
        playFeedbackSound(false)

        val authUid = currentUserId.value
        val repo = socialRepository
        if (authUid != null && repo != null) {
            viewModelScope.launch {
                repo.updateOutingRsvpTransactional(outingId, authUid, choice).onFailure { err ->
                    authStatusMessage.value = "Error al guardar tu asistencia: ${err.localizedMessage}"
                }
            }
        }
    }

    fun updateOutingCoordinates(
        outingId: String,
        isDestination: Boolean,
        lat: Double,
        lng: Double,
        placeLabel: String? = null
    ) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        val updated = if (isDestination) {
            target.copy(
                destLat = lat,
                destLng = lng,
                location = placeLabel?.takeIf { it.isNotBlank() } ?: target.location
            )
        } else {
            target.copy(
                meetLat = lat,
                meetLng = lng,
                meetingPoint = placeLabel?.takeIf { it.isNotBlank() } ?: target.meetingPoint
            )
        }
        saveOrUpdateOuting(updated)
    }

    fun addPhotoToOutingAlbum(outingId: String, photoUriOrPreset: String) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: outings.value.firstOrNull() ?: return
        val updatedPhotos = (listOf(photoUriOrPreset) + target.albumPhotos).take(20)
        saveOrUpdateOuting(target.copy(albumPhotos = updatedPhotos))
    }

    fun deleteOuting(outingId: String) {
        outings.value = outings.value.filterNot { it.id == outingId }
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.deleteOuting(outingId).onFailure { err ->
                    authStatusMessage.value = "Error al eliminar la salida: ${err.localizedMessage}"
                }
            }
        }
    }

    // --- World 1: Group Chat & Voice Notes ---
    fun sendGroupTextMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: "local_user",
            senderName = userProfile.value.username,
            groupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode },
            text = clean,
            messageType = "TEXT"
        )
        groupChatMessages.value = groupChatMessages.value + msg
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid).onFailure { err ->
                    authStatusMessage.value = "Error al enviar mensaje: ${err.localizedMessage}"
                }
            }
        }
    }

    fun sendGroupVoiceMessage(
        durationSec: Int,
        transcriptionText: String,
        audioUrl: String = ""
    ) {
        val cleanDur = durationSec.coerceAtLeast(1)
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: "local_user",
            senderName = userProfile.value.username,
            groupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode },
            text = "Nota de voz (0:${cleanDur.toString().padStart(2, '0')})",
            messageType = "VOICE",
            audioDurationSec = cleanDur.toLong(),
            audioUrl = audioUrl,
            transcription = transcriptionText.trim()
        )
        groupChatMessages.value = groupChatMessages.value + msg
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid).onFailure { err ->
                    authStatusMessage.value = "Error al enviar nota de voz: ${err.localizedMessage}"
                }
            }
        }
    }

    fun shareOutingToGroupChat(outing: Outing) {
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: "local_user",
            senderName = userProfile.value.username,
            groupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode },
            text = "📌 ¡He compartido la salida '${outing.title}'! ¿Quién se apunta?",
            messageType = "OUTING_SHARE",
            sharedOutingId = outing.id,
            sharedOutingTitle = "${outing.title} (${outing.dateIso} • ${outing.time} • ${outing.budgetPerPerson})"
        )
        groupChatMessages.value = groupChatMessages.value + msg
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid).onFailure { err ->
                    authStatusMessage.value = "Error al compartir salida en el chat: ${err.localizedMessage}"
                }
            }
        }
    }

    fun toggleReactionOnChatMessage(messageId: String, emoji: String) {
        val currentList = groupChatMessages.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == messageId }
        if (idx == -1) return
        val msg = currentList[idx]
        val newReactions = if (msg.reactions.contains(emoji)) {
            msg.reactions - emoji
        } else {
            (msg.reactions + emoji).take(10)
        }
        currentList[idx] = msg.copy(reactions = newReactions)
        groupChatMessages.value = currentList

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.updateMessageReactions(messageId, newReactions).onFailure { err ->
                    authStatusMessage.value = "Error al actualizar reacción: ${err.localizedMessage}"
                }
            }
        }
    }

    // --- World 1: Instantáneas (Stories) ---
    fun publishInstantaneaStory(
        caption: String,
        filterStyle: String,
        mediaUri: String,
        outingTitle: String
    ) {
        val story = OutingStory(
            id = "story_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            authorId = currentUserId.value ?: "local_user",
            authorName = userProfile.value.username,
            groupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode },
            caption = caption.ifBlank { "¡Momento épico con el grupo! ✨" },
            filterStyle = filterStyle,
            mediaUri = mediaUri,
            outingTitle = outingTitle,
            likesCount = 1L
        )
        stories.value = listOf(story) + stories.value
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.createStory(story, uid).onFailure { err ->
                    authStatusMessage.value = "Error al publicar instantánea: ${err.localizedMessage}"
                }
            }
        }
    }

    fun likeInstantaneaStory(storyId: String) {
        val current = stories.value.toMutableList()
        val idx = current.indexOfFirst { it.id == storyId }
        if (idx == -1) return
        val target = current[idx]
        val newCount = target.likesCount + 1L
        current[idx] = target.copy(likesCount = newCount)
        stories.value = current

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.likeStory(storyId, newCount).onFailure { err ->
                    authStatusMessage.value = "Error al dar me gusta: ${err.localizedMessage}"
                }
            }
        }
    }

    // --- World 1: Agentic AI Assistant Command Execution ---
    fun sendSocialAgentPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || isSocialAgentBusy.value) return

        socialAgentMessages.value = socialAgentMessages.value + ChatMessage("user", clean)
        isSocialAgentBusy.value = true

        viewModelScope.launch {
            try {
                val summary = outings.value.joinToString("\n") {
                    "- ${it.title} (${it.dateIso} ${it.time}) en ${it.location} | Estado: ${it.status} | Llevar: ${it.budgetPerPerson} | Kit: ${it.kitItems}"
                }
                val actionResp = GeminiClient.runSocialAgentCommand(clean, summary)
                var executionBanner = ""

                when (actionResp.actionType.uppercase()) {
                    "CREATE_OUTING" -> {
                        val newOuting = Outing(
                            title = actionResp.title.ifBlank { "Salida creada con IA" },
                            location = actionResp.location.ifBlank { "Centro" },
                            meetingPoint = actionResp.meetingPoint.ifBlank { "Punto de encuentro principal" },
                            dateIso = actionResp.dateIso.ifBlank { todayIsoDate() },
                            time = actionResp.time.ifBlank { "20:00" },
                            status = actionResp.status.ifBlank { OutingStatus.CONFIRMED.name },
                            budgetPerPerson = actionResp.budgetPerPerson.ifBlank { "15 ${defaultCurrency.value}" },
                            kitItems = actionResp.kitItems,
                            additionalInfo = actionResp.additionalInfo
                        )
                        saveOrUpdateOuting(newOuting)
                        executionBanner = "\n\n✅ [Acción ejecutada: Salida '${newOuting.title}' añadida al calendario (${newOuting.dateIso}) y al mapa con estado ${newOuting.outingStatus.label}]"
                    }
                    "UPDATE_STATUS" -> {
                        val target = findOutingByQuery(actionResp.targetTitleQuery)
                        if (target != null) {
                            val newSt = OutingStatus.fromString(actionResp.status)
                            updateOutingStatusColor(target.id, newSt)
                            executionBanner = "\n\n✅ [Acción ejecutada: Estado de '${target.title}' cambiado a ${newSt.label}]"
                        } else {
                            executionBanner = "\n\n⚠️ [No encontré una salida que coincida con '${actionResp.targetTitleQuery}'. Indica el nombre exacto.]"
                        }
                    }
                    "RSVP" -> {
                        val target = findOutingByQuery(actionResp.targetTitleQuery)
                        if (target != null) {
                            val rsvp = RsvpChoice.fromString(actionResp.rsvpChoice)
                            updateOutingRsvp(target.id, rsvp)
                            executionBanner = "\n\n✅ [Acción ejecutada: Asistencia marcada como '${rsvp.label}' en '${target.title}']"
                        } else {
                            executionBanner = "\n\n⚠️ [No encontré una salida que coincida con '${actionResp.targetTitleQuery}'.]"
                        }
                    }
                    "UPDATE_KIT" -> {
                        val target = findOutingByQuery(actionResp.targetTitleQuery)
                        if (target != null && actionResp.kitItems.isNotBlank()) {
                            saveOrUpdateOuting(target.copy(kitItems = actionResp.kitItems))
                            executionBanner = "\n\n✅ [Acción ejecutada: Kit de la Quedada actualizado en '${target.title}']"
                        } else if (target == null) {
                            executionBanner = "\n\n⚠️ [No encontré la salida '${actionResp.targetTitleQuery}' para actualizar el kit.]"
                        }
                    }
                    "SWITCH_THEME" -> {
                        val targetTheme = when (actionResp.themeTarget.uppercase()) {
                            "DARK", "OSCURO" -> ThemeMode.SOCIAL_OSCURO
                            "LIGHT", "CLARO" -> ThemeMode.SOCIAL_CLARO
                            "ZEN" -> ThemeMode.MINIMALISTA
                            else -> if (themeMode.value == ThemeMode.SOCIAL_OSCURO) ThemeMode.SOCIAL_CLARO else ThemeMode.SOCIAL_OSCURO
                        }
                        updateThemeMode(targetTheme)
                        executionBanner = "\n\n✅ [Acción ejecutada: Tema cambiado a ${targetTheme.name}]"
                    }
                }

                socialAgentMessages.value = socialAgentMessages.value + ChatMessage(
                    "ai",
                    actionResp.replyText + executionBanner
                )
            } finally {
                isSocialAgentBusy.value = false
            }
        }
    }

    private fun findOutingByQuery(query: String): Outing? {
        val clean = query.trim()
        if (clean.isBlank()) {
            return if (outings.value.size == 1) outings.value.first() else null
        }
        return outings.value.firstOrNull {
            it.title.contains(clean, ignoreCase = true) || it.location.contains(clean, ignoreCase = true)
        }
    }

    // --- World 2: Study Calendar Events (Persisted in Room) ---
    fun addStudyEvent(title: String, details: String, dateIso: String, time: String = "19:00") {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            repository.insertStudyEvent(
                StudyEvent(
                    title = cleanTitle,
                    details = details.trim(),
                    dateIso = dateIso.ifBlank { todayIsoDate() },
                    time = time.ifBlank { "19:00" }
                )
            )
            NotificationHelper.showOutingNotification(
                context = repository.appContext,
                enabled = notificationsEnabled.value,
                title = "Estudio agendado: $cleanTitle",
                body = "${dateIso.ifBlank { todayIsoDate() }} • ${time.ifBlank { "19:00" }}"
            )
        }
    }

    fun toggleStudyEventCompleted(event: StudyEvent) {
        viewModelScope.launch {
            repository.setStudyEventCompleted(event.id, !event.completed)
            if (!event.completed) {
                repository.addPowerXP(25)
                repository.updateStreak()
                studyPoints.value = repository.getStudyPoints()
                streakDays.value = repository.getStreakDays()
                updateAchievements()
            }
        }
    }

    fun deleteStudyEvent(eventId: Int) {
        viewModelScope.launch {
            repository.deleteStudyEvent(eventId)
        }
    }

    // --- World 2: Bible & Spiritual Methods ---
    fun saveRecentChapter(bibleId: String, chapterId: String, reference: String) {
        viewModelScope.launch {
            repository.insertRecentChapter(
                RecentChapter(
                    chapterId = chapterId,
                    bibleId = bibleId,
                    reference = reference
                )
            )
            repository.updateStreak()
            streakDays.value = repository.getStreakDays()
        }
    }

    fun saveFavoriteVerse(sectionId: String, reference: String, text: String) {
        viewModelScope.launch {
            repository.insertFavoriteVerse(
                FavoriteVerse(
                    sectionId = sectionId,
                    verseReference = reference,
                    verseText = text
                )
            )
            playFeedbackSound(false)
        }
    }

    fun deleteFavoriteVerse(id: Int) {
        viewModelScope.launch {
            repository.deleteFavoriteVerse(id)
        }
    }

    fun clearRawChapterExplanation() {
        rawChapterExplanation.value = null
        isGeneratingRawExplanation.value = false
    }

    fun explainRawChapter(chapterTitle: String, chapterContent: String) {
        if (isGeneratingRawExplanation.value) return
        isGeneratingRawExplanation.value = true
        rawChapterExplanation.value = null
        viewModelScope.launch {
            try {
                val prompt = "Proporciona un breve resumen, contexto histórico y aplicación espiritual del siguiente capítulo bíblico. Sé conciso y teológicamente preciso.\nCapítulo: $chapterTitle\nContenido:\n${chapterContent.take(8000)}"
                val response = withTimeout(60_000L) {
                    generativeModel.generateContent(prompt)
                }
                val responseText = response.text
                rawChapterExplanation.value = if (!responseText.isNullOrBlank()) {
                    responseText
                } else {
                    "El modelo devolvió una respuesta vacía."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("MainViewModel", "Raw chapter explanation timed out", e)
                rawChapterExplanation.value = "La generación tardó más de 60 segundos. Por favor, inténtalo de nuevo."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Raw chapter explanation failed", e)
                rawChapterExplanation.value = "Error al conectar con la IA: ${e.localizedMessage ?: "Error desconocido"}"
            } finally {
                isGeneratingRawExplanation.value = false
            }
        }
    }

    fun explainChapter(section: BibleSection) {
        if (section.aiExplanation != null) {
            chapterExplanation.value = section.aiExplanation
            return
        }
        if (isGeneratingExplanation.value) return
        isGeneratingExplanation.value = true
        chapterExplanation.value = null
        viewModelScope.launch {
            try {
                val prompt = "Proporciona un breve resumen y el contexto histórico del siguiente capítulo bíblico. Sé conciso y teológicamente preciso, enfatizando su significado principal. Capítulo: ${section.title}\nContenido:\n${section.content}"
                val response = withTimeout(60_000L) {
                    generativeModel.generateContent(prompt)
                }
                val responseText = response.text
                if (!responseText.isNullOrBlank()) {
                    chapterExplanation.value = responseText
                    repository.updateAiExplanation(section.id, responseText)
                    selectedSection.value = selectedSection.value?.copy(aiExplanation = responseText)
                } else {
                    chapterExplanation.value = "No se pudo obtener un resumen en este momento."
                }
            } catch (e: TimeoutCancellationException) {
                chapterExplanation.value = "La generación tardó más de 60 segundos."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chapterExplanation.value = "Error al conectar con la IA: ${e.localizedMessage ?: "Error desconocido"}"
            } finally {
                isGeneratingExplanation.value = false
            }
        }
    }

    fun selectSection(section: BibleSection?) {
        selectedSection.value = section
        if (section != null) {
            chapterExplanation.value = section.aiExplanation
            val params = android.os.Bundle().apply {
                putString("section_id", section.id)
                putString("section_title", section.title)
            }
            repository.logEvent("select_content", params)
        } else {
            chapterExplanation.value = null
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val verseNotes: StateFlow<List<VerseNote>> = selectedSection
        .filterNotNull()
        .flatMapLatest { section ->
            repository.getVerseNotesForSection(section.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveVerseNote(sectionId: String, verseRef: String, note: String) {
        if (note.isBlank()) return
        viewModelScope.launch {
            repository.insertVerseNote(
                VerseNote(
                    sectionId = sectionId,
                    verseReference = verseRef,
                    noteText = note.trim()
                )
            )
            studyPoints.value = repository.getStudyPoints()
            updateAchievements()
        }
    }

    fun getNotesForChapter(chapterId: String) = repository.getVerseNotesForSection(chapterId)

    fun deleteVerseNote(id: Int) {
        viewModelScope.launch {
            repository.deleteVerseNote(id)
        }
    }

    fun setBibleVersion(version: String) {
        bibleVersion.value = version
        viewModelScope.launch { repository.preferences.setBibleVersion(version) }
    }

    fun toggleAnalytics(enabled: Boolean) {
        repository.setAnalyticsEnabled(enabled)
        analyticsEnabled.value = enabled
    }

    fun toggleBookmark(id: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.setBookmarked(id, !currentVal)
        }
    }

    fun toggleRead(id: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.markRead(id, !currentVal)
            studyPoints.value = repository.getStudyPoints()
            streakDays.value = repository.getStreakDays()
            updateAchievements()
        }
    }

    fun saveAnnotation(id: String, text: String) {
        viewModelScope.launch {
            repository.updateNotes(id, text)
            studyPoints.value = repository.getStudyPoints()
            updateAchievements()
        }
    }

    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank() || isGeneratingResponse.value) return
        val currentList = chatMessages.value.toMutableList()
        currentList.add(ChatMessage("user", prompt.trim()))
        chatMessages.value = currentList
        isGeneratingResponse.value = true

        viewModelScope.launch {
            try {
                val answer = GeminiClient.chatWithSaul(prompt.trim())
                var displayAnswer = answer

                if (displayAnswer.contains("[CMD:FONT:")) {
                    val regex = "\\[CMD:FONT:(\\d+)]".toRegex()
                    regex.find(displayAnswer)?.let { match ->
                        match.groupValues[1].toFloatOrNull()?.let { updateTextSize(it) }
                        displayAnswer = displayAnswer.replace(match.value, "")
                    }
                }
                if (displayAnswer.contains("[CMD:THEME:ZEN]")) {
                    updateThemeMode(ThemeMode.MINIMALISTA)
                    displayAnswer = displayAnswer.replace("[CMD:THEME:ZEN]", "")
                }
                if (displayAnswer.contains("[CMD:THEME:DEFAULT]")) {
                    updateThemeMode(ThemeMode.PERGAMINO)
                    displayAnswer = displayAnswer.replace("[CMD:THEME:DEFAULT]", "")
                }
                if (displayAnswer.contains("[CMD:EVENT:")) {
                    val regex = "\\[CMD:EVENT:(.*?)]".toRegex()
                    regex.find(displayAnswer)?.let { match ->
                        val parts = match.groupValues[1].split("|")
                        val evTitle = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "Estudio Bíblico" }
                        val evDetails = parts.getOrNull(1)?.trim().orEmpty()
                        val evDate = parts.getOrNull(2)?.trim().orEmpty().ifBlank { todayIsoDate() }
                        addStudyEvent(evTitle, evDetails, evDate)
                        displayAnswer = displayAnswer.replace(
                            match.value,
                            "\n✅ [Evento guardado en tu Calendario Bíblico: $evTitle ($evDate)]\n"
                        )
                    }
                }
                if (displayAnswer.contains("[CMD:VERSE:")) {
                    val regex = "\\[CMD:VERSE:(.*?)]".toRegex()
                    regex.find(displayAnswer)?.let { match ->
                        val ref = match.groupValues[1].trim()
                        setQuery(ref)
                        displayAnswer = displayAnswer.replace(match.value, "\n🔎 [Búsqueda preparada: $ref]\n")
                    }
                }

                chatMessages.value = chatMessages.value + ChatMessage("ai", displayAnswer.trim())
                repository.addPowerXP(25)
                repository.updateStreak()
                studyPoints.value = repository.getStudyPoints()
                streakDays.value = repository.getStreakDays()
                updateAchievements()
            } finally {
                isGeneratingResponse.value = false
            }
        }
    }

    fun selectCategory(cat: String) {
        activeCategory.value = cat
    }

    fun setQuery(query: String) {
        searchTerm.value = query
    }

    fun submitTriviaAnswer(selectedIndex: Int) {
        triviaAnsweredStatus.value = selectedIndex
        if (selectedIndex == currentTrivia.value.correctIndex) {
            repository.addPowerXP(100)
            repository.updateStreak()
            studyPoints.value = repository.getStudyPoints()
            streakDays.value = repository.getStreakDays()
            playFeedbackSound(true)
            updateAchievements()
        }
    }

    fun nextTrivia() {
        val nextIndex = (0 until triviaPool.size).filter { it != currentTrivia.value.id - 1 }.randomOrNull() ?: 0
        currentTrivia.value = triviaPool[nextIndex]
        triviaAnsweredStatus.value = null
    }

    private fun updateAchievements() {
        val points = repository.getStudyPoints()
        val streak = repository.getStreakDays()
        val list = mutableListOf<String>()
        if (points >= 50) list.add("Primeros Pasos en la Palabra (50 XP)")
        if (points >= 150) list.add("Estudioso del Tabernáculo (150 XP)")
        if (points >= 350) list.add("Compañero de Estudio (350 XP)")
        if (points >= 600) list.add("Teólogo Dedicado (600 XP)")
        if (streak >= 3) list.add("Racha Constante ($streak días)")
        unlockedAchievements.value = list
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        viewModelScope.launch { repository.preferences.setThemeMode(mode) }
    }

    fun updateTextSize(size: Float) {
        val clamped = size.coerceIn(12f, 34f)
        textSizeSp.value = clamped
        viewModelScope.launch { repository.preferences.setTextSizeSp(clamped) }
    }

    fun updateTextPadding(padding: Float) {
        val clamped = padding.coerceIn(8f, 32f)
        textPaddingDp.value = clamped
        viewModelScope.launch { repository.preferences.setTextPaddingDp(clamped) }
    }

    fun handleSyncWithFirebase() {
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid == null || repo == null) {
            authStatusMessage.value = "Inicia sesión con Google para sincronizar en la nube"
            return
        }
        viewModelScope.launch {
            authStatusMessage.value = "Sincronizando perfil, grupo y salidas..."
            val profileResult = repo.saveUserProfile(userProfile.value.copy(userId = uid), uid)
            val groupResult = repo.createOrEnsureGroup(
                groupId = userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode },
                groupName = userProfile.value.groupName,
                creatorUid = uid,
                creatorName = userProfile.value.username
            )
            var outingFailures = 0
            outings.value.forEach { outing ->
                val res = repo.upsertOuting(outing.copy(groupId = userProfile.value.groupId), uid)
                if (res.isFailure) outingFailures++
            }

            if (profileResult.isSuccess && groupResult.isSuccess && outingFailures == 0) {
                authStatusMessage.value = "✅ Sincronización completada con Firebase Firestore"
                playFeedbackSound(true)
            } else {
                val reason = profileResult.exceptionOrNull()?.localizedMessage
                    ?: groupResult.exceptionOrNull()?.localizedMessage
                    ?: "$outingFailures salidas no pudieron sincronizarse"
                authStatusMessage.value = "⚠️ Sincronización parcial: $reason"
            }
        }
    }

    fun handleLogout() {
        stopCloudObservers()
        repository.logout()
        GeminiClient.resetSessions()
        currentUserId.value = null
        isUserLoggedIn.value = false
        userEmail.value = ""
        activeSocialGroup.value = null
        observedGroupId = null
        userProfile.value = defaultGuestProfile
        groupMembers.value = defaultGuestMembers
        outings.value = defaultOutings
        groupChatMessages.value = defaultGroupChat
        stories.value = defaultStories
        authStatusMessage.value = "Sesión cerrada correctamente"
    }

    fun scheduleCalendarEvent(context: Context, section: BibleSection) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Estudio: ${section.title}")
                putExtra(CalendarContract.Events.DESCRIPTION, "Pasaje: ${section.passageReference}\n\n${section.description}")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Calendar.getInstance().timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Calendar.getInstance().timeInMillis + 3600000)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir la aplicación de calendario", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareVerse(context: Context, text: String, title: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "«$text» — ($title)")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir pasaje bíblico"))
    }

    fun shareVerseNote(context: Context, verseRef: String, noteText: String, chapterTitle: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Versículo: $verseRef ($chapterTitle)\n\nMi Reflexión:\n\"$noteText\"")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir reflexión"))
    }
}

class ViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
