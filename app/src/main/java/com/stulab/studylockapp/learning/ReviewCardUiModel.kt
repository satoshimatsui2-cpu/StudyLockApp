package com.stulab.studylockapp.learning

import androidx.annotation.DrawableRes
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.data.RelatedWord

/**
 * レビューカードの表示用モデル
 */
data class ReviewCardUiModel(
    val modeChipText: String,
    @DrawableRes val modeChipIconRes: Int,
    val questionText: String,
    val showListeningCompare: Boolean,
    val showWrongResult: Boolean,
    val isUnknownAnswer: Boolean,
    /**
     * 正解エリアでの音声再生を許可するかどうか。
     * 回答が英語になるモードの場合に true となります。
     */
    val canPlayCorrectAnswer: Boolean,
    /**
     * 不正解エリアでの音声再生を許可するかどうか。
     * 回答が英語になるモードかつ、誤答が存在し、「わからない」ではない場合に true となります。
     */
    val canPlayWrongAnswer: Boolean,
    /**
     * 結果エリア外の単語音声再生UIを表示するかどうか。
     * 結果エリア内で再生可能なモードでは重複を避けるため false になります。
     * 主に回答が日本語のモード（EN_TO_JP）で、問題である英語単語を聴くために使用します。
     */
    val showAudioControls: Boolean,
    val wrongAnswerText: String,
    val correctAnswerText: String,
    val sentence: String,
    val sentenceJp: String,
    val playButtonsEnabled: Boolean,
    // New fields for synonyms and antonyms
    val synonymHintTitle: String?,
    val synonymHintBody: String?,
    val antonyms: List<RelatedWord>
)

object ReviewCardMapper {

    /**
     * 回答（正解/不正解エリア）が英語であり、エリアタップでの再生を許可すべきモードかどうかを判定
     */
    private fun isResultAnswerPlayableMode(mode: QuizMode?): Boolean {
        return when (mode) {
            QuizMode.JP_TO_EN,
            QuizMode.LISTEN_EN,
            QuizMode.LISTEN_FILL_BLANK,
            QuizMode.FILL_BLANK,
            QuizMode.SYNONYM_PICK,
            QuizMode.ANTONYM_PICK,
            QuizMode.SENTENCE_SORT -> true
            else -> false
        }
    }

    fun map(state: LearningUiState): ReviewCardUiModel {
        val quiz = state.quiz
        val mode = quiz?.mode
        
        val canPlayResultAudio = isResultAnswerPlayableMode(mode)
        // 誤答ブロックを表示すべき条件（「わからない」時は表示しない）
        val showWrong = !state.isLastAnswerCorrect && !state.isUnknownAnswer

        val modeIcon = when (mode) {
            QuizMode.LISTEN_EN, QuizMode.LISTEN_FILL_BLANK -> R.drawable.ic_headphones_24
            else -> R.drawable.ic_translate_24
        }

        // 結果エリア外の単語音声コントロールを表示する条件：
        // EN_TO_JP のように、回答が日本語で、かつ問題文（英語）の音声を聞く必要があるモードのみ。
        // それ以外の英語回答モードは結果エリアで再生するため、重複回避のため非表示。
        val showAudioControls = (mode == QuizMode.EN_TO_JP)

        return ReviewCardUiModel(
            modeChipText = state.reviewModeLabel,
            modeChipIconRes = modeIcon,
            questionText = state.reviewQuestionText,
            showListeningCompare = false, // 永久廃止（結果エリアに統合）
            showWrongResult = !state.isLastAnswerCorrect,
            isUnknownAnswer = state.isUnknownAnswer,
            canPlayCorrectAnswer = canPlayResultAudio,
            canPlayWrongAnswer = canPlayResultAudio && showWrong,
            showAudioControls = showAudioControls,
            wrongAnswerText = state.reviewUserAnswerText,
            correctAnswerText = state.reviewCorrectAnswerText,
            sentence = state.currentWord?.sentence ?: "",
            sentenceJp = state.currentWord?.japaneseSentence ?: "",
            playButtonsEnabled = (state.silentMode == SilentMode.OFF),
            synonymHintTitle = state.reviewSynonymHintTitle,
            synonymHintBody = state.reviewSynonymHintBody,
            antonyms = state.reviewAntonyms
        )
    }
}
