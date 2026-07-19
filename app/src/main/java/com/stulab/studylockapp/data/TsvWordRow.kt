package com.stulab.studylockapp.data

/**
 * TSVの一行分の生データを保持するDTO
 */
data class TsvWordRow(
    val no: String,
    val grade: String,
    val word: String,
    val japanese: String,
    val description: String,
    val sentence: String,
    val japaneseSentence: String,
    val pos: String,
    val difficulty: String,
    val frequency: String,
    val choicesEnJa: String,
    val choicesJaEn: String,
    val choicesListening: String,
    val synonyms: String,
    val antonyms: String
)
