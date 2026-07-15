package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.WordStudyLogEntity
import com.example.studylockapp.data.db.StudyLogDao
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.db.WordMasteryDao
import com.example.studylockapp.data.db.WordMasteryEntity
import com.example.studylockapp.data.StudyHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private val studyLogDao: StudyLogDao,
    private val appSettings: com.example.studylockapp.data.AppSettings, // 追加
    private var userLevel: Int = 3
) {
    private val choiceGenerator = ChoiceGenerator()
    var silentMode: SilentMode = SilentMode.OFF
    var includeOtherGradeReviews: Boolean = false
    private var pendingReviewPickedInSession = 0
    private var reviewsDoneInCurrentMixedCycle = 0 // 現在のサイクルでこなした復習数

    companion object {
        private const val SESSION_PENDING_LIMIT = 3
        private const val REVIEWS_BEFORE_NEW_WORD = 4 // 5問から4問に変更
        private const val TAG = "GradeFlow"
    }

    fun resetSessionStats() {
        pendingReviewPickedInSession = 0
        reviewsDoneInCurrentMixedCycle = 0
    }

    suspend fun nextQuiz(): QuizData? = withContext(Dispatchers.IO) {
        val word = selectNextWord() ?: return@withContext null
        
        // 選択された単語の種類を判定してサイクル用カウンタを更新
        val mastery = masteryDao.getMastery(word.no)
        if (mastery != null && (mastery.level > 0 || mastery.lastSeen > 0)) {
            // 復習（一度でも見たことがある）の場合
            reviewsDoneInCurrentMixedCycle++
        } else {
            // 新規の場合、サイクルをリセット
            reviewsDoneInCurrentMixedCycle = 0
        }

        val finalMastery = mastery ?: WordMasteryEntity(wordId = word.no)
        val baseMode = determineActualMode(finalMastery, QuizMode.valueOf(finalMastery.scheduledMode))
        val actualMode = resolveModeForWord(word, baseMode)

        val choices = choiceGenerator.generateChoices(word, actualMode)

         QuizData(
            id = UUID.randomUUID().toString(),
            mode = actualMode,
            word = word,
            question = when (actualMode) {
                QuizMode.JP_TO_EN -> word.japanese.trim()
                QuizMode.EN_TO_JP, QuizMode.LISTEN_EN -> word.word.trim()
                QuizMode.FILL_BLANK -> createFillBlankQuestion(word)
                QuizMode.LISTEN_FILL_BLANK -> createListenFillBlankQuestion(word)
                QuizMode.SYNONYM_PICK -> word.synonyms.filter { it.word.trim().isNotBlank() }.shuffled().firstOrNull()?.word?.trim() ?: ""
                QuizMode.ANTONYM_PICK -> word.antonyms.filter { it.word.trim().isNotBlank() }.shuffled().firstOrNull()?.word?.trim() ?: ""
                QuizMode.SENTENCE_SORT -> word.japaneseSentence.trim().ifBlank { word.japanese.trim() }
                else -> word.word.trim()
            },
            choices = choices,
            answer = when (actualMode) {
                QuizMode.EN_TO_JP -> word.japanese.trim()
                QuizMode.SENTENCE_SORT -> word.sentence.trim()
                else -> word.word.trim()
            },
            sortTokens = if (actualMode == QuizMode.SENTENCE_SORT) {
                word.sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.shuffled()
            } else null
        )
    }

    private fun resolveModeForWord(word: WordEntity, mode: QuizMode): QuizMode {
        val isPhrase = word.pos.trim().lowercase() == "phrase"
        var currentMode = mode

        if (currentMode == QuizMode.SYNONYM_PICK) {
            val hasValidSynonym = word.synonyms.any { it.word.trim().isNotBlank() }
            if (!hasValidSynonym) {
                currentMode = QuizMode.FILL_BLANK
            }
        }

        if (currentMode == QuizMode.ANTONYM_PICK) {
            val hasValidAntonym = word.antonyms.any { it.word.trim().isNotBlank() }
            if (!hasValidAntonym) return QuizMode.EN_TO_JP
            return currentMode
        }

        if (currentMode == QuizMode.FILL_BLANK || currentMode == QuizMode.LISTEN_FILL_BLANK) {
            if (isPhrase) {
                return QuizMode.JP_TO_EN
            }
            if (FillBlankTextBuilder.build(word.sentence, word.word) == null) {
                return QuizMode.JP_TO_EN
            }
        }

        if (currentMode == QuizMode.SENTENCE_SORT) {
            val tokens = word.sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 3 || word.japaneseSentence.isBlank() || word.sentence.isBlank()) {
                return QuizMode.JP_TO_EN
            }
        }

        return currentMode
    }

    private fun createFillBlankQuestion(word: WordEntity): String {
        val blanked = FillBlankTextBuilder.build(word.sentence, word.word) ?: ""
        return "${word.japaneseSentence.trim()}\n\n$blanked"
    }

    private fun createListenFillBlankQuestion(word: WordEntity): String {
        return FillBlankTextBuilder.build(word.sentence, word.word) ?: ""
    }

    private fun isListeningMode(mode: QuizMode): Boolean {
        return mode == QuizMode.LISTEN_EN || mode == QuizMode.LISTEN_FILL_BLANK
    }

    private fun shouldSkipForSilentMode(scheduledMode: QuizMode): Boolean {
        return silentMode == SilentMode.ON && isListeningMode(scheduledMode)
    }

    private fun determineActualMode(mastery: WordMasteryEntity, scheduled: QuizMode): QuizMode {
        if (silentMode == SilentMode.OFF) {
            if (mastery.pendingListenReview && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
                return QuizMode.LISTEN_EN
            }
        }
        return scheduled
    }

    /**
     * 出題可能な単語がない場合の理由を判定する
     */
    suspend fun getEmptyStateReason(): LearningEmptyState = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        
        // 本日 23:59:59.999
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        // 本日の残り復習数を確認
        val currentGrade = userLevel
        val includeOthers = if (includeOtherGradeReviews) 1 else 0
        val isSilent = if (silentMode == SilentMode.ON) 1 else 0
        
        val remainingToday = masteryDao.countRemainingReviewsAvailable(
            now = endOfDay,
            currentGrade = currentGrade,
            includeOtherGrades = includeOthers,
            isSilentMode = isSilent
        )

        if (remainingToday > 0) {
            // 現在はないが、本日分は存在している場合
            LearningEmptyState.NoReviewAvailable
        } else {
            // 本日の復習はすべて完了（サイレントモード判定を含む）
            
            // サイレントモード特有の判定：
            // もし今サイレントモードで、バランスモード（isSilentMode=0）ならまだ残りがある場合、
            // 「サイレントモード分は終了」状態にする
            if (silentMode == SilentMode.ON) {
                val remainingNormal = masteryDao.countRemainingReviewsAvailable(
                    now = endOfDay,
                    currentGrade = currentGrade,
                    includeOtherGrades = includeOthers,
                    isSilentMode = 0
                )
                if (remainingNormal > 0) {
                    return@withContext LearningEmptyState.SilentModeFinishedButNormalAvailable
                }
            }

            // 本日の復習はすべて完了。あとは新規ノルマが終わっているか。
            LearningEmptyState.DailyGoalMet
        }
    }

    private suspend fun selectNextWord(): WordEntity? {
        val now = System.currentTimeMillis()
        
        // 1. まず復習対象（期限切れ）があるか確認
        val dueReviewWord = getAnyDueReviewWord(now)

        // 2. 混ぜるロジックの判定
        // 復習が一定数（5問）終わっていない、かつ復習対象がある場合は復習を優先
        if (reviewsDoneInCurrentMixedCycle < REVIEWS_BEFORE_NEW_WORD && dueReviewWord != null) {
            return dueReviewWord
        }

        // 3. 復習ノルマ達成 or 復習対象なし の場合、新規を試みる
        val newWord = getAvailableNewWord()
        if (newWord != null) {
            return newWord
        }

        // 4. 新規が出せない（ノルマ終了など）場合は、残っている復習を出す
        return dueReviewWord
    }

    /**
     * 出題期限が来ている復習単語を1つ取得する（既存の優先順位ロジックをカプセル化）
     */
    private suspend fun getAnyDueReviewWord(now: Long): WordEntity? {
        val allDueMasteries = masteryDao.getDueMasteries(now).shuffled()
        
        // 自級の復習
        for (mastery in allDueMasteries) {
            val scheduledMode = runCatching { QuizMode.valueOf(mastery.scheduledMode) }.getOrDefault(QuizMode.EN_TO_JP)
            if (shouldSkipForSilentMode(scheduledMode)) continue
            val word = wordDao.getWordById(mastery.wordId)
            if (word != null && word.grade == userLevel) return word
        }

        // 他級の復習
        if (includeOtherGradeReviews) {
            for (mastery in allDueMasteries) {
                val scheduledMode = runCatching { QuizMode.valueOf(mastery.scheduledMode) }.getOrDefault(QuizMode.EN_TO_JP)
                if (shouldSkipForSilentMode(scheduledMode)) continue
                val word = wordDao.getWordById(mastery.wordId)
                if (word != null && word.grade != userLevel) return word
            }
        }
        
        // リスニング復習待ち
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries(now)
            for (mastery in pendingMasteries.shuffled()) {
                val word = wordDao.getWordById(mastery.wordId)
                if (word != null && word.grade == userLevel) return word
            }
        }
        return null
    }

    /**
     * 出題可能な新規単語を1つ取得する（ノルマ制限を考慮）
     */
    private suspend fun getAvailableNewWord(): WordEntity? {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        
        val startedTodayCount = masteryDao.countStartedNewWordsToday(startOfDay)
        val dailyTarget = appSettings.dailyNewWordTarget
        
        if (startedTodayCount < dailyTarget) {
            return wordDao.getPriorityNewWordByGrade(userLevel)
        }
        return null
    }

    suspend fun submitAnswer(
        word: WordEntity, 
        isCorrect: Boolean, 
        actualMode: QuizMode,
        timingSettings: ReviewTimingSettings,
        isUnknown: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        appSettings.lastStudyDate = sdf.format(java.util.Date(now))

        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = now

        // 1. 学習ログを記録 (ミリ秒)
        // 正解・不正解・わからないに関わらずすべて記録する
        studyLogDao.insert(WordStudyLogEntity(
            wordId = word.no,
            mode = actualMode.name,
            learnedAt = now
        ))

        // 2. マスタリー状態の更新
        if (actualMode == QuizMode.LISTEN_EN && silentMode == SilentMode.OFF && mastery.pendingListenReview) {
            mastery.pendingListenReview = false
            pendingReviewPickedInSession++
        }

        if (isCorrect) MasteryScheduler.onCorrect(mastery, actualMode, silentMode == SilentMode.ON, timingSettings)
        else MasteryScheduler.onWrong(mastery, actualMode, timingSettings, isUnknown)

        masteryDao.insertOrUpdate(mastery)

        // 3. マスター累計数のFirestore同期は ViewModel で非同期に実行するためここでは行わない
    }

    suspend fun getMasteryCount(tier: MasteryTier): Int = withContext(Dispatchers.IO) {
        when (tier) {
            MasteryTier.BASIC_MASTER -> masteryDao.countBasicMastered()
            MasteryTier.LONG_TERM_MASTER -> masteryDao.countLongTermMastered()
            else -> 0
        }
    }

    suspend fun getMasteryLevel(wordId: Int): Int = withContext(Dispatchers.IO) {
        masteryDao.getMastery(wordId)?.level ?: 0
    }
}
