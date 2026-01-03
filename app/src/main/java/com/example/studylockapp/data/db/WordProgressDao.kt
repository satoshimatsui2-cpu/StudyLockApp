package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.studylockapp.data.WordProgressEntity

@Dao
interface WordProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WordProgressEntity)

    @Update
    suspend fun update(progress: WordProgressEntity)

    @Query("SELECT * FROM word_progress")
    suspend fun getAll(): List<WordProgressEntity>

    @Query("SELECT * FROM word_progress WHERE wordId = :wordId AND mode = :mode LIMIT 1")
    suspend fun getProgress(wordId: Int, mode: String): WordProgressEntity?

    /**
     * ★期限到来（nextDueAtSec <= nowSec）の wordId を古い順で返す
     * 出題優先順位 1 に使う
     */
    @Query(
        """
        SELECT wordId FROM word_progress
        WHERE mode = :mode
          AND nextDueAtSec <= :nowSec
        ORDER BY nextDueAtSec ASC
        """
    )
    suspend fun getDueWordIdsOrdered(mode: String, nowSec: Long): List<Int>

    @Query("SELECT DISTINCT wordId FROM word_progress WHERE mode = :mode")
    suspend fun getProgressIds(mode: String): List<Int>

    @Query("SELECT COUNT(DISTINCT wordId) FROM word_progress WHERE lastAnsweredAt BETWEEN :startTime AND :endTime")
    suspend fun getLearnedWordCount(startTime: Long, endTime: Long): Int
}