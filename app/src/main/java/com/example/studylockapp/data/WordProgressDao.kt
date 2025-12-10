package com.example.studylockapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WordProgressEntity)

    @Query("SELECT * FROM word_progress WHERE wordId = :wordId AND mode = :mode LIMIT 1")
    suspend fun getProgress(wordId: Int, mode: String): WordProgressEntity?

    @Query("""
        SELECT wordId FROM word_progress 
        WHERE mode = :mode AND nextDueDate <= :today
    """)
    suspend fun getDueWordIds(mode: String, today: Long): List<Int>

    @Query("SELECT DISTINCT wordId FROM word_progress WHERE mode = :mode")
    suspend fun getProgressIds(mode: String): List<Int>
}