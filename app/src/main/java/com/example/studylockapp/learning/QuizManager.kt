package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.db.WordDao
import com.example.studylockapp.data.db.WordMasteryDao
import com.example.studylockapp.data.db.WordMasteryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 学習の進行とモード決定を管理するクラス (Final Integrated & Fallback Enhanced Version)
 */
class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private var userLevel: Int = 2
) {
    private val choiceGenerator = ChoiceGenerator(wordDao)
    
    var audioStudyMode: AudioStudyMode = AudioStudyMode.NORMAL
    private var pendingReviewPickedInSession = 0

    companion object {
        private const val SESSION_PENDING_LIMIT = 3
        private const val TAG = "QuizFlow"
    }

    enum class AudioStudyMode { NORMAL, AUDIO_RESTRICTED }

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
        
        Log.e(TAG, "[NextQuiz] SELECTED: ${word.word}, ActualMode: $actualMode, Level: ${mastery.level}")

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
        if (mastery.pendingListenReview && audioStudyMode == AudioStudyMode.NORMAL && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            return QuizMode.LISTEN_EN
        }
        if (scheduled == QuizMode.LISTEN_EN && audioStudyMode == AudioStudyMode.AUDIO_RESTRICTED) {
            return QuizMode.JP_TO_EN
        }
        return scheduled
    }

    private suspend fun selectNextWord(): WordEntity? {
        val now = System.currentTimeMillis()
        
        // 1. 復習期限切れ
        val dueMasteries = masteryDao.getDueMasteries(now)
        val dueCount = dueMasteries.size
        if (dueMasteries.isNotEmpty()) {
            val targetId = dueMasteries.sortedBy { it.nextReviewTime }.take(3).shuffled().first().wordId
            val word = wordDao.getWordById(targetId)
            if (word != null) {
                Log.e(TAG, "[SelectWord] Picked DUE: ${word.word}. userLevel=$userLevel, dueCount=$dueCount")
                return word
            }
        }

        // 2. 音声復習待ち (通常モード & セッション枠内)
        var pendingCount = 0
        if (audioStudyMode == AudioStudyMode.NORMAL && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            pendingCount = pendingMasteries.size
            if (pendingMasteries.isNotEmpty()) {
                val targetId = pendingMasteries.shuffled().first().wordId
                val word = wordDao.getWordById(targetId)
                if (word != null) {
                    Log.e(TAG, "[SelectWord] Picked PENDING: ${word.word}. userLevel=$userLevel, dueCount=$dueCount, pendingCount=$pendingCount")
                    return word
                }
            }
        }

        // 3. 新規または未習得をランダムに (userLevel 指定)
        val levelWord = wordDao.getRandomWordByLevel(userLevel)
        val levelWordFound = levelWord != null
        if (levelWord != null) {
            Log.e(TAG, "[SelectWord] Picked NEW (Level match): ${levelWord.word}. userLevel=$userLevel, dueCount=$dueCount, pendingCount=$pendingCount, levelWordFound=$levelWordFound")
            return levelWord
        }

        // 4. 最終フォールバック: 条件なしで 1 件取得
        val anyWord = wordDao.getAnyRandomWord()
        val anyWordFound = anyWord != null
        Log.e(TAG, "[SelectWord] Final Fallback Info: userLevel=$userLevel, dueCount=$dueCount, pendingCount=$pendingCount, levelWordFound=$levelWordFound, anyWordFound=$anyWordFound")
        
        if (anyWord != null) {
            Log.e(TAG, "[SelectWord] Picked ANY (Fallback): ${anyWord.word}")
            return anyWord
        }

        Log.e(TAG, "[SelectWord] CRITICAL: No words found in DB at all.")
        return null
    }

    suspend fun submitAnswer(word: WordEntity, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = System.currentTimeMillis()

        if (isCorrect) {
            MasteryScheduler.onCorrect(mastery, audioStudyMode == AudioStudyMode.AUDIO_RESTRICTED)
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
