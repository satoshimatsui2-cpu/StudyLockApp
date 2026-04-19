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
 * 初回起動時のインポート完了を待機してから出題を開始するように修正されました。
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

    private val _uiState = MutableStateFlow(LearningUiState(totalSteps = totalCount))
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
     * インポート処理の完了を確実に待機してから最初の問題をロードします。
     */
    fun loadInitialQuiz() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            withContext(Dispatchers.IO) {
                // インポート完了を待つ
                TsvImporter(context, wordDao).seedIfNeeded()
                
                // 診断ログ
                val total = wordDao.countAllWords()
                val currentGrade = appSettings.currentLearningGrade
                Log.e(TAG, "[SeedCheck] import finished. totalWords=$total, currentGrade=$currentGrade")
            }
            
            loadNextQuiz()
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
                
                // 現在の単語の MasteryEntity を取得して現在の Tier を特定
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
        // サイレントモード時はリクエスト自体を無効化
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
            
            // Tier 比較用に回答前の状態を取得
            val oldMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val oldTier = MasteryScheduler.getTier(oldMastery)

            quizManager.submitAnswer(currentQuiz.word, selectedAnswer == currentQuiz.answer, currentQuiz.mode)

            // 回答後の状態を取得して Tier 変化を検知
            val newMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val newLevel = newMastery.level
            val newTier = MasteryScheduler.getTier(newMastery)
            
            val isLevelUp = newLevel > oldLevel
            if (isLevelUp) levelUpsInSession++
            
            // 習得度変化の検知とイベント発火
            if (oldTier != MasteryTier.BASIC_MASTER && newTier == MasteryTier.BASIC_MASTER) {
                basicMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowBasicMasterCelebration)
            }
            if (oldTier != MasteryTier.LONG_TERM_MASTER && newTier == MasteryTier.LONG_TERM_MASTER) {
                longTermMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowLongTermMasterCelebration)
            }

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

            // Synonyms / Antonyms 判定ロジック
            var synonymTitle: String? = null
            var synonymBody: String? = null
            if (!isCorrect && (currentQuiz.mode == QuizMode.JP_TO_EN || currentQuiz.mode == QuizMode.LISTEN_EN)) {
                val synonym = currentQuiz.word.synonyms.find { it.word.equals(selectedAnswer, ignoreCase = true) }
                if (synonym != null) {
                    synonymTitle = "惜しい不正解"
                    synonymBody = if (synonym.note.isNotBlank()) synonym.note else "意味は近いが今回の正解ではない"
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
                    wrongWord = wrongWordEntity,
                    reviewSynonymHintTitle = synonymTitle,
                    reviewSynonymHintBody = synonymBody,
                    reviewAntonyms = currentQuiz.word.antonyms
                ) 
            }

            if (isCorrect) {
                withContext(Dispatchers.IO) { pointManager.add(10) }
                
                // 飛び級演出判定
                if (newLevel - oldLevel >= 2) {
                    _uiEvent.send(LearningUiEvent.ShowFlyingLevelUp(oldLevel, newLevel))
                }

                // Tier変化の通知（LearningActivity 側で ShowMasteryBadge を受けて演出を行う）
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
