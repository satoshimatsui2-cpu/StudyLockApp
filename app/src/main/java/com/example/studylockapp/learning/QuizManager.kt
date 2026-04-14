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
 * 学習の進行とモード決定を管理するクラス (SilentMode 統合版)
 */
class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private var userLevel: Int = 2
) {
    private val choiceGenerator = ChoiceGenerator(wordDao)
    
    // サイレントモード状態 (ViewModelから注入される)
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

        if (actualMode == QuizMode.LISTEN_EN && mastery.pendingListenReview) {
            pendingReviewPickedInSession++
        }

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
        // サイレントモード OFF の時だけ、音声復習待ちを優先的に出す
        if (silentMode == SilentMode.OFF) {
            if (mastery.pendingListenReview && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
                return QuizMode.LISTEN_EN
            }
        }

        // サイレントモード ON の時は LISTEN_EN を回避
        if (silentMode == SilentMode.ON) {
            if (scheduled == QuizMode.LISTEN_EN) {
                // 音声が必要なステップなら代替モード（日本語->英語）にする
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
            val targetId = dueMasteries.sortedBy { it.nextReviewTime }.take(3).shuffled().first().wordId
            val word = wordDao.getWordById(targetId)
            if (word != null) return word
        }

        // 2. 音声復習待ち (通常モード & セッション枠内)
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            if (pendingMasteries.isNotEmpty()) {
                val targetId = pendingMasteries.shuffled().first().wordId
                val word = wordDao.getWordById(targetId)
                if (word != null) return word
            }
        }

        // 3. 新規または未習得
        val levelWord = wordDao.getRandomWordByLevel(userLevel)
        if (levelWord != null) return levelWord

        // 4. 最終フォールバック
        return wordDao.getAnyRandomWord()
    }

    suspend fun submitAnswer(word: WordEntity, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = System.currentTimeMillis()

        if (isCorrect) {
            // サイレントモードONの場合は restricted=true として判定。
            // これにより MasteryScheduler 側で pendingListenReview が維持される。
            MasteryScheduler.onCorrect(mastery, silentMode == SilentMode.ON)
        } else {
            MasteryScheduler.onWrong(mastery)
        }

        masteryDao.insertOrUpdate(mastery)
        Log.e(TAG, "[Answer] ${word.word} Correct: $isCorrect -> NewLevel: ${mastery.level}")
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
