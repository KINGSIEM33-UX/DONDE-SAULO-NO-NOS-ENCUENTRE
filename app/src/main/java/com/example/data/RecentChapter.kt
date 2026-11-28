package com.example.data

import androidx.room.Entity

@Entity(
    tableName = "recent_chapters",
    primaryKeys = ["chapterId", "bibleId"]
)
data class RecentChapter(
    val chapterId: String, // e.g. "JHN.3"
    val bibleId: String,   // e.g. "592420522e16049f-01"
    val reference: String, // e.g. "Juan 3"
    val timestamp: Long = System.currentTimeMillis()
)
