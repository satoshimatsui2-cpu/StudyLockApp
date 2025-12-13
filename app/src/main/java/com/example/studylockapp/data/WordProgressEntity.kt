package com.example.studylockapp.data

import androidx.room.Entity

@Entity(
    tableName = "word_progress",
    primaryKeys = ["wordId", "mode"]
)
data class WordProgressEntity(
    val wordId: Int,
    val mode: String,          // "meaning" / "listening"
    val level: Int,
    val nextDueAtSec: Long,    // ★Due_date（秒：epoch seconds）
    val lastAnsweredAt: Long   // millis（回答した時刻。ログや統計用）
)