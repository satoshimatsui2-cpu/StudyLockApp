package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.studylockapp.data.UnlockHistoryEntity

@Dao
interface UnlockHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: UnlockHistoryEntity)

    @Update
    suspend fun update(history: UnlockHistoryEntity)

    @Query("SELECT * FROM unlock_history ORDER BY unlockedAt DESC LIMIT 100")
    suspend fun getLatest100(): List<UnlockHistoryEntity>
}
