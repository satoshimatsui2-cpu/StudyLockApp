package com.stulab.studylockapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengeRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ChallengeRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ChallengeRepository(context, db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testGetPronunciationWords_PrioritizesNotOk() = runBlocking {
        // Insert words
        val words = (1..10).map { createWord(it, 3) }
        db.wordDao().upsertAll(words)

        // Mark as studied
        (1..10).forEach { 
            db.wordMasteryDao().insertOrUpdate(WordMasteryEntity(wordId = it, challengeCount = 1))
        }

        // Mark 1..5 as OK
        (1..5).forEach { 
            db.voiceCheckDao().recordResult(it.toLong(), true, 0.9f, "word")
        }

        val result = repository.getPronunciationWords(3)
        assertEquals(3, result.size)
        // Should contain IDs from 6 to 10 (not OK)
        val ids = result.map { it.no }.toSet()
        ids.forEach { id ->
            assertTrue("ID $id should be between 6 and 10", id in 6..10)
        }
    }

    @Test
    fun testGetPronunciationWords_DefersFailedWords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettings = AppSettings(context)
        appSettings.clearPronunciationChallenge()
        appSettings.deferredPronunciationIds = emptySet()

        // Insert 1..10 words
        val words = (1..10).map { createWord(it, 3) }
        db.wordDao().upsertAll(words)
        (1..10).forEach { 
            db.wordMasteryDao().insertOrUpdate(WordMasteryEntity(wordId = it, challengeCount = 1))
        }

        // Suppose 1, 2, 3 failed in previous session
        appSettings.deferredPronunciationIds = setOf(1L, 2L, 3L)

        val result = repository.getPronunciationWords(3)
        assertEquals(3, result.size)
        
        val resultIds = result.map { it.no.toLong() }.toSet()
        // 1, 2, 3 should NOT be in result because there are other candidates (4..10)
        assertTrue("Result should not contain deferred IDs 1,2,3. Result: $resultIds", 
            resultIds.intersect(setOf(1L, 2L, 3L)).isEmpty())
        
        // After success, deferred IDs should be cleared
        assertTrue("Deferred IDs should be cleared after successful session creation", 
            appSettings.deferredPronunciationIds.isEmpty())
    }

    @Test
    fun testGetPronunciationWords_UsesDeferredIfCandidatesShort() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettings = AppSettings(context)
        appSettings.clearPronunciationChallenge()
        appSettings.deferredPronunciationIds = emptySet()

        // Only 4 words in grade 3
        val words = (1..4).map { createWord(it, 3) }
        db.wordDao().upsertAll(words)
        (1..4).forEach { 
            db.wordMasteryDao().insertOrUpdate(WordMasteryEntity(wordId = it, challengeCount = 1))
        }

        // Suppose 1, 2, 3, 4 failed. (Actually 1,2,3 are deferred, 4 is preferred)
        appSettings.deferredPronunciationIds = setOf(1L, 2L, 3L)

        val result = repository.getPronunciationWords(3)
        assertEquals(3, result.size)
        
        val resultIds = result.map { it.no.toLong() }.toSet()
        // Must contain 4 (preferred), and 2 from (1,2,3)
        assertTrue("Result must contain ID 4", resultIds.contains(4L))
        assertEquals("Should have 3 words total", 3, resultIds.size)
    }

    @Test
    fun testGetPronunciationWords_EnsuresDistinct() = runBlocking {
        // Only 2 words available in total
        val words = (1..2).map { createWord(it, 3) }
        db.wordDao().upsertAll(words)
        (1..2).forEach { 
            db.wordMasteryDao().insertOrUpdate(WordMasteryEntity(wordId = it, challengeCount = 1))
        }

        val result = repository.getPronunciationWords(3)
        // Should be empty because cannot reach 3 distinct words
        assertTrue("Result should be empty if less than 3 distinct words are available. size=${result.size}", 
            result.isEmpty())
    }

    private fun createWord(no: Int, grade: Int) = WordEntity(
        no = no,
        grade = grade,
        word = "word$no",
        japanese = "ja$no",
        description = "",
        sentence = "sentence$no",
        japaneseSentence = "ja_sentence$no",
        pos = "noun",
        difficulty = 1,
        frequency = 1,
        choicesEnJa = emptyList(),
        choicesJaEn = emptyList(),
        choicesListening = emptyList(),
        synonyms = emptyList(),
        antonyms = emptyList()
    )
}
