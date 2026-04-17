package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.SilentMode
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.db.WordMasteryDao
import com.example.studylockapp.data.db.WordMasteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 学習の進行とモード決定を管理するクラス
 */
class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private var userLevel: Int = 2
) {
    // 新しい ChoiceGenerator は WordDao を必要としません
    private val choiceGenerator = ChoiceGenerator()
    
    var silentMode: SilentMode = SilentMode.OFF
    private var pendingReviewPickedInSession = 0

    companion object {
        private const val SESSION_PENDING_LIMIT = 3
        private const val TAG = "QuizFlow"
    }

    fun resetSessionStats() {
        pendingReviewPickedInSession = 0
    }

    suspend fun nextQuiz(): QuizData? = withContext(Dispatchers.IO) {
        val word = selectNextWord()
        if (word == null) {
            Log.e(TAG, "[NextQuiz] FAILED: DB might be empty.")
            return@withContext null
        }
        
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        val scheduledMode = QuizMode.valueOf(mastery.scheduledMode)
        val actualMode = determineActualMode(mastery, scheduledMode)
        
        val choices = choiceGenerator.generateChoices(word, actualMode)
            
        QuizData(
            id = UUID.randomUUID().toString(),
            mode = actualMode,
            word = word,
            question = when (actualMode) {
                QuizMode.JP_TO_EN -> word.japanese
                QuizMode.EN_TO_JP -> word.word
                QuizMode.LISTEN_EN -> word.word
                else -> word.word
            },
            choices = choices,
            answer = if (actualMode == QuizMode.EN_TO_JP) word.japanese else word.word
        )
    }

    private fun determineActualMode(mastery: WordMasteryEntity, scheduled: QuizMode): QuizMode {
        if (silentMode == SilentMode.OFF) {
            if (mastery.pendingListenReview && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
                return QuizMode.LISTEN_EN
            }
        }

        if (silentMode == SilentMode.ON) {
            if (scheduled == QuizMode.LISTEN_EN) {
                return QuizMode.JP_TO_EN
            }
        }
        
        return scheduled
    }

    private suspend fun selectNextWord(): WordEntity? {
        val now = System.currentTimeMillis()
        
        // 1. 復習期限切れ
        val dueMasteries = masteryDao.getDueMasteries(now)
        if (dueMasteries.isNotEmpty()) {
            val mastery = dueMasteries.sortedBy { it.nextReviewTime }.take(3).shuffled().first()
            val word = wordDao.getWordById(mastery.wordId)
            if (word != null) return word
        }

        // 2. 音声復習待ち
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            if (pendingMasteries.isNotEmpty()) {
                val mastery = pendingMasteries.shuffled().first()
                val word = wordDao.getWordById(mastery.wordId)
                if (word != null) return word
            }
        }

        // 3. 新規または未習得 (WordDao のメソッド名を getRandomWordByGrade に合わせる)
        val gradeWord = wordDao.getRandomWordByGrade(userLevel)
        if (gradeWord != null) return gradeWord

        // 4. 最終フォールバック
        return wordDao.getAnyRandomWord()
    }

    /**
     * 回答提出
     */
    suspend fun submitAnswer(word: WordEntity, isCorrect: Boolean, actualMode: QuizMode) = withContext(Dispatchers.IO) {
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = System.currentTimeMillis()

        if (actualMode == QuizMode.LISTEN_EN && silentMode == SilentMode.OFF && mastery.pendingListenReview) {
            mastery.pendingListenReview = false
            pendingReviewPickedInSession++
        }

        if (isCorrect) {
            MasteryScheduler.onCorrect(mastery, actualMode, silentMode == SilentMode.ON)
        } else {
            MasteryScheduler.onWrong(mastery, actualMode)
        }

        masteryDao.insertOrUpdate(mastery)
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
