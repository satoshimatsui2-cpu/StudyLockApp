package com.stulab.studylockapp.data

import android.content.Context
import android.util.Log
import com.stulab.studylockapp.data.db.WordDao
import com.stulab.studylockapp.data.db.WordMasteryDao
import com.stulab.studylockapp.learning.SpellingEligibilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChallengeRepository(private val context: Context, private val db: AppDatabase) {
    private val wordDao = db.wordDao()
    private val masteryDao = db.wordMasteryDao()
    private val voiceDao = db.voiceCheckDao()
    private val spellingDao = db.spellingProgressDao()

    suspend fun getPronunciationWords(grade: Int): List<WordEntity> = withContext(Dispatchers.IO) {
        val appSettings = AppSettings(context)
        val deferredIds = appSettings.deferredPronunciationIds

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
        
        // 2. deferredIds に含まれないものを優先
        val preferredCandidates = notOkStudied.filter { it.no.toLong() !in deferredIds }
        
        val withSentence = preferredCandidates.filter { it.sentence.isNotBlank() }
        val withoutSentence = preferredCandidates.filter { it.sentence.isBlank() }

        var pool = (withSentence.shuffled() + withoutSentence.shuffled()).distinctBy { it.no }
        
        // 候補が3語未満の場合、deferredIds の単語を不足分として使用する
        if (pool.size < 3) {
            val deferredPool = notOkStudied.filter { it.no.toLong() in deferredIds }.shuffled()
            pool = (pool + deferredPool).distinctBy { it.no }
        }

        val result = if (pool.size >= 3) {
            pool.take(3)
        } else {
            // それでも3語に届かない場合は、PASSED済みの学習済み単語も混ぜる
            val passedStudied = (studiedWords - notOkStudied.toSet()).shuffled()
            (pool + passedStudied).distinctBy { it.no }.take(3)
        }

        // 最終的な結果が3件（かつ重複なし）であることを保証する
        if (result.size < 3 || result.distinctBy { it.no }.size != 3) {
            Log.w("ChallengeRepo", "Failed to select 3 distinct words for pronunciation. count=${result.size}")
            return@withContext emptyList()
        }

        // 新しいセッションを正常に作成した後、deferredPronunciationIdsをクリアする
        if (result.isNotEmpty()) {
            appSettings.deferredPronunciationIds = emptySet()
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
