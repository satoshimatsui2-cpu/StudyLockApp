package com.stulab.studylockapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.SpellingRepository
import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.learning.SpellingAnswerChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellingQuestionUiModel(
    val wordId: Long,
    val japanese: String,
    val pos: String
)

data class SpellingCheckUiState(
    val currentQuestionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val currentQuestion: SpellingQuestionUiModel? = null,
    val isCorrect: Boolean? = null,
    val isFinished: Boolean = false,
    val hintText: String? = null,
    val hintUsed: Boolean = false, // Current question hint usage
    val isAnswerShown: Boolean = false,
    val isAnswering: Boolean = false,
    val userInput: String = ""
)

class SpellingCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpellingRepository(AppDatabase.getInstance(application))
    private val wordDao = AppDatabase.getInstance(application).wordDao()

    private val _uiState = MutableStateFlow(SpellingCheckUiState())
    val uiState = _uiState.asStateFlow()

    private var questions: List<WordEntity> = emptyList()
    private val MAX_QUESTIONS = 5

    fun loadQuestions(wordIds: LongArray?, grade: Int? = null) {
        viewModelScope.launch {
            val words = if (wordIds != null && wordIds.isNotEmpty()) {
                wordDao.getWordsByIds(wordIds.map { it.toInt() })
            } else if (grade != null) {
                repository.getEligibleWordsByGrade(grade).take(MAX_QUESTIONS)
            } else {
                repository.getEligibleWordsForPrompt(System.currentTimeMillis()).take(MAX_QUESTIONS)
            }
            questions = words.shuffled()
            if (questions.isNotEmpty()) {
                _uiState.update { it.copy(
                    totalQuestions = questions.size,
                    currentQuestion = questions[0].toUiModel()
                ) }
            } else {
                _uiState.update { it.copy(isFinished = true) }
            }
        }
    }

    fun onUserInputChange(input: String) {
        _uiState.update { it.copy(userInput = input, isCorrect = null) }
    }

    fun submitAnswer() {
        val currentIndex = uiState.value.currentQuestionIndex
        if (currentIndex >= questions.size) return
        if (uiState.value.isAnswering || uiState.value.isCorrect != null) return

        val currentWord = questions[currentIndex]
        val isCorrect = SpellingAnswerChecker.check(uiState.value.userInput, currentWord.word)
        
        _uiState.update { it.copy(isAnswering = true) }

        viewModelScope.launch {
            val currentState = uiState.value
            // Update repository. Hint used logic is handled inside recordResult
            repository.recordResult(
                currentWord.no.toLong(),
                isCorrect,
                currentState.hintUsed
            )
            _uiState.update { it.copy(
                isAnswering = false,
                isCorrect = isCorrect,
                isAnswerShown = currentState.isAnswerShown || !isCorrect
            ) }
        }
    }

    fun showHint() {
        val currentIndex = uiState.value.currentQuestionIndex
        if (currentIndex >= questions.size) return
        val currentWord = questions[currentIndex]

        val spelling = currentWord.word.trim()
        val hint = buildString {
            append("ヒント：${spelling.length}文字")
            if (spelling.isNotEmpty()) {
                append(", 最初は '${spelling[0].uppercase()}'")
            }
            if (spelling.contains(" ")) {
                append(", 空白あり")
            }
            if (spelling.contains("-")) {
                append(", ハイフンあり")
            }
        }
        _uiState.update { it.copy(hintText = hint, hintUsed = true) }
    }

    fun nextQuestion() {
        val nextIndex = uiState.value.currentQuestionIndex + 1
        if (nextIndex < questions.size) {
            _uiState.update { it.copy(
                currentQuestionIndex = nextIndex,
                currentQuestion = questions[nextIndex].toUiModel(),
                isCorrect = null,
                hintText = null,
                hintUsed = false,
                isAnswerShown = false,
                userInput = ""
            ) }
        } else {
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun getCurrentCorrectSpelling(): String? {
        val currentIndex = uiState.value.currentQuestionIndex
        return questions.getOrNull(currentIndex)?.word
    }

    private fun WordEntity.toUiModel() = SpellingQuestionUiModel(
        wordId = no.toLong(),
        japanese = japanese,
        pos = pos
    )
}
