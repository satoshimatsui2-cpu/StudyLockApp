package com.example.studylockapp.data.db

import androidx.room.*

@Dao
interface WordMasteryDao {
    @Query("SELECT * FROM word_mastery WHERE wordId = :wordId")
    suspend fun getMastery(wordId: Int): WordMasteryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(mastery: WordMasteryEntity)

    @Query("SELECT * FROM word_mastery WHERE nextReviewTime > 0 AND nextReviewTime <= :now")
    suspend fun getDueMasteries(now: Long): List<WordMasteryEntity>

    @Query("SELECT COUNT(*) FROM word_mastery WHERE isBasicMastered = 1")
    suspend fun countBasicMastered(): Int

    @Query("SELECT COUNT(*) FROM word_mastery WHERE isLongTermMastered = 1")
    suspend fun countLongTermMastered(): Int

    @Query("SELECT * FROM word_mastery WHERE pendingListenReview = 1")
    suspend fun getPendingListenMasteries(): List<WordMasteryEntity>
    
    @Query("SELECT * FROM word_mastery")
    suspend fun getAllMasteries(): List<WordMasteryEntity>
}
