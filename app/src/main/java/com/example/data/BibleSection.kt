package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bible_sections")
data class BibleSection(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String, // "Tabernáculo", "Sacerdotes", "Devocional"
    val content: String, // Default / RVR1960
    val contentNTV: String = "", // Nueva Traducción Viviente
    val contentNVI: String = "", // Nueva Versión Internacional
    val contentTLA: String = "", // Traducción en Lenguaje Actual
    val details: String, // Additional study comments
    val passageReference: String,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val notes: String = "",
    val readTimestamp: Long = 0L,
    val rating: Int = 0, // Personal rating out of 5 stars
    val aiExplanation: String? = null // Offline cache for AI-generated chapter explanations
)
