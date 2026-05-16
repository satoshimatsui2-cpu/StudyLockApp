package com.example.studylockapp.data.practical

/**
 * 実践テストの問題ドメインモデル。
 */
data class PracticalQuestion(
    val no: String,
    val grade: Int,
    val unit: String,
    val question: String,
    val choices: List<String>,
    val correctOptionIndex: Int, // 1-based (TSV準拠)
    val explanation: String,
    val type: PracticalQuizMode
)
