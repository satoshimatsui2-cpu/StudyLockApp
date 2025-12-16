package com.example.studylockapp.data

import androidx.room.Entity

@Entity(
    tableName = "word_progress",
    primaryKeys = ["wordId", "mode"]
)
data class WordProgressEntity(
    val wordId: Int,
    val mode: String,
    val level: Int,
    val nextDueAtSec: Long,
    val lastAnsweredAt: Long,
    val studyCount: Int = 0   // ←追加：学習回数（初期値0）
)