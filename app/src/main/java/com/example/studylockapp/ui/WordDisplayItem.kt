package com.example.studylockapp.ui

data class WordDisplayItem(
    val no: Int,
    val word: String,
    val japanese: String,
    val grade: String,
    val pos: String,
    val category: String,
    val mLevel: Int?,
    val mDue: Long?,
    val lLevel: Int?,
    val lDue: Long?,
    val mDueText: String,
    val lDueText: String
)
