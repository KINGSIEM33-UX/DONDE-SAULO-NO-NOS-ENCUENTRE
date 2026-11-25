package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleSectionDao {
    @Query("SELECT * FROM bible_sections ORDER BY id ASC")
    fun getAllSections(): Flow<List<BibleSection>>

    @Query("SELECT * FROM bible_sections WHERE id = :id LIMIT 1")
    suspend fun getSectionById(id: String): BibleSection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sections: List<BibleSection>)

    @Update
    suspend fun updateSection(section: BibleSection)

    @Query("UPDATE bible_sections SET isRead = :isRead, readTimestamp = :timestamp WHERE id = :id")
    suspend fun markRead(id: String, isRead: Boolean, timestamp: Long)

    @Query("UPDATE bible_sections SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun setBookmarked(id: String, isBookmarked: Boolean)

    @Query("UPDATE bible_sections SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("UPDATE bible_sections SET aiExplanation = :explanation WHERE id = :id")
    suspend fun updateAiExplanation(id: String, explanation: String)
}
