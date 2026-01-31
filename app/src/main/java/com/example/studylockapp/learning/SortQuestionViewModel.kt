package com.example.studylockapp.learning

import androidx.lifecycle.ViewModel
import com.example.studylockapp.data.SortQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class SortQuestionUiState(
    val question: SortQuestion? = null,
    val choiceWords: List<String> = emptyList(),
    val answerWords: List<String> = emptyList(),
    val isCorrect: Boolean? = null,
    val hasScored: Boolean = false
)

class SortQuestionViewModel(
    private val random: Random = Random.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(SortQuestionUiState())
    val uiState: StateFlow<SortQuestionUiState> = _uiState.asStateFlow()

    fun setQuestion(question: SortQuestion) {
        _uiState.value = SortQuestionUiState(
            question = question,
            choiceWords = question.words.shuffled(random), // 注入されたRandomを使用
            answerWords = emptyList(),
            isCorrect = null,
            hasScored = false
        )
    }

    /**
     * 選択肢の単語を解答欄に移動します。
     */
    fun selectWord(word: String) {
        _uiState.update {
            val newChoiceWords = it.choiceWords.toMutableList()
            newChoiceWords.remove(word)

            val newAnswerWords = it.answerWords + word

            it.copy(
                choiceWords = newChoiceWords,
                answerWords = newAnswerWords,
                isCorrect = null,
                hasScored = false
            )
        }
    }

    /**
     * 解答欄の単語を選択肢に戻します。
     */
    fun deselectWord(word: String) {
        _uiState.update {
            val newAnswerWords = it.answerWords.toMutableList()
            newAnswerWords.remove(word)

            val newChoiceWords = it.choiceWords + word

            it.copy(
                choiceWords = newChoiceWords,
                answerWords = newAnswerWords,
                isCorrect = null,
                hasScored = false
            )
        }
    }

    /**
     * 解答が正しいかチェックします。
     */
    fun checkAnswer() {
        _uiState.update {
            val isCorrect = it.question?.words == it.answerWords
            it.copy(isCorrect = isCorrect)
        }
    }

    /**
     * UI側で採点処理が完了したことを通知するために呼び出します。
     * これにより、二重採点を防ぎます。
     */
    fun markScored() {
        _uiState.update {
            it.copy(hasScored = true)
        }
    }
}