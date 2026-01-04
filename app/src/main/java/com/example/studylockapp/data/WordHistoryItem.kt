package com.example.studylockapp.data

data class WordHistoryItem(
    val id: Long, // ユニークID (DiffUtil用)
    val word: String,
    val meaning: String,
    val englishDesc: String, // 英語の説明
    val grade: String, // gradeプロパティを追加
    val statuses: List<ModeStatus>,
    var isExpanded: Boolean = false // 展開状態の管理フラグ
)

data class ModeStatus(
    val modeName: String, // "英日", "Listening" etc.
    val level: Int,       // 1-8
    val nextReviewDate: Long, // Epoch millis
    val isReviewNeeded: Boolean
)
