package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_verses")
data class FavoriteVerse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sectionId: String,
    val verseReference: String,
    val verseText: String, // Or just save the reference if text isn't needed, but text is nice
    val timestamp: Long = System.currentTimeMillis()
)
