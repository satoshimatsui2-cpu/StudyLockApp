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
        repository = ChallengeRepository(db)
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
