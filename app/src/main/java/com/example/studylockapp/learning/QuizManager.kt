package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.db.WordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class QuizManager(
    private val wordDao: WordDao,
    private var userLevel: Int = 2
) {
    private val learningStates = mutableMapOf<Int, LearningState>()
    private val retryQueue: Queue<WordEntity> = LinkedList()

    suspend fun nextQuiz(): QuizData? = withContext(Dispatchers.IO) {
        val word = selectNextWord() ?: return@withContext null
        val state = learningStates[word.no]

        // モード選択
        val mode = selectMode(state)

        val choices = when (mode) {
            QuizMode.EN_TO_JP -> {
                val rawDistractors = (word.confusion + word.related).filter { it.isNotBlank() }
                val distractors = wordDao.getJapaneseByWords(rawDistractors).toMutableList()
                if (distractors.size < 10) {
                    distractors.addAll(wordDao.getRandomJapaneseDistractors(word.japanese, word.grade, 10))
                }
                QuizLogic.createChoices(word.japanese, distractors)
            }
            QuizMode.JP_TO_EN, QuizMode.LISTEN_EN -> {
                val distractors = word.confusion.filter { it.isNotBlank() }.toMutableList()
                if (distractors.size < 10) {
                    distractors.addAll(wordDao.getRandomDistractors(word.word, word.grade, 10))
                }
                QuizLogic.createChoices(word.word, distractors)
            }
            else -> emptyList()
        }
            
        QuizData(
            mode = mode,
            word = word,
            question = when (mode) {
                QuizMode.JP_TO_EN -> word.japanese
                QuizMode.EN_TO_JP -> word.word
                else -> "" 
            },
            choices = choices,
            answer = if (mode == QuizMode.EN_TO_JP) word.japanese else word.word
        )
    }

    /**
     * 習熟度に応じてモードを選択。
     */
    private fun selectMode(state: LearningState?): QuizMode {
        return when {
            // 初見 or 正解1回未満 -> 日本語から意味を選ぶ
            //state == null || state.correctCount < 1 -> QuizMode.JP_TO_EN
            state == null || state.correctCount < 1 -> QuizMode.LISTEN_EN
            // 正解1回 -> 英語から日本語を選ぶ
            state.correctCount < 2 -> QuizMode.EN_TO_JP
            // それ以降はリスニング主体のランダム
            else -> if (Random().nextBoolean()) QuizMode.LISTEN_EN else QuizMode.EN_TO_JP
        }
    }

    private suspend fun selectNextWord(): WordEntity? {
        val now = System.currentTimeMillis()
        val dueId = learningStates.values
            .filter { it.nextReviewTime in 1..now }
            .map { it.wordId }.shuffled().firstOrNull()
        
        if (dueId != null) {
            val word = wordDao.getWordById(dueId)
            if (word != null) return word
        }

        if (retryQueue.isNotEmpty()) {
            return retryQueue.poll()
        }

        return wordDao.getRandomWordByLevel(userLevel)
    }

    fun submitAnswer(word: WordEntity, isCorrect: Boolean) {
        val state = learningStates.getOrPut(word.no) { LearningState(word.no) }
        state.lastSeen = System.currentTimeMillis()

        if (isCorrect) {
            state.correctCount++
            state.nextReviewTime = ReviewScheduler.calculateNextReview(state.correctCount)
        } else {
            state.wrongCount++
            state.nextReviewTime = 0 
            if (!retryQueue.contains(word)) {
                retryQueue.add(word)
            }
        }
    }
}
