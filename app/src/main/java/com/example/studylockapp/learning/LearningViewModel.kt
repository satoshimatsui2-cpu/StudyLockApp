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
import com.example.studylockapp.data.StudyHistoryRepository
import com.example.studylockapp.data.practical.PracticalQuizMode
import com.example.studylockapp.data.practical.PracticalTestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

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
    private val appSettings: AppSettings,
    private val practicalRepo: PracticalTestRepository
) : ViewModel() {

    private val totalCount = 20
    private var solvedInSession = 0
    
    private var levelUpsInSession = 0
    private var basicMastersInSession = 0
    private var longTermMastersInSession = 0

    // 初期化時にポイントマネージャーから累計を取得
    private val _uiState = MutableStateFlow(
        LearningUiState(
            totalSteps = totalCount,
            totalPoints = pointManager.getTotal(),
            selectedCharacterId = appSettings.selectedCharacterId
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = Channel<LearningUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    companion object {
        private const val TAG = "LearningViewModel"
        const val UNKNOWN_ANSWER_LABEL = "わからない"
    }

    init {
        // 設定の同期
        val currentSilent = appSettings.silentMode
        val currentOtherGrades = appSettings.includeOtherGrades
        val currentChoiceMode = appSettings.choiceModeEnabled
        
        quizManager.silentMode = currentSilent
        quizManager.includeOtherGradeReviews = currentOtherGrades
        
        _uiState.update { state -> 
            state.copy(
                silentMode = currentSilent,
                includeOtherGradeReviews = currentOtherGrades,
                choicesInitiallyVisible = currentChoiceMode
            ) 
        }
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
                            }
                        }
                    }
                    appSettings.hasResetMasteryForFix = true
                }
                
                TsvImporter(context, wordDao, appSettings).seedIfNeeded()
                wordDao.countAllWords()
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

        viewModelScope.launch {
            updateQuotaInternal()
            
            // モード切替時は画面をリセット（現在のクイズを破棄して再取得）
            // 特にサイレントON時に音声問題が表示されている状態を防ぐ
            _uiState.update { it.copy(quiz = null, isAnswering = false, isReviewing = false) }
            loadNextQuiz()
        }
    }

    fun setIncludeOtherGradeReviews(enabled: Boolean) {
        appSettings.includeOtherGrades = enabled
        quizManager.includeOtherGradeReviews = enabled
        _uiState.update { it.copy(includeOtherGradeReviews = enabled) }

        viewModelScope.launch {
            updateQuotaInternal()
            // 空状態だった場合は再読み込みを試みる
            if (_uiState.value.emptyState != null) {
                _uiState.update { it.copy(emptyState = null) }
                loadNextQuiz()
            }
        }
    }
    
    fun setChoicesInitiallyVisible(enabled: Boolean) {
        appSettings.choiceModeEnabled = enabled
        _uiState.update { it.copy(choicesInitiallyVisible = enabled) }
    }

    private fun getReviewTimingSettings(): ReviewTimingSettings {
        return ReviewTimingSettings(
            correctSameDayDelayMillis = appSettings.level1RetrySec * 1000L,
            wrongSameDayDelayMillis = appSettings.wrongRetrySec * 1000L,
            unknownSameDayDelayMillis = appSettings.dontKnowRetrySec * 1000L
        )
    }

    fun loadNextQuiz() {
        if (solvedInSession == 0) quizManager.resetSessionStats()
        
        viewModelScope.launch {
            // 読み込み開始時に状態をリセット (空状態も解除)
            _uiState.update { state -> 
                state.copy(isLoading = true, isAnswering = false, isReviewing = false, emptyState = null) 
            }
            
            val quiz = quizManager.nextQuiz()
            
            // 出題不可または10問終了時の判定
            if (quiz == null || solvedInSession >= totalCount) {
                val emptyReason = quizManager.getEmptyStateReason()
                
                if (emptyReason is LearningEmptyState.SilentModeFinishedButNormalAvailable) {
                    _uiState.update { it.copy(isLoading = false, emptyState = emptyReason) }
                    return@launch
                }
                
                if (quiz == null) {
                    // ★ これ以上問題がない場合は、TOPに戻らず空状態を表示する
                    _uiState.update { it.copy(isLoading = false, emptyState = emptyReason) }
                    
                    // 目標達成しているなら、その上にお祝い演出を出す
                    if (emptyReason is LearningEmptyState.DailyGoalMet) {
                        updateQuotaInternal()
                        finishSession()
                    }
                } else {
                    // 10問解き終わった（まだ他に問題はある）場合は、通常の終了処理へ
                    _uiState.update { it.copy(isLoading = false) }
                    updateQuotaInternal()
                    finishSession()
                }
                return@launch
            }

            val targetLevel = if (appSettings.isTargetLearningGradeSet) {
                appSettings.safeTargetLearningGrade.toIntOrNull()?.takeIf { it in 1..7 } ?: 3
            } else 0

            // IO
            val counts = withContext(Dispatchers.IO) {
                val b = quizManager.getMasteryCount(MasteryTier.BASIC_MASTER)
                val l = quizManager.getMasteryCount(MasteryTier.LONG_TERM_MASTER)
                val m = masteryDao.getMastery(quiz.word.no) ?: WordMasteryEntity(wordId = quiz.word.no)
                val total = pointManager.getTotal()
                Triple(Triple(b, l, m), total, null)
            }
            
            val basicCount = counts.first.first
            val longTermCount = counts.first.second
            val mastery = counts.first.third
            val currentTier = MasteryScheduler.getTier(mastery)
            val currentTotalPoints = counts.second

            val importance = quiz.mode.getAudioImportance()
            val hasRisk = audioChecker.isSilenceRisk()
            val isSilent = _uiState.value.silentMode == SilentMode.ON
            
            val warning = if (!isSilent && hasRisk && (importance == QuizMode.AudioImportance.REQUIRED || importance == QuizMode.AudioImportance.OPTIONAL)) {
                AudioWarningState(
                    message = if (importance == QuizMode.AudioImportance.REQUIRED) requiredWarningText else optionalWarningText,
                    isCritical = (importance == QuizMode.AudioImportance.REQUIRED)
                )
            } else null

            _uiState.update { state -> 
                state.copy(
                    comboCount = state.comboCount,
                    totalPoints = currentTotalPoints, 
                    quiz = quiz, 
                    isLoading = false,
                    currentStep = solvedInSession + 1,
                    progress = ((solvedInSession * 100) / totalCount).coerceAtMost(100),
                    audioWarning = warning,
                    basicMasterCount = basicCount,
                    longTermMasterCount = longTermCount,
                    currentTier = currentTier,
                    currentLevel = mastery.level,
                    targetLevel = targetLevel,
                    isLevelJustIncreased = false,
                    currentWord = quiz.word,
                    wordGrade = quiz.word.grade
                ) 
            }
            
            if (!isSilent) {
                if (shouldAutoPlayQuestionAudio(quiz.mode)) {
                    val audioText = getQuestionAudioTextForMode(quiz)
                    if (audioText != null) {
                        requestAudioPlayback(audioText)
                    }
                }
            }
            
            updateQuotaInternal()
        }
    }

    private suspend fun updateQuotaInternal() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val now = System.currentTimeMillis()

        val results = withContext(Dispatchers.IO) {
            val startedToday = masteryDao.countStartedNewWordsToday(startOfDay)
            
            val currentGrade = appSettings.safeLearningGrade.toIntOrNull() ?: 3
            val includeOthers = if (appSettings.includeOtherGrades) 1 else 0
            val isSilent = if (appSettings.silentMode == SilentMode.OFF) 0 else 1

            // 1. 今すぐ解ける復習数
            val remainingNow = masteryDao.countRemainingReviewsAvailable(
                now = now,
                currentGrade = currentGrade,
                includeOtherGrades = includeOthers,
                isSilentMode = isSilent
            )

            // 2. 今日中に解く必要がある全ての復習数 (未来分も含む)
            val remainingToday = masteryDao.countRemainingReviewsAvailable(
                now = endOfDay,
                currentGrade = currentGrade,
                includeOtherGrades = includeOthers,
                isSilentMode = isSilent
            )

            // ★ 音声も含めた今日の残り (目標達成判定用)
            val remainingNormalToday = masteryDao.countRemainingReviewsAvailable(
                now = endOfDay,
                currentGrade = currentGrade,
                includeOtherGrades = includeOthers,
                isSilentMode = 0
            )

            arrayOf(startedToday, remainingNow, remainingToday, remainingNormalToday)
        }

        val newDone = results[0]
        val reviewRemainingNow = results[1]
        val reviewRemainingNormalToday = results[3]

        val target = appSettings.dailyNewWordTarget
        val newRemaining = (target - newDone).coerceAtLeast(0)

        // ノルマ達成時の継続記録更新 (新規 0 且つ 今日の復習すべて 0 の場合)
        // ※ここでは音声OFF時の残りも含めて判定する（完全に終わった時だけ更新）
        if (newRemaining == 0 && reviewRemainingNormalToday == 0) {
            updateGoalStreakInternal()
            
            // フレンドへの通知ブロードキャスト
            viewModelScope.launch(Dispatchers.IO) {
                val totalNew = appSettings.dailyNewWordTarget
                val totalReviews = masteryDao.countCompletedReviewsToday(startOfDay, System.currentTimeMillis())
                val currentStreak = appSettings.dailyGoalStreak
                StudyHistoryRepository.broadcastGoalMet(totalNew, totalReviews, currentStreak)
            }
        }

        _uiState.update { it.copy(
            newWordsRemaining = newRemaining,
            reviewWordsRemaining = reviewRemainingNow,
            reviewWordsTotalToday = reviewRemainingNormalToday, // 常に当日期限の総数（音声含む）を表示
            reviewWordsNormalTotalToday = reviewRemainingNormalToday
        ) }
    }

    private fun updateGoalStreakInternal() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayStr = sdf.format(java.util.Date())
        
        if (appSettings.lastGoalMetDate == todayStr) return // 今日は既に達成済み

        val lastMet = appSettings.lastGoalMetDate
        if (lastMet != null) {
            val lastDate = sdf.parse(lastMet)
            val yesterday = java.util.Calendar.getInstance().apply { 
                add(java.util.Calendar.DAY_OF_YEAR, -1) 
            }.time
            val yesterdayStr = sdf.format(yesterday)
            
            if (lastMet == yesterdayStr) {
                appSettings.dailyGoalStreak += 1
            } else {
                appSettings.dailyGoalStreak = 1
            }
        } else {
            appSettings.dailyGoalStreak = 1
        }
        
        appSettings.lastGoalMetDate = todayStr
        appSettings.totalGoalsMetCount += 1
    }

    fun refreshPoints() {
        viewModelScope.launch {
            val total = withContext(Dispatchers.IO) { pointManager.getTotal() }
            _uiState.update { it.copy(totalPoints = total) }
        }
    }

    private fun getQuestionAudioTextForMode(quiz: QuizData): String? {
        val word = quiz.word
        return when (quiz.mode) {
            QuizMode.EN_TO_JP -> word.word
            QuizMode.LISTEN_EN -> word.word
            QuizMode.LISTEN_FILL_BLANK -> word.sentence
            else -> null
        }
    }

    private fun shouldAutoPlayQuestionAudio(mode: QuizMode): Boolean {
        return when (mode) {
            QuizMode.EN_TO_JP,
            QuizMode.LISTEN_EN,
            QuizMode.LISTEN_FILL_BLANK -> true
            else -> false
        }
    }

    fun onNextAfterReview() {
        if (_uiState.value.isFinished) return
        loadNextQuiz()
    }

    fun requestAudioPlayback(text: String? = null) {
        if (_uiState.value.silentMode == SilentMode.ON) return

        val playText = text ?: run {
            val quiz = _uiState.value.quiz ?: return
            getQuestionAudioTextForMode(quiz) ?: return
        }
        
        viewModelScope.launch {
            _uiEvent.send(LearningUiEvent.PlayAudio(playText))
        }
    }

    fun submitUnknownAnswer() {
        submitAnswer(UNKNOWN_ANSWER_LABEL)
    }

    fun submitAnswer(selectedAnswer: String) {
        val currentQuiz = _uiState.value.quiz ?: return
        if (_uiState.value.isAnswering || _uiState.value.isReviewing) return
        
        // 1. 状態を「解答中」にする
        _uiState.update { state -> state.copy(isAnswering = true) }
        
        viewModelScope.launch {
            val wordId = currentQuiz.word.no
            val oldLevel = quizManager.getMasteryLevel(wordId)
            val oldMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val oldTier = MasteryScheduler.getTier(oldMastery)

            val isUnknown = selectedAnswer == UNKNOWN_ANSWER_LABEL
            val isCorrect = !isUnknown && (selectedAnswer.trim().lowercase() == currentQuiz.answer.trim().lowercase())

            val timingSettings = getReviewTimingSettings()
            
            var gainedPoints = 0
            if (isCorrect) {
                val basePoint = appSettings.getBasePoint(currentQuiz.mode)
                val targetGradeInt = appSettings.safeTargetLearningGrade.toIntOrNull() ?: 3
                gainedPoints = RewardPointCalculator.calculate(basePoint, currentQuiz.word.grade, targetGradeInt)
            }

            // 2. ローカルDBの更新 (Roomは十分高速とみなすが、Firestoreは待たない)
            withContext(Dispatchers.IO) {
                quizManager.submitAnswer(currentQuiz.word, isCorrect, currentQuiz.mode, timingSettings, isUnknown)
                if (isCorrect) {
                    pointManager.add(gainedPoints)
                }
            }

            // 3. 更新後のマスタリー状態を取得
            val newMastery = withContext(Dispatchers.IO) { masteryDao.getMastery(wordId) } ?: WordMasteryEntity(wordId = wordId)
            val newLevel = newMastery.level
            val newTier = MasteryScheduler.getTier(newMastery)
            
            val isLevelUp = newLevel > oldLevel
            if (isLevelUp) levelUpsInSession++
            
            if (oldLevel < 5 && newLevel == 5) {
                _uiEvent.send(LearningUiEvent.ShowLevel5BonusInduction(currentQuiz.word))
            }

            if (oldTier != MasteryTier.BASIC_MASTER && newTier == MasteryTier.BASIC_MASTER) {
                basicMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowBasicMasterCelebration)
            }
            if (oldTier != MasteryTier.LONG_TERM_MASTER && newTier == MasteryTier.LONG_TERM_MASTER) {
                longTermMastersInSession++
                _uiEvent.send(LearningUiEvent.ShowLongTermMasterCelebration)
            }

            solvedInSession++
            
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
                currentQuiz.word.sentence
            } else {
                currentQuiz.word.word
            }

            var wrongWordEntity: WordEntity? = null
            if (currentQuiz.mode == QuizMode.LISTEN_EN && !isCorrect && !isUnknown) {
                wrongWordEntity = withContext(Dispatchers.IO) {
                    wordDao.getWordBySpelling(selectedAnswer)
                }
            }

            var synonymTitle: String? = null
            var synonymBody: String? = null
            if (!isCorrect && !isUnknown && (currentQuiz.mode == QuizMode.JP_TO_EN || currentQuiz.mode == QuizMode.LISTEN_EN)) {
                val synonym = currentQuiz.word.synonyms.find { it.word.equals(selectedAnswer, ignoreCase = true) }
                if (synonym != null) {
                    synonymTitle = "惜しい不正解"
                    synonymBody = if (synonym.note.isNotBlank()) synonym.note else "意味は近いが今回の正解ではない"
                }
            }

            // 加算後の総保有ポイントを取得
            val latestTotal = pointManager.getTotal()

            // 4. UI状態の更新 (判定表示)
            _uiState.update { state -> 
                state.copy(
                    comboCount = if (isCorrect) state.comboCount + 1 else 0, 
                    totalPoints = latestTotal,
                    progress = ((solvedInSession * 100) / totalCount).coerceAtMost(100),
                    currentTier = newTier,
                    currentLevel = newLevel,
                    isLevelJustIncreased = if (isCorrect) isLevelUp else false,
                    isLastAnswerCorrect = isCorrect,
                    isUnknownAnswer = isUnknown,
                    reviewModeLabel = modeLabel,
                    reviewQuestionText = questionText,
                    reviewUserAnswerText = selectedAnswer,
                    reviewCorrectAnswerText = correctDisplay,
                    showListeningCompare = (currentQuiz.mode == QuizMode.LISTEN_EN && !isCorrect && !isUnknown),
                    wrongWord = wrongWordEntity,
                    reviewSynonymHintTitle = synonymTitle,
                    reviewSynonymHintBody = synonymBody,
                    reviewAntonyms = currentQuiz.word.antonyms
                ) 
            }

            // 5. 解答イベントの送信 (正解/不正解アニメーション等)
            if (isCorrect) {
                if (newLevel - oldLevel >= 2) _uiEvent.send(LearningUiEvent.ShowFlyingLevelUp(oldLevel, newLevel))
                _uiEvent.send(LearningUiEvent.ShowCorrect(gainedPoints, currentQuiz.answer, oldTier != newTier))
                if (oldTier != newTier) _uiEvent.send(LearningUiEvent.ShowMasteryBadge(newTier))
            } else {
                _uiEvent.send(LearningUiEvent.ShowWrong(selectedAnswer, currentQuiz.answer, isUnknown))
            }

            // 6. Firestoreへの保存 (バックグラウンドで fire-and-forget)
            val gradeStr = currentQuiz.word.grade.toString()
            val modeName = currentQuiz.mode.name
            val wordText = currentQuiz.word.word
            val gp = gainedPoints
            viewModelScope.launch(Dispatchers.IO) {
                // 学習結果の保存 (最新の保有ポイントをスナップショットとして渡す)
                StudyHistoryRepository.save(
                    grade = gradeStr,
                    mode = modeName,
                    isCorrect = isCorrect,
                    points = gp,
                    word = wordText,
                    currentTotalPoints = latestTotal
                )
                
                // マスター累計の同期 (拡張版)
                val lv1 = masteryDao.countByLevel(1)
                val lv2 = masteryDao.countByLevel(2)
                val lv3 = masteryDao.countByLevel(3)
                val short = masteryDao.countBasicMastered()
                val long = masteryDao.countLongTermMastered()
                StudyHistoryRepository.updateMasteryCounts(
                    lv1 = lv1,
                    lv2 = lv2,
                    lv3 = lv3,
                    shortMasterCount = short,
                    longMasterCount = long
                )
            }
            
            updateQuotaInternal()
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
            try {
                // 学習中のグレードを取得 (QuizManager に渡しているものと同じ値)
                val grade = appSettings.safeLearningGrade.toIntOrNull()?.takeIf { it in 1..7 } ?: 3

                // ★ 本日のノルマが完全に0になったかチェック（音声ありモードを含めて判定）
                val isGoalMet = _uiState.value.newWordsRemaining == 0 && _uiState.value.reviewWordsNormalTotalToday == 0

                if (isGoalMet) {
                    // 目標達成お祝いイベントを送信
                    val character = com.example.studylockapp.data.notification.StudyCharacter.fromId(appSettings.selectedCharacterId)
                    val message = com.example.studylockapp.data.notification.CharacterLines.getLine(
                        character, 
                        com.example.studylockapp.data.notification.NotificationContext.GOAL_COMPLETED,
                        name = appSettings.userName ?: "君"
                    )
                    _uiEvent.send(LearningUiEvent.ShowGrandCelebration(character.id, character.displayName, message))
                    return@launch
                }

                // 20問のフルセッションを完了した時のみ、実践テストへの移行を検討する
                val isFullSessionCompleted = solvedInSession >= totalCount

                // 実践テスト問題があるか確認 (穴埋め または リスニング)
                val hasPractical = if (isFullSessionCompleted) {
                    withContext(Dispatchers.IO) {
                        practicalRepo.hasQuestions(PracticalQuizMode.FILL_BLANK, grade) ||
                                (appSettings.silentMode == SilentMode.OFF && practicalRepo.hasQuestions(PracticalQuizMode.LISTENING, grade))
                    }
                } else false
                
                if (hasPractical) {
                    _uiEvent.send(LearningUiEvent.NavigateToPracticalTest(grade))
                } else {
                    // ★ 目標未達だがこれ以上解ける問題がない場合は、TOPに戻らず空状態を表示（loadNextQuiz側と同期）
                    val nextOne = quizManager.nextQuiz()
                    if (nextOne == null) {
                        val emptyReason = quizManager.getEmptyStateReason()
                        _uiState.update { it.copy(emptyState = emptyReason) }
                    } else {
                        _uiEvent.send(LearningUiEvent.QuizFinished)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking practical questions", e)
                // エラー時は通常終了へ
                _uiEvent.send(LearningUiEvent.QuizFinished)
            }
        }
    }

    /**
     * 実践テスト終了後、次の学習セッションを開始する。
     */
    fun startNextSessionAfterPracticalTest() {
        solvedInSession = 0
        levelUpsInSession = 0
        basicMastersInSession = 0
        longTermMastersInSession = 0

        _uiState.update { state ->
            state.copy(
                isFinished = false,
                isReviewing = false,
                isAnswering = false,
                progress = 0,
                currentStep = 1,
                sessionLevelUpCount = 0,
                sessionBasicMasterGained = 0,
                sessionLongTermMasterGained = 0,
                totalPoints = pointManager.getTotal()
            )
        }

        loadNextQuiz()
    }
}
