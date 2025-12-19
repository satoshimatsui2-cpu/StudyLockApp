package com.example.studylockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LockedAppDao {

    @Query("SELECT * FROM locked_apps ORDER BY label ASC")
    suspend fun getAll(): List<LockedAppEntity>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): LockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LockedAppEntity)

    @Update
    suspend fun update(entity: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM locked_apps WHERE isLocked = 1")
    suspend fun countLocked(): Int

    @Query("UPDATE locked_apps SET isLocked = 0")
    suspend fun disableAllLocks()
}

