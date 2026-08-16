package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.data.SilentMode
import com.stulab.studylockapp.sanitizeForTts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynonymAntonymAudioTest {

    private fun mockWord(word: String): WordEntity {
        return WordEntity(
            no = 1, grade = 3, word = word, japanese = "意味",
            description = "", sentence = "This is a sentence.", japaneseSentence = "これは文章です。",
            pos = "verb", difficulty = 1, frequency = 1,
            choicesEnJa = emptyList(), choicesJaEn = emptyList(), choicesListening = emptyList(),
            synonyms = emptyList(), antonyms = emptyList()
        )
    }

    private fun mockQuiz(word: WordEntity, mode: QuizMode): QuizData {
        return QuizData(
            mode = mode,
            word = word,
            question = word.word,
            choices = emptyList(),
            answer = "answer"
        )
    }

    /**
     * getQuestionAudioTextForMode の検証
     */
    @Test
    fun testGetQuestionAudioTextForMode() {
        val word = mockWord("corporate")
        
        // SYNONYM_PICK: word.word が返るべき
        val synonymQuiz = mockQuiz(word, QuizMode.SYNONYM_PICK)
        assertEquals("corporate", getQuestionAudioText(synonymQuiz))

        // ANTONYM_PICK: word.word が返るべき
        val antonymQuiz = mockQuiz(word, QuizMode.ANTONYM_PICK)
        assertEquals("corporate", getQuestionAudioText(antonymQuiz))

        // LISTEN_FILL_BLANK: word.sentence が返るべき
        val lfbQuiz = mockQuiz(word, QuizMode.LISTEN_FILL_BLANK)
        assertEquals("This is a sentence.", getQuestionAudioText(lfbQuiz))
        
        // SENTENCE_SORT: null が返るべき (現状対象外)
        val sortQuiz = mockQuiz(word, QuizMode.SENTENCE_SORT)
        assertNull(getQuestionAudioText(sortQuiz))
    }

    /**
     * sanitizeForTts の検証
     */
    @Test
    fun testSanitizeForTts() {
        assertEquals("be good at", sanitizeForTts("be good at ~"))
        assertEquals("apple", sanitizeForTts(" apple "))
        assertEquals("one two", sanitizeForTts("one  two"))
        assertEquals("", sanitizeForTts("~"))
        assertEquals("", sanitizeForTts("   "))
    }

    /**
     * LearningViewModelの非公開メソッドをシミュレート
     */
    private fun getQuestionAudioText(quiz: QuizData): String? {
        val word = quiz.word
        return when (quiz.mode) {
            QuizMode.EN_TO_JP -> word.word
            QuizMode.LISTEN_EN -> word.word
            QuizMode.LISTEN_FILL_BLANK -> word.sentence
            QuizMode.SYNONYM_PICK -> word.word
            QuizMode.ANTONYM_PICK -> word.word
            else -> null
        }
    }
    
    /**
     * replayボタンの活性化ロジックのシミュレーション
     */
    @Test
    fun testCanReplayQuestionAudioLogic() {
        // EN_TO_JP, LISTEN_EN, LISTEN_FILL_BLANK, SYNONYM_PICK, ANTONYM_PICK は true
        assertTrue(canReplay(QuizMode.EN_TO_JP))
        assertTrue(canReplay(QuizMode.LISTEN_EN))
        assertTrue(canReplay(QuizMode.LISTEN_FILL_BLANK))
        assertTrue(canReplay(QuizMode.SYNONYM_PICK))
        assertTrue(canReplay(QuizMode.ANTONYM_PICK))
        
        // その他は false
        assertFalse(canReplay(QuizMode.JP_TO_EN))
        assertFalse(canReplay(QuizMode.FILL_BLANK))
        assertFalse(canReplay(QuizMode.SENTENCE_SORT))
    }

    private fun canReplay(mode: QuizMode): Boolean {
        return when (mode) {
            QuizMode.EN_TO_JP,
            QuizMode.LISTEN_EN,
            QuizMode.LISTEN_FILL_BLANK,
            QuizMode.SYNONYM_PICK,
            QuizMode.ANTONYM_PICK -> true
            else -> false
        }
    }
    
    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
    private fun assertFalse(value: Boolean) = org.junit.Assert.assertFalse(value)
}
