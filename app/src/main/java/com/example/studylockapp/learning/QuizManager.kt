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

        // 調査用ログ
        Log.e(
            TAG,
            "[ModeCheck] word=${word.word}, level=${mastery.level}, scheduledMode=${mastery.scheduledMode}, actualMode=$actualMode"
        )

        val choices = choiceGenerator.generateChoices(word, actualMode)

        // デバッグログ
        Log.d(TAG, "[QuizDebug] mode=$actualMode, word=${word.word}, choices=${choices.joinToString()}")

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

    /**
     * 指定された方針に基づいたフォールバック判定
     */
    private fun resolveModeForWord(word: WordEntity, mode: QuizMode): QuizMode {
        val isPhrase = word.pos.trim().lowercase() == "phrase"
        var currentMode = mode

        // 1. SYNONYM_PICK: 有効な synonym が無ければ FILL_BLANK にフォールバック
        if (currentMode == QuizMode.SYNONYM_PICK) {
            val hasValidSynonym = word.synonyms.any { it.word.trim().isNotBlank() }
            if (!hasValidSynonym) {
                currentMode = QuizMode.FILL_BLANK
            }
        }

        // 2. ANTONYM_PICK: 有効な antonym が無ければ EN_TO_JP
        if (currentMode == QuizMode.ANTONYM_PICK) {
            val hasValidAntonym = word.antonyms.any { it.word.trim().isNotBlank() }
            if (!hasValidAntonym) return QuizMode.EN_TO_JP
            return currentMode
        }

        // 3. phrase は穴埋め(文脈系)対象外 -> JP_TO_EN にフォールバック
        if ((currentMode == QuizMode.FILL_BLANK || currentMode == QuizMode.LISTEN_FILL_BLANK) && isPhrase) {
            return QuizMode.JP_TO_EN
        }

        // 4. SENTENCE_SORT: 不適合なら JP_TO_EN
        if (currentMode == QuizMode.SENTENCE_SORT) {
            val tokens = word.sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 3 || word.japaneseSentence.isBlank() || word.sentence.isBlank()) {
                return QuizMode.JP_TO_EN
            }
        }

        return currentMode
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
            Log.e(TAG, "[FillBlankReplaceFailed] word=${word.word}, sentence=${word.sentence}")
            "(      )"
        }
    }

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

        // ★ 更新前ログ
        Log.e(TAG, "[BeforeUpdate] word=${word.word}, level=${mastery.level}, currentScheduled=${mastery.scheduledMode}")

        if (isCorrect) MasteryScheduler.onCorrect(mastery, actualMode, silentMode == SilentMode.ON)
        else MasteryScheduler.onWrong(mastery, actualMode)

        // ★ 更新後ログ
        Log.e(TAG, "[AfterScheduler] word=${word.word}, nextLevel=${mastery.level}, nextScheduled=${mastery.scheduledMode}")

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