package com.stulab.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "words")
@TypeConverters(WordConverters::class)
data class WordEntity(
    @PrimaryKey val no: Int,
    val grade: Int,
    val word: String,
    val japanese: String,
    val description: String,
    val sentence: String,
    val japaneseSentence: String,
    val pos: String,
    val difficulty: Int,
    val frequency: Int,
    val choicesEnJa: List<String>,
    val choicesJaEn: List<String>,
    val choicesListening: List<String>,
    val synonyms: List<RelatedWord>,
    val antonyms: List<RelatedWord>
)
