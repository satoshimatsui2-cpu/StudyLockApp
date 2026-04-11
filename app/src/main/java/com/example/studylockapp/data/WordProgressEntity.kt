package com.example.studylockapp.data

import androidx.room.Entity

@Entity(
    tableName = "word_progress",
    primaryKeys = ["wordId", "mode"]
)
data class WordProgressEntity(
    val wordId: Int,
    val mode: String,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastSeen: Long = 0
)
