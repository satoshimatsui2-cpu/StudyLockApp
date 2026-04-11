package com.example.studylockapp.data

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
    val description: String?,
    val sentence: String?,
    val japaneseSentence: String?,
    val phonetic: String?,
    val type: String = "word",
    val pos: String?,
    val difficulty: Int = 1,
    val frequency: Int = 1,
    val related: List<String>,
    val confusion: List<String>
)
