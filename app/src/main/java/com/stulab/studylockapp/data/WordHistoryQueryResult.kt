package com.stulab.studylockapp.data

/**
 * 学習履歴画面表示用のクエリ結果を受け取るためのPOJO
 */
data class WordHistoryQueryResult(
    val no: Int,
    val word: String,
    val japanese: String,
    val description: String,
    val sentence: String,
    val japaneseSentence: String,
    val pos: String,
    val grade: Int,

    val level: Int,
    val scheduledMode: String,
    val nextReviewTime: Long,
    val lastSeen: Long,
    val lastCorrectTime: Long,

    val challengeCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val currentStreak: Int,
    val bestStreak: Int,

    val isBasicMastered: Boolean,
    val isLongTermMastered: Boolean,
    val pendingListenReview: Boolean,
    val deferredListenCount: Int
)
