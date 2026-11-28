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
import com.example.data.GroupInvite
import com.example.data.GroupInviteCrypto
import com.example.data.GroupMember
import com.example.data.GroupPost
import com.example.data.GroupRole
import com.example.data.belongsToActiveGroup
import com.example.data.InviteRedemptionOutcome
import com.example.data.NotificationHelper
import com.example.data.Outing
import com.example.data.OutingRsvp
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.data.RecentChapter
import com.example.data.Repository
import com.example.data.RsvpChoice
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
import com.google.firebase.firestore.FirebaseFirestoreException
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
    val world1Tab = MutableStateFlow("profile") // "profile", "feed", "outings", "map", "chat", "games"
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
    val activeGroupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val activeGroupInvites = MutableStateFlow<List<GroupInvite>>(emptyList())
    val groupOutingRsvpState = MutableStateFlow<CloudLoadState>(CloudLoadState.GuestDemo)
    val groupFeedState = MutableStateFlow(GroupFeedState())
    val currentGroupRole = MutableStateFlow<GroupRole?>(null)
    val inviteActionFeedback = MutableStateFlow<String?>(null)
    val isInviteActionBusy = MutableStateFlow(false)
    val socialSyncState = MutableStateFlow(SocialSyncSnapshot.forGuestDemo())

    private var currentAuthDisplayName: String? = null
    private var currentAuthPhotoUrl: String? = null
    private var profileJob: Job? = null
    private var groupJob: Job? = null
    private var membersJob: Job? = null
    private var invitesJob: Job? = null
    private var outingsJob: Job? = null
    private var groupOutingRsvpsJob: Job? = null
    private var chatJob: Job? = null
    private var storiesJob: Job? = null
    private var groupFeedJob: Job? = null
    private var groupFeedObservedScope: String? = null
    private var groupOutingRsvpObservedScope: String? = null
    private var groupOutingRsvpCachedScope: String? = null
    private var observedGroupId: String? = null
    private var groupOutingsBase: List<Outing> = emptyList()
    private var groupOutingRsvpResponses: List<OutingRsvp> = emptyList()

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
        groupOutingRsvpState.value = CloudLoadState.Loading
        activeSocialGroup.value = null
        activeGroupMembers.value = emptyList()
        activeGroupInvites.value = emptyList()
        currentGroupRole.value = null
        inviteActionFeedback.value = null
        isInviteActionBusy.value = false
        selectedOutingForDetail.value = null
        outings.value = emptyList()

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
        activeGroupMembers.value = emptyList()
        activeGroupInvites.value = emptyList()
        currentGroupRole.value = GroupRole.OWNER
        inviteActionFeedback.value = null
        isInviteActionBusy.value = false
        observedGroupId = null
        selectedOutingForDetail.value = null
        groupOutingRsvpState.value = CloudLoadState.GuestDemo
        userProfile.value = defaultGuestProfile
        groupMembers.value = defaultGuestMembers
        outings.value = defaultOutings
        groupChatMessages.value = defaultGroupChat
        stories.value = defaultStories
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
        groupOutingRsvpState.value = CloudLoadState.Loading
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
            groupOutingRsvpState.value = unavailableErr
            return
        }
        stopCloudObservers()

        profileJob = viewModelScope.launch {
            repo.observeUserProfile(uid)
                .catch { e ->
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
                    if (groupOutingRsvpState.value is CloudLoadState.Loading) {
                        groupOutingRsvpState.value = CloudLoadState.Error(
                            message = err.message,
                            hasCachedRealData = false
                        )
                    }
                    authStatusMessage.value = err.message
                }
                .collect { remoteProfile ->
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
        resetGroupOutingRsvpObserver()
        groupOutingRsvpState.value = CloudLoadState.Loaded
        observedGroupId = PERSONAL_SCOPE_SENTINEL
        groupJob?.cancel()
        membersJob?.cancel()
        invitesJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()
        groupJob = null
        membersJob = null
        invitesJob = null
        groupFeedJob = null
        groupFeedObservedScope = null
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)

        activeSocialGroup.value = null
        activeGroupMembers.value = emptyList()
        activeGroupInvites.value = emptyList()
        currentGroupRole.value = null
        groupMembers.value = emptyList()
        outings.value = emptyList()
        selectedOutingForDetail.value = null
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
                    outings.value = remoteOutings
                    socialSyncState.value = socialSyncState.value.copy(outingsState = CloudLoadState.Loaded)
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeChatMessages(uid)
                .catch { e ->
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
                    groupChatMessages.value = remoteMessages
                    socialSyncState.value = socialSyncState.value.copy(chatState = CloudLoadState.Loaded)
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeStories(uid)
                .catch { e ->
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
                    stories.value = remoteStories
                    socialSyncState.value = socialSyncState.value.copy(storiesState = CloudLoadState.Loaded)
                }
        }
    }

    private fun startGroupScopedObservers(uid: String, groupId: String, profile: UserProfile) {
        val repo = socialRepository ?: return
        resetGroupOutingRsvpObserver()
        groupOutingRsvpState.value = CloudLoadState.Loading
        observedGroupId = groupId
        groupJob?.cancel()
        membersJob?.cancel()
        invitesJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()
        invitesJob = null
        groupFeedJob = null
        groupFeedObservedScope = null
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.LOADING)

        activeSocialGroup.value = null
        activeGroupMembers.value = emptyList()
        activeGroupInvites.value = emptyList()
        currentGroupRole.value = null
        outings.value = emptyList()
        selectedOutingForDetail.value = null

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
                    Log.w("MainViewModel", "Group observer error: ${e.message}")
                    if (isCurrentGroupFeedScope(uid, groupId)) {
                        groupFeedState.value = GroupFeedState(
                            status = GroupFeedStatus.ERROR,
                            message = "No se pudo verificar el acceso al feed del grupo."
                        )
                    }
                        val err = SocialSessionPolicy.resolveErrorState(
                            scopeLabel = "el grupo",
                            throwable = e,
                            currentOrigin = socialSyncState.value.origin,
                            currentItemCount = groupMembers.value.size
                        )
                        socialSyncState.value = socialSyncState.value.copy(groupState = err)
                        failGroupOutingRsvpObserver("No se pudo verificar el acceso al grupo para cargar las respuestas.")
                }
                .collect { remoteGroup ->
                    activeSocialGroup.value = remoteGroup
                    if (remoteGroup != null) {
                        if (remoteGroup.name.isNotBlank() && userProfile.value.groupName != remoteGroup.name) {
                            userProfile.value = userProfile.value.copy(groupName = remoteGroup.name)
                        }
                        refreshDerivedGroupState(uid, remoteGroup, activeGroupMembers.value)
                        ensureGroupOutingRsvpObserver(uid, groupId, remoteGroup)
                        // Reversible self-backfill for ALREADY-EXISTING legacy memberIds members
                        if (remoteGroup.memberIds.contains(uid) &&
                            activeGroupMembers.value.none { it.userId == uid }
                        ) {
                            viewModelScope.launch {
                                repo.backfillLegacyMembershipIfNeeded(
                                    group = remoteGroup,
                                    userId = uid,
                                    displayName = userProfile.value.username.ifBlank { profile.username }
                                )
                            }
                        }
                    } else {
                        groupMembers.value = emptyList()
                        currentGroupRole.value = null
                        activeGroupMembers.value = emptyList()
                        failGroupOutingRsvpObserver("No se pudo verificar la generación del grupo para las respuestas.")
                        groupFeedJob?.cancel()
                        groupFeedJob = null
                        groupFeedObservedScope = null
                        groupFeedState.value = GroupFeedState(
                            status = GroupFeedStatus.ERROR,
                            message = "No tienes acceso al feed de este grupo."
                        )
                        ensureInvitesObserver(groupId, canManage = false)
                    }
                    socialSyncState.value = socialSyncState.value.copy(groupState = CloudLoadState.Loaded)
                }
        }

        membersJob = viewModelScope.launch {
            repo.observeGroupMembers(groupId)
                .catch { e ->
                    Log.w("MainViewModel", "Group members observer error: ${e.message}")
                    if (isCurrentGroupFeedScope(uid, groupId)) {
                        val message = SocialSessionPolicy.formatLoadErrorMessage(
                            scopeLabel = "la membresía de este grupo",
                            throwable = e
                        )
                        groupFeedState.value = GroupFeedState(
                            status = GroupFeedStatus.ERROR,
                            message = message
                        )
                    }
                    failGroupOutingRsvpObserver("No se pudo verificar tu membresía para responder a las salidas.")
                }
                .collect { v2Members ->
                    activeGroupMembers.value = v2Members
                    refreshDerivedGroupState(uid, activeSocialGroup.value, v2Members)
                    ensureGroupOutingRsvpObserver(uid, groupId, activeSocialGroup.value)
                }
        }

        outingsJob = viewModelScope.launch {
            repo.observeGroupOutings(groupId)
                .catch { e ->
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
                    // Real accounts NEVER auto-seed fictional demo outings into Firestore.
                    groupOutingsBase = remoteOutings
                    applyGroupOutingRsvpOverlay()
                    ensureGroupOutingRsvpObserver(uid, groupId, activeSocialGroup.value)
                    socialSyncState.value = socialSyncState.value.copy(outingsState = CloudLoadState.Loaded)
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeGroupChatMessages(groupId)
                .catch { e ->
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
                    groupChatMessages.value = remoteMessages
                    socialSyncState.value = socialSyncState.value.copy(chatState = CloudLoadState.Loaded)
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeGroupStories(groupId)
                .catch { e ->
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
                    stories.value = remoteStories
                    socialSyncState.value = socialSyncState.value.copy(storiesState = CloudLoadState.Loaded)
                }
        }
    }

    private fun refreshDerivedGroupState(
        uid: String,
        group: SocialGroup?,
        v2Members: List<GroupMember>
    ) {
        val merged = LinkedHashMap<String, Pair<String, GroupRole>>()
        // First add authoritative v2 subcollection members
        v2Members.forEach { member ->
            merged[member.userId] = member.displayName.ifBlank { "Miembro" } to member.groupRole
        }
        // Merge any un-backfilled legacy memberIds so no legacy member disappears during transition
        group?.memberIds?.forEach { legacyUid ->
            if (!merged.containsKey(legacyUid)) {
                val legacyName = group.memberNames[legacyUid]?.ifBlank { "Miembro" } ?: "Miembro"
                val legacyRole = if (group.createdBy == legacyUid) GroupRole.OWNER else GroupRole.MEMBER
                merged[legacyUid] = legacyName to legacyRole
            }
        }

        groupMembers.value = merged.entries.map { (memberUid, pair) ->
            val (name, role) = pair
            val selfSuffix = if (memberUid == uid) " (Tú)" else ""
            val roleSuffix = when (role) {
                GroupRole.OWNER -> " · Propietario"
                GroupRole.ADMIN -> " · Admin"
                GroupRole.MEMBER -> ""
            }
            "$name$selfSuffix$roleSuffix"
        }

        val resolvedRole = merged[uid]?.second
            ?: when {
                group != null && group.createdBy == uid -> GroupRole.OWNER
                else -> null
            }
        currentGroupRole.value = resolvedRole
        val activeGid = group?.id?.ifBlank { observedGroupId.orEmpty() } ?: observedGroupId.orEmpty()
        if (activeGid.isNotBlank() && activeGid != PERSONAL_SCOPE_SENTINEL) {
            ensureInvitesObserver(activeGid, canManage = resolvedRole?.canManageInvites == true)
            ensureGroupFeedObserver(uid, activeGid)
        }
    }

    private fun isCurrentGroupFeedScope(uid: String, groupId: String, generationId: String? = null): Boolean {
        return uid.isNotBlank() && groupId.isNotBlank() &&
            currentUserId.value == uid &&
            socialSyncState.value.origin == DataOrigin.CLOUD_REAL &&
            observedGroupId == groupId &&
            (generationId == null || activeSocialGroup.value?.let {
                it.id == groupId && it.generationId == generationId
            } == true)
    }

    private fun ensureGroupFeedObserver(uid: String, groupId: String) {
        val generationId = activeSocialGroup.value
            ?.takeIf { it.id == groupId }
            ?.generationId
            .orEmpty()
        if (!generationId.matches(Regex("^[a-f0-9]{32}$"))) {
            groupFeedJob?.cancel()
            groupFeedJob = null
            groupFeedObservedScope = null
            groupFeedState.value = GroupFeedState(
                status = GroupFeedStatus.ERROR,
                message = "No se pudo verificar la generación del grupo para abrir el feed."
            )
            return
        }
        if (!isCurrentGroupFeedScope(uid, groupId, generationId)) return
        val membership = activeGroupMembers.value.firstOrNull {
            it.userId == uid && it.groupId == groupId && it.groupGenerationId == generationId
        }
        if (membership == null) {
            groupFeedJob?.cancel()
            groupFeedJob = null
            groupFeedObservedScope = null
            groupFeedState.value = GroupFeedState(
                status = GroupFeedStatus.ERROR,
                message = "Tu membresía todavía no está confirmada para este feed privado."
            )
            return
        }

        val scope = "$uid::$groupId::$generationId"
        if (groupFeedObservedScope == scope && groupFeedJob?.isActive == true) return
        val repo = socialRepository ?: run {
            groupFeedState.value = GroupFeedState(
                status = GroupFeedStatus.ERROR,
                message = "Firebase no está disponible en esta compilación."
            )
            return
        }
        groupFeedJob?.cancel()
        groupFeedObservedScope = scope
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.LOADING)
        groupFeedJob = viewModelScope.launch {
            repo.observeGroupPosts(groupId)
                .catch { e ->
                    if (!isCurrentGroupFeedScope(uid, groupId, generationId)) return@catch
                    Log.w("MainViewModel", "Group feed observer error: ${e.message}")
                    groupFeedObservedScope = null
                    groupFeedState.value = GroupFeedState(
                        status = GroupFeedStatus.ERROR,
                        message = "No se pudo cargar el feed privado del grupo. Comprueba tu acceso y vuelve a actualizar."
                    )
                }
                .collect { posts ->
                    val stillMember = activeGroupMembers.value.any {
                        it.userId == uid && it.groupId == groupId && it.groupGenerationId == generationId
                    }
                    if (!isCurrentGroupFeedScope(uid, groupId, generationId) || !stillMember) return@collect
                    val currentGenerationPosts = posts.filter {
                        it.belongsToGeneration(groupId, generationId)
                    }
                    groupFeedState.value = groupFeedState.value.copy(
                        status = if (currentGenerationPosts.isEmpty()) GroupFeedStatus.EMPTY else GroupFeedStatus.READY,
                        posts = currentGenerationPosts,
                        message = null
                    )
                }
        }
    }

    private fun ensureInvitesObserver(groupId: String, canManage: Boolean) {
        val repo = socialRepository ?: return
        if (!canManage) {
            invitesJob?.cancel()
            invitesJob = null
            activeGroupInvites.value = emptyList()
            return
        }
        if (invitesJob != null) return
        invitesJob = viewModelScope.launch {
            repo.observeGroupInvites(groupId)
                .catch { e ->
                    Log.w("MainViewModel", "Group invites observer error: ${e.message}")
                }
                .collect { invites ->
                    activeGroupInvites.value = invites
            }
        }
    }

    private fun ensureGroupOutingRsvpObserver(uid: String, groupId: String, group: SocialGroup?) {
        val currentGroup = group?.takeIf { it.id == groupId }
        val generationId = currentGroup?.generationId.orEmpty()
        if (currentGroup == null || !generationId.matches(Regex("^[a-f0-9]{32}$"))) {
            failGroupOutingRsvpObserver(
                "No se puede verificar la generación actual del grupo. Las respuestas requieren una migración segura."
            )
            return
        }
        if (!isCurrentGroupFeedScope(uid, groupId, generationId)) return

        val hasCurrentV2Membership = activeGroupMembers.value.any {
            it.userId == uid && it.groupId == groupId && it.groupGenerationId == generationId
        }
        if (!hasCurrentV2Membership) {
            failGroupOutingRsvpObserver(
                "Tu membresía v2 aún no está verificada. No se mostrarán ni guardarán respuestas hasta confirmar el acceso seguro al grupo."
            )
            return
        }
        val currentMemberIds = activeGroupMembers.value
            .filter { it.groupId == groupId && it.groupGenerationId == generationId }
            .map { it.userId }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val repo = socialRepository ?: run {
            failGroupOutingRsvpObserver("Firebase no está disponible para cargar las respuestas del grupo.")
            return
        }
        val scope = "$uid::$groupId::$generationId::${currentMemberIds.joinToString("|")}"
        if (groupOutingRsvpObservedScope == scope && groupOutingRsvpsJob?.isActive == true) return

        groupOutingRsvpsJob?.cancel()
        groupOutingRsvpObservedScope = scope
        if (groupOutingRsvpCachedScope != scope) {
            groupOutingRsvpResponses = emptyList()
            groupOutingRsvpCachedScope = null
        }
        groupOutingRsvpState.value = CloudLoadState.Loading
        applyGroupOutingRsvpOverlay()
        groupOutingRsvpsJob = viewModelScope.launch {
            repo.observeGroupOutingRsvps(groupId, generationId, currentMemberIds)
                .catch { e ->
                    if (!isCurrentGroupFeedScope(uid, groupId, generationId)) return@catch
                    if (!isCurrentGroupRsvpScope(uid, groupId, generationId)) {
                        failGroupOutingRsvpObserver(
                            "Tu membresía v2 ya no está confirmada; las respuestas se han ocultado."
                        )
                        return@catch
                    }
                    Log.w("MainViewModel", "Group outing RSVP observer error: ${e.message}")
                    val message = SocialSessionPolicy.formatLoadErrorMessage(
                        scopeLabel = "las respuestas de asistencia del grupo",
                        throwable = e
                    )
                    val keepCachedSnapshot = SocialSessionPolicy.shouldRetainCachedGroupRsvpSnapshot(
                        scopeIsStillAuthorized = isCurrentGroupRsvpScope(uid, groupId, generationId),
                        failureIsTransient = isTransientGroupRsvpFailure(e)
                    )
                    if (keepCachedSnapshot) {
                        groupOutingRsvpObservedScope = null
                        groupOutingRsvpState.value = CloudLoadState.Error(
                            message = message,
                            hasCachedRealData = groupOutingRsvpCachedScope == scope
                        )
                    } else {
                        failGroupOutingRsvpObserver(message)
                    }
                }
                .collect { responses ->
                    if (!isCurrentGroupRsvpScope(uid, groupId, generationId)) return@collect
                    groupOutingRsvpResponses = responses
                    groupOutingRsvpCachedScope = scope
                    groupOutingRsvpState.value = CloudLoadState.Loaded
                    applyGroupOutingRsvpOverlay()
                }
        }
    }

    private fun isCurrentGroupRsvpScope(uid: String, groupId: String, generationId: String): Boolean {
        return isCurrentGroupFeedScope(uid, groupId, generationId) &&
            activeGroupMembers.value.any {
                it.userId == uid && it.groupId == groupId && it.groupGenerationId == generationId
            }
    }

    private fun applyGroupOutingRsvpOverlay() {
        val group = activeSocialGroup.value ?: return
        val groupId = group.id
        val generationId = group.generationId
        if (!generationId.matches(Regex("^[a-f0-9]{32}$"))) return

        val currentMemberIds = activeGroupMembers.value
            .filter { it.groupId == groupId && it.groupGenerationId == generationId }
            .map { it.userId }
            .toSet()
        val responsesByOuting = groupOutingRsvpResponses
            .filter { it.userId in currentMemberIds && it.belongsToCurrentGroup(groupId, generationId) }
            .groupBy { it.outingId }
        val uid = currentUserId.value
        val updated = groupOutingsBase.map { outing ->
            if (outing.groupId != groupId || outing.groupGenerationId != generationId) {
                outing
            } else {
                val responses = responsesByOuting[outing.id].orEmpty()
                outing.copy(
                    rsvpYesCount = responses.count { it.rsvpChoice == RsvpChoice.YES }.toLong(),
                    rsvpMaybeCount = responses.count { it.rsvpChoice == RsvpChoice.MAYBE }.toLong(),
                    rsvpNoCount = responses.count { it.rsvpChoice == RsvpChoice.NO }.toLong(),
                    myRsvp = responses.firstOrNull { it.userId == uid }?.choice ?: RsvpChoice.NONE.name,
                    rsvpByUser = emptyMap()
                )
            }
        }.sortedBy { it.dateIso + " " + it.time }
        outings.value = updated
        selectedOutingForDetail.value = selectedOutingForDetail.value?.let { selected ->
            updated.firstOrNull { it.id == selected.id } ?: selected
        }
    }

    private fun failGroupOutingRsvpObserver(message: String) {
        groupOutingRsvpsJob?.cancel()
        groupOutingRsvpsJob = null
        groupOutingRsvpObservedScope = null
        groupOutingRsvpCachedScope = null
        groupOutingRsvpResponses = emptyList()
        groupOutingRsvpState.value = CloudLoadState.Error(message, hasCachedRealData = false)
        applyGroupOutingRsvpOverlay()
    }

    private fun resetGroupOutingRsvpObserver() {
        groupOutingRsvpsJob?.cancel()
        groupOutingRsvpsJob = null
        groupOutingRsvpObservedScope = null
        groupOutingRsvpCachedScope = null
        groupOutingsBase = emptyList()
        groupOutingRsvpResponses = emptyList()
    }

    private fun findRsvpFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error as Throwable?) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun isTransientGroupRsvpFailure(error: Throwable): Boolean {
        return findRsvpFirestoreException(error)?.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.UNKNOWN
        )
    }

    private fun stopCloudObservers() {
        resetGroupOutingRsvpObserver()
        profileJob?.cancel()
        groupJob?.cancel()
        membersJob?.cancel()
        invitesJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        groupFeedJob?.cancel()
        profileJob = null
        groupJob = null
        membersJob = null
        invitesJob = null
        outingsJob = null
        chatJob = null
        storiesJob = null
        groupFeedJob = null
        groupFeedObservedScope = null
        groupFeedState.value = GroupFeedState(status = GroupFeedStatus.SIGNED_OUT)
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
        val uid = currentUserId.value
        val repo = socialRepository
        val activeGid = SocialSessionPolicy.resolveTargetGroupId(updated)
        if (uid != null && repo != null && activeGid.isNotBlank()) {
            viewModelScope.launch {
                repo.updateOwnMemberDisplayName(activeGid, uid, updated.username)
            }
        }
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

    fun clearInviteActionFeedback() {
        inviteActionFeedback.value = null
    }

    fun createNewOutingGroup(groupNameInput: String) {
        val cleanName = groupNameInput.trim().ifBlank { "Grupo de Salidas" }
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            isInviteActionBusy.value = true
            inviteActionFeedback.value = "Creando grupo seguro en la nube…"
            viewModelScope.launch {
                repo.createGroupAtomic(
                    groupName = cleanName,
                    creatorUid = uid,
                    creatorName = userProfile.value.username
                ).onSuccess { createdGroup ->
                    // groupInviteCode is intentionally empty: groupId is NEVER reused as an invite code.
                    val updated = userProfile.value.copy(
                        groupName = createdGroup.name,
                        groupInviteCode = "",
                        groupId = createdGroup.id
                    )
                    userProfile.value = updated
                    repo.saveUserProfile(updated, uid)
                    startGroupScopedObservers(uid, createdGroup.id, updated)
                    val msg = "Grupo '${createdGroup.name}' creado. Genera una invitación revocable para añadir personas."
                    inviteActionFeedback.value = msg
                    authStatusMessage.value = msg
                }.onFailure { err ->
                    val msg = "No se pudo crear el grupo en la nube: ${err.localizedMessage ?: "error de red o permisos"}"
                    inviteActionFeedback.value = msg
                    authStatusMessage.value = msg
                }
                isInviteActionBusy.value = false
            }
        } else {
            val demoGroupId = "demo_local_group"
            val updated = userProfile.value.copy(
                groupName = cleanName,
                groupInviteCode = "",
                groupId = demoGroupId
            )
            userProfile.value = updated
            currentGroupRole.value = GroupRole.OWNER
            activeGroupInvites.value = emptyList()
            groupMembers.value = listOf("${updated.username} (Tú) · Propietario")
            val msg = "Grupo '$cleanName' creado solo en modo demo local (inicia sesión para sincronizar)."
            inviteActionFeedback.value = msg
            authStatusMessage.value = msg
        }
    }

    fun generateGroupInvite(
        maxUses: Int = 1,
        expiresInHours: Int = 24,
        onCreated: (GroupInvite) -> Unit = {}
    ) {
        val uid = currentUserId.value
        val repo = socialRepository
        val activeGroupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value)
        val groupName = activeSocialGroup.value?.name?.ifBlank { userProfile.value.groupName }
            ?: userProfile.value.groupName.ifBlank { "Grupo" }

        if (activeGroupId.isBlank()) {
            inviteActionFeedback.value = "Primero crea o únete a un grupo para generar invitaciones."
            return
        }

        if (uid != null && repo != null) {
            if (currentGroupRole.value?.canManageInvites != true) {
                inviteActionFeedback.value = "Solo el propietario o administradores pueden crear invitaciones."
                return
            }
            isInviteActionBusy.value = true
            inviteActionFeedback.value = "Generando invitación segura de 160 bits…"
            viewModelScope.launch {
                repo.createGroupInvite(
                    groupId = activeGroupId,
                    groupNamePreview = groupName,
                    creatorUid = uid,
                    maxUses = maxUses.toLong(),
                    expiresInHours = expiresInHours.toLong()
                ).onSuccess { invite ->
                    val usesLabel = if (maxUses == 1) "1 uso" else "$maxUses usos"
                    inviteActionFeedback.value = "Invitación creada ($usesLabel · ${expiresInHours}h). Compártela o revócala cuando quieras."
                    onCreated(invite)
                }.onFailure { err ->
                    inviteActionFeedback.value = "No se pudo crear la invitación: ${err.localizedMessage ?: "sin permisos o sin conexión"}"
                }
                isInviteActionBusy.value = false
            }
        } else {
            val demoToken = GroupInviteCrypto.generateHighEntropyInviteToken()
            val demoInvite = GroupInvite(
                token = demoToken,
                groupId = activeGroupId,
                groupNamePreview = groupName,
                createdBy = SocialSessionPolicy.GUEST_DEMO_USER_ID,
                revoked = false,
                expiresAt = com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + expiresInHours * 3_600_000L)),
                maxUses = maxUses.toLong(),
                useCount = 0L
            )
            activeGroupInvites.value = listOf(demoInvite) + activeGroupInvites.value
            inviteActionFeedback.value = "Invitación de demostración local creada (no sincronizada en la nube)."
            onCreated(demoInvite)
        }
    }

    fun revokeGroupInvite(token: String) {
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            isInviteActionBusy.value = true
            viewModelScope.launch {
                repo.revokeGroupInvite(token)
                    .onSuccess {
                        inviteActionFeedback.value = "Invitación revocada. Ya no podrá usarse para entrar al grupo."
                    }
                    .onFailure { err ->
                        inviteActionFeedback.value = "No se pudo revocar la invitación: ${err.localizedMessage ?: "error de permisos"}"
                    }
                isInviteActionBusy.value = false
            }
        } else {
            activeGroupInvites.value = activeGroupInvites.value.map {
                if (it.token == token) it.copy(revoked = true) else it
            }
            inviteActionFeedback.value = "Invitación local revocada."
        }
    }

    fun joinOutingGroupByCode(inviteCodeInput: String, groupNameHint: String = "") {
        val normalized = GroupInviteCrypto.normalizeInviteToken(inviteCodeInput)
        if (normalized.isBlank()) {
            val msg = "Pega un token de invitación válido (INV-…)."
            inviteActionFeedback.value = msg
            authStatusMessage.value = msg
            return
        }
        if (GroupInviteCrypto.isLegacyGroupCode(inviteCodeInput)) {
            val msg = "Los códigos antiguos de 4 dígitos (ej. QUEDA-2026) ya no permiten autounirse por seguridad. Pide al propietario del grupo una invitación nueva (INV-…)."
            inviteActionFeedback.value = msg
            authStatusMessage.value = msg
            return
        }

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            isInviteActionBusy.value = true
            inviteActionFeedback.value = "Validando invitación y uniéndote de forma atómica…"
            viewModelScope.launch {
                when (
                    val outcome = repo.redeemInviteAndJoinGroupAtomic(
                        rawTokenInput = inviteCodeInput,
                        userId = uid,
                        displayName = userProfile.value.username
                    )
                ) {
                    is InviteRedemptionOutcome.Joined -> {
                        val updated = userProfile.value.copy(
                            groupId = outcome.group.id,
                            groupInviteCode = "",
                            groupName = outcome.group.name.ifBlank { groupNameHint.ifBlank { "Grupo" } }
                        )
                        userProfile.value = updated
                        repo.saveUserProfile(updated, uid)
                        startGroupScopedObservers(uid, outcome.group.id, updated)
                        val msg = if (outcome.alreadyMember) {
                            "Ya perteneces a '${updated.groupName}' (no se consumió ningún uso adicional)."
                        } else {
                            "¡Te has unido a '${updated.groupName}'!"
                        }
                        inviteActionFeedback.value = msg
                        authStatusMessage.value = msg
                    }
                    is InviteRedemptionOutcome.LegacyCodeUnsupported -> {
                        val msg = "Los códigos antiguos (ej. QUEDA-2026) ya no admiten autounirse. Pide al propietario una invitación segura (INV-…)."
                        inviteActionFeedback.value = msg
                        authStatusMessage.value = msg
                    }
                    is InviteRedemptionOutcome.InvalidOrUnavailable -> {
                        inviteActionFeedback.value = outcome.message
                        authStatusMessage.value = outcome.message
                    }
                    is InviteRedemptionOutcome.Error -> {
                        inviteActionFeedback.value = outcome.message
                        authStatusMessage.value = outcome.message
                    }
                }
                isInviteActionBusy.value = false
            }
        } else {
            if (!GroupInviteCrypto.isHighEntropyInviteToken(normalized)) {
                val msg = "Formato de token no válido. Debe empezar por INV- y contener el código completo."
                inviteActionFeedback.value = msg
                authStatusMessage.value = msg
                return
            }
            val displayGroup = groupNameHint.trim().ifBlank { "Grupo Demo" }
            val updated = userProfile.value.copy(
                groupId = "demo_joined_group",
                groupInviteCode = "",
                groupName = displayGroup
            )
            userProfile.value = updated
            currentGroupRole.value = GroupRole.MEMBER
            val msg = "Unión simulada solo en modo demo local. Inicia sesión con Google para unirte a un grupo real."
            inviteActionFeedback.value = msg
            authStatusMessage.value = msg
        }
    }

    fun leaveCurrentGroup() {
        val uid = currentUserId.value
        val repo = socialRepository
        val activeGroupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value)
        if (activeGroupId.isBlank()) return

        if (uid != null && repo != null) {
            if (currentGroupRole.value == GroupRole.OWNER) {
                inviteActionFeedback.value = "Eres el propietario del grupo; las reglas protegen la propiedad para evitar grupos huérfanos."
                return
            }
            isInviteActionBusy.value = true
            viewModelScope.launch {
                repo.leaveGroup(activeGroupId, uid)
                    .onSuccess {
                        val updated = userProfile.value.copy(
                            groupId = "",
                            groupInviteCode = "",
                            groupName = ""
                        )
                        userProfile.value = updated
                        repo.saveUserProfile(updated, uid)
                        startPersonalScopedObservers(uid)
                        val msg = "Has salido del grupo correctamente."
                        inviteActionFeedback.value = msg
                        authStatusMessage.value = msg
                    }
                    .onFailure { err ->
                        inviteActionFeedback.value = "No se pudo salir del grupo: ${err.localizedMessage ?: "error de permisos o conexión"}"
                    }
                isInviteActionBusy.value = false
            }
        } else {
            val updated = userProfile.value.copy(groupId = "", groupInviteCode = "", groupName = "")
            userProfile.value = updated
            activeGroupInvites.value = emptyList()
            groupMembers.value = emptyList()
            currentGroupRole.value = null
            inviteActionFeedback.value = "Has salido del grupo local (modo demo)."
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

    // --- World 1: Outing CRUD, Calendar & Map Actions ---
    fun saveOrUpdateOuting(outing: Outing) {
        val existingList = outings.value.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == outing.id && outing.id.isNotBlank() }
        val activeGroupId = resolveActiveWriteGroupId()
        val finalOuting = if (outing.id.isBlank()) {
            outing.copy(
                id = "outing_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                ownerId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
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
        if (!target.groupId.startsWith("personal_") && !socialSyncState.value.isGuestDemo) {
            val uid = currentUserId.value ?: return
            val group = activeSocialGroup.value
            val generationId = target.groupGenerationId
            if (group?.id != target.groupId || group.generationId != generationId ||
                groupOutingRsvpState.value !is CloudLoadState.Loaded ||
                !isCurrentGroupRsvpScope(uid, target.groupId, generationId)
            ) {
                val message = (groupOutingRsvpState.value as? CloudLoadState.Error)?.message
                    ?: "Tu acceso v2 al grupo aún no está verificado; no se ha guardado ninguna respuesta."
                authStatusMessage.value = message
                return
            }
            val repo = socialRepository ?: return
            playFeedbackSound(false)
            viewModelScope.launch {
                repo.updateGroupOutingRsvpTransactional(
                    outingId = outingId,
                    groupId = target.groupId,
                    groupGenerationId = generationId,
                    choice = choice,
                    userId = uid
                ).onFailure { err ->
                    val message = "No se pudo guardar tu respuesta: ${err.localizedMessage ?: "comprueba tu acceso y conexión"}"
                    if (isCurrentGroupFeedScope(uid, target.groupId, generationId) &&
                        (!isCurrentGroupRsvpScope(uid, target.groupId, generationId) ||
                            findRsvpFirestoreException(err)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    ) {
                        failGroupOutingRsvpObserver(message)
                    }
                    authStatusMessage.value = message
                }
            }
            return
        }

        val uid = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID

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
        val target = outings.value.firstOrNull { it.id == outingId } ?: return
        if (!socialSyncState.value.isGuestDemo && !target.groupId.startsWith("personal_")) {
            authStatusMessage.value =
                "Las salidas grupales no se borran para conservar sus respuestas; márcala como cancelada."
            return
        }
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

    // --- World 1: Private group feed ---
    fun visibleGroupFeedPosts(posts: List<GroupPost>): List<GroupPost> {
        val uid = currentUserId.value ?: return emptyList()
        val group = activeSocialGroup.value ?: return emptyList()
        val generationId = group.generationId
        val isV2Member = activeGroupMembers.value.any {
            it.userId == uid && it.groupId == group.id && it.groupGenerationId == generationId
        }
        if (!isV2Member || !isCurrentGroupFeedScope(uid, group.id, generationId)) return emptyList()
        return posts.filter { it.belongsToGeneration(group.id, generationId) }
    }

    fun retryGroupFeed() {
        val uid = currentUserId.value ?: return
        val groupId = activeSocialGroup.value?.id ?: return
        if (!isCurrentGroupFeedScope(uid, groupId)) return
        groupFeedJob?.cancel()
        groupFeedJob = null
        groupFeedObservedScope = null
        ensureGroupFeedObserver(uid, groupId)
    }

    fun retryGroupOutingRsvps() {
        val uid = currentUserId.value ?: return
        val group = activeSocialGroup.value ?: return
        val generationId = group.generationId
        if (!isCurrentGroupRsvpScope(uid, group.id, generationId)) {
            failGroupOutingRsvpObserver(
                "Tu membresía v2 ya no está confirmada; las respuestas se han ocultado."
            )
            return
        }
        groupOutingRsvpsJob?.cancel()
        groupOutingRsvpsJob = null
        groupOutingRsvpObservedScope = null
        ensureGroupOutingRsvpObserver(uid, group.id, group)
    }

    fun createGroupPost(text: String) {
        val uid = currentUserId.value
        val repo = socialRepository
        val group = activeSocialGroup.value
        val cleanText = text.trim().take(2000)
        if (uid.isNullOrBlank() || repo == null || group == null) {
            groupFeedState.value = groupFeedState.value.copy(
                status = GroupFeedStatus.ERROR,
                message = "Inicia sesión y entra en un grupo para publicar."
            )
            return
        }
        if (cleanText.isBlank()) return
        if (groupFeedState.value.isPublishing) return
        val membership = activeGroupMembers.value.firstOrNull {
            it.belongsToActiveGroup(uid, group)
        }
        if (membership == null || !isCurrentGroupFeedScope(uid, group.id)) {
            groupFeedState.value = groupFeedState.value.copy(
                status = GroupFeedStatus.ERROR,
                message = "Tu membresía todavía no está confirmada para publicar en este grupo."
            )
            return
        }

        val groupId = group.id
        val authorName = membership.displayName.trim().ifBlank { "Miembro" }
        groupFeedState.value = groupFeedState.value.copy(
            isPublishing = true,
            message = null,
            actionMessage = null
        )
        viewModelScope.launch {
            repo.createGroupPost(
                GroupPost(
                    authorId = uid,
                    authorName = authorName,
                    groupId = groupId,
                    text = cleanText
                ),
                uid
            ).onSuccess {
                if (isCurrentGroupFeedScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        isPublishing = false,
                        actionMessage = null,
                        publishSuccessToken = groupFeedState.value.publishSuccessToken + 1,
                        publishSuccessGroupId = groupId
                    )
                }
            }.onFailure { err ->
                if (isCurrentGroupFeedScope(uid, groupId)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        isPublishing = false,
                        actionMessage = "No se pudo publicar: ${err.localizedMessage ?: "revisa tu conexión y acceso"}"
                    )
                }
            }
        }
    }

    fun deleteGroupPost(postId: String) {
        val uid = currentUserId.value ?: return
        val group = activeSocialGroup.value ?: return
        val post = groupFeedState.value.posts.firstOrNull { it.id == postId } ?: return
        val isV2Member = activeGroupMembers.value.any { it.belongsToActiveGroup(uid, group) }
        if (post.authorId != uid || post.groupId != group.id || !isV2Member ||
            !isCurrentGroupFeedScope(uid, group.id) || postId in groupFeedState.value.deletingPostIds
        ) return
        val repo = socialRepository ?: return
        groupFeedState.value = groupFeedState.value.copy(
            deletingPostIds = groupFeedState.value.deletingPostIds + postId,
            actionMessage = null
        )
        viewModelScope.launch {
            repo.deleteGroupPost(postId, group.id, uid).onSuccess {
                if (isCurrentGroupFeedScope(uid, group.id)) {
                    groupFeedState.value = groupFeedState.value.copy(
                        deletingPostIds = groupFeedState.value.deletingPostIds - postId,
                        actionMessage = null
                    )
                }
            }.onFailure { err ->
                if (isCurrentGroupFeedScope(uid, group.id)) {
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
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value),
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
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value),
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
            senderId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            senderName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value),
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
            authorId = currentUserId.value ?: SocialSessionPolicy.GUEST_DEMO_USER_ID,
            authorName = userProfile.value.username.ifBlank { "Usuario" },
            groupId = SocialSessionPolicy.resolveTargetGroupId(userProfile.value),
            caption = caption.ifBlank { "Recuerdo compartido con el grupo" },
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
