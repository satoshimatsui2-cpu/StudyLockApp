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

    suspend fun recordResult(wordId: Long, isCorrect: Boolean, hintUsed: Boolean) {
        val existing = spellingDao.getProgress(wordId) ?: return
        val now = System.currentTimeMillis()

        // Requirements:
        // - Hit without hints on first try (not really first try ever, but "without hints")
        // - "If you answer perfectly without hints, clear it."
        // - "After hint use, it's PRACTICING."
        // - "After wrong answer, it's PRACTICING until correct without hints in later session."
        
        val finalStatus = if (isCorrect && !hintUsed) {
            SpellingStatus.CLEARED
        } else {
            if (existing.status == SpellingStatus.CLEARED) SpellingStatus.CLEARED else SpellingStatus.PRACTICING
        }

        val updated = existing.copy(
            status = finalStatus,
            attemptCount = existing.attemptCount + 1,
            correctCount = if (isCorrect) existing.correctCount + 1 else existing.correctCount,
            lastResultCorrect = isCorrect,
            hintUsed = hintUsed,
            lastAttemptAt = now,
            clearedAt = if (finalStatus == SpellingStatus.CLEARED && existing.status != SpellingStatus.CLEARED) now else existing.clearedAt
        )
        spellingDao.insertOrUpdate(updated)
    }

    suspend fun updateLastPromptedAt(wordIds: List<Long>, now: Long) {
        spellingDao.updateLastPromptedAt(wordIds, now)
    }
}
