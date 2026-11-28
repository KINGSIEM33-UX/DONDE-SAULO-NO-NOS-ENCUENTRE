package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVerseDao {
    @Query("SELECT * FROM favorite_verses ORDER BY timestamp DESC")
    fun getAllFavoriteVerses(): Flow<List<FavoriteVerse>>

    @Query("SELECT * FROM favorite_verses WHERE sectionId = :sectionId AND verseReference = :verseReference AND (bibleId = :bibleId OR (bibleId IS NULL AND :bibleId IS NULL)) LIMIT 1")
    suspend fun findByIdentity(sectionId: String, bibleId: String?, verseReference: String): FavoriteVerse?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteVerse(favoriteVerse: FavoriteVerse): Long

    @Transaction
    suspend fun insertFavoriteVerseIfAbsent(favoriteVerse: FavoriteVerse) {
        if (findByIdentity(favoriteVerse.sectionId, favoriteVerse.bibleId, favoriteVerse.verseReference) == null) {
            insertFavoriteVerse(favoriteVerse)
        }
    }

    @Query("DELETE FROM favorite_verses WHERE id = :id")
    suspend fun deleteFavoriteVerse(id: Int)
}
