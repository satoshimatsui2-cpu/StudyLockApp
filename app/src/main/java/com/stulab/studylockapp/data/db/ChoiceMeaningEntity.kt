package com.stulab.studylockapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "choice_meanings")
data class ChoiceMeaningEntity(
    @PrimaryKey
    val lookupKey: String,
    val normalizedText: String,
    val displayText: String,
    val japanese: String,
    val quizMode: String? = null,
    val sourceWordId: Long? = null,
    val note: String? = null
)
