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
    val studyCount: Int = 0,
    val lastResult: Boolean = true, // 追加：直近の回答結果（true:正解, false:不正解）
    val wrongCount: Int = 0         // 追加：累計不正解回数
)