package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlock_history")
data class UnlockHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usedPoints: Int,
    val unlockDurationSec: Long,
    val unlockedAt: Long, // Epoch seconds
    var cancelled: Boolean = false
)
