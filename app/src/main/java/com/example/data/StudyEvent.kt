package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_events")
data class StudyEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val details: String = "",
    val dateIso: String, // YYYY-MM-DD
    val time: String = "19:00",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
