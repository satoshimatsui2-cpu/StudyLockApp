package com.example.studylockapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    val no: Int,
    val grade: String,
    @PrimaryKey val word: String,
    val japanese: String,
    val description: String?,
    val smallTopicId: String?,
    val mediumCategoryId: String?
)