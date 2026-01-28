
package com.example.studylockapp.data

data class FillBlankQuestion(
    val id: Int,
    val grade: String,
    val unit: Int,
    val question: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String
)
