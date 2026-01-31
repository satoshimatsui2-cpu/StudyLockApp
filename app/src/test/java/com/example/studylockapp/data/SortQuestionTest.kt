package com.example.studylockapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SortQuestionTest {

    @Test
    fun `words property should split englishSentence by space`() {
        // Arrange
        val question = SortQuestion(
            id = "so1",
            grade = "英検5級",
            unit = "1",
            japaneseText = "これはペンです。",
            englishSentence = "This is a pen."
        )

        // Act
        val words = question.words

        // Assert
        val expectedWords = listOf("This", "is", "a", "pen.")
        assertEquals(expectedWords, words)
    }

    @Test
    fun `words property should handle single word`() {
        // Arrange
        val question = SortQuestion(
            id = "so2",
            grade = "英検5級",
            unit = "1",
            japaneseText = "こんにちは",
            englishSentence = "Hello"
        )

        // Act
        val words = question.words

        // Assert
        val expectedWords = listOf("Hello")
        assertEquals(expectedWords, words)
    }
}