package com.stulab.studylockapp.ui

data class ProgressMetricUiModel(
    val label: String,
    val iconResId: Int,
    val completedCount: Int,
    val eligibleCount: Int
) {
    val percentage: Int
        get() = if (eligibleCount > 0) {
            (completedCount * 100 / eligibleCount).coerceIn(0, 100)
        } else 0
    
    val displayText: String
        get() = "$completedCount（$percentage%）"
}

data class TopProgressUiModel(
    val shortTerm: ProgressMetricUiModel,
    val longTerm: ProgressMetricUiModel,
    val spelling: ProgressMetricUiModel,
    val wordPronunciation: ProgressMetricUiModel,
    val sentencePronunciation: ProgressMetricUiModel
)
