package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VerseNoteDao {
    @Query("SELECT * FROM verse_notes WHERE sectionId = :sectionId AND bibleId = :bibleId ORDER BY timestamp DESC")
    fun getNotesForSection(sectionId: String, bibleId: String): Flow<List<VerseNote>>

    @Query("SELECT * FROM verse_notes WHERE sectionId = :sectionId AND bibleId IS NULL ORDER BY timestamp DESC")
    fun getLegacyNotesForSection(sectionId: String): Flow<List<VerseNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: VerseNote)

    @Query("DELETE FROM verse_notes WHERE id = :id")
    suspend fun deleteNote(id: Int)
}
