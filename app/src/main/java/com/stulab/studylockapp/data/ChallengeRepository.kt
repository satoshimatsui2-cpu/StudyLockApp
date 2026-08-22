package com.stulab.studylockapp.data

import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryDao
import com.stulab.studylockapp.learning.SpellingEligibilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChallengeRepository(private val db: AppDatabase) {
    private val wordDao = db.wordDao()
    private val masteryDao = db.wordMasteryDao()
    private val voiceDao = db.voiceCheckDao()
    private val spellingDao = db.spellingProgressDao()

    suspend fun getPronunciationWords(grade: Int): List<WordEntity> = withContext(Dispatchers.IO) {
        val allWords = wordDao.getWordsByGrade(grade)
        if (allWords.isEmpty()) return@withContext emptyList()

        val wordIds = allWords.map { it.no }
        val masteries = masteryDao.getAllMasteries().filter { it.wordId in wordIds }
        val studiedWords = allWords.filter { word -> 
            masteries.any { it.wordId == word.no && it.challengeCount > 0 } 
        }

        if (studiedWords.isEmpty()) return@withContext emptyList()

        val voiceResults = voiceDao.getAllResultsByIds(wordIds.map { it.toLong() })
        val okWordIds = voiceResults.filter { it.checkType == "word" && it.checked }.map { it.wordId.toInt() }.toSet()

        // 1. 学習済み かつ 単語発音がまだ PASSED ではない
        val notOkStudied = studiedWords.filter { it.no !in okWordIds }
        
        // 2. 例文を持つ単語を優先する
        val withSentence = notOkStudied.filter { it.sentence.isNotBlank() }
        val withoutSentence = notOkStudied.filter { it.sentence.isBlank() }

        val pool = withSentence.shuffled() + withoutSentence.shuffled()
        
        val result = if (pool.size >= 3) {
            pool.take(3)
        } else {
            // 3語に届かない場合は、PASSED済みの学習済み単語も混ぜる
            val passedStudied = (studiedWords - notOkStudied.toSet()).shuffled()
            (pool + passedStudied).take(3)
        }

        result
    }

    suspend fun getSpellingWords(grade: Int): List<WordEntity> = withContext(Dispatchers.IO) {
        val allWords = wordDao.getWordsByGrade(grade)
        if (allWords.isEmpty()) return@withContext emptyList()

        val wordIds = allWords.map { it.no }
        val masteries = masteryDao.getAllMasteries().filter { it.wordId in wordIds }
        val studiedWordIds = masteries.filter { it.challengeCount > 0 }.map { it.wordId }.toSet()

        val spellingProgresses = spellingDao.getProgressByIds(wordIds.map { it.toLong() })
        val clearedWordIds = spellingProgresses.filter { it.status == SpellingStatus.CLEARED }.map { it.wordId.toInt() }.toSet()

        val candidates = allWords.filter { it.no in studiedWordIds && SpellingEligibilityChecker.isEligible(it) }
        val notClearedCandidates = candidates.filter { it.no !in clearedWordIds }

        val result = if (notClearedCandidates.size >= 5) {
            notClearedCandidates.shuffled().take(5)
        } else {
            (notClearedCandidates + (candidates - notClearedCandidates.toSet()).shuffled()).take(5)
        }
        result
    }
}
