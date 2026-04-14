package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.SilentMode

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
    val sessionPoints: Int = 0,
    val comboCount: Int = 0,
    
    // 学習音声 (通常 / サイレント)
    val silentMode: SilentMode = SilentMode.OFF,
    
    val audioWarning: AudioWarningState? = null,
    
    // Mastery Info
    val basicMasterCount: Int = 0,
    val longTermMasterCount: Int = 0, // ViewModel と完全に一致させる
    val currentTier: MasteryTier = MasteryTier.LEARNING,
    val currentLevel: Int = 0,
    val targetLevel: Int = 5,
    val isLevelJustIncreased: Boolean = false,
    val wordGrade: Int = 5,

    // Review Info
    val isLastAnswerCorrect: Boolean = false,
    val reviewModeLabel: String = "",
    val reviewQuestionText: String = "",
    val reviewUserAnswerText: String = "",
    val reviewCorrectAnswerText: String = "",
    val showListeningCompare: Boolean = false,
    val wrongWord: WordEntity? = null,

    // Session Summary
    val sessionLevelUpCount: Int = 0,
    val sessionBasicMasterGained: Int = 0,
    val sessionLongTermMasterGained: Int = 0
)

data class AudioWarningState(
    val message: String,
    val isCritical: Boolean
)
