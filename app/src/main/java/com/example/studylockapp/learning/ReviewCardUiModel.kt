package com.example.studylockapp.learning

import androidx.annotation.DrawableRes
import com.example.studylockapp.R
import com.example.studylockapp.data.SilentMode

/**
 * レビューカードの表示用モデル
 */
data class ReviewCardUiModel(
    val modeChipText: String,
    @DrawableRes val modeChipIconRes: Int,
    val questionText: String,
    val showListeningCompare: Boolean,
    val showWrongResult: Boolean,
    val wrongAnswerText: String,
    val correctAnswerText: String,
    val compareWrongPhonetic: String,
    val compareCorrectPhonetic: String,
    val phonetic: String,
    val sentence: String,
    val sentenceJp: String,
    val playButtonsEnabled: Boolean
)

object ReviewCardMapper {
    fun map(state: LearningUiState): ReviewCardUiModel {
        val quiz = state.quiz
        val isListenWrong = quiz?.mode == QuizMode.LISTEN_EN && !state.isLastAnswerCorrect && state.showListeningCompare
        
        val modeIcon = when (quiz?.mode) {
            QuizMode.LISTEN_EN -> R.drawable.ic_hearing_24
            else -> R.drawable.ic_translate_24
        }

        return ReviewCardUiModel(
            modeChipText = state.reviewModeLabel,
            modeChipIconRes = modeIcon,
            questionText = state.reviewQuestionText,
            showListeningCompare = isListenWrong,
            showWrongResult = !state.isLastAnswerCorrect,
            wrongAnswerText = state.reviewUserAnswerText,
            correctAnswerText = state.reviewCorrectAnswerText,
            compareWrongPhonetic = state.wrongWord?.phonetic ?: "",
            compareCorrectPhonetic = state.currentWord?.phonetic ?: "",
            phonetic = state.currentWord?.phonetic ?: "",
            sentence = state.currentWord?.sentence ?: "",
            sentenceJp = state.currentWord?.japaneseSentence ?: "",
            playButtonsEnabled = (state.silentMode == SilentMode.OFF)
        )
    }
}
