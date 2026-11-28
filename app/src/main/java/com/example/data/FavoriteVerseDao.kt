package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVerseDao {
    @Query("SELECT * FROM favorite_verses ORDER BY timestamp DESC")
    fun getAllFavoriteVerses(): Flow<List<FavoriteVerse>>

    @Query("SELECT * FROM favorite_verses WHERE sectionId = :sectionId AND verseReference = :verseReference AND (bibleId = :bibleId OR (bibleId IS NULL AND :bibleId IS NULL)) LIMIT 1")
    suspend fun findByIdentity(sectionId: String, bibleId: String?, verseReference: String): FavoriteVerse?

    @RawQuery
    suspend fun insertIfAbsent(query: SupportSQLiteQuery): Int

    @Transaction
    suspend fun insertFavoriteVerseIfAbsent(favoriteVerse: FavoriteVerse) {
        insertIfAbsent(SimpleSQLiteQuery(
            """
                INSERT INTO favorite_verses (sectionId, bibleId, verseReference, verseText, timestamp)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM favorite_verses
                    WHERE sectionId = ?
                      AND verseReference = ?
                      AND (bibleId = ? OR (bibleId IS NULL AND ? IS NULL))
                )
            """.trimIndent(),
            arrayOf<Any?>(
                favoriteVerse.sectionId,
                favoriteVerse.bibleId,
                favoriteVerse.verseReference,
                favoriteVerse.verseText,
                favoriteVerse.timestamp,
                favoriteVerse.sectionId,
                favoriteVerse.verseReference,
                favoriteVerse.bibleId,
                favoriteVerse.bibleId
            )
        )
    }

    @Query("DELETE FROM favorite_verses WHERE id = :id")
    suspend fun deleteFavoriteVerse(id: Int)
}
