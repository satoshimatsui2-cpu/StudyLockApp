package com.example.studylockapp.learning

sealed class LearningUiEvent {
    data class ShowCorrect(
        val gainedPoints: Int,
        val answer: String,
        val tierChanged: Boolean = false
    ) : LearningUiEvent()

    data class ShowWrong(
        val selected: String,
        val correct: String
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
}
