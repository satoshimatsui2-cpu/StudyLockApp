package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppUnlockDao {

    @Query("SELECT * FROM app_unlocks WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): AppUnlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppUnlockEntity)

    @Query("DELETE FROM app_unlocks WHERE packageName = :packageName")
    suspend fun clear(packageName: String)

    @Query("DELETE FROM app_unlocks WHERE unlockedUntilSec <= :nowSec")
    suspend fun clearExpired(nowSec: Long)
}

