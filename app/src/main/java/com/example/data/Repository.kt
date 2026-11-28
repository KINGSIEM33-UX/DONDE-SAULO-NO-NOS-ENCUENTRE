package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Repository(
    private val bibleSectionDao: BibleSectionDao,
    private val verseNoteDao: VerseNoteDao,
    private val favoriteVerseDao: FavoriteVerseDao,
    private val recentChapterDao: RecentChapterDao,
    private val studyEventDao: StudyEventDao,
    private val context: Context
) {
    val appContext: Context get() = context.applicationContext
    val preferences: AppPreferences = AppPreferences(context.applicationContext)

    val allSections: Flow<List<BibleSection>> = bibleSectionDao.getAllSections()
    val allFavoriteVerses: Flow<List<FavoriteVerse>> = favoriteVerseDao.getAllFavoriteVerses()
    val recentChapters: Flow<List<RecentChapter>> = recentChapterDao.getRecentChapters()
    val studyEvents: Flow<List<StudyEvent>> = studyEventDao.getAllEvents()

    suspend fun insertStudyEvent(event: StudyEvent): Long = studyEventDao.insertEvent(event)
    suspend fun updateStudyEvent(event: StudyEvent) = studyEventDao.updateEvent(event)
    suspend fun setStudyEventCompleted(id: Int, completed: Boolean) = studyEventDao.setCompleted(id, completed)
    suspend fun deleteStudyEvent(id: Int) = studyEventDao.deleteEvent(id)

    suspend fun insertRecentChapter(chapter: RecentChapter) {
        recentChapterDao.insertRecentChapter(chapter)
    }

    suspend fun clearRecentChapters() {
        recentChapterDao.clearRecents()
    }

    suspend fun insertFavoriteVerse(favoriteVerse: FavoriteVerse) {
        if (favoriteVerseDao.findByIdentity(favoriteVerse.sectionId, favoriteVerse.bibleId, favoriteVerse.verseReference) == null) {
            favoriteVerseDao.insertFavoriteVerse(favoriteVerse)
        }
    }

    suspend fun deleteFavoriteVerse(id: Int) {
        favoriteVerseDao.deleteFavoriteVerse(id)
    }

    fun getVerseNotesForSection(sectionId: String, bibleId: String): Flow<List<VerseNote>> =
        verseNoteDao.getNotesForSection(sectionId, bibleId)

    fun getLegacyVerseNotesForSection(sectionId: String): Flow<List<VerseNote>> =
        verseNoteDao.getLegacyNotesForSection(sectionId)

    suspend fun insertVerseNote(note: VerseNote) {
        verseNoteDao.insertNote(note)
        addPowerXP(10)
    }

    suspend fun deleteVerseNote(id: Int) {
        verseNoteDao.deleteNote(id)
    }

    val isFirebaseAvailable: Boolean by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(context) != null
            }
        } catch (e: Exception) {
            Log.e("Repository", "Firebase initialization failed: ${e.message}")
            false
        }
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        if (isFirebaseAvailable) FirebaseAuth.getInstance() else null
    }

    val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        } else null
    }

    private val firebaseAnalytics: FirebaseAnalytics? by lazy {
        if (isFirebaseAvailable) FirebaseAnalytics.getInstance(context) else null
    }

    suspend fun markRead(id: String, isRead: Boolean) {
        val existing = bibleSectionDao.getSectionById(id)
        val wasAlreadyRead = existing?.isRead == true
        val timestamp = if (isRead) System.currentTimeMillis() else 0L
        bibleSectionDao.markRead(id, isRead, timestamp)

        if (isRead && !wasAlreadyRead) {
            val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
            val rewarded = prefs.getStringSet("rewarded_sections", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (rewarded.add(id)) {
                prefs.edit().putStringSet("rewarded_sections", rewarded).apply()
                addPowerXP(50)
            }
            updateStreak()
        }
    }

    suspend fun setBookmarked(id: String, isBookmarked: Boolean) {
        bibleSectionDao.setBookmarked(id, isBookmarked)
    }

    suspend fun updateNotes(id: String, notes: String) {
        val existing = bibleSectionDao.getSectionById(id)
        val hadNotes = !existing?.notes.isNullOrBlank()
        bibleSectionDao.updateNotes(id, notes)
        if (notes.isNotBlank() && !hadNotes) {
            addPowerXP(20)
        }
    }

    suspend fun updateAiExplanation(id: String, explanation: String) {
        bibleSectionDao.updateAiExplanation(id, explanation)
    }

    fun getStudyPoints(): Int {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        return prefs.getInt("study_points", 0)
    }

    fun addPowerXP(amount: Int) {
        if (amount <= 0) return
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        val current = prefs.getInt("study_points", 0)
        prefs.edit().putInt("study_points", current + amount).apply()
    }

    fun getStreakDays(): Int {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        val lastDate = prefs.getString("last_study_date", null) ?: return prefs.getInt("streak_days", 0)
        val today = formatDateIso(Date())
        val yesterday = formatDateIso(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        return if (lastDate == today || lastDate == yesterday) {
            prefs.getInt("streak_days", 0)
        } else {
            0
        }
    }

    fun updateStreak(nowDate: Date = Date()) {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        val today = formatDateIso(nowDate)
        val lastDate = prefs.getString("last_study_date", null)
        if (lastDate == today) return

        val cal = Calendar.getInstance().apply {
            time = nowDate
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterday = formatDateIso(cal.time)
        val currentStreak = prefs.getInt("streak_days", 0)
        val nextStreak = if (lastDate == yesterday) currentStreak + 1 else 1

        prefs.edit()
            .putInt("streak_days", nextStreak)
            .putString("last_study_date", today)
            .apply()
    }

    private fun formatDateIso(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    }

    fun getCurrentUserEmail(): String {
        return firebaseAuth?.currentUser?.email ?: ""
    }

    fun isUserLoggedIn(): Boolean {
        return firebaseAuth?.currentUser != null
    }

    fun logout() {
        if (isFirebaseAvailable) {
            firebaseAuth?.signOut()
        }
    }

    fun isAnalyticsEnabled(): Boolean {
        return context.getSharedPreferences("saulo_settings", Context.MODE_PRIVATE)
            .getBoolean("analytics_enabled", true)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        context.getSharedPreferences("saulo_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("analytics_enabled", enabled)
            .apply()
        firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun logEvent(eventName: String, params: android.os.Bundle? = null) {
        if (isAnalyticsEnabled()) {
            firebaseAnalytics?.logEvent(eventName, params)
        }
    }
}
