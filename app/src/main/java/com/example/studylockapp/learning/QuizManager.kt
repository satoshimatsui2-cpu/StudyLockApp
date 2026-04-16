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
 * 学習の進行とモード決定を管理するクラス (SilentMode 統合・再出題バグ修正版)
 */
class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private var userLevel: Int = 2
) {
    private val choiceGenerator = ChoiceGenerator(wordDao)
    
    var silentMode: SilentMode = SilentMode.OFF
    private var pendingReviewPickedInSession = 0

    companion object {
        private const val SESSION_PENDING_LIMIT = 3
        private const val TAG = "QuizFlow"
    }

    fun resetSessionStats() {
        pendingReviewPickedInSession = 0
        Log.e(TAG, "Session stats reset. Recover limit: $SESSION_PENDING_LIMIT")
    }

    suspend fun nextQuiz(): QuizData? = withContext(Dispatchers.IO) {
        val word = selectNextWord()
        if (word == null) {
            Log.e(TAG, "[NextQuiz] FAILED: All fallback selection failed. DB might be empty.")
            return@withContext null
        }
        
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        val scheduledMode = QuizMode.valueOf(mastery.scheduledMode)
        val actualMode = determineActualMode(mastery, scheduledMode)
        
        Log.e(TAG, "[NextQuiz] SELECTED: ${word.word}, ActualMode: $actualMode, Level: ${mastery.level}, Silent: $silentMode")

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
            // 音声復習枠（SESSION_PENDING_LIMIT）が残っている場合のみ、フラグのある単語を LISTEN_EN に強制
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
            if (word != null) {
                Log.e(TAG, "[SelectReason] source=DUE, word=${word.word}, level=${mastery.level}, nextReviewTime=${mastery.nextReviewTime}, pending=${mastery.pendingListenReview}")
                return word
            }
        }

        // 2. 音声復習待ち (通常モード & セッション枠内)
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            if (pendingMasteries.isNotEmpty()) {
                val mastery = pendingMasteries.shuffled().first()
                val word = wordDao.getWordById(mastery.wordId)
                if (word != null) {
                    Log.e(TAG, "[SelectReason] source=PENDING, word=${word.word}, level=${mastery.level}, nextReviewTime=${mastery.nextReviewTime}, pending=${mastery.pendingListenReview}")
                    return word
                }
            }
        }

        // 3. 新規または未習得
        val levelWord = wordDao.getRandomWordByLevel(userLevel)
        if (levelWord != null) {
            val mastery = masteryDao.getMastery(levelWord.no) ?: WordMasteryEntity(wordId = levelWord.no)
            Log.e(TAG, "[SelectReason] source=NEW, word=${levelWord.word}, level=${mastery.level}, nextReviewTime=${mastery.nextReviewTime}, pending=${mastery.pendingListenReview}")
            return levelWord
        }

        // 4. 最終フォールバック
        val anyWord = wordDao.getAnyRandomWord()
        if (anyWord != null) {
            val mastery = masteryDao.getMastery(anyWord.no) ?: WordMasteryEntity(wordId = anyWord.no)
            Log.e(TAG, "[SelectReason] source=ANY, word=${anyWord.word}, level=${mastery.level}, nextReviewTime=${mastery.nextReviewTime}, pending=${mastery.pendingListenReview}")
            return anyWord
        }

        return null
    }

    /**
     * 回答提出
     */
    suspend fun submitAnswer(word: WordEntity, isCorrect: Boolean, actualMode: QuizMode) = withContext(Dispatchers.IO) {
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = System.currentTimeMillis()

        // 音声復習フラグの解除ロジック
        // LISTEN_EN を実際に出題できたなら、その結果（正誤）に関わらず「借り」を返済したものとみなす
        if (actualMode == QuizMode.LISTEN_EN && silentMode == SilentMode.OFF && mastery.pendingListenReview) {
            mastery.pendingListenReview = false
            pendingReviewPickedInSession++ // 実際に解き終わったタイミングでセッション回収数を増やす
            Log.e(TAG, "[Recovery] Pending review cleared for ${word.word}. Session count: $pendingReviewPickedInSession")
        }

        if (isCorrect) {
            MasteryScheduler.onCorrect(mastery, actualMode, silentMode == SilentMode.ON)
        } else {
            MasteryScheduler.onWrong(mastery, actualMode)
        }

        masteryDao.insertOrUpdate(mastery)
        
        // 詳細な結果ログ
        Log.e(TAG, "[AfterAnswer] word=${word.word}, correct=$isCorrect, " +
            "level=${mastery.level}, actualMode=$actualMode, scheduledMode=${mastery.scheduledMode}, " +
            "pending=${mastery.pendingListenReview}, nextReviewTime=${mastery.nextReviewTime}, " +
            "deltaSec=${(mastery.nextReviewTime - System.currentTimeMillis()) / 1000}")
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
