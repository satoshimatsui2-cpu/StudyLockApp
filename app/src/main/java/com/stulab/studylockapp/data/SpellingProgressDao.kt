package com.stulab.studylockapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpellingProgressDao {
    @Query("SELECT * FROM spelling_progress WHERE wordId = :wordId")
    suspend fun getProgress(wordId: Long): SpellingProgressEntity?

    @Query("SELECT * FROM spelling_progress WHERE wordId IN (:wordIds)")
    suspend fun getProgressByIds(wordIds: List<Long>): List<SpellingProgressEntity>

    @Query("SELECT COUNT(DISTINCT wordId) FROM spelling_progress WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade) AND status = 'CLEARED'")
    fun countPassedSpellingWordsFlow(grade: Int): Flow<Int>

    @Query("SELECT COUNT(DISTINCT `no`) FROM words WHERE grade = :grade AND `no` IN (SELECT wordId FROM word_mastery WHERE challengeCount > 0) AND `no` NOT IN (SELECT wordId FROM spelling_progress WHERE status = 'CLEARED')")
    fun countEligibleSpellingWordsFlow(grade: Int): Flow<Int>

    @Query("SELECT * FROM spelling_progress WHERE status != 'CLEARED' AND eligibleAt <= :now")
    suspend fun getEligibleProgresses(now: Long): List<SpellingProgressEntity>

    @Query("SELECT * FROM spelling_progress WHERE wordId IN (SELECT `no` FROM words WHERE grade = :grade) AND status != 'CLEARED'")
    suspend fun getEligibleProgressesByGrade(grade: Int): List<SpellingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: SpellingProgressEntity)

    @Query("UPDATE spelling_progress SET lastPromptedAt = :now WHERE wordId IN (:wordIds)")
    suspend fun updateLastPromptedAt(wordIds: List<Long>, now: Long)
}
