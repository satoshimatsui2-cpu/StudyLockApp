package com.example.studylockapp.learning

/**
 * 警告表示のUIモデル
 */
data class AudioWarningState(
    val message: String,
    val isCritical: Boolean // true: 必須モードでの警告, false: 推奨モードでの通知
)

/**
 * 学習画面のUI状態を管理するデータクラス
 */
data class LearningUiState(
    val quiz: QuizData? = null,
    val comboCount: Int = 0,
    val sessionPoints: Int = 0, // セッション内で獲得した合計ポイント
    val currentStep: Int = 0,    // 現在の問題番号 (1〜10)
    val totalSteps: Int = 10,   // セッションの総問題数
    val progress: Int = 0,      // 進捗率 (0〜100)
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
    val isAnswering: Boolean = false,
    val isAutoPlayEnabled: Boolean = true, // 自動再生のON/OFF状態
    val audioWarning: AudioWarningState? = null // 無音リスク警告状態
)

/**
 * UIへの一回限りの演出通知イベント（Channelで配信）
 */
sealed class LearningUiEvent {
    data class ShowCorrect(val gainedPoints: Int, val answer: String) : LearningUiEvent()
    data class ShowWrong(val selected: String, val correct: String) : LearningUiEvent()
    data class PlayAudio(val text: String) : LearningUiEvent()
    object QuizFinished : LearningUiEvent()
}
