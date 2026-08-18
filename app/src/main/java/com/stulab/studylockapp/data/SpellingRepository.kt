package com.stulab.studylockapp.data

import com.stulab.studylockapp.learning.SpellingEligibilityChecker

class SpellingRepository(private val db: AppDatabase) {
    private val spellingDao = db.spellingProgressDao()
    private val wordDao = db.wordDao()

    suspend fun getProgress(wordId: Long) = spellingDao.getProgress(wordId)

    suspend fun getProgressByIds(wordIds: List<Long>) = spellingDao.getProgressByIds(wordIds)

    suspend fun unlockIfNeeded(wordId: Int) {
        val word = wordDao.getWordById(wordId) ?: return
        if (!SpellingEligibilityChecker.isEligible(word)) return

        val existing = spellingDao.getProgress(wordId.toLong())
        if (existing == null) {
            val now = System.currentTimeMillis()
            val newProgress = SpellingProgressEntity(
                wordId = wordId.toLong(),
                status = SpellingStatus.NOT_STARTED,
                unlockedAt = now,
                eligibleAt = now + 3600000 // 1 hour later
            )
            spellingDao.insertOrUpdate(newProgress)
        }
    }

    suspend fun getEligibleWordsForPrompt(now: Long): List<WordEntity> {
        val eligibleProgresses = spellingDao.getEligibleProgresses(now)
        if (eligibleProgresses.isEmpty()) return emptyList()

        val wordIds = eligibleProgresses.map { it.wordId.toInt() }
        return wordDao.getWordsByIds(wordIds)
    }

    /**
     * Get words that:
     * 1. Belong to the grade.
     * 2. Have been studied (challengeCount > 0).
     * 3. Are eligible for spelling check.
     */
    suspend fun getTotalStudyCountByGrade(grade: Int): Int {
        val allWords = wordDao.getWordsByGrade(grade)
        if (allWords.isEmpty()) return 0
        
        val wordIds = allWords.map { it.no }
        val masteries = db.wordMasteryDao().getAllMasteries().filter { it.wordId in wordIds }
        val studiedWordIds = masteries.filter { it.challengeCount > 0 }.map { it.wordId }.toSet()
        
        return allWords.count { word ->
            word.no in studiedWordIds && SpellingEligibilityChecker.isEligible(word)
        }
    }

    suspend fun getEligibleWordsByGrade(grade: Int): List<WordEntity> {
        val allWords = wordDao.getWordsByGrade(grade)
        if (allWords.isEmpty()) return emptyList()
        
        val wordIds = allWords.map { it.no }
        val masteries = db.wordMasteryDao().getAllMasteries().filter { it.wordId in wordIds }
        val studiedWordIds = masteries.filter { it.challengeCount > 0 }.map { it.wordId }.toSet()
        
        val spellingProgresses = spellingDao.getProgressByIds(wordIds.map { it.toLong() })
        val clearedWordIds = spellingProgresses.filter { it.status == SpellingStatus.CLEARED }.map { it.wordId.toInt() }.toSet()
        
        return allWords.filter { word ->
            word.no in studiedWordIds && 
            SpellingEligibilityChecker.isEligible(word) && 
            word.no !in clearedWordIds
        }
    }

    suspend fun recordResult(wordId: Long, isCorrect: Boolean, hintUsed: Boolean) {
        val existing = spellingDao.getProgress(wordId)
        val now = System.currentTimeMillis()

        if (existing == null) {
            // Create a new record if it doesn't exist
            // Hint used -> No CLEARED status (it's for the first "pure" correct answer)
            val finalStatus = if (isCorrect && !hintUsed) SpellingStatus.CLEARED else SpellingStatus.PRACTICING
            val newProgress = SpellingProgressEntity(
                wordId = wordId,
                status = finalStatus,
                unlockedAt = now,
                eligibleAt = now,
                attemptCount = 1,
                correctCount = if (isCorrect && !hintUsed) 1 else 0,
                lastResultCorrect = isCorrect,
                hintUsed = hintUsed,
                lastAttemptAt = now,
                clearedAt = if (finalStatus == SpellingStatus.CLEARED) now else null
            )
            spellingDao.insertOrUpdate(newProgress)
            return
        }

        // Requirements:
        // - Correct answer WITHOUT HINT = CLEARED
        // - Once CLEARED, stay CLEARED (never downgrade)
        
        val wasAlreadyCleared = existing.status == SpellingStatus.CLEARED
        val finalStatus = if (wasAlreadyCleared || (isCorrect && !hintUsed)) {
            SpellingStatus.CLEARED
        } else {
            SpellingStatus.PRACTICING
        }

        val updated = existing.copy(
            status = finalStatus,
            attemptCount = existing.attemptCount + 1,
            // Only increment correctCount if no hint was used (as per user request: "hint used -> don't add to correctCount")
            correctCount = if (isCorrect && !hintUsed) existing.correctCount + 1 else existing.correctCount,
            lastResultCorrect = isCorrect,
            hintUsed = hintUsed,
            lastAttemptAt = now,
            clearedAt = if (finalStatus == SpellingStatus.CLEARED && !wasAlreadyCleared) now else existing.clearedAt
        )
        spellingDao.insertOrUpdate(updated)
    }

    suspend fun updateLastPromptedAt(wordIds: List<Long>, now: Long) {
        spellingDao.updateLastPromptedAt(wordIds, now)
    }
}
