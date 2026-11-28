package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVerseDao {
    @Query("SELECT * FROM favorite_verses ORDER BY timestamp DESC")
    fun getAllFavoriteVerses(): Flow<List<FavoriteVerse>>

    @Query("SELECT * FROM favorite_verses WHERE sectionId = :sectionId AND verseReference = :verseReference AND (bibleId = :bibleId OR (bibleId IS NULL AND :bibleId IS NULL)) LIMIT 1")
    suspend fun findByIdentity(sectionId: String, bibleId: String?, verseReference: String): FavoriteVerse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteVerse(favoriteVerse: FavoriteVerse)

    @Query("DELETE FROM favorite_verses WHERE id = :id")
    suspend fun deleteFavoriteVerse(id: Int)
}
