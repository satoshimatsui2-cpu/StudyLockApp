package com.example.studylockapp.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.db.WordMasteryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearningViewModel(
    private val context: Context,
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private val quizManager: QuizManager,
    private val pointManager: PointManager,
    private val audioChecker: LearningAudioStateChecker,
    private val requiredWarningText: String,
    private val optionalWarningText: String,
    private val appSettings: AppSettings
) : ViewModel() {

    private val totalCount = 10
    private var solvedInSession = 0
    
    private var levelUpsInSession = 0
    private var basicMastersInSession = 0
    private var longTermMastersInSession = 0

    private val _uiState = MutableStateFlow(LearningUiState(totalSteps = totalCount))
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<LearningUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        // 設定の同期
        val currentSilent = appSettings.silentMode
        quizManager.silentMode = currentSilent
        _uiState.update { state -> state.copy(silentMode = currentSilent) }

        viewModelScope.launch(Dispatchers.IO) {
            CsvImporter.seedIfNeeded(context, wordDao)
        }
    }

    // 学習音声設定の切り替え (通常 <-> サイレント)
    fun toggleSilentMode() {
        val nextMode = if (_uiState.value.silentMode == SilentMode.OFF) SilentMode.ON else SilentMode.OFF
        appSettings.silentMode = nextMode
        quizManager.silentMode = nextMode
        _uiState.update { state -> state.copy(silentMode = nextMode) }

        // サイレントにした時に説明未表示なら、説明イベントを飛ばす
        if (nextMode == SilentMode.ON && !appSettings.hasShownSilentExplanation) {
            viewModelScope.launch {
                _uiEvent.send(LearningUiEvent.ShowSilentModeExplanation)
            }
        }
    }

    // 「今後表示しない」の保存
    fun markSilentExplanationShown() {
        appSettings.hasShownSilentExplanation = true
    }

    fun loadNextQuiz() {
        if (solvedInSession == 0) {
            quizManager.resetSessionStats()
        }

        if (solvedInSession >= totalCount) {
            finishSession()
            return
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, isAnswering = false, isReviewing = false) }
            
            val quiz = quizManager.nextQuiz()
            if (quiz != null) {
                val importance = quiz.mode.getAudioImportance()
                val hasRisk = audioChecker.isSilenceRisk()
                
                // サイレントモード時は警告を出さない
                val isSilent = _uiState.value.silentMode == SilentMode.ON
                val warning = if (!isSilent && hasRisk && (importance == QuizMode.AudioImportance.REQUIRED || importance == QuizMode.AudioImportance.OPTIONAL)) {
                    AudioWarningState(
                        message = if (importance == QuizMode.AudioImportance.REQUIRED) requiredWarningText else optionalWarningText,
                        isCritical = (importance == QuizMode.AudioImportance.REQUIRED)
                    )
                } else null

                val basicCount = quizManager.getMasteryCount(MasteryTier.BASIC_MASTER)
                val longTermCount = quizManager.getMasteryCount(MasteryTier.LONG_TERM_MASTER)
                
                // バグ修正: 現在の単語の MasteryEntity を取得して getTier に渡す
                val mastery = withContext(Dispatchers.IO) {
                    masteryDao.getMastery(quiz.word.no)
                } ?: com.example.studylockapp.data.db.WordMasteryEntity(wordId = quiz.word.no)
                
                val currentTier = MasteryScheduler.getTier(mastery)

                _uiState.update { state -> 
                    state.copy(
                        quiz = quiz, 
                        isLoading = false,
                        currentStep = solvedInSession + 1,
                        progress = (solvedInSession * 100) / totalCount,
                        audioWarning = warning,
                        basicMasterCount = basicCount,
                        longTermMasterCount = longTermCount,
                        currentTier = currentTier,
                        currentLevel = mastery.level,
                        isLevelJustIncreased = false,
                        currentWord = quiz.word,
                        wordGrade = quiz.word.grade
                    ) 
                }
                
                // 通常モードなら自動再生
                if (!isSilent) {
                    val shouldAutoPlay = when (importance) {
                        QuizMode.AudioImportance.REQUIRED -> true
                        QuizMode.AudioImportance.OPTIONAL -> true
                        else -> false
                    }
                    if (shouldAutoPlay) requestAudioPlayback()
                }
            } else {
                finishSession()
            }
        }
    }

    fun onNextAfterReview() {
        if (_uiState.value.isFinished) return
        loadNextQuiz()
    }

    fun requestAudioPlayback(text: String? = null) {
        // サイレントモード時はリクエスト自体を無効化 (手動再生も音を出さない)
        if (_uiState.value.silentMode == SilentMode.ON) return

        val playText = text ?: _uiState.value.quiz?.word?.word ?: return
        viewModelScope.launch {
            _uiEvent.send(LearningUiEvent.PlayAudio(playText))
        }
    }

    fun submitAnswer(selectedAnswer: String) {
        val currentQuiz = _uiState.value.quiz ?: return
        if (_uiState.value.isAnswering || _uiState.value.isReviewing) return
        _uiState.update { state -> state.copy(isAnswering = true) }
        
        viewModelScope.launch {
            val wordId = currentQuiz.word.no
            val oldLevel = quizManager.getMasteryLevel(wordId)
            
            // バグ修正: 回答提出前後の MasteryEntity を取得して正確な Tier を比較する
            val oldMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: com.example.studylockapp.data.db.WordMasteryEntity(wordId = wordId)
            val oldTier = MasteryScheduler.getTier(oldMastery)

            quizManager.submitAnswer(currentQuiz.word, selectedAnswer == currentQuiz.answer, currentQuiz.mode)

            val newMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: com.example.studylockapp.data.db.WordMasteryEntity(wordId = wordId)
            val newLevel = newMastery.level
            val newTier = MasteryScheduler.getTier(newMastery)
            
            val isLevelUp = newLevel > oldLevel
            if (isLevelUp) levelUpsInSession++
            if (oldTier != MasteryTier.BASIC_MASTER && newTier == MasteryTier.BASIC_MASTER) basicMastersInSession++
            if (oldTier != MasteryTier.LONG_TERM_MASTER && newTier == MasteryTier.LONG_TERM_MASTER) longTermMastersInSession++

            solvedInSession++
            val isCorrect = (selectedAnswer == currentQuiz.answer)
            
            val modeLabel = when (currentQuiz.mode) {
                QuizMode.EN_TO_JP -> "英語 → 日本語"
                QuizMode.JP_TO_EN -> "日本語 → 英語"
                QuizMode.LISTEN_EN -> "リスニング"
                else -> currentQuiz.mode.name
            }
            val questionText = if (currentQuiz.mode == QuizMode.LISTEN_EN) "聞こえた英単語" else currentQuiz.question
            val correctDisplay = if (currentQuiz.mode == QuizMode.EN_TO_JP) {
                currentQuiz.word.japanese.takeIf { it.isNotBlank() } ?: currentQuiz.answer
            } else {
                currentQuiz.word.word
            }

            var wrongWordEntity: WordEntity? = null
            if (currentQuiz.mode == QuizMode.LISTEN_EN && !isCorrect) {
                wrongWordEntity = withContext(Dispatchers.IO) {
                    wordDao.getWordBySpelling(selectedAnswer)
                }
            }

            _uiState.update { state -> 
                state.copy(
                    comboCount = if (isCorrect) state.comboCount + 1 else 0, 
                    sessionPoints = if (isCorrect) state.sessionPoints + 10 else state.sessionPoints,
                    progress = (solvedInSession * 100) / totalCount,
                    currentTier = newTier,
                    currentLevel = newLevel,
                    isLevelJustIncreased = isLevelUp,
                    isLastAnswerCorrect = isCorrect,
                    reviewModeLabel = modeLabel,
                    reviewQuestionText = questionText,
                    reviewUserAnswerText = selectedAnswer,
                    reviewCorrectAnswerText = correctDisplay,
                    showListeningCompare = (currentQuiz.mode == QuizMode.LISTEN_EN && !isCorrect),
                    wrongWord = wrongWordEntity
                ) 
            }

            if (isCorrect) {
                withContext(Dispatchers.IO) { pointManager.add(10) }
                
                // 飛び級判定 (2段階以上のアップ)
                if (newLevel - oldLevel >= 2) {
                    _uiEvent.send(LearningUiEvent.ShowFlyingLevelUp(oldLevel, newLevel))
                }

                _uiEvent.send(LearningUiEvent.ShowCorrect(10, currentQuiz.answer, oldTier != newTier))
                if (oldTier != newTier) {
                    _uiEvent.send(LearningUiEvent.ShowMasteryBadge(newTier))
                }
            } else {
                _uiEvent.send(LearningUiEvent.ShowWrong(selectedAnswer, currentQuiz.answer))
            }
        }
    }

    fun startReview() {
        _uiState.update { state -> state.copy(isReviewing = true, isAnswering = false) }
    }

    private fun finishSession() {
        _uiState.update { state -> 
            state.copy(
                isFinished = true, 
                progress = 100,
                sessionLevelUpCount = levelUpsInSession,
                sessionBasicMasterGained = basicMastersInSession,
                sessionLongTermMasterGained = longTermMastersInSession
            ) 
        }
        viewModelScope.launch { 
            _uiEvent.send(LearningUiEvent.QuizFinished) 
        }
    }
}
