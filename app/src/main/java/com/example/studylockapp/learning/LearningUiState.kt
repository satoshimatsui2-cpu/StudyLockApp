package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity

/**
 * 警告表示のUIモデル
 */
data class AudioWarningState(
    val message: String,
    val isCritical: Boolean
)

/**
 * 学習画面のUI状態
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
    
    // マスター統計
    val basicMasterCount: Int = 0,
    val longTermMasterCount: Int = 0,
    val currentTier: MasteryTier = MasteryTier.LEARNING,
    
    // 進捗データ
    val currentLevel: Int = 0,
    val isLevelJustIncreased: Boolean = false,
    val targetLevel: Int = 4,
    val wordGrade: Int = 0,
    
    // --- レビュー状態 (次のクイズロードまで保持) ---
    val isReviewing: Boolean = false,
    val currentWord: WordEntity? = null,
    val lastUserAnswer: String? = null,
    val correctAnswerText: String? = null,
    val isLastAnswerCorrect: Boolean = true,
    
    // セッション成果
    val sessionLevelUpCount: Int = 0,
    val sessionBasicMasterGained: Int = 0,
    val sessionLongTermMasterGained: Int = 0
)

/**
 * UIへの一回限りの演出通知イベント
 */
sealed class LearningUiEvent {
    data class ShowCorrect(val gainedPoints: Int, val answer: String, val tierChanged: Boolean = false) : LearningUiEvent()
    data class ShowWrong(val selected: String, val correct: String) : LearningUiEvent()
    data class PlayAudio(val text: String) : LearningUiEvent()
    object QuizFinished : LearningUiEvent()
    data class ShowMasteryBadge(val tier: MasteryTier) : LearningUiEvent()
}
