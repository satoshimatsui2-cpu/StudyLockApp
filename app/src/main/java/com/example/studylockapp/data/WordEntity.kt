package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val no: Int,
    val grade: String,
    val word: String,
    val japanese: String,
    val pos: String?,          // 品詞（不明なら null 許可）
    val category: String       // "word", "熟語", "sentence" 等
)

