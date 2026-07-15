package com.example.studylockapp.learning

/**
 * 学習画面で出題可能なデータがない場合の空状態を定義。
 */
sealed class LearningEmptyState {
    /**
     * 本日の目標（新規単語ノルマなど）を達成した場合。
     */
    object DailyGoalMet : LearningEmptyState()

    /**
     * 現在学習できる（復習期限が来ている）データがない場合。
     */
    object NoReviewAvailable : LearningEmptyState()

    /**
     * サイレントモードでの出題は終わったが、通常モードなら出題がある場合。
     */
    object SilentModeFinishedButNormalAvailable : LearningEmptyState()
}
