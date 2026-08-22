package com.stulab.studylockapp.data

import android.content.Context
import android.util.Log
import com.stulab.studylockapp.data.VoiceCheckDao
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryDao
import com.stulab.studylockapp.data.db.WordMasteryEntity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PronunciationSessionTest {

    private lateinit var repository: ChallengeRepository
    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var masteryDao: WordMasteryDao
    private lateinit var voiceDao: VoiceCheckDao
    private lateinit var context: Context
    private lateinit var appSettings: AppSettings

    @Before
    fun setup() {
        ShadowLog.stream = System.out
        context = mockk(relaxed = true)
        db = mockk()
        wordDao = mockk()
        masteryDao = mockk()
        voiceDao = mockk()
        appSettings = mockk(relaxed = true)

        every { db.wordDao() } returns wordDao
        every { db.wordMasteryDao() } returns masteryDao
        every { db.voiceCheckDao() } returns voiceDao
        every { db.spellingProgressDao() } returns mockk()
        
        // Mock AppSettings constructor call inside repository if any, 
        // or ensure repository uses the mocked context which returns mocked prefs
        // Actually ChallengeRepository creates AppSettings(context)
        // So we might need to mock AppSettings constructor or the SharedPreferences
        
        mockkConstructor(AppSettings::class)
        every { anyConstructed<AppSettings>().deferredPronunciationIds } returns emptySet()
        every { anyConstructed<AppSettings>().deferredPronunciationIds = any() } just Runs

        repository = ChallengeRepository(context, db)
    }

    @Test
    fun `getPronunciationWords returns empty if less than 3 distinct words available`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple")
        val word2 = createWord(2, grade, "banana")
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1, word2)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 1),
            WordMasteryEntity(wordId = 2, challengeCount = 1)
        )
        coEvery { voiceDao.getAllResultsByIds(any()) } returns emptyList()

        val result = repository.getPronunciationWords(grade)
        assertTrue("Should be empty when only 2 words available", result.isEmpty())
    }

    @Test
    fun `getPronunciationWords ensures 3 distinct words`() = runBlocking {
        val grade = 3
        val word1 = createWord(1, grade, "apple")
        val word2 = createWord(2, grade, "banana")
        val word3 = createWord(3, grade, "cherry")
        
        coEvery { wordDao.getWordsByGrade(grade) } returns listOf(word1, word2, word3)
        coEvery { masteryDao.getAllMasteries() } returns listOf(
            WordMasteryEntity(wordId = 1, challengeCount = 1),
            WordMasteryEntity(wordId = 2, challengeCount = 1),
            WordMasteryEntity(wordId = 3, challengeCount = 1)
        )
        coEvery { voiceDao.getAllResultsByIds(any()) } returns emptyList()

        val result = repository.getPronunciationWords(grade)
        assertEquals(3, result.size)
        assertEquals(3, result.distinctBy { it.no }.size)
    }

    private fun createWord(no: Int, grade: Int, word: String) = WordEntity(
        no = no,
        grade = grade,
        word = word,
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
