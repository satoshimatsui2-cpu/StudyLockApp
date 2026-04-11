package com.example.studylockapp.learning

/**
 * 各単語の学習状態（メモリ内管理用）
 */
data class LearningState(
    val wordId: Int,
    var correctCount: Int = 0,
    var wrongCount: Int = 0,
    var nextReviewTime: Long = 0, // 0: 未学習, 1以上: 復習待ち時刻
    var lastSeen: Long = 0
) {
    // 累計の苦手度（将来的な重み付け出題に使用可能）
    val weaknessScore: Int get() = wrongCount - correctCount
}
