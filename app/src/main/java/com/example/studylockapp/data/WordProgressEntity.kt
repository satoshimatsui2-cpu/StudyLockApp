package com.example.studylockapp.data

import androidx.room.Entity

@Entity(
    tableName = "word_progress",
    primaryKeys = ["wordId", "mode"]
)
data class WordProgressEntity(
    val wordId: Int,            // WordEntity.no を参照
    val mode: String,           // "meaning" or "listening"
    val level: Int = 0,         // 0～8
    val nextDueDate: Long = 0L, // 次回出題日 (epochDay)
    val lastAnsweredAt: Long = 0L
)

