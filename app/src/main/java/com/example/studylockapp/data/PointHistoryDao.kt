package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.studylockapp.data.PointHistoryEntity

@Dao
interface PointHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PointHistoryEntity)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM point_history WHERE dateEpochDay = :date")
    suspend fun getSumByDate(date: Long): Int

    @Query("""
    SELECT COALESCE(SUM(delta), 0)
    FROM point_history
    WHERE mode = :mode AND dateEpochDay = :day
    """)
    suspend fun sumDeltaByModeAndDay(mode: String, day: Long): Int
}