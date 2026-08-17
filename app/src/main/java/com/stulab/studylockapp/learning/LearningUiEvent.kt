package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity

/**
 * 学習画面（LearningActivity）で発生するイベントを定義する密封クラス。
 */
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

    /**
     * 実践テスト画面へ遷移するためのイベント。
     * 単語学習で選択していたグレードを引き継ぎます。
     */
    data class NavigateToPracticalTest(
        val grade: Int
    ) : LearningUiEvent()

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

    // LV5到達時のボーナス誘導
    data class ShowLevel5BonusInduction(
        val word: WordEntity
    ) : LearningUiEvent()

    // ド派手な目標達成お祝い
    data class ShowGrandCelebration(
        val characterId: String,
        val characterName: String,
        val message: String,
        val emotionId: String? = null
    ) : LearningUiEvent()

    // 新しいパートナーが解放された時のお知らせ
    data class ShowNewCharacterAvailable(
        val characterName: String
    ) : LearningUiEvent()
    // スペルチェック特別問題への誘導
    data class ShowSpellingCheckInvite(
        val wordIds: List<Long>
    ) : LearningUiEvent()
}
