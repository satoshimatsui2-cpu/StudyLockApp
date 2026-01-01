package com.example.studylockapp.ui

data class WordDisplayItem(
    val no: Int,
    val word: String,
    val grade: String,

    // English -> Japanese
    val mLevel: Int?,
    val mDue: Long?,
    val mDueText: String,

    // Listening
    val lLevel: Int?,
    val lDue: Long?,
    val lDueText: String,

    // Japanese -> English
    val jeLevel: Int?,
    val jeDue: Long?,
    val jeDueText: String,

    // English -> English (Word -> Meaning)
    val ee1Level: Int?,
    val ee1Due: Long?,
    val ee1DueText: String,

    // English -> English (Meaning -> Word)
    val ee2Level: Int?,
    val ee2Due: Long?,
    val ee2DueText: String
)
