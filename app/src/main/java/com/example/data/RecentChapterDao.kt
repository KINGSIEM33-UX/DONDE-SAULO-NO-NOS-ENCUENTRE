package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentChapterDao {
    @Query("SELECT * FROM recent_chapters ORDER BY timestamp DESC LIMIT 50")
    fun getRecentChapters(): Flow<List<RecentChapter>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentChapter(chapter: RecentChapter)

    @Query("DELETE FROM recent_chapters")
    suspend fun clearRecents()
}
