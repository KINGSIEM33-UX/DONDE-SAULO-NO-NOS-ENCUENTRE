package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

class Repository(
    private val bibleSectionDao: BibleSectionDao,
    private val verseNoteDao: VerseNoteDao,
    private val favoriteVerseDao: FavoriteVerseDao,
    private val recentChapterDao: RecentChapterDao,
    private val context: Context
) {
    val allSections: Flow<List<BibleSection>> = bibleSectionDao.getAllSections()
    val allFavoriteVerses: Flow<List<FavoriteVerse>> = favoriteVerseDao.getAllFavoriteVerses()
    val recentChapters: Flow<List<RecentChapter>> = recentChapterDao.getRecentChapters()

    suspend fun insertRecentChapter(chapter: RecentChapter) {
        recentChapterDao.insertRecentChapter(chapter)
    }

    suspend fun clearRecentChapters() {
        recentChapterDao.clearRecents()
    }

    suspend fun insertFavoriteVerse(favoriteVerse: FavoriteVerse) {
        favoriteVerseDao.insertFavoriteVerse(favoriteVerse)
    }

    suspend fun deleteFavoriteVerse(id: Int) {
        favoriteVerseDao.deleteFavoriteVerse(id)
    }

    fun getVerseNotesForSection(sectionId: String): Flow<List<VerseNote>> = verseNoteDao.getNotesForSection(sectionId)

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
        val timestamp = if (isRead) System.currentTimeMillis() else 0L
        bibleSectionDao.markRead(id, isRead, timestamp)

        if (isRead) {
            addPowerXP(50)
            updateStreak()
        }
    }

    suspend fun setBookmarked(id: String, isBookmarked: Boolean) {
        bibleSectionDao.setBookmarked(id, isBookmarked)
    }

    suspend fun updateNotes(id: String, notes: String) {
        bibleSectionDao.updateNotes(id, notes)
        if (notes.isNotEmpty()) {
            addPowerXP(20)
        }
    }

    suspend fun updateAiExplanation(id: String, explanation: String) {
        bibleSectionDao.updateAiExplanation(id, explanation)
    }

    fun getStudyPoints(): Int {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        return prefs.getInt("study_points", 120)
    }

    fun addPowerXP(amount: Int) {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        val current = prefs.getInt("study_points", 120)
        prefs.edit().putInt("study_points", current + amount).apply()
    }

    fun getStreakDays(): Int {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        return prefs.getInt("streak_days", 3)
    }

    private fun updateStreak() {
        val prefs = context.getSharedPreferences("saulo_progress", Context.MODE_PRIVATE)
        val streak = prefs.getInt("streak_days", 3)
        prefs.edit().putInt("streak_days", streak + 1).apply()
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

    suspend fun syncWithCloud() {
        // Cloud synchronization handled via SocialRepository
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
