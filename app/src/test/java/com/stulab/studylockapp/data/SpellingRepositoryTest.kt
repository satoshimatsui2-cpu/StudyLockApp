package com.stulab.studylockapp.data

import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryDao
import com.stulab.studylockapp.data.db.WordMasteryEntity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpellingRepositoryTest {

    private lateinit var repository: SpellingRepository
    private lateinit var db: AppDatabase
    private lateinit var spellingDao: SpellingProgressDao
    private lateinit var wordDao: WordDao
    private lateinit var masteryDao: WordMasteryDao

    @Before
    fun setup() {
        db = mockk()
        spellingDao = mockk()
        wordDao = mockk()
        masteryDao = mockk()

        every { db.spellingProgressDao() } returns spellingDao
        every { db.wordDao() } returns wordDao
        every { db.wordMasteryDao() } returns masteryDao

        repository = SpellingRepository(db)
    }

    @Test
    fun `getEligibleWordsByGrade excludes words never studied`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple")
        val word2 = createWord(2, grade, "banana")
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1, word2)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 0),
            WordMasteryEntity(wordId = 2, challengeCount = 1)
        )
        coEvery { spellingDao.getProgressByIds(listOf(1L, 2L)) } returns emptyList()

        val result = repository.getEligibleWordsByGrade(grade)
        assertEquals(1, result.size)
        assertEquals("banana", result[0].word)
    }

    @Test
    fun `getEligibleWordsByGrade includes practicing words`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple")
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 1)
        )
        coEvery { spellingDao.getProgressByIds(listOf(1L)) } returns listOf(
            SpellingProgressEntity(wordId = 1, status = SpellingStatus.PRACTICING, unlockedAt = 0, eligibleAt = 0)
        )

        val result = repository.getEligibleWordsByGrade(grade)
        assertEquals(1, result.size)
    }

    @Test
    fun `getEligibleWordsByGrade excludes cleared words`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple")
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 1)
        )
        coEvery { spellingDao.getProgressByIds(listOf(1L)) } returns listOf(
            SpellingProgressEntity(wordId = 1, status = SpellingStatus.CLEARED, unlockedAt = 0, eligibleAt = 0)
        )

        val result = repository.getEligibleWordsByGrade(grade)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTotalStudyCountByGrade counts eligible studied words`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple") // studied, eligible
        val word2 = createWord(2, grade, "banana") // not studied
        val word3 = createWord(3, grade, "~~~") // studied, NOT eligible
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1, word2, word3)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 1),
            WordMasteryEntity(wordId = 2, challengeCount = 0),
            WordMasteryEntity(wordId = 3, challengeCount = 1)
        )
        
        val count = repository.getTotalStudyCountByGrade(grade)
        assertEquals(1, count)
    }

    @Test
    fun `recordResult marks as cleared only when hint not used`() = runBlocking {
        val wordId = 1L
        val existing = SpellingProgressEntity(
            wordId = wordId, status = SpellingStatus.PRACTICING, unlockedAt = 0, eligibleAt = 0
        )
        coEvery { spellingDao.getProgress(wordId) } returns existing
        coEvery { spellingDao.insertOrUpdate(any()) } just Runs

        // Correct answer with hint -> stays PRACTICING
        repository.recordResult(wordId, isCorrect = true, hintUsed = true)

        val slot1 = slot<SpellingProgressEntity>()
        coVerify(exactly = 1) { spellingDao.insertOrUpdate(capture(slot1)) }
        assertEquals(SpellingStatus.PRACTICING, slot1.captured.status)
        assertNull(slot1.captured.clearedAt)

        // Reset mocks or use fresh slots/verifications
        clearMocks(spellingDao, answers = false)
        coEvery { spellingDao.getProgress(wordId) } returns existing

        // Correct answer without hint -> becomes CLEARED
        repository.recordResult(wordId, isCorrect = true, hintUsed = false)
        val slot2 = slot<SpellingProgressEntity>()
        coVerify(exactly = 1) { spellingDao.insertOrUpdate(capture(slot2)) }
        assertEquals(SpellingStatus.CLEARED, slot2.captured.status)
        assertNotNull(slot2.captured.clearedAt)
    }

    @Test
    fun `recordResult does not downgrade cleared status`() = runBlocking {
        val wordId = 1L
        val existing = SpellingProgressEntity(
            wordId = wordId, status = SpellingStatus.CLEARED, unlockedAt = 0, eligibleAt = 0, clearedAt = 123
        )
        coEvery { spellingDao.getProgress(wordId) } returns existing
        coEvery { spellingDao.insertOrUpdate(any()) } just Runs

        // Wrong answer later
        repository.recordResult(wordId, isCorrect = false, hintUsed = false)

        val slot = slot<SpellingProgressEntity>()
        coVerify { spellingDao.insertOrUpdate(capture(slot)) }
        assertEquals(SpellingStatus.CLEARED, slot.captured.status)
        assertEquals(123L, slot.captured.clearedAt)
    }

    private fun createWord(no: Int, grade: Int, spelling: String) = WordEntity(
        no = no,
        grade = grade,
        word = spelling,
        japanese = "意味",
        description = "",
        sentence = "Example sentence.",
        japaneseSentence = "",
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
