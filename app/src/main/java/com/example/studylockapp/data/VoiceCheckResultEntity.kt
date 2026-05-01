package com.example.studylockapp.data

import androidx.room.Entity

@Entity(tableName = "voice_check_results", primaryKeys = ["wordId", "checkType"])
data class VoiceCheckResultEntity(
    val wordId: Long,
    val checkType: String = "word",
    val checked: Boolean = false,
    val successCount: Int = 0,
    val attemptCount: Int = 0,
    val bestConfidence: Float = 0f,
    val lastCheckedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
