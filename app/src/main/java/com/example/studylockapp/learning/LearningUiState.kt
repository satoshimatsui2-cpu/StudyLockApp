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
    val sessionPoints: Int = 0,
    val currentStep: Int = 0,
    val totalSteps: Int = 10,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
    val isAnswering: Boolean = false,
    val isAutoPlayEnabled: Boolean = true,
    val audioStudyMode: QuizManager.AudioStudyMode = QuizManager.AudioStudyMode.NORMAL,
    val audioWarning: AudioWarningState? = null,
    
    // マスター件数
    val basicMasterCount: Int = 0,
    val longTermMasterCount: Int = 0,
    val currentTier: MasteryTier = MasteryTier.LEARNING
)

/**
 * UIへの一回限りの演出通知イベント
 */
sealed class LearningUiEvent {
    data class ShowCorrect(val gainedPoints: Int, val answer: String, val tierChanged: Boolean = false) : LearningUiEvent()
    data class ShowWrong(val selected: String, val correct: String) : LearningUiEvent()
    data class PlayAudio(val text: String) : LearningUiEvent()
    object QuizFinished : LearningUiEvent()
}
