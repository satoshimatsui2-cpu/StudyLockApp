package com.example.studylockapp.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.PointManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearningViewModel(
    private val quizManager: QuizManager,
    private val pointManager: PointManager
) : ViewModel() {

    private val totalCount = 10
    private var solvedInSession = 0

    private val _uiState = MutableStateFlow(LearningUiState(totalSteps = totalCount))
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<LearningUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun loadNextQuiz() {
        if (solvedInSession >= totalCount) {
            finishSession()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isAnswering = false) }
            
            val quiz = quizManager.nextQuiz()
            if (quiz != null) {
                _uiState.update { 
                    it.copy(
                        quiz = quiz, 
                        isLoading = false,
                        currentStep = solvedInSession + 1,
                        progress = (solvedInSession * 100) / totalCount
                    ) 
                }
                // 自動再生
                if (quiz.mode == QuizMode.LISTEN_EN) {
                    requestAudioPlayback()
                }
            } else {
                finishSession()
            }
        }
    }

    /**
     * 音声再生をリクエスト。手動（聞き直し）時もこれを使う。
     */
    fun requestAudioPlayback() {
        val text = _uiState.value.quiz?.word?.word ?: return
        viewModelScope.launch {
            _uiEvent.send(LearningUiEvent.PlayAudio(text))
        }
    }

    fun submitAnswer(selectedAnswer: String) {
        val currentQuiz = _uiState.value.quiz ?: return
        if (_uiState.value.isAnswering) return

        _uiState.update { it.copy(isAnswering = true) }
        val isCorrect = selectedAnswer == currentQuiz.answer

        quizManager.submitAnswer(currentQuiz.word, isCorrect)

        viewModelScope.launch {
            solvedInSession++
            val newProgress = (solvedInSession * 100) / totalCount

            if (isCorrect) {
                val gain = 10
                withContext(Dispatchers.IO) {
                    pointManager.add(gain)
                }

                _uiState.update { 
                    it.copy(
                        comboCount = it.comboCount + 1,
                        sessionPoints = it.sessionPoints + gain,
                        progress = newProgress
                    ) 
                }
                _uiEvent.send(LearningUiEvent.ShowCorrect(gain, currentQuiz.answer))
            } else {
                _uiState.update { 
                    it.copy(
                        comboCount = 0, 
                        progress = newProgress
                    )
                }
                _uiEvent.send(LearningUiEvent.ShowWrong(selectedAnswer, currentQuiz.answer))
            }
        }
    }

    private fun finishSession() {
        _uiState.update { it.copy(isFinished = true, progress = 100) }
        viewModelScope.launch {
            _uiEvent.send(LearningUiEvent.QuizFinished)
        }
    }
}
