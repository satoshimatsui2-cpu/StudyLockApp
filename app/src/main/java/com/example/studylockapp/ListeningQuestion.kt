package com.example.studylockapp

data class ListeningQuestion(
    val id: Int,
    val grade: String,
    val script: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int, // 0-based
    val explanation: String
)