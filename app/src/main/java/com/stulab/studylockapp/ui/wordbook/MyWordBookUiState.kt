package com.stulab.studylockapp.ui.wordbook

data class MyWordBookUiState(
    val slots: List<WordBookSlot> = emptyList(),
    val isLoading: Boolean = false,
    val processingGrade: Int? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val validationErrors: List<String> = emptyList()
)

data class WordBookSlot(
    val grade: Int,
    val displayName: String,
    val customName: String?,
    val wordCount: Int,
    val isRegistered: Boolean
)
