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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteVerse(favoriteVerse: FavoriteVerse)

    @Query("DELETE FROM favorite_verses WHERE id = :id")
    suspend fun deleteFavoriteVerse(id: Int)
}
