package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val no: Int,
    val grade: String,
    val word: String,
    val japanese: String,
    val english: String,    // ★追加
    val pos: String,
    val category: String,
    val actors: String      // ★追加（例: A / -）
)

