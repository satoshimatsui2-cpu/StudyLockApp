package com.example.studylockapp.learning

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studylockapp.data.TsvImporter
import com.example.studylockapp.data.PointManager
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.db.WordMasteryDao
import com.example.studylockapp.data.db.WordMasteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 学習画面のメイン ViewModel。
 */
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

    // 初期化時にポイントマネージャーから累計を取得
    private val _uiState = MutableStateFlow(
        LearningUiState(
            totalSteps = totalCount,
            totalPoints = pointManager.getTotal()
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<LearningUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    companion object {
        private const val TAG = "LearningViewModel"
    }

    init {
        // 設定の同期
        val currentSilent = appSettings.silentMode
        quizManager.silentMode = currentSilent
        _uiState.update { state -> state.copy(silentMode = currentSilent) }
    }

    /**
     * 初回のクイズ読み込み。
     */
    fun loadInitialQuiz() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            withContext(Dispatchers.IO) {
                // ★ 既存データの1回限定自動補正
                if (!appSettings.hasResetMasteryForFix) {
                    val allMasteries = masteryDao.getAllMasteries()
                    allMasteries.forEach { m ->
                        // LV5未満なのに並べ替えが予約されている不具合データを特定して修正
                        if (m.level < 5 && m.scheduledMode == QuizMode.SENTENCE_SORT.name) {
                            val correctedMode = when (m.level) {
                                0, 1 -> QuizMode.EN_TO_JP
                                2 -> QuizMode.JP_TO_EN
                                3 -> QuizMode.LISTEN_EN
                                4 -> QuizMode.FILL_BLANK
                                else -> null
                            }
                            if (correctedMode != null) {
                                m.scheduledMode = correctedMode.name
                                masteryDao.insertOrUpdate(m)
                                Log.e(TAG, "[AutoFix] wordId=${m.wordId} level=${m.level} corrected to ${m.scheduledMode}")
                            }
                        }
                    }
                    appSettings.hasResetMasteryForFix = true
                }
                
                TsvImporter(context, wordDao).seedIfNeeded()
                val total = wordDao.countAllWords()
                val currentGrade = appSettings.currentLearningGrade
                Log.e(TAG, "[SeedCheck] import finished. totalWords=$total, currentGrade=$currentGrade")
            }
            loadNextQuiz()
        }
    }

    fun toggleSilentMode() {
        val nextMode = if (_uiState.value.silentMode == SilentMode.OFF) SilentMode.ON else SilentMode.OFF
        appSettings.silentMode = nextMode
        quizManager.silentMode = nextMode
        _uiState.update { state -> state.copy(silentMode = nextMode) }

        if (nextMode == SilentMode.ON && !appSettings.hasShownSilentExplanation) {
            viewModelScope.launch {
                _uiEvent.send(LearningUiEvent.ShowSilentModeExplanation)
            }
        }
    }

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
            
            if (quiz == null) {
                _uiState.update { it.copy(isLoading = false) }
                if (solvedInSession == 0) {
                    // 初回ロードでクイズが取れなかった場合は、単語がないイベントを発行
                    _uiEvent.send(LearningUiEvent.NoAvailableWords)
                } else {
                    finishSession()
                }
                return@launch
            }

            // quiz が null でない場合の通常処理
            val importance = quiz.mode.getAudioImportance()
            val hasRisk = audioChecker.isSilenceRisk()
            
            val isSilent = _uiState.value.silentMode == SilentMode.ON
            val warning = if (!isSilent && hasRisk && (importance == QuizMode.AudioImportance.REQUIRED || importance == QuizMode.AudioImportance.OPTIONAL)) {
                AudioWarningState(
                    message = if (importance == QuizMode.AudioImportance.REQUIRED) requiredWarningText else optionalWarningText,
                    isCritical = (importance == QuizMode.AudioImportance.REQUIRED)
                )
            } else null

            val basicCount = quizManager.getMasteryCount(MasteryTier.BASIC_MASTER)
            val longTermCount = quizManager.getMasteryCount(MasteryTier.LONG_TERM_MASTER)
            
            val mastery = withContext(Dispatchers.IO) {
                masteryDao.getMastery(quiz.word.no)
            } ?: WordMasteryEntity(wordId = quiz.word.no)
            
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
            
            if (!isSilent) {
                val shouldAutoPlay = when (importance) {
                    QuizMode.AudioImportance.REQUIRED -> true
                    QuizMode.AudioImportance.OPTIONAL -> true
                    else -> false
                }
                if (shouldAutoPlay) requestAudioPlayback()
            }
        }
    }

    fun onNextAfterReview() {
        if (_uiState.value.isFinished) return
        loadNextQuiz()
    }

    /**
     * 音声再生をリクエストします。
     */
    fun requestAudioPlayback(text: String? = null) {
        if (_uiState.value.silentMode == SilentMode.ON) return

        val playText = text ?: run {
            val quiz = _uiState.value.quiz ?: return
            if (quiz.mode == QuizMode.LISTEN_FILL_BLANK) {
                quiz.word.sentence
            } else {
                quiz.word.word
            }
        }
        
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
            
            val oldMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val oldTier = MasteryScheduler.getTier(oldMastery)

            quizManager.submitAnswer(currentQuiz.word, selectedAnswer.trim().lowercase() == currentQuiz.answer.trim().lowercase(), currentQuiz.mode)

            val newMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val newLevel = newMastery.level
            val newTier = MasteryScheduler.getTier(newMastery)
            
            val isLevelUp = newLevel > oldLevel
            if (isLevelUp) levelUpsInSession++
            
            if (oldTier != MasteryTier.BASIC_MASTER && newTier == MasteryTier.BASIC_MASTER) {
                basicMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowBasicMasterCelebration)
            }
            if (oldTier != MasteryTier.LONG_TERM_MASTER && newTier == MasteryTier.LONG_TERM_MASTER) {
                longTermMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowLongTermMasterCelebration)
            }

            solvedInSession++
            val isCorrect = (selectedAnswer.trim().lowercase() == currentQuiz.answer.trim().lowercase())
            
            val modeLabel = when (currentQuiz.mode) {
                QuizMode.EN_TO_JP -> "英語 → 日本語"
                QuizMode.JP_TO_EN -> "日本語 → 英語"
                QuizMode.LISTEN_EN -> "リスニング"
                QuizMode.FILL_BLANK -> "穴埋め"
                QuizMode.LISTEN_FILL_BLANK -> "リスニング(文脈)"
                QuizMode.SENTENCE_SORT -> "英文並び替え"
                else -> currentQuiz.mode.name
            }
            val questionText = when(currentQuiz.mode) {
                QuizMode.LISTEN_EN -> "聞こえた英単語"
                QuizMode.LISTEN_FILL_BLANK -> "聞こえた英文の空欄"
                else -> currentQuiz.question
            }
            val correctDisplay = if (currentQuiz.mode == QuizMode.EN_TO_JP) {
                currentQuiz.word.japanese.takeIf { it.isNotBlank() } ?: currentQuiz.answer
            } else if (currentQuiz.mode == QuizMode.SENTENCE_SORT) {
                currentQuiz.word.sentence // 並び替えは英文を正解表示にする
            } else {
                currentQuiz.word.word
            }

            var wrongWordEntity: WordEntity? = null
            if (currentQuiz.mode == QuizMode.LISTEN_EN && !isCorrect) {
                wrongWordEntity = withContext(Dispatchers.IO) {
                    wordDao.getWordBySpelling(selectedAnswer)
                }
            }

            // Synonyms / Antonyms 判定 (復元)
            var synonymTitle: String? = null
            var synonymBody: String? = null
            if (!isCorrect && (currentQuiz.mode == QuizMode.JP_TO_EN || currentQuiz.mode == QuizMode.LISTEN_EN)) {
                val synonym = currentQuiz.word.synonyms.find { it.word.equals(selectedAnswer, ignoreCase = true) }
                if (synonym != null) {
                    synonymTitle = "惜しい不正解"
                    synonymBody = if (synonym.note.isNotBlank()) synonym.note else "意味は近いが今回の正解ではない"
                }
            }

            if (isCorrect) {
                withContext(Dispatchers.IO) { pointManager.add(10) }
            }

            _uiState.update { state -> 
                state.copy(
                    comboCount = if (isCorrect) state.comboCount + 1 else 0, 
                    totalPoints = pointManager.getTotal(), // 累計を反映
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
                    wrongWord = wrongWordEntity,
                    reviewSynonymHintTitle = synonymTitle,
                    reviewSynonymHintBody = synonymBody,
                    reviewAntonyms = currentQuiz.word.antonyms
                ) 
            }

            if (isCorrect) {
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
