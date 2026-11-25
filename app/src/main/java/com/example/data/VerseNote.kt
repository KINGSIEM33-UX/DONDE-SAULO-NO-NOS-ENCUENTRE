package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verse_notes")
data class VerseNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sectionId: String,
    val verseReference: String,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)
