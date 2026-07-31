package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.data.RelatedWord

data class LearningUiState(
    val isLoading: Boolean = false,
    val isAnswering: Boolean = false,
    val isReviewing: Boolean = false,
    val isFinished: Boolean = false,
    val quiz: QuizData? = null,
    val currentWord: WordEntity? = null,
    val currentStep: Int = 1,
    val totalSteps: Int = 10,
    val progress: Int = 0,
    val totalPoints: Int = 0, // セッション毎ではなく累計ポイントを保持
    val comboCount: Int = 0,
    
    // 学習音声 (通常 / サイレント)
    val silentMode: SilentMode = SilentMode.OFF,
    
    val audioWarning: AudioWarningState? = null,

    // 設定の永続化対象
    val includeOtherGradeReviews: Boolean = false,
    val choicesInitiallyVisible: Boolean = true,
    
    // Mastery Info
    val basicCount: Int = 0,
    val longTermCount: Int = 0,
    val basicMasterCount: Int = 0,
    val longTermMasterCount: Int = 0,
    val currentTier: MasteryTier = MasteryTier.LEARNING,
    val currentLevel: Int = 0,
    val targetLevel: Int = 0, // 初期値を 0 (未設定) に変更
    val isLevelJustIncreased: Boolean = false,
    val wordGrade: Int = 5,

    // Review Info
    val isLastAnswerCorrect: Boolean = false,
    val isUnknownAnswer: Boolean = false,
    val reviewModeLabel: String = "",
    val reviewQuestionText: String = "",
    val reviewUserAnswerText: String = "",
    val reviewCorrectAnswerText: String = "",
    val showListeningCompare: Boolean = false,
    val wrongWord: WordEntity? = null,
    val isFavorite: Boolean = false,
    val isFavoriteUpdating: Boolean = false,

    // Synonyms / Antonyms for Review
    val reviewSynonymHintTitle: String? = null,
    val reviewSynonymHintBody: String? = null,
    val reviewAntonyms: List<RelatedWord> = emptyList(),

    // Session Summary
    val sessionLevelUpCount: Int = 0,
    val sessionBasicMasterGained: Int = 0,
    val sessionLongTermMasterGained: Int = 0,

    // ノルマ残り
    val newWordsRemaining: Int = 0,
    val reviewWordsRemaining: Int = 0,
    val reviewWordsTotalToday: Int = 0,
    val reviewWordsNormalTotalToday: Int = 0,

    // 空状態
    val emptyState: LearningEmptyState? = null,
    val countdownText: String? = null,
    val selectedCharacterId: String = "george",
    val userName: String = "きみ"
)

data class AudioWarningState(
    val message: String,
    val isCritical: Boolean
)
