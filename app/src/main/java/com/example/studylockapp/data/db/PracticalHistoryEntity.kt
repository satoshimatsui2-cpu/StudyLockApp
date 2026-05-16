package com.example.studylockapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 実践テストの解答履歴を保存するエンティティ
 */
@Entity(tableName = "practical_history")
data class PracticalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionNo: String,
    val questionType: String,
    val grade: Int,
    val unit: String,
    val questionText: String,
    val choicesJson: String,
    val correctAnswer: String,
    val selectedAnswer: String,
    val isCorrect: Boolean,
    val points: Int,
    val explanation: String,
    val answeredAt: Long,
    val sessionId: String
)
