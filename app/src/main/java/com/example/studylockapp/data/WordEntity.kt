package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    val no: Int,
    val grade: String,
    @PrimaryKey val word: String, // PK を word に変更
    val japanese: String,
    val english: String?,
    val pos: String?,
    val category: String?,
    val actors: String?
)