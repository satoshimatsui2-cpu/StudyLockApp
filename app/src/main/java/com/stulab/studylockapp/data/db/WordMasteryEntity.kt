package com.stulab.studylockapp.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.learning.QuizMode

@Entity(
    tableName = "word_mastery",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["no"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wordId"], unique = true)]
)
data class WordMasteryEntity(
    @PrimaryKey val wordId: Int,
    var level: Int = 0,
    var scheduledMode: String = QuizMode.EN_TO_JP.name,
    var nextReviewTime: Long = 0,
    var challengeCount: Int = 0,
    var successCount: Int = 0,
    var failureCount: Int = 0,
    
    // モード別試行回数 (Migration SQL に合わせて追加)
    var enToJpAttempts: Int = 0,
    var enToJpCorrects: Int = 0,
    var jpToEnAttempts: Int = 0,
    var jpToEnCorrects: Int = 0,
    var listenAttempts: Int = 0,
    var listenCorrects: Int = 0,

    var currentStreak: Int = 0,
    var bestStreak: Int = 0,
    var isBasicMastered: Boolean = false,
    var isLongTermMastered: Boolean = false,
    var pendingListenReview: Boolean = false,
    var deferredListenCount: Int = 0,
    var lastCorrectTime: Long = 0,
    var lastSeen: Long = 0
)
