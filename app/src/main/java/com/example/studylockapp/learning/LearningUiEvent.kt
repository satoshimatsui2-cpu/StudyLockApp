package com.example.studylockapp.learning

sealed class LearningUiEvent {
    data class ShowCorrect(
        val gainedPoints: Int,
        val answer: String,
        val tierChanged: Boolean = false
    ) : LearningUiEvent()

    data class ShowWrong(
        val selected: String,
        val correct: String,
        val isUnknown: Boolean = false
    ) : LearningUiEvent()

    data class PlayAudio(
        val text: String
    ) : LearningUiEvent()

    object QuizFinished : LearningUiEvent()

    data class ShowMasteryBadge(
        val tier: MasteryTier
    ) : LearningUiEvent()

    object ShowSilentModeExplanation : LearningUiEvent()

    // 飛び級演出用イベント
    data class ShowFlyingLevelUp(
        val oldLevel: Int,
        val newLevel: Int
    ) : LearningUiEvent()

    // 基礎マスター達成時の専用演出
    object ShowBasicMasterCelebration : LearningUiEvent()

    // 長期マスター達成時の専用演出
    object ShowLongTermMasterCelebration : LearningUiEvent()

    // 出題可能な単語がない場合のイベント
    object NoAvailableWords : LearningUiEvent()
}
