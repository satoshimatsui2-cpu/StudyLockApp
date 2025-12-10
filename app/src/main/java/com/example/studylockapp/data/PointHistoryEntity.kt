package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "point_history")
data class PointHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,          // "meaning" or "listening"
    val dateEpochDay: Long,    // LocalDate.now().toEpochDay()
    val delta: Int             // 獲得ポイント
)

