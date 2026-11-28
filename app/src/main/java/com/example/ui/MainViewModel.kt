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
import com.example.data.CloudLoadState
import com.example.data.DataOrigin
import com.example.data.FavoriteVerse
import com.example.data.GeminiClient
import com.example.data.GroupChatMessage
import com.example.data.GroupFeedState
import com.example.data.GroupFeedStatus
import com.example.data.GroupPost
import com.example.data.MediaStorageHelper
import com.example.data.NotificationHelper
import com.example.data.Outing
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.data.RecentChapter
import com.example.data.Repository
import com.example.data.RsvpChoice
import com.example.data.SocialContentAuthzPolicy
import com.example.data.SocialGroup
import com.example.data.SocialRepository
import com.example.data.SocialSessionPolicy
import com.example.data.SocialSyncSnapshot
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

    private companion object {
        const val PERSONAL_SCOPE_SENTINEL = "__personal_no_group__"
    }

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
    val socialSyncState = MutableStateFlow(SocialSyncSnapshot.forGuestDemo())

    private var currentAuthDisplayName: String? = null
    private var currentAuthPhotoUrl: String? = null
    private var profileJob: Job? = null
    private var groupJob: Job? = null
    private var outingsJob: Job? = null
    private var chatJob: Job? = null
    private var storiesJob: Job? = null
    private var groupFeedJob: Job? = null
    private var observedGroupId: String? = null
    private var cloudObserverGeneration = 0L
    private var cloudSessionGeneration = 0L

    private fun isCurrentCloudObserver(uid: String, generation: Long): Boolean {
        return generation == cloudObserverGeneration &&
            currentUserId.value == uid &&
            socialSyncState.value.origin == DataOrigin.CLOUD_REAL
    }

    private fun isCurrentCloudSession(uid: String, generation: Long): Boolean {
        return generation == cloudSessionGeneration &&
            currentUserId.value == uid &&
            socialSyncState.value.origin == DataOrigin.CLOUD_REAL
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            val isSameActiveRealSession =
                currentUserId.value == user.uid && socialSyncState.value.origin == DataOrigin.CLOUD_REAL
            currentUserId.value = user.uid
            isUserLoggedIn.value = true
            userEmail.value = user.email ?: ""
            currentAuthDisplayName = user.displayName
            currentAuthPhotoUrl = user.photoUrl?.toString()
            if (!isSameActiveRealSession) {
                onAuthenticatedSessionStarted(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    photoUrl = user.photoUrl?.toString()
                )
            }
        } else {
            val wasAuthenticated = isUserLoggedIn.value ||
                currentUserId.value != null ||
                socialSyncState.value.origin == DataOrigin.CLOUD_REAL
            currentUserId.value = null
            isUserLoggedIn.value = false
            userEmail.value = ""
            currentAuthDisplayName = null
            currentAuthPhotoUrl = null
            if (wasAuthenticated) {
                enterGuestDemoMode()
            } else {
                stopCloudObservers()
                activeSocialGroup.value = null
                observedGroupId = null
                groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)
                socialSyncState.value = SocialSyncSnapshot.forGuestDemo()
            }
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
    val groupFeedState = MutableStateFlow(GroupFeedState())

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

    // --- Session Mode Transitions (Guest Demo vs. Authenticated Cloud Real) ---
    private fun onAuthenticatedSessionStarted(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        stopCloudObservers()
        observedGroupId = null
        activeSocialGroup.value = null
        selectedOutingForDetail.value = null

        // Immediately discard all Guest Demo data so fictional items are never shown or written as real.
        userProfile.value = SocialSessionPolicy.buildCleanRealUserProfile(
            uid = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl
        )
        groupMembers.value = emptyList()
        outings.value = emptyList()
        groupChatMessages.value = emptyList()
        stories.value = emptyList()
        socialSyncState.value = SocialSyncSnapshot.forRealUserLoading()

        startCloudObservers(uid)
    }

    private fun enterGuestDemoMode() {
        stopCloudObservers()
        activeSocialGroup.value = null
        observedGroupId = null
        selectedOutingForDetail.value = null
        userProfile.value = defaultGuestProfile
        groupMembers.value = defaultGuestMembers
        outings.value = defaultOutings
        groupChatMessages.value = defaultGroupChat
        stories.value = defaultStories
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)
        socialSyncState.value = SocialSyncSnapshot.forGuestDemo()
    }

    fun refreshCloudData() {
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid == null || repo == null) {
            authStatusMessage.value = "Inicia sesión con Google para cargar tus datos en la nube"
            return
        }
        observedGroupId = null
        socialSyncState.value = SocialSyncSnapshot.forRealUserLoading()
        authStatusMessage.value = "Actualizando datos desde Firebase…"
        startCloudObservers(uid)
    }

    // --- Cloud Realtime Observers (Read-Only on Sign-In, Zero Fictional Auto-Seeding) ---
    private fun startCloudObservers(uid: String) {
        val repo = socialRepository
        if (repo == null) {
            val unavailableErr = CloudLoadState.Error(
                message = "Firebase Firestore no está inicializado en esta compilación.",
                hasCachedRealData = false
            )
            socialSyncState.value = SocialSyncSnapshot(
                origin = DataOrigin.CLOUD_REAL,
                profileState = unavailableErr,
                groupState = unavailableErr,
                outingsState = unavailableErr,
                chatState = unavailableErr,
                storiesState = unavailableErr
            )
            return
        }
        stopCloudObservers()
        val sessionGeneration = cloudSessionGeneration

        profileJob = viewModelScope.launch {
            repo.observeUserProfile(uid)
                .catch { e ->
                    if (!isCurrentCloudSession(uid, sessionGeneration)) return@catch
                    Log.w("MainViewModel", "Profile observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "tu perfil",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = 0
                    )
                    val cur = socialSyncState.value
                    socialSyncState.value = cur.copy(
                        origin = DataOrigin.CLOUD_REAL,
                        profileState = err,
                        groupState = if (cur.groupState is CloudLoadState.Loading) err else cur.groupState,
                        outingsState = if (cur.outingsState is CloudLoadState.Loading) {
                            CloudLoadState.Error(err.message, hasCachedRealData = outings.value.isNotEmpty())
                        } else cur.outingsState,
                        chatState = if (cur.chatState is CloudLoadState.Loading) {
                            CloudLoadState.Error(err.message, hasCachedRealData = groupChatMessages.value.isNotEmpty())
                        } else cur.chatState,
                        storiesState = if (cur.storiesState is CloudLoadState.Loading) {
                            CloudLoadState.Error(err.message, hasCachedRealData = stories.value.isNotEmpty())
                        } else cur.storiesState
                    )
                    authStatusMessage.value = err.message
                }
                .collect { remoteProfile ->
                    if (!isCurrentCloudSession(uid, sessionGeneration)) return@collect
                    val (resolvedProfile, _) = SocialSessionPolicy.resolveProfileOnCloudEmit(
                        remoteProfile = remoteProfile,
                        uid = uid,
                        displayName = currentAuthDisplayName,
                        email = userEmail.value,
                        photoUrl = currentAuthPhotoUrl
                    )
                    userProfile.value = resolvedProfile
                    socialSyncState.value = socialSyncState.value.copy(
                        origin = DataOrigin.CLOUD_REAL,
                        profileState = CloudLoadState.Loaded
                    )

                    val targetGroup = SocialSessionPolicy.resolveTargetGroupId(resolvedProfile)
                    if (targetGroup.isNotBlank()) {
                        if (observedGroupId != targetGroup) {
                            startGroupScopedObservers(uid, targetGroup, resolvedProfile)
                        }
                    } else {
                        if (observedGroupId != PERSONAL_SCOPE_SENTINEL) {
                            startPersonalScopedObservers(uid)
                        }
                    }
                }
        }
    }

    private fun startPersonalScopedObservers(uid: String) {
        val repo = socialRepository ?: return
        cloudObserverGeneration += 1
        val generation = cloudObserverGeneration
        observedGroupId = PERSONAL_SCOPE_SENTINEL
        groupJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)

        activeSocialGroup.value = null
        groupMembers.value = emptyList()
        socialSyncState.value = socialSyncState.value.copy(
            origin = DataOrigin.CLOUD_REAL,
            groupState = CloudLoadState.Loaded,
            outingsState = CloudLoadState.Loading,
            chatState = CloudLoadState.Loading,
            storiesState = CloudLoadState.Loading
        )

        outingsJob = viewModelScope.launch {
            repo.observeOutings(uid)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Personal outings observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "tus salidas",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = outings.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(outingsState = err)
                    authStatusMessage.value = err.message
                }
                .collect { remoteOutings ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    outings.value = remoteOutings
                    socialSyncState.value = socialSyncState.value.copy(outingsState = CloudLoadState.Loaded)
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeChatMessages(uid)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Personal chat observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "los mensajes",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = groupChatMessages.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(chatState = err)
                }
                .collect { remoteMessages ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    groupChatMessages.value = remoteMessages
                    socialSyncState.value = socialSyncState.value.copy(chatState = CloudLoadState.Loaded)
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeStories(uid)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Personal stories observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "las instantáneas",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = stories.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(storiesState = err)
                }
                .collect { remoteStories ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    stories.value = remoteStories
                    socialSyncState.value = socialSyncState.value.copy(storiesState = CloudLoadState.Loaded)
                }
        }
    }

    private fun startGroupScopedObservers(uid: String, groupId: String, profile: UserProfile) {
        val repo = socialRepository ?: return
        cloudObserverGeneration += 1
        val generation = cloudObserverGeneration
        observedGroupId = groupId
        groupJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()

        socialSyncState.value = socialSyncState.value.copy(
            origin = DataOrigin.CLOUD_REAL,
            groupState = CloudLoadState.Loading,
            outingsState = CloudLoadState.Loading,
            chatState = CloudLoadState.Loading,
            storiesState = CloudLoadState.Loading
        )

        groupJob = viewModelScope.launch {
            repo.observeGroup(groupId)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Group observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "el grupo",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = groupMembers.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(groupState = err)
                }
                .collect { remoteGroup ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    activeSocialGroup.value = remoteGroup
                    if (remoteGroup != null) {
                        val realMembers = remoteGroup.memberIds.map { memberUid ->
                            val name = remoteGroup.memberNames[memberUid]?.ifBlank { "Miembro" } ?: "Miembro"
                            if (memberUid == uid) "$name (Tú)" else name
                        }
                        groupMembers.value = realMembers
                        if (remoteGroup.name.isNotBlank() && userProfile.value.groupName != remoteGroup.name) {
                            userProfile.value = userProfile.value.copy(groupName = remoteGroup.name)
                        }
                    } else {
                        groupMembers.value = emptyList()
                    }
                    socialSyncState.value = socialSyncState.value.copy(groupState = CloudLoadState.Loaded)
                }
        }

        outingsJob = viewModelScope.launch {
            repo.observeGroupOutings(groupId)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Group outings observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "las salidas del grupo",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = outings.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(outingsState = err)
                    authStatusMessage.value = err.message
                }
                .collect { remoteOutings ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    // Real accounts NEVER auto-seed fictional demo outings into Firestore.
                    outings.value = remoteOutings
                    socialSyncState.value = socialSyncState.value.copy(outingsState = CloudLoadState.Loaded)
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeGroupChatMessages(groupId)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Chat observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "los mensajes del grupo",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = groupChatMessages.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(chatState = err)
                }
                .collect { remoteMessages ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    groupChatMessages.value = remoteMessages
                    socialSyncState.value = socialSyncState.value.copy(chatState = CloudLoadState.Loaded)
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeGroupStories(groupId)
                .catch { e ->
                    if (!isCurrentCloudObserver(uid, generation)) return@catch
                    Log.w("MainViewModel", "Stories observer error: ${e.message}")
                    val err = SocialSessionPolicy.resolveErrorState(
                        scopeLabel = "las instantáneas del grupo",
                        throwable = e,
                        currentOrigin = socialSyncState.value.origin,
                        currentItemCount = stories.value.size
                    )
                    socialSyncState.value = socialSyncState.value.copy(storiesState = err)
                }
                .collect { remoteStories ->
                    if (!isCurrentCloudObserver(uid, generation)) return@collect
                    stories.value = remoteStories
                    socialSyncState.value = socialSyncState.value.copy(storiesState = CloudLoadState.Loaded)
                }
        }

        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.LOADING)
        groupFeedJob = viewModelScope.launch {
            repo.observeGroupPosts(groupId)
                .catch { e ->
                    if (!isCurrentGroupScope(uid, groupId)) return@catch
                    Log.w("MainViewModel", "Group feed observer error: ${e.message}")
                    groupFeedState.value = groupFeedState.value.copy(
                        status = GroupFeedStatus.ERROR,
                        message = "No se pudo cargar el feed del grupo. Comprueba tu sesión y pertenencia."
                    )
                }
                .collect { remotePosts ->
                    if (!isCurrentGroupScope(uid, groupId)) return@collect
                    groupFeedState.value = groupFeedState.value.copy(
                        status = if (remotePosts.isEmpty()) GroupFeedStatus.EMPTY else GroupFeedStatus.READY,
                        posts = remotePosts,
                        message = null
                    )
            }
        }
    }

    private fun isCurrentGroupScope(uid: String, groupId: String): Boolean {
        return com.example.data.isCurrentGroupFeedScope(currentUserId.value, uid, observedGroupId, groupId)
    }

    private fun isCurrentWriteScope(uid: String, groupId: String): Boolean {
        return currentUserId.value == uid && resolveActiveWriteGroupId() == groupId
    }

    private fun stopCloudObservers() {
        cloudObserverGeneration += 1
        cloudSessionGeneration += 1
        profileJob?.cancel()
        groupJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)
        profileJob = null
        groupJob = null
        outingsJob = null
        chatJob = null
        storiesJob = null
        groupFeedJob = null
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
        if (currentUserId.value != null && MediaStorageHelper.isLocalMediaReference(newAvatar)) {
            authStatusMessage.value = "No se puede guardar un avatar local en una cuenta autenticada"
            return
        }
        val updated = userProfile.value.copy(
            username = newUsername.ifBlank { userProfile.value.username },
            bio = newBio,
            avatarUrl = newAvatar
        )
        userProfile.value = updated
        persistProfileIfAuthenticated(updated)
    }

    suspend fun updateProfileInfoAwaitable(
        newUsername: String,
        newBio: String,
        newAvatar: String,
        expectedUid: String?,
        expectedGroupId: String
    ): Result<Unit> {
        val session = validateMediaWriteSession(expectedUid, expectedGroupId, newAvatar)
        if (session.isFailure) return session
        val updated = userProfile.value.copy(
            username = newUsername.ifBlank { userProfile.value.username },
            bio = newBio,
            avatarUrl = newAvatar
        )
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null) {
            if (repo == null) return Result.failure(IllegalStateException("Repositorio Firestore no disponible"))
            val saved = try { repo.updateUserProfileInfo(uid, updated) } catch (e: CancellationException) { throw e }
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
            if (currentUserId.value != expectedUid || activeGroupId() != expectedGroupId) {
                return Result.failure(IllegalStateException("La sesión o el grupo cambiaron durante el guardado"))
            }
        }
        userProfile.value = updated
        return Result.success(Unit)
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

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.createOrEnsureGroup(code, cleanName, uid, updated.username)
                    .onSuccess {
                        repo.saveUserProfile(updated, uid).onSuccess {
                            userProfile.value = updated
                            startGroupScopedObservers(uid, code, updated)
                            authStatusMessage.value = "Grupo '$cleanName' creado en la nube con código $code"
                        }.onFailure { profileError ->
                            authStatusMessage.value = "Grupo creado, pero no se pudo guardar el perfil: ${profileError.localizedMessage}"
                        }
                    }
                    .onFailure { err ->
                        authStatusMessage.value = "Error al crear grupo en la nube: ${err.localizedMessage}"
                    }
            }
        } else {
            userProfile.value = updated
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
                    val profileSaved = repo.saveUserProfile(updated, uid)
                    if (profileSaved.isFailure) {
                        authStatusMessage.value = "Te has unido al grupo, pero no se pudo guardar el perfil: ${profileSaved.exceptionOrNull()?.localizedMessage}"
                        return@onSuccess
                    }
                    userProfile.value = updated
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

    private fun resolveActiveWriteGroupId(): String {
        val uid = currentUserId.value
        val profileGroup = SocialSessionPolicy.resolveTargetGroupId(userProfile.value)
        return if (uid != null) {
            SocialSessionPolicy.resolveGroupIdForWrite(profileGroup, uid)
        } else {
            profileGroup.ifBlank { SocialSessionPolicy.GUEST_DEMO_GROUP_ID }
        }
    }

    private fun canWriteInScope(targetGroupId: String): Boolean {
        return SocialContentAuthzPolicy.canWriteToGroupScope(
            targetGroupId = targetGroupId,
            currentUserId = currentUserId.value,
            activeGroupId = observedGroupId,
            isCurrentGroupMember = currentGroupRole.value != null,
            isGuestDemo = socialSyncState.value.isGuestDemo
        )
    }

    // --- World 1: Outing CRUD, Calendar & Map Actions ---
    fun saveOrUpdateOuting(outing: Outing) {
        val existingList = outings.value.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == outing.id && outing.id.isNotBlank() }
        val activeGroupId = resolveActiveWriteGroupId()
        val uid = currentUserId.value
        val isGuestDemo = socialSyncState.value.isGuestDemo

        val isNew = existingIdx < 0
        val finalOuting = if (isNew) {
            if (!canWriteInScope(activeGroupId)) {
                authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
                return
            }
            val ownerUid = uid ?: SocialSessionPolicy.GUEST_DEMO_USER_ID
            val initialChoice = RsvpChoice.fromString(outing.myRsvp)
            val initialMap = if (initialChoice == RsvpChoice.NONE) emptyMap() else mapOf(ownerUid to initialChoice.name)
            outing.copy(
                id = outing.id.ifBlank { "outing_${UUID.randomUUID().toString().replace("-", "").take(12)}" },
                ownerId = ownerUid,
                groupId = activeGroupId,
                myRsvp = initialChoice.name,
                rsvpByUser = initialMap,
                rsvpYesCount = if (initialChoice == RsvpChoice.YES) 1L else 0L,
                rsvpMaybeCount = if (initialChoice == RsvpChoice.MAYBE) 1L else 0L,
                rsvpNoCount = if (initialChoice == RsvpChoice.NO) 1L else 0L
            )
        } else {
            val existing = existingList[existingIdx]
            if (!SocialContentAuthzPolicy.canEditOrCancelOuting(existing, uid, isGuestDemo)) {
                authStatusMessage.value = SocialContentAuthzPolicy.OWNER_ONLY_OUTING_ERROR
                return
            }
            if (!canWriteInScope(existing.groupId)) {
                authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
                return
            }
            // Strictly preserve immutable ownerId/groupId and never overwrite RSVPs/counters during editorial edits.
            outing.copy(
                id = existing.id,
                ownerId = existing.ownerId,
                groupId = existing.groupId,
                rsvpByUser = existing.rsvpByUser,
                rsvpYesCount = existing.rsvpYesCount,
                rsvpMaybeCount = existing.rsvpMaybeCount,
                rsvpNoCount = existing.rsvpNoCount,
                myRsvp = existing.myRsvp
            )
        }

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

        val repo = socialRepository
        if (uid != null && repo != null) {
            if (SocialSessionPolicy.isOutingAllowedForCloudWrite(
                    origin = socialSyncState.value.origin,
                    outing = finalOuting
                )) {
                viewModelScope.launch {
                    repo.upsertOuting(finalOuting, uid).onFailure { err ->
                        authStatusMessage.value = "No se pudo sincronizar '${finalOuting.title}': ${err.localizedMessage}"
                    }
                }
            } else {
                authStatusMessage.value = "La salida no se sincronizó: los datos de demostración no se guardan en la nube."
            }
        }
    }

    fun updateOutingStatusColor(outingId: String, newStatus: OutingStatus) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        saveOrUpdateOuting(target.copy(status = newStatus.name))
    }

    fun updateOutingRsvp(outingId: String, choice: RsvpChoice) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        val authUid = currentUserId.value
        val uid = authUid ?: SocialSessionPolicy.GUEST_DEMO_USER_ID
        val isGuestDemo = socialSyncState.value.isGuestDemo

        if (!isGuestDemo && !canWriteInScope(target.groupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }

        val updated: Outing = if (authUid != null || target.rsvpByUser.isNotEmpty()) {
            val plan = SocialContentAuthzPolicy.computeSelfRsvpTransition(
                existingByUser = target.rsvpByUser,
                existingYes = target.rsvpYesCount,
                existingMaybe = target.rsvpMaybeCount,
                existingNo = target.rsvpNoCount,
                userId = uid,
                newChoice = choice
            )
            if (plan.isNoOp) return
            target.copy(
                // Do not overwrite shared myRsvp on authenticated multi-user docs; UI reads rsvpByUser[uid].
                myRsvp = if (authUid == null) choice.name else target.myRsvp,
                rsvpByUser = plan.nextRsvpByUser,
                rsvpYesCount = plan.nextYesCount,
                rsvpMaybeCount = plan.nextMaybeCount,
                rsvpNoCount = plan.nextNoCount
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
            val updatedMap = target.rsvpByUser.toMutableMap()
            if (choice == RsvpChoice.NONE) updatedMap.remove(uid) else updatedMap[uid] = choice.name
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

    suspend fun addPhotoToOutingAlbum(
        outingId: String,
        photoUriOrPreset: String,
        expectedUid: String? = currentUserId.value,
        expectedGroupId: String = activeGroupId()
    ): Result<Unit> {
        val session = validateMediaWriteSession(expectedUid, expectedGroupId, photoUriOrPreset)
        if (session.isFailure) return session
        val target = outings.value.firstOrNull { it.id == outingId }
            ?: return Result.failure(IllegalArgumentException("Salida no encontrada"))
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null) {
            if (repo == null) return Result.failure(IllegalStateException("Repositorio Firestore no disponible"))
            val saved = try { repo.addOutingAlbumPhotoTransactional(outingId, photoUriOrPreset, uid) } catch (e: CancellationException) { throw e }
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
            if (currentUserId.value != expectedUid || activeGroupId() != expectedGroupId) {
                return Result.failure(IllegalStateException("La sesión o el grupo cambiaron durante el guardado"))
            }
        }
        outings.value = outings.value.map { outing ->
            if (outing.id == outingId) {
                outing.copy(albumPhotos = (listOf(photoUriOrPreset) + outing.albumPhotos).distinct().take(20))
            } else {
                outing
            }
        }
        return Result.success(Unit)
    }

    fun deleteOuting(outingId: String) {
        val previous = outings.value
        val target = previous.firstOrNull { it.id == outingId } ?: return
        val uid = currentUserId.value
        if (!SocialContentAuthzPolicy.canDeleteOuting(target, uid, socialSyncState.value.isGuestDemo)) {
            authStatusMessage.value = SocialContentAuthzPolicy.OWNER_ONLY_OUTING_ERROR
            return
        }
        if (!canWriteInScope(target.groupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }
        outings.value = outings.value.filterNot { it.id == outingId }
        val repo = socialRepository
        if (uid != null && repo != null) {
            val expectedGroupId = target.groupId.ifBlank { resolveActiveWriteGroupId() }
            viewModelScope.launch {
                repo.deleteOuting(outingId).onSuccess {
                    if (isCurrentWriteScope(uid, expectedGroupId)) {
                        outings.value = outings.value.filterNot { it.id == outingId }
                    }
                }.onFailure { err ->
                    if (isCurrentWriteScope(uid, expectedGroupId)) outings.value = previous
                    authStatusMessage.value = "Error al eliminar la salida: ${err.localizedMessage}"
                }
            }
        } else {
            outings.value = previous.filterNot { it.id == outingId }
        }
    }

    // --- World 1: Private group feed ---
    fun visibleGroupFeedPosts(posts: List<GroupPost>): List<GroupPost> {
        val uid = currentUserId.value ?: return emptyList()
        val group = activeSocialGroup.value ?: return emptyList()
        if (uid !in group.memberIds || !isCurrentGroupScope(uid, group.id)) return emptyList()
        return posts.filter { it.groupId == group.id }
    }

    fun createGroupPost(text: String) {
        val uid = currentUserId.value
        val repo = socialRepository
        val group = activeSocialGroup.value
        val clean = text.trim()
        if (uid.isNullOrBlank() || repo == null || group == null || uid !in group.memberIds) {
            groupFeedState.value = groupFeedState.value.copy(
                status = GroupFeedStatus.ERROR,
                message = "Inicia sesión y entra en un grupo válido para publicar."
            )
            return
        }
        if (clean.isBlank()) return
        if (groupFeedState.value.isPublishing) return
        val groupId = group.id
        if (!isCurrentGroupScope(uid, groupId)) return
        groupFeedState.value = groupFeedState.value.copy(
            isPublishing = true,
            actionMessage = null
        )
        viewModelScope.launch {
            repo.createGroupPost(
                GroupPost(
                    authorId = uid,
                    authorName = userProfile.value.username,
                    groupId = groupId,
                    text = clean
                ),
                uid
            ).onSuccess {
                if (isCurrentGroupScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        isPublishing = false,
                        actionMessage = null,
                        publishSuccessToken = groupFeedState.value.publishSuccessToken + 1,
                        publishSuccessGroupId = groupId
                    )
                }
            }.onFailure { err ->
                if (isCurrentGroupScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        isPublishing = false,
                        actionMessage = "No se pudo publicar: ${err.localizedMessage ?: "permiso denegado"}"
                    )
                }
            }
        }
    }

    fun deleteGroupPost(postId: String) {
        val uid = currentUserId.value ?: return
        val groupId = activeSocialGroup.value?.id ?: return
        val post = groupFeedState.value.posts.firstOrNull { it.id == postId } ?: return
        if (post.authorId != uid) return
        if (post.groupId != groupId || !isCurrentGroupScope(uid, groupId)) return
        if (postId in groupFeedState.value.deletingPostIds) return
        val repo = socialRepository ?: return
        groupFeedState.value = groupFeedState.value.copy(
            deletingPostIds = groupFeedState.value.deletingPostIds + postId,
            actionMessage = null
        )
        viewModelScope.launch {
            repo.deleteGroupPost(postId, uid).onSuccess {
                if (isCurrentGroupScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        deletingPostIds = groupFeedState.value.deletingPostIds - postId,
                        actionMessage = null
                    )
                }
            }.onFailure { err ->
                if (isCurrentGroupScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        deletingPostIds = groupFeedState.value.deletingPostIds - postId,
                        actionMessage = "No se pudo borrar la publicación: ${err.localizedMessage ?: "permiso denegado"}"
                    )
                }
            }
        }
    }

    // --- World 1: Group Chat & Voice Notes ---
    fun sendGroupTextMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val targetGroupId = resolveActiveWriteGroupId()
        if (!canWriteInScope(targetGroupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = targetGroupId,
            text = clean,
            messageType = "TEXT"
        )
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid).onSuccess {
                    if (isCurrentWriteScope(uid, msg.groupId)) groupChatMessages.value = groupChatMessages.value + msg
                }.onFailure { err ->
                    if (isCurrentWriteScope(uid, msg.groupId)) {
                        authStatusMessage.value = "Error al enviar mensaje: ${err.localizedMessage}"
                    }
                }
            }
        } else {
            groupChatMessages.value = groupChatMessages.value + msg
        }
    }

    suspend fun sendGroupVoiceMessage(
        durationSec: Int,
        transcriptionText: String,
        audioUrl: String = "",
        expectedUid: String? = currentUserId.value,
        expectedGroupId: String = activeGroupId()
    ): Result<Unit> {
        val session = validateMediaWriteSession(expectedUid, expectedGroupId, audioUrl)
        if (session.isFailure) return session
        val targetGroupId = resolveActiveWriteGroupId()
        if (!canWriteInScope(targetGroupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return Result.failure(IllegalStateException(SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR))
        }
        val cleanDur = durationSec.coerceAtLeast(1)
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = targetGroupId,
            text = "Nota de voz (0:${cleanDur.toString().padStart(2, '0')})",
            messageType = "VOICE",
            audioDurationSec = cleanDur.toLong(),
            audioUrl = audioUrl,
            transcription = transcriptionText.trim()
        )
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null) {
            if (repo == null) return Result.failure(IllegalStateException("Repositorio Firestore no disponible"))
            val saved = try { repo.sendChatMessage(msg, uid) } catch (e: CancellationException) { throw e }
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        }
        groupChatMessages.value = groupChatMessages.value + msg
        return Result.success(Unit)
    }

    fun shareOutingToGroupChat(outing: Outing) {
        val targetGroupId = resolveActiveWriteGroupId()
        if (!canWriteInScope(targetGroupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = targetGroupId,
            text = "He compartido la salida '${outing.title}'. ¿Quién se apunta?",
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
        val authUid = currentUserId.value
        val uid = authUid ?: SocialSessionPolicy.GUEST_DEMO_USER_ID
        if (authUid != null && !canWriteInScope(msg.groupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }

        val plan = SocialContentAuthzPolicy.computeSelfChatReactionTransition(
            existingReactionsByUser = msg.reactionsByUser,
            userId = uid,
            emoji = emoji
        )
        currentList[idx] = msg.copy(reactionsByUser = plan.nextReactionsByUser)
        groupChatMessages.value = currentList

        val repo = socialRepository
        if (authUid != null && repo != null) {
            viewModelScope.launch {
                repo.toggleMessageReactionTransactional(messageId, authUid, emoji).onFailure { err ->
                    authStatusMessage.value = "Error al actualizar reacción: ${err.localizedMessage}"
                }
            }
        }
    }

    // --- World 1: Instantáneas (Stories) ---
    suspend fun publishInstantaneaStory(
        caption: String,
        filterStyle: String,
        mediaUri: String,
        outingTitle: String,
        expectedUid: String? = currentUserId.value,
        expectedGroupId: String = activeGroupId()
    ): Result<Unit> {
        val session = validateMediaWriteSession(expectedUid, expectedGroupId, mediaUri)
        if (session.isFailure) return session
        val targetGroupId = resolveActiveWriteGroupId()
        if (!canWriteInScope(targetGroupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return Result.failure(IllegalStateException(SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR))
        }
        val story = OutingStory(
            id = "story_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            authorId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            authorName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = targetGroupId,
            caption = caption.ifBlank { "Recuerdo compartido con el grupo" },
            filterStyle = filterStyle,
            mediaUri = mediaUri,
            outingTitle = outingTitle,
            likesCount = 0L,
            likedByUser = emptyMap()
        )
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null) {
            if (repo == null) return Result.failure(IllegalStateException("Repositorio Firestore no disponible"))
            val saved = try { repo.createStory(story, uid) } catch (e: CancellationException) { throw e }
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        }
        stories.value = listOf(story) + stories.value
        return Result.success(Unit)
    }

    private fun activeGroupId(): String =
        userProfile.value.groupId.ifBlank { userProfile.value.groupInviteCode }

    private fun validateMediaWriteSession(
        expectedUid: String?,
        expectedGroupId: String,
        mediaReference: String
    ): Result<Unit> {
        if (currentUserId.value != expectedUid || activeGroupId() != expectedGroupId) {
            return Result.failure(IllegalStateException("La sesión o el grupo cambiaron; se canceló la publicación"))
        }
        if (!currentUserId.value.isNullOrBlank() && MediaStorageHelper.isLocalMediaReference(mediaReference)) {
            return Result.failure(IllegalArgumentException("Las cuentas autenticadas requieren una URL remota de Storage"))
        }
        return Result.success(Unit)
    }

    fun likeInstantaneaStory(storyId: String) {
        val current = stories.value.toMutableList()
        val idx = current.indexOfFirst { it.id == storyId }
        if (idx == -1) return
        val target = current[idx]
        val authUid = currentUserId.value
        val uid = authUid ?: SocialSessionPolicy.GUEST_DEMO_USER_ID
        if (authUid != null && target.groupId.isNotBlank() && !canWriteInScope(target.groupId)) {
            authStatusMessage.value = SocialContentAuthzPolicy.NON_MEMBER_WRITE_ERROR
            return
        }

        val plan = SocialContentAuthzPolicy.computeSelfStoryLikeTransition(
            existingLikedByUser = target.likedByUser,
            existingLikesCount = target.likesCount,
            userId = uid
        )
        current[idx] = target.copy(
            likesCount = plan.nextLikesCount,
            likedByUser = plan.nextLikedByUser
        )
        stories.value = current

        val repo = socialRepository
        if (authUid != null && repo != null) {
            viewModelScope.launch {
                repo.toggleStoryLikeTransactional(storyId, authUid).onFailure { err ->
                    authStatusMessage.value = "Error al actualizar me gusta: ${err.localizedMessage}"
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
        // Never blindly push local/guest demo data to Firestore on sync;
        // refresh real cloud observers instead so remote data is loaded honestly.
        refreshCloudData()
    }

    fun handleLogout() {
        repository.logout()
        GeminiClient.resetSessions()
        currentUserId.value = null
        isUserLoggedIn.value = false
        userEmail.value = ""
        currentAuthDisplayName = null
        currentAuthPhotoUrl = null
        enterGuestDemoMode()
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)
        authStatusMessage.value = "Sesión cerrada correctamente (modo invitado)"
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
