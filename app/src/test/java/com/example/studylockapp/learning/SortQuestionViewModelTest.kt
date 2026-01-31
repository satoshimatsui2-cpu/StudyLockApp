package com.example.studylockapp.learning

import com.example.studylockapp.data.SortQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class SortQuestionViewModelTest {

    private lateinit var viewModel: SortQuestionViewModel
    private lateinit var question: SortQuestion
    private val testSeed = 123L // 固定のシード値

    @Before
    fun setUp() {
        viewModel = SortQuestionViewModel(Random(testSeed)) // 固定シードを持つRandomを注入
        question = SortQuestion(
            id = "so1",
            grade = "英検5級",
            unit = "1",
            japaneseText = "これはペンです。",
            englishSentence = "This is a pen."
        )
        viewModel.setQuestion(question)
    }

    @Test
    fun `setQuestion should initialize state with predictable shuffle`() {
        val uiState = viewModel.uiState.value
        val expectedShuffledWords = question.words.shuffled(Random(testSeed))

        assertEquals(question, uiState.question)
        assertEquals("Words should be shuffled predictably", expectedShuffledWords, uiState.choiceWords)
        assertTrue(uiState.answerWords.isEmpty())
        assertNull(uiState.isCorrect)
    }

    @Test
    fun `selectWord should move word from choices to answers`() {
        val wordToSelect = viewModel.uiState.value.choiceWords.first()

        viewModel.selectWord(wordToSelect)

        val uiState = viewModel.uiState.value
        assertFalse("Word should be removed from choices", uiState.choiceWords.contains(wordToSelect))
        assertTrue("Word should be added to answers", uiState.answerWords.contains(wordToSelect))
        assertEquals("Answer should contain one word", 1, uiState.answerWords.size)
    }

    @Test
    fun `deselectWord should move word from answers to choices`() {
        // Arrange: first, select a word
        val wordToMove = viewModel.uiState.value.choiceWords.first()
        viewModel.selectWord(wordToMove)

        // Act: deselect the word
        viewModel.deselectWord(wordToMove)

        val uiState = viewModel.uiState.value
        assertTrue("Word should be back in choices", uiState.choiceWords.contains(wordToMove))
        assertFalse("Word should be removed from answers", uiState.answerWords.contains(wordToMove))
        assertTrue("Answers should be empty", uiState.answerWords.isEmpty())
    }

    @Test
    fun `checkAnswer should be correct when order is right`() {
        // Arrange: select words in the correct order. We need to know the shuffled order first.
        val shuffledWords = question.words.shuffled(Random(testSeed))
        val correctWordsInShuffledOrder = question.words.map { word -> shuffledWords.find { it == word }!! }

        // Simulate selecting words in correct original order from the shuffled list
        correctWordsInShuffledOrder.forEach { word ->
            // Since selectWord doesn't care about the instance but the value, we can just use original words
        }
        question.words.forEach { viewModel.selectWord(it) }

        // Act
        viewModel.checkAnswer()

        // Assert
        assertTrue("Answer should be correct", viewModel.uiState.value.isCorrect == true)
    }

    @Test
    fun `checkAnswer should be incorrect when order is wrong`() {
        // Arrange: manually set a wrong order
        val shuffledWords = viewModel.uiState.value.choiceWords
        shuffledWords.forEach { viewModel.selectWord(it) } // Select in shuffled (likely wrong) order

        // Act
        viewModel.checkAnswer()

        // Assert
        if (question.words != shuffledWords) {
            assertFalse("Answer should be incorrect", viewModel.uiState.value.isCorrect == true)
        }
    }
}