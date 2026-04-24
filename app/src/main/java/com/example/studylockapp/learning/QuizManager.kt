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
    private var userLevel: Int = 3
) {
    private val choiceGenerator = ChoiceGenerator()
    var silentMode: SilentMode = SilentMode.OFF
    private var pendingReviewPickedInSession = 0

    companion object {
        private const val SESSION_PENDING_LIMIT = 3
        private const val TAG = "GradeFlow" // ログタグを統一
    }

    fun resetSessionStats() {
        pendingReviewPickedInSession = 0
    }

    suspend fun nextQuiz(): QuizData? = withContext(Dispatchers.IO) {
        val word = selectNextWord() ?: return@withContext null
        val mastery = masteryDao.getMastery(word.no) ?: WordMasteryEntity(wordId = word.no)

        val baseMode = determineActualMode(mastery, QuizMode.valueOf(mastery.scheduledMode))
        val actualMode = resolveModeForWord(word, baseMode)

        Log.d(TAG, "selected word=${word.word}, word.grade=${word.grade}, userLevel=$userLevel")

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

        if ((currentMode == QuizMode.FILL_BLANK || currentMode == QuizMode.LISTEN_FILL_BLANK) && isPhrase) {
            return QuizMode.JP_TO_EN
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
        
        // 1. 復習対象(通常)の抽出
        val dueMasteries = masteryDao.getDueMasteries(now)
        if (dueMasteries.isNotEmpty()) {
            val candidates = dueMasteries.sortedBy { it.nextReviewTime }.take(20).shuffled()
            for (mastery in candidates) {
                val word = wordDao.getWordById(mastery.wordId)
                // 復習対象も現在の userLevel に一致するものだけに絞る
                if (word != null && word.grade == userLevel) {
                    Log.d(TAG, "selected due word=${word.word}, grade=${word.grade}, userLevel=$userLevel")
                    return word
                }
            }
        }
        
        // 2. 音声復習待ちの抽出
        if (silentMode == SilentMode.OFF && pendingReviewPickedInSession < SESSION_PENDING_LIMIT) {
            val pendingMasteries = masteryDao.getPendingListenMasteries()
            if (pendingMasteries.isNotEmpty()) {
                for (mastery in pendingMasteries.shuffled()) {
                    val word = wordDao.getWordById(mastery.wordId)
                    if (word != null && word.grade == userLevel) {
                        Log.d(TAG, "selected pending word=${word.word}, grade=${word.grade}, userLevel=$userLevel")
                        return word
                    }
                }
            }
        }
        
        // 3. 新規単語の抽出 (wordDao側でgradeフィルタ済み)
        val newWord = wordDao.getRandomNewWordByGrade(userLevel)
        Log.d(TAG, "selected new word=${newWord?.word}, grade=${newWord?.grade}, userLevel=$userLevel")
        return newWord
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
