package com.example.studylockapp.learning

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.CsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.db.WordDao
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
    private val quizManager: QuizManager,
    private val pointManager: PointManager,
    private val audioChecker: LearningAudioStateChecker,
    private val requiredWarningText: String,
    private val optionalWarningText: String
) : ViewModel() {

    private val totalCount = 10
    private var solvedInSession = 0
    
    // セッション成果の蓄積
    private var levelUpsInSession = 0
    private var basicMastersInSession = 0
    private var longTermMastersInSession = 0

    private val _uiState = MutableStateFlow(LearningUiState(totalSteps = totalCount))
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<LearningUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            CsvImporter.seedIfNeeded(context, wordDao)
        }
    }

    fun setAudioStudyMode(mode: QuizManager.AudioStudyMode) {
        quizManager.audioStudyMode = mode
        _uiState.update { it.copy(audioStudyMode = mode) }
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
            _uiState.update { it.copy(isLoading = true, isAnswering = false) }
            
            val quiz = quizManager.nextQuiz()
            if (quiz != null) {
                val importance = quiz.mode.getAudioImportance()
                val hasRisk = audioChecker.isSilenceRisk()
                val autoPlayEnabled = _uiState.value.isAutoPlayEnabled
                
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

                val basicCount = quizManager.getMasteryCount(MasteryTier.BASIC_MASTER)
                val longTermCount = quizManager.getMasteryCount(MasteryTier.LONG_TERM_MASTER)
                val currentLevel = quizManager.getMasteryLevel(quiz.word.no)

                _uiState.update { 
                    it.copy(
                        quiz = quiz, 
                        isLoading = false,
                        currentStep = solvedInSession + 1,
                        progress = (solvedInSession * 100) / totalCount,
                        audioWarning = warning,
                        basicMasterCount = basicCount,
                        longTermMasterCount = longTermCount,
                        currentTier = MasteryScheduler.getTier(currentLevel),
                        currentLevel = currentLevel,
                        isLevelJustIncreased = false,
                        isBasicMasteredJustNow = false,
                        isLongTermMasteredJustNow = false
                    ) 
                }
                
                if (autoPlayEnabled) {
                    val shouldAutoPlay = when (importance) {
                        QuizMode.AudioImportance.REQUIRED -> true
                        QuizMode.AudioImportance.OPTIONAL -> true
                        else -> false
                    }
                    if (shouldAutoPlay) requestAudioPlayback()
                }
            } else {
                val count = withContext(Dispatchers.IO) { wordDao.countAllWords() }
                Log.e("QuizFlow", "quizManager.nextQuiz() returned null. Total words: $count")
                finishSession()
            }
        }
    }

    fun toggleAutoPlay() {
        _uiState.update { it.copy(isAutoPlayEnabled = !it.isAutoPlayEnabled) }
    }

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
        
        viewModelScope.launch {
            val wordId = currentQuiz.word.no
            val oldLevel = quizManager.getMasteryLevel(wordId)
            val oldTier = MasteryScheduler.getTier(oldLevel)

            quizManager.submitAnswer(currentQuiz.word, selectedAnswer == currentQuiz.answer)

            val newLevel = quizManager.getMasteryLevel(wordId)
            val newTier = MasteryScheduler.getTier(newLevel)
            
            val isLevelUp = newLevel > oldLevel
            val isBasicJustNow = oldTier != MasteryTier.BASIC_MASTER && newTier == MasteryTier.BASIC_MASTER
            val isLongTermJustNow = oldTier != MasteryTier.LONG_TERM_MASTER && newTier == MasteryTier.LONG_TERM_MASTER

            if (isLevelUp) levelUpsInSession++
            if (isBasicJustNow) basicMastersInSession++
            if (isLongTermJustNow) longTermMastersInSession++

            solvedInSession++
            val isCorrect = selectedAnswer == currentQuiz.answer
            if (isCorrect) {
                withContext(Dispatchers.IO) { pointManager.add(10) }
                _uiState.update { 
                    it.copy(
                        comboCount = it.comboCount + 1, 
                        sessionPoints = it.sessionPoints + 10,
                        progress = (solvedInSession * 100) / totalCount,
                        currentTier = newTier,
                        currentLevel = newLevel,
                        isLevelJustIncreased = isLevelUp,
                        isBasicMasteredJustNow = isBasicJustNow,
                        isLongTermMasteredJustNow = isLongTermJustNow
                    ) 
                }
                _uiEvent.send(LearningUiEvent.ShowCorrect(10, currentQuiz.answer, oldTier != newTier))
                
                if (isBasicJustNow || isLongTermJustNow) {
                    _uiEvent.send(LearningUiEvent.ShowMasteryBadge(newTier))
                }
            } else {
                _uiState.update { 
                    it.copy(
                        comboCount = 0, 
                        progress = (solvedInSession * 100) / totalCount,
                        currentTier = newTier,
                        currentLevel = newLevel,
                        isLevelJustIncreased = false
                    ) 
                }
                _uiEvent.send(LearningUiEvent.ShowWrong(selectedAnswer, currentQuiz.answer))
            }
        }
    }

    private fun finishSession() {
        _uiState.update { 
            it.copy(
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
