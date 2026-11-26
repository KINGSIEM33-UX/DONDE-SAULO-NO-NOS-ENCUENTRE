package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.adoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "adora_preferences")

data class UserSettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SOCIAL_CLARO,
    val defaultCurrency: String = "€",
    val notificationsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val textSizeSp: Float = 18f,
    val textPaddingDp: Float = 16f,
    val bibleVersion: String = "RVR1960",
    val world2ActiveTab: String = "library"
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val TEXT_SIZE_SP = floatPreferencesKey("text_size_sp")
        val TEXT_PADDING_DP = floatPreferencesKey("text_padding_dp")
        val BIBLE_VERSION = stringPreferencesKey("bible_version")
        val WORLD2_ACTIVE_TAB = stringPreferencesKey("world2_active_tab")
        val SEEDED_CLOUD_UIDS = stringSetPreferencesKey("seeded_cloud_uids")
    }

    val settingsFlow: Flow<UserSettingsSnapshot> = context.adoraDataStore.data.map { prefs ->
        val savedTheme = prefs[Keys.THEME_MODE]
        val theme = ThemeMode.entries.firstOrNull { it.name == savedTheme } ?: ThemeMode.SOCIAL_CLARO
        UserSettingsSnapshot(
            themeMode = theme,
            defaultCurrency = prefs[Keys.DEFAULT_CURRENCY] ?: "€",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS_ENABLED] ?: true,
            textSizeSp = (prefs[Keys.TEXT_SIZE_SP] ?: 18f).coerceIn(12f, 34f),
            textPaddingDp = (prefs[Keys.TEXT_PADDING_DP] ?: 16f).coerceIn(8f, 32f),
            bibleVersion = prefs[Keys.BIBLE_VERSION] ?: "RVR1960",
            world2ActiveTab = prefs[Keys.WORLD2_ACTIVE_TAB] ?: "library"
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.adoraDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCurrency(currency: String) {
        context.adoraDataStore.edit { it[Keys.DEFAULT_CURRENCY] = currency }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.adoraDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.adoraDataStore.edit { it[Keys.SOUND_EFFECTS_ENABLED] = enabled }
    }

    suspend fun setTextSizeSp(size: Float) {
        context.adoraDataStore.edit { it[Keys.TEXT_SIZE_SP] = size.coerceIn(12f, 34f) }
    }

    suspend fun setTextPaddingDp(padding: Float) {
        context.adoraDataStore.edit { it[Keys.TEXT_PADDING_DP] = padding.coerceIn(8f, 32f) }
    }

    suspend fun setBibleVersion(version: String) {
        context.adoraDataStore.edit { it[Keys.BIBLE_VERSION] = version }
    }

    suspend fun setWorld2ActiveTab(tab: String) {
        context.adoraDataStore.edit { it[Keys.WORLD2_ACTIVE_TAB] = tab }
    }

    suspend fun hasSeededCloudForUser(uid: String): Boolean {
        val prefs = context.adoraDataStore.data.first()
        return prefs[Keys.SEEDED_CLOUD_UIDS]?.contains(uid) == true
    }

    suspend fun markCloudSeededForUser(uid: String) {
        context.adoraDataStore.edit { prefs ->
            val current = prefs[Keys.SEEDED_CLOUD_UIDS] ?: emptySet()
            prefs[Keys.SEEDED_CLOUD_UIDS] = current + uid
        }
    }
}
