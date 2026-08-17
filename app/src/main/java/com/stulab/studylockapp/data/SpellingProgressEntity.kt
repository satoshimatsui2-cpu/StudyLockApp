package com.stulab.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SpellingStatus {
    NOT_STARTED,
    PRACTICING,
    CLEARED
}

@Entity(tableName = "spelling_progress")
data class SpellingProgressEntity(
    @PrimaryKey val wordId: Long,
    val status: SpellingStatus,
    val unlockedAt: Long,
    val eligibleAt: Long,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val lastResultCorrect: Boolean? = null,
    val hintUsed: Boolean = false,
    val lastAttemptAt: Long? = null,
    val lastPromptedAt: Long? = null,
    val clearedAt: Long? = null
)
