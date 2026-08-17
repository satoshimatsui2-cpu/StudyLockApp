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

data class SpellingCheckUiState(
    val currentQuestionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val currentWord: WordEntity? = null,
    val isCorrect: Boolean? = null,
    val isFinished: Boolean = false,
    val hintText: String? = null,
    val hintUsed: Boolean = false,
    val isAnswerShown: Boolean = false,
    val userInput: String = "",
    val selectedCharacterId: String = "leo"
)

class SpellingCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpellingRepository(AppDatabase.getInstance(application))
    private val wordDao = AppDatabase.getInstance(application).wordDao()
    private val appSettings = com.stulab.studylockapp.data.AppSettings(application)

    private val _uiState = MutableStateFlow(SpellingCheckUiState(
        selectedCharacterId = appSettings.selectedCharacterId
    ))
    val uiState = _uiState.asStateFlow()

    private var questions: List<WordEntity> = emptyList()
    private val MAX_QUESTIONS = 5

    fun loadQuestions(wordIds: LongArray?) {
        viewModelScope.launch {
            val words = if (wordIds != null && wordIds.isNotEmpty()) {
                wordDao.getWordsByIds(wordIds.map { it.toInt() })
            } else {
                repository.getEligibleWordsForPrompt(System.currentTimeMillis()).take(MAX_QUESTIONS)
            }
            questions = words
            if (questions.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    totalQuestions = questions.size,
                    currentWord = questions[0]
                )
            } else {
                _uiState.value = _uiState.value.copy(isFinished = true)
            }
        }
    }

    fun onUserInputChange(input: String) {
        _uiState.value = _uiState.value.copy(userInput = input, isCorrect = null)
    }

    fun submitAnswer() {
        val currentWord = uiState.value.currentWord ?: return
        val isCorrect = SpellingAnswerChecker.check(uiState.value.userInput, currentWord.word)
        
        viewModelScope.launch {
            val currentState = uiState.value
            // 正解を見た後、またはヒント使用後はクリア扱いにしない
            val effectiveHintUsed = currentState.hintUsed || currentState.isAnswerShown

            repository.recordResult(
                currentWord.no.toLong(),
                isCorrect,
                effectiveHintUsed
            )
            _uiState.update { it.copy(
                isCorrect = isCorrect,
                isAnswerShown = currentState.isAnswerShown || !isCorrect
            ) }
        }
    }

    fun showHint() {
        val currentWord = uiState.value.currentWord ?: return
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
        _uiState.value = _uiState.value.copy(hintText = hint, hintUsed = true)
    }

    fun nextQuestion() {
        val nextIndex = uiState.value.currentQuestionIndex + 1
        if (nextIndex < questions.size) {
            _uiState.value = _uiState.value.copy(
                currentQuestionIndex = nextIndex,
                currentWord = questions[nextIndex],
                isCorrect = null,
                hintText = null,
                hintUsed = false,
                userInput = ""
            )
        } else {
            _uiState.value = _uiState.value.copy(isFinished = true)
        }
    }
}
