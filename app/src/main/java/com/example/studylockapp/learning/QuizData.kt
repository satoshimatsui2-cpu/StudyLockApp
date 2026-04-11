package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity
import java.util.UUID

data class QuizData(
    val id: String = UUID.randomUUID().toString(),
    val mode: QuizMode,
    val word: WordEntity,
    val question: String,
    val choices: List<String>,
    val answer: String,
    val extra: Any? = null
)
