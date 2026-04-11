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
    private val pointManager: PointManager,
    private val audioChecker: LearningAudioStateChecker,
    private val requiredWarningText: String,
    private val optionalWarningText: String
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
                // 音声状態のチェックと警告モデルの生成
                val importance = quiz.mode.getAudioImportance()
                val hasRisk = audioChecker.isSilenceRisk()
                val autoPlayEnabled = _uiState.value.isAutoPlayEnabled
                
                // 警告を出すかどうかの判定
                val shouldShowWarning = when (importance) {
                    QuizMode.AudioImportance.REQUIRED -> hasRisk
                    QuizMode.AudioImportance.OPTIONAL -> hasRisk && autoPlayEnabled
                    QuizMode.AudioImportance.NONE -> false
                }

                val warning = if (shouldShowWarning) {
                    AudioWarningState(
                        message = if (importance == QuizMode.AudioImportance.REQUIRED) requiredWarningText else optionalWarningText,
                        isCritical = (importance == QuizMode.AudioImportance.REQUIRED)
                    )
                } else null

                _uiState.update { 
                    it.copy(
                        quiz = quiz, 
                        isLoading = false,
                        currentStep = solvedInSession + 1,
                        progress = (solvedInSession * 100) / totalCount,
                        audioWarning = warning
                    ) 
                }
                
                // 自動再生判定: 設定が有効な場合のみ自動再生を行う
                val shouldAutoPlay = if (autoPlayEnabled) {
                    when (importance) {
                        QuizMode.AudioImportance.REQUIRED -> true
                        QuizMode.AudioImportance.OPTIONAL -> true
                        else -> false
                    }
                } else {
                    false
                }

                if (shouldAutoPlay) {
                    requestAudioPlayback()
                }
            } else {
                finishSession()
            }
        }
    }

    /**
     * 自動再生設定のトグル
     */
    fun toggleAutoPlay() {
        _uiState.update { it.copy(isAutoPlayEnabled = !it.isAutoPlayEnabled) }
        // トグル直後に現在のクイズの警告状態を再評価することも可能だが、
        // 今回は「次の問題ロード時」の評価に合わせる最小修正とする。
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
        
        quizManager.submitAnswer(currentQuiz.word, selectedAnswer == currentQuiz.answer)

        viewModelScope.launch {
            solvedInSession++
            val isCorrect = selectedAnswer == currentQuiz.answer
            if (isCorrect) {
                withContext(Dispatchers.IO) { pointManager.add(10) }
                _uiState.update { 
                    it.copy(
                        comboCount = it.comboCount + 1, 
                        sessionPoints = it.sessionPoints + 10,
                        progress = (solvedInSession * 100) / totalCount
                    ) 
                }
                _uiEvent.send(LearningUiEvent.ShowCorrect(10, currentQuiz.answer))
            } else {
                _uiState.update { 
                    it.copy(
                        comboCount = 0, 
                        progress = (solvedInSession * 100) / totalCount
                    ) 
                }
                _uiEvent.send(LearningUiEvent.ShowWrong(selectedAnswer, currentQuiz.answer))
            }
        }
    }

    private fun finishSession() {
        _uiState.update { it.copy(isFinished = true, progress = 100) }
        viewModelScope.launch { _uiEvent.send(LearningUiEvent.QuizFinished) }
    }
}
