package com.example.studylockapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvDataLoaderTest {

    private lateinit var csvDataLoader: CsvDataLoader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        csvDataLoader = CsvDataLoader(context)
    }

    @Test
    fun loadSortQuestions_shouldLoadAllQuestionsFromCsv() {
        // Act
        val questions = csvDataLoader.loadSortQuestions()

        // Assert
        assertEquals("Should load all 10 questions from the CSV file.", 10, questions.size)
    }

    @Test
    fun loadSortQuestions_shouldCorrectlyParseTheFirstQuestion() {
        // Act
        val questions = csvDataLoader.loadSortQuestions()

        // Assert
        assertTrue("Question list should not be empty.", questions.isNotEmpty())
        val firstQuestion = questions.first()
        assertEquals("so1", firstQuestion.id)
        assertEquals("英検5級", firstQuestion.grade)
        assertEquals("1", firstQuestion.unit)
        assertEquals("これはペンです。", firstQuestion.japaneseText)
        assertEquals("This is a pen.", firstQuestion.englishSentence)
    }
}