package com.stulab.studylockapp.data

import androidx.room.*

@Dao
interface SpellingProgressDao {
    @Query("SELECT * FROM spelling_progress WHERE wordId = :wordId")
    suspend fun getProgress(wordId: Long): SpellingProgressEntity?

    @Query("SELECT * FROM spelling_progress WHERE wordId IN (:wordIds)")
    suspend fun getProgressByIds(wordIds: List<Long>): List<SpellingProgressEntity>

    @Query("SELECT * FROM spelling_progress WHERE status != 'CLEARED' AND eligibleAt <= :now")
    suspend fun getEligibleProgresses(now: Long): List<SpellingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: SpellingProgressEntity)

    @Query("UPDATE spelling_progress SET lastPromptedAt = :now WHERE wordId IN (:wordIds)")
    suspend fun updateLastPromptedAt(wordIds: List<Long>, now: Long)
}
