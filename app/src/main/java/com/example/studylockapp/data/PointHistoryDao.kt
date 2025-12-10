package com.example.studylockapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PointHistoryDao {
    @Insert
    suspend fun insert(history: PointHistoryEntity)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM point_history WHERE dateEpochDay = :date")
    suspend fun getSumByDate(date: Long): Int
}

