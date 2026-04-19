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
import java.util.regex.Pattern

class QuizManager(
    private val wordDao: WordDao,
    private val masteryDao: WordMasteryDao,
    private var userLevel: Int = 2
) {
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
        val word = selectNextWord() ?: return@withContext null
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        
        val baseMode = determineActualMode(mastery, QuizMode.valueOf(mastery.scheduledMode))
        val actualMode = resolveModeForWord(word, baseMode)
        
        val choices = choiceGenerator.generateChoices(word, actualMode)
        
        // デバッグログ: 選択肢が空でないか確認
        Log.e(TAG, "[ListenFillBlankDebug] mode=$actualMode, word=${word.word}, choices=${choices.joinToString()}")
            
        QuizData(
            id = UUID.randomUUID().toString(),
            mode = actualMode,
            word = word,
            question = when (actualMode) {
                QuizMode.JP_TO_EN -> word.japanese.trim()
                QuizMode.EN_TO_JP, QuizMode.LISTEN_EN -> word.word.trim()
                QuizMode.FILL_BLANK -> createFillBlankQuestion(word)
                QuizMode.LISTEN_FILL_BLANK -> createListenFillBlankQuestion(word)
                else -> word.word.trim()
            },
            choices = choices,
            answer = if (actualMode == QuizMode.EN_TO_JP) word.japanese.trim() else word.word.trim()
        )
    }

    /**
     * pos=phrase は穴埋め対象外。JP_TO_EN にフォールバック。
     */
    private fun resolveModeForWord(word: WordEntity, mode: QuizMode): QuizMode {
        val isPhrase = word.pos.trim().lowercase() == "phrase"
        return if ((mode == QuizMode.FILL_BLANK || mode == QuizMode.LISTEN_FILL_BLANK) && isPhrase) {
            QuizMode.JP_TO_EN
        } else {
            mode
        }
    }

    private fun createFillBlankQuestion(word: WordEntity): String {
        val blanked = createBlankedSentence(word)
        return "${word.japaneseSentence.trim()}\n\n$blanked"
    }

    private fun createListenFillBlankQuestion(word: WordEntity): String {
        return createBlankedSentence(word)
    }

    private fun createBlankedSentence(word: WordEntity): String {
        val s = word.sentence.trim()
        val w = word.word.trim()
        if (s.isBlank()) return "(      )"

        val flexibleWord = Pattern.quote(w).replace(" ", "\\s+")
        val pattern = Pattern.compile("\\b$flexibleWord\\b", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(s)
        
        return if (matcher.find()) {
            matcher.replaceFirst("＿＿＿")
        } else {
            Log.e(TAG, "[FillBlankReplaceFailed] word=${word.word}, wordId=${word.no}, sentence=${word.sentence}")
            "(      )"
        }
    }

    /**
     * サイレント時は LISTEN_FILL_BLANK を音声なしの FILL_BLANK へ落とす。
     */
    private fun determineActualMode(mastery: WordMasteryEntity, scheduled: QuizMode): QuizMode {
        if (silentMode == SilentMode.OFF) {
            if (mastery.pendingListenReview && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
                return QuizMode.LISTEN_EN
            }
        }
        if (silentMode == SilentMode.ON) {
            return when (scheduled) {
                QuizMode.LISTEN_EN -> QuizMode.JP_TO_EN
                QuizMode.LISTEN_FILL_BLANK -> QuizMode.FILL_BLANK
                else -> scheduled
            }
        }
        return scheduled
    }

    private suspend fun selectNextWord(): WordEntity? {
        val now = System.currentTimeMillis()
        val dueMasteries = masteryDao.getDueMasteries(now)
        if (dueMasteries.isNotEmpty()) {
            val mastery = dueMasteries.sortedBy { it.nextReviewTime }.take(3).shuffled().first()
            val word = wordDao.getWordById(mastery.wordId)
            if (word != null) return word
        }
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            if (pendingMasteries.isNotEmpty()) {
                val mastery = pendingMasteries.shuffled().first()
                val word = wordDao.getWordById(mastery.wordId)
                if (word != null) return word
            }
        }
        return wordDao.getRandomNewWordByGrade(userLevel)
    }

    suspend fun submitAnswer(word: WordEntity, isCorrect: Boolean, actualMode: QuizMode) = withContext(Dispatchers.IO) {
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        mastery.lastSeen = System.currentTimeMillis()

        if (actualMode == QuizMode.LISTEN_EN && silentMode == SilentMode.OFF && mastery.pendingListenReview) {
            mastery.pendingListenReview = false
            pendingReviewPickedInSession++
        }

        if (isCorrect) MasteryScheduler.onCorrect(mastery, actualMode, silentMode == SilentMode.ON)
        else MasteryScheduler.onWrong(mastery, actualMode)

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
