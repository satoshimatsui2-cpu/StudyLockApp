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

/**
 * 学習の進行とモード決定を管理するクラス。
 */
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
        val word = selectNextWord()
        if (word == null) {
            Log.e(TAG, "[NextQuiz] FAILED: No candidates available.")
            return@withContext null
        }
        
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)
        val scheduledMode = QuizMode.valueOf(mastery.scheduledMode)
        val actualMode = determineActualMode(mastery, scheduledMode)
        
        Log.d(TAG, "[NextQuiz] SELECTED: ${word.word} (ID:${word.no}), Mode: $actualMode, LV: ${mastery.level}")

        val choices = choiceGenerator.generateChoices(word, actualMode)
            
        QuizData(
            id = UUID.randomUUID().toString(),
            mode = actualMode,
            word = word,
            question = when (actualMode) {
                QuizMode.JP_TO_EN -> word.japanese
                QuizMode.EN_TO_JP -> word.word
                QuizMode.LISTEN_EN -> word.word
                QuizMode.FILL_BLANK -> createFillBlankQuestion(word)
                else -> word.word
            },
            choices = choices,
            answer = if (actualMode == QuizMode.EN_TO_JP) word.japanese else word.word
        )
    }

    /**
     * 穴埋め問題用のテキストを生成します。
     * word を ＿＿＿ に置換します。
     */
    private fun createFillBlankQuestion(word: WordEntity): String {
        val sentence = word.sentence
        if (sentence.isBlank()) return word.japanese

        // 単語境界 (\b) を使って正確に置換を試みる。
        val pattern = Pattern.compile("\\b" + Pattern.quote(word.word) + "\\b", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(sentence)
        
        val replacedSentence = if (matcher.find()) {
            matcher.replaceAll("＿＿＿")
        } else {
            // 単語境界で見つからない場合は単純置換
            if (sentence.contains(word.word, ignoreCase = true)) {
                sentence.replace(word.word, "＿＿＿", ignoreCase = true)
            } else {
                sentence
            }
        }
        
        return "${word.japaneseSentence}\n\n$replacedSentence"
    }

    private fun determineActualMode(mastery: WordMasteryEntity, scheduled: QuizMode): QuizMode {
        if (silentMode == SilentMode.OFF) {
            if (mastery.pendingListenReview && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
                return QuizMode.LISTEN_EN
            }
        }
        if (silentMode == SilentMode.ON && scheduled == QuizMode.LISTEN_EN) {
            return QuizMode.JP_TO_EN
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
        val newWord = wordDao.getRandomNewWordByGrade(userLevel)
        if (newWord != null) return newWord
        return null
    }

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
