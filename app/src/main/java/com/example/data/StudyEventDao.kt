package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyEventDao {
    @Query("SELECT * FROM study_events ORDER BY dateIso ASC, time ASC")
    fun getAllEvents(): Flow<List<StudyEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: StudyEvent): Long

    @Update
    suspend fun updateEvent(event: StudyEvent)

    @Query("UPDATE study_events SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Int, completed: Boolean)

    @Query("DELETE FROM study_events WHERE id = :id")
    suspend fun deleteEvent(id: Int)
}
