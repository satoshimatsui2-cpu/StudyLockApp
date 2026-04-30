package com.example.studylockapp.data

/**
 * 学習履歴画面のリスト表示用データクラス
 */
data class WordHistoryItem(
    val id: Long,
    val word: String,
    val japanese: String,
    val description: String,
    val sentence: String,
    val japaneseSentence: String,
    val gradeLabel: String,         // 例: "5級"
    val levelLabel: String,         // 例: "LV 4"
    val tierLabel: String,          // 例: "基礎マスター"
    val reviewStatusLabel: String,  // 例: "復習待ち" or "次回: 04/30 18:00"
    val lastSeenLabel: String,      // 例: "最終学習: 04/30 14:10"
    val scoreLabel: String,         // 例: "成功 3 / 失敗 1"
    val scheduledModeLabel: String, // 例: "英日"
    val hasPendingListenReview: Boolean,
    val grade: Int,                 // フィルタリング用
    var isExpanded: Boolean = false
)
