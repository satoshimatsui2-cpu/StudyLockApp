package com.example.studylockapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一時解放状態。unlockedUntilSec を過ぎたらロック扱い。
 * epoch seconds で保存（Due仕様と揃える）
 */
@Entity(tableName = "app_unlocks")
data class AppUnlockEntity(
    @PrimaryKey val packageName: String,
    val unlockedUntilSec: Long
)

