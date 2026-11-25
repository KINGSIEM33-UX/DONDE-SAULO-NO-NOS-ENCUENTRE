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
import com.example.data.Outing
import com.example.data.OutingStatus
import com.example.data.OutingStory
import com.example.data.RecentChapter
import com.example.data.Repository
import com.example.data.RsvpChoice
import com.example.data.SocialRepository
import com.example.data.UserProfile
import com.example.data.VerseNote
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
import java.util.Calendar
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

    // --- Two Worlds Switcher State ---
    val currentWorld = MutableStateFlow(AppWorld.WORLD_1_SOCIAL)
    val world1Tab = MutableStateFlow("profile") // "profile", "outings", "map", "chat", "games"
    val showSettingsModal = MutableStateFlow(false)
    val showSocialAgentModal = MutableStateFlow(false)
    val showCameraStoriesModal = MutableStateFlow(false)
    val showOutingHistoryModal = MutableStateFlow(false)
    val defaultCurrency = MutableStateFlow("€")
    val notificationsEnabled = MutableStateFlow(true)
    val soundEffectsEnabled = MutableStateFlow(true)

    // --- Theme State (Defaults to Modern Social Light Mode) ---
    val themeMode = MutableStateFlow(ThemeMode.SOCIAL_CLARO)

    // --- Auth & Cloud Session State ---
    val currentUserId = MutableStateFlow<String?>(null)
    val isUserLoggedIn = MutableStateFlow(false)
    val userEmail = MutableStateFlow("")
    val authStatusMessage = MutableStateFlow<String?>(null)

    private var profileJob: Job? = null
    private var outingsJob: Job? = null
    private var chatJob: Job? = null
    private var storiesJob: Job? = null

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
        }
    }

    // --- World 1: User Profile, Tastes/Curiosities & Group ---
    val userProfile = MutableStateFlow(
        UserProfile(
            userId = "local_user",
            username = "Alex_Quedadas",
            bio = "Organizando los mejores planes del grupo ✨ | Mapas, rutas y juegos",
            avatarUrl = "avatar_neon",
            curiosities = listOf(
                "🍕 Fanático de las pizzas artesanales",
                "🎳 Campeón invicto en los bolos",
                "🏔️ Rutas de montaña los sábados",
                "🎮 Rey del Impostor y 2048",
                "📸 Fotógrafo oficial de las salidas"
            ),
            groupId = "GRUPO-QUEDADAS",
            groupInviteCode = "QUEDA-2026",
            groupName = "Peña Los Inseparables 🔥",
            gamesHighScore = 340L
        )
    )

    val groupMembers = MutableStateFlow(
        listOf(
            "Alex (Tú) 👑",
            "Sofía ✨",
            "Mateo 🎸",
            "Valeria 📸",
            "Lucas 🍕",
            "Elena 🌟"
        )
    )

    // --- World 1: Outings (Salidas y Quedadas) ---
    // Includes Tuesday Nov 10 Confirmed (double green circle), Planned (gray circle), and Cancelled (red circle)
    private val defaultOutings = listOf(
        Outing(
            id = "outing_nov10_confirmed",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Cena & Noche de Bolera",
            location = "Ozone Bowling & Burger Plaza",
            meetingPoint = "Fuente de la Plaza Mayor (Entrada Norte)",
            dateIso = "2026-11-10", // Martes 10 de noviembre (Doble círculo verde)
            time = "20:30",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "18 €",
            kitItems = "Calcetines para bolera, carnet joven y batería portátil",
            additionalInfo = "¡Reserva confirmada a nombre de Alex! Puntualidad para no perder la pista 4.",
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
            id = "outing_nov14_planned",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Ruta de Senderismo y Picnic",
            location = "Mirador del Valle Verde",
            meetingPoint = "Estación Central - Andén 2",
            dateIso = "2026-11-14", // Sábado 14 de noviembre (Círculo gris: Pensada)
            time = "09:30",
            status = OutingStatus.PLANNED.name,
            budgetPerPerson = "12 €",
            kitItems = "Mochila, 1.5L de agua, gorra, bocadillo y manta de picnic",
            additionalInfo = "Pendiente de confirmar según el clima del jueves.",
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
            id = "outing_nov19_cancelled",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Cine al Aire Libre",
            location = "Terraza Parque del Sur",
            meetingPoint = "Puerta de Alcalá",
            dateIso = "2026-11-19", // Jueves 19 de noviembre (Círculo rojo: Cancelada)
            time = "21:00",
            status = OutingStatus.CANCELLED.name,
            budgetPerPerson = "10 €",
            kitItems = "Chaqueta abrigada y palomitas",
            additionalInfo = "Cancelada por aviso de lluvia intensa. La moveremos al próximo mes.",
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
            id = "outing_nov25_confirmed",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Tarde de Escape Room & Helados",
            location = "Enigma Escape Center",
            meetingPoint = "Cafetería Sol (Esquina Reloj)",
            dateIso = "2026-11-25",
            time = "18:00",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "22 €",
            kitItems = "Gafas de ver, ropa cómoda y muchas neuronas",
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
            id = "outing_history_2025",
            ownerId = "local_user",
            groupId = "QUEDA-2026",
            title = "Barbacoa de Verano 2025",
            location = "Lago Azul Club",
            meetingPoint = "Parking Norte",
            dateIso = "2025-08-16",
            time = "13:00",
            status = OutingStatus.CONFIRMED.name,
            budgetPerPerson = "25 €",
            kitItems = "Bañador, toalla, protector solar y hielo",
            additionalInfo = "Una de las mejores salidas históricas del grupo.",
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

    // --- World 1: Group Social Chat Messages ---
    private val defaultGroupChat = listOf(
        GroupChatMessage(
            id = "msg_1",
            senderId = "sofia_1",
            senderName = "Sofía ✨",
            groupId = "QUEDA-2026",
            text = "¡Chicos! Ya marqué en el calendario el martes 10 de noviembre con el doble circulito verde 🟢 para la Cena y Bolera.",
            messageType = "TEXT",
            reactions = listOf("🔥", "❤️")
        ),
        GroupChatMessage(
            id = "msg_2",
            senderId = "mateo_2",
            senderName = "Mateo 🎸",
            groupId = "QUEDA-2026",
            text = "Nota de voz (0:08)",
            messageType = "VOICE",
            audioDurationSec = 8L,
            transcription = "Transcripción: ¡Genial! Yo llevo la batería portátil y nos vemos en el punto de encuentro de la Plaza Mayor a las ocho y media.",
            reactions = listOf("🙌", "👍")
        ),
        GroupChatMessage(
            id = "msg_3",
            senderId = "valeria_3",
            senderName = "Valeria 📸",
            groupId = "QUEDA-2026",
            text = "He subido una instantánea nueva en la camarita de Salidas 📷✨ ¡Miradla!",
            messageType = "OUTING_SHARE",
            sharedOutingId = "outing_nov10_confirmed",
            sharedOutingTitle = "Cena & Noche de Bolera (Mar 10 Nov • 18 €)",
            reactions = listOf("❤️")
        )
    )
    val groupChatMessages = MutableStateFlow<List<GroupChatMessage>>(defaultGroupChat)

    // --- World 1: Instantáneas (Stories) ---
    private val defaultStories = listOf(
        OutingStory(
            id = "story_1",
            authorId = "valeria_3",
            authorName = "Valeria 📸",
            caption = "¡Calentando motores para el martes 10 en la bolera! 🎳🔥",
            filterStyle = "NEON_VIBES",
            mediaUri = "story_bowling_neon",
            outingTitle = "Cena & Noche de Bolera",
            likesCount = 14L
        ),
        OutingStory(
            id = "story_2",
            authorId = "sofia_1",
            authorName = "Sofía ✨",
            caption = "Vistas del Mirador para la ruta del sábado 🏔️🌿",
            filterStyle = "GOLDEN_SUNSET",
            mediaUri = "story_mountain_sunset",
            outingTitle = "Ruta de Senderismo y Picnic",
            likesCount = 9L
        ),
        OutingStory(
            id = "story_3",
            authorId = "mateo_2",
            authorName = "Mateo 🎸",
            caption = "¿Quién ganará hoy al Impostor en la quedada? 🕵️‍♂️🍕",
            filterStyle = "CYBER_RETRO",
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
                "¡Hola! Soy tu Agente IA de Salidas y Quedadas 🤖✨. Puedo crear salidas automáticamente en el calendario y el mapa, cambiar su color (Confirmada 🟢, Pensada ⚪, Cancelada 🔴), anotar tu asistencia o sugerirte planes y presupuestos. ¿Qué organizamos hoy?"
            )
        )
    )
    val isSocialAgentBusy = MutableStateFlow(false)

    // --- World 2: Bible & Spiritual Study State (Preserved) ---
    val searchTerm = MutableStateFlow("")
    val activeCategory = MutableStateFlow("Todos")

    val favoriteVerses: StateFlow<List<FavoriteVerse>> = repository.allFavoriteVerses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChapters: StateFlow<List<RecentChapter>> = repository.recentChapters
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
                "Bienvenido al Mundo Bíblico de estudio y reflexión espiritual. Dialoguemos sobre las Escrituras, el Tabernáculo o cualquier pasaje que desees profundizar."
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
        )
    )

    val currentTrivia = MutableStateFlow(triviaPool[0])
    val triviaAnsweredStatus = MutableStateFlow<Int?>(null)

    private val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    var chapterExplanation = MutableStateFlow<String?>(null)
    var isGeneratingExplanation = MutableStateFlow(false)
    var rawChapterExplanation = MutableStateFlow<String?>(null)
    var isGeneratingRawExplanation = MutableStateFlow(false)

    init {
        updateAchievements()
        try {
            FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        } catch (e: Exception) {
            Log.w("MainViewModel", "FirebaseAuth listener registration skipped: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        } catch (_: Exception) {
        }
        stopCloudObservers()
    }

    // --- Cloud Realtime Observers ---
    private fun startCloudObservers(uid: String) {
        val repo = socialRepository ?: return
        stopCloudObservers()

        profileJob = viewModelScope.launch {
            repo.observeUserProfile(uid)
                .catch { e -> Log.w("MainViewModel", "Profile observer error: ${e.message}") }
                .collect { remoteProfile ->
                    if (remoteProfile != null) {
                        userProfile.value = remoteProfile
                    } else {
                        repo.saveUserProfile(userProfile.value.copy(userId = uid), uid)
                    }
                }
        }

        outingsJob = viewModelScope.launch {
            repo.observeOutings(uid)
                .catch { e -> Log.w("MainViewModel", "Outings observer error: ${e.message}") }
                .collect { remoteOutings ->
                    if (remoteOutings.isNotEmpty()) {
                        outings.value = remoteOutings
                    } else {
                        // Seed initial outings to user's cloud account on first login
                        defaultOutings.forEach { seed ->
                            repo.createOuting(seed.copy(ownerId = uid), uid)
                        }
                    }
                }
        }

        chatJob = viewModelScope.launch {
            repo.observeChatMessages(uid)
                .catch { e -> Log.w("MainViewModel", "Chat observer error: ${e.message}") }
                .collect { remoteMessages ->
                    if (remoteMessages.isNotEmpty()) {
                        val combined = (defaultGroupChat + remoteMessages)
                            .distinctBy { it.id }
                        groupChatMessages.value = combined
                    }
                }
        }

        storiesJob = viewModelScope.launch {
            repo.observeStories(uid)
                .catch { e -> Log.w("MainViewModel", "Stories observer error: ${e.message}") }
                .collect { remoteStories ->
                    if (remoteStories.isNotEmpty()) {
                        val combined = (remoteStories + defaultStories).distinctBy { it.id }
                        stories.value = combined
                    }
                }
        }
    }

    private fun stopCloudObservers() {
        profileJob?.cancel()
        outingsJob?.cancel()
        chatJob?.cancel()
        storiesJob?.cancel()
        profileJob = null
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
            themeMode.value = ThemeMode.SOCIAL_CLARO
        }
    }

    fun toggleDarkLightSocialTheme() {
        themeMode.value = if (themeMode.value == ThemeMode.SOCIAL_OSCURO) {
            ThemeMode.SOCIAL_CLARO
        } else {
            ThemeMode.SOCIAL_OSCURO
        }
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
        persistProfileIfAuthenticated(updated)
        authStatusMessage.value = "Grupo '$cleanName' creado con código $code"
    }

    fun joinOutingGroupByCode(inviteCodeInput: String, groupNameHint: String = "") {
        val cleanCode = inviteCodeInput.trim().uppercase()
        if (cleanCode.isBlank()) return
        val displayGroup = groupNameHint.trim().ifBlank { "Grupo $cleanCode" }
        val updated = userProfile.value.copy(
            groupId = cleanCode,
            groupInviteCode = cleanCode,
            groupName = displayGroup
        )
        userProfile.value = updated
        if (!groupMembers.value.any { it.contains("Nuevo miembro") }) {
            groupMembers.value = groupMembers.value + "Miembro ($cleanCode) 🎉"
        }
        persistProfileIfAuthenticated(updated)
        authStatusMessage.value = "¡Te has unido al grupo con el código $cleanCode!"
    }

    fun addMiniGameScore(points: Int) {
        val newScore = userProfile.value.gamesHighScore + points
        val updated = userProfile.value.copy(gamesHighScore = newScore)
        userProfile.value = updated
        persistProfileIfAuthenticated(updated)
    }

    private fun persistProfileIfAuthenticated(profile: UserProfile) {
        val uid = currentUserId.value ?: return
        val repo = socialRepository ?: return
        viewModelScope.launch {
            repo.saveUserProfile(profile, uid)
        }
    }

    // --- World 1: Outing CRUD, Calendar & Map Actions ---
    fun saveOrUpdateOuting(outing: Outing) {
        val existingList = outings.value.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == outing.id && outing.id.isNotBlank() }
        val finalOuting = if (outing.id.isBlank()) {
            outing.copy(
                id = "outing_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                ownerId = currentUserId.value ?: "local_user",
                groupId = userProfile.value.groupInviteCode
            )
        } else {
            outing
        }

        if (existingIdx >= 0) {
            existingList[existingIdx] = finalOuting
        } else {
            existingList.add(finalOuting)
        }
        outings.value = existingList.sortedBy { it.dateIso + " " + it.time }

        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                if (existingIdx >= 0) {
                    repo.updateOuting(finalOuting.copy(ownerId = uid))
                } else {
                    repo.createOuting(finalOuting.copy(ownerId = uid), uid)
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

        val updated = target.copy(
            myRsvp = choice.name,
            rsvpYesCount = yes,
            rsvpMaybeCount = maybe,
            rsvpNoCount = no
        )
        saveOrUpdateOuting(updated)
        if (selectedOutingForDetail.value?.id == outingId) {
            selectedOutingForDetail.value = updated
        }
    }

    fun updateOutingCoordinates(
        outingId: String,
        isDestination: Boolean,
        lat: Double,
        lng: Double,
        placeLabel: String? = null
    ) {
        val target = outings.value.firstOrNull { it.id == outingId } ?: outings.value.firstOrNull() ?: return
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
                repo.deleteOuting(outingId)
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
            groupId = userProfile.value.groupInviteCode,
            text = clean,
            messageType = "TEXT"
        )
        groupChatMessages.value = groupChatMessages.value + msg
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid)
            }
        }
    }

    fun sendGroupVoiceMessage(durationSec: Int, transcriptionText: String) {
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: "local_user",
            senderName = userProfile.value.username,
            groupId = userProfile.value.groupInviteCode,
            text = "Nota de voz (0:${durationSec.toString().padStart(2, '0')})",
            messageType = "VOICE",
            audioDurationSec = durationSec.toLong(),
            transcription = "Transcripción: $transcriptionText"
        )
        groupChatMessages.value = groupChatMessages.value + msg
        val uid = currentUserId.value
        val repo = socialRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.sendChatMessage(msg, uid)
            }
        }
    }

    fun shareOutingToGroupChat(outing: Outing) {
        val msg = GroupChatMessage(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            senderId = currentUserId.value ?: "local_user",
            senderName = userProfile.value.username,
            groupId = userProfile.value.groupInviteCode,
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
                repo.sendChatMessage(msg, uid)
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
                repo.updateMessageReactions(messageId, newReactions)
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
                repo.createStory(story, uid)
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
                repo.likeStory(storyId, newCount)
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
                            dateIso = actionResp.dateIso.ifBlank { "2026-11-10" },
                            time = actionResp.time.ifBlank { "20:00" },
                            status = actionResp.status.ifBlank { OutingStatus.CONFIRMED.name },
                            budgetPerPerson = actionResp.budgetPerPerson.ifBlank { "15 €" },
                            kitItems = actionResp.kitItems,
                            additionalInfo = actionResp.additionalInfo
                        )
                        saveOrUpdateOuting(newOuting)
                        executionBanner = "\n\n✅ [Acción ejecutada: Salida '${newOuting.title}' añadida al calendario (${newOuting.dateIso}) y al mapa con estado ${newOuting.outingStatus.label}]"
                    }
                    "UPDATE_STATUS" -> {
                        val target = outings.value.firstOrNull {
                            actionResp.targetTitleQuery.isNotBlank() &&
                                it.title.contains(actionResp.targetTitleQuery, ignoreCase = true)
                        } ?: outings.value.firstOrNull()
                        if (target != null) {
                            val newSt = OutingStatus.fromString(actionResp.status)
                            updateOutingStatusColor(target.id, newSt)
                            executionBanner = "\n\n✅ [Acción ejecutada: Estado de '${target.title}' cambiado a ${newSt.label}]"
                        }
                    }
                    "RSVP" -> {
                        val target = outings.value.firstOrNull {
                            actionResp.targetTitleQuery.isNotBlank() &&
                                it.title.contains(actionResp.targetTitleQuery, ignoreCase = true)
                        } ?: outings.value.firstOrNull()
                        if (target != null) {
                            val rsvp = RsvpChoice.fromString(actionResp.rsvpChoice)
                            updateOutingRsvp(target.id, rsvp)
                            executionBanner = "\n\n✅ [Acción ejecutada: Asistencia marcada como '${rsvp.label}' en '${target.title}']"
                        }
                    }
                    "UPDATE_KIT" -> {
                        val target = outings.value.firstOrNull()
                        if (target != null && actionResp.kitItems.isNotBlank()) {
                            saveOrUpdateOuting(target.copy(kitItems = actionResp.kitItems))
                            executionBanner = "\n\n✅ [Acción ejecutada: Kit de la Quedada actualizado en '${target.title}']"
                        }
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

    // --- World 2: Preserved Bible & Spiritual Methods ---
    fun saveRecentChapter(bibleId: String, chapterId: String, reference: String) {
        viewModelScope.launch {
            repository.insertRecentChapter(
                RecentChapter(
                    chapterId = chapterId,
                    bibleId = bibleId,
                    reference = reference
                )
            )
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
        viewModelScope.launch {
            repository.insertVerseNote(
                VerseNote(
                    sectionId = sectionId,
                    verseReference = verseRef,
                    noteText = note
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
        if (prompt.isBlank()) return
        val currentList = chatMessages.value.toMutableList()
        currentList.add(ChatMessage("user", prompt))
        chatMessages.value = currentList
        isGeneratingResponse.value = true

        viewModelScope.launch {
            val answer = GeminiClient.chatWithSaul(prompt)
            var displayAnswer = answer
            if (displayAnswer.contains("[CMD:FONT:")) {
                val regex = "\\[CMD:FONT:(\\d+)]".toRegex()
                val match = regex.find(displayAnswer)
                match?.let {
                    val size = it.groupValues[1].toFloatOrNull()
                    if (size != null) updateTextSize(size)
                    displayAnswer = displayAnswer.replace(it.value, "")
                }
            }
            if (displayAnswer.contains("[CMD:THEME:ZEN]")) {
                updateThemeMode(ThemeMode.MINIMALISTA)
                displayAnswer = displayAnswer.replace("[CMD:THEME:ZEN]", "")
            }
            if (displayAnswer.contains("[CMD:EVENT:")) {
                val regex = "\\[CMD:EVENT:(.*?)]".toRegex()
                val match = regex.find(displayAnswer)
                match?.let {
                    displayAnswer = displayAnswer.replace(it.value, "\n[Evento agendado: ${it.groupValues[1]}]\n")
                }
            }
            if (displayAnswer.contains("[CMD:VERSE:")) {
                val regex = "\\[CMD:VERSE:(.*?)]".toRegex()
                val match = regex.find(displayAnswer)
                match?.let {
                    setQuery(it.groupValues[1])
                    displayAnswer = displayAnswer.replace(it.value, "\n[Versículo buscado: ${it.groupValues[1]}]\n")
                }
            }

            val updatedList = chatMessages.value.toMutableList()
            updatedList.add(ChatMessage("ai", displayAnswer.trim()))
            chatMessages.value = updatedList
            isGeneratingResponse.value = false
            repository.addPowerXP(25)
            studyPoints.value = repository.getStudyPoints()
            updateAchievements()
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
            studyPoints.value = repository.getStudyPoints()
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
        val list = mutableListOf<String>()
        if (points >= 100) list.add("Iniciado en el Templo (100 XP)")
        if (points >= 200) list.add("Estudioso del Tabernáculo (200 XP)")
        if (points >= 400) list.add("Compañero de Saulo (400 XP)")
        if (points >= 600) list.add("Teólogo Imperial (600 XP)")
        unlockedAchievements.value = list
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    fun updateTextSize(size: Float) {
        textSizeSp.value = size
    }

    fun handleSyncWithFirebase() {
        val uid = currentUserId.value
        if (uid != null) {
            persistProfileIfAuthenticated(userProfile.value)
            authStatusMessage.value = "Datos sincronizados en Firebase Cloud Firestore"
        } else {
            authStatusMessage.value = "Inicia sesión con Google para sincronizar en la nube"
        }
    }

    fun handleLogout() {
        repository.logout()
        isUserLoggedIn.value = false
        userEmail.value = ""
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
