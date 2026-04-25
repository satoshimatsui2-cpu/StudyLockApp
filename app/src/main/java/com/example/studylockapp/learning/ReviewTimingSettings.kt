package com.example.studylockapp.learning

/**
 * 当日再出題のタイミング設定をまとめたデータクラス
 */
data class ReviewTimingSettings(
    val correctSameDayDelayMillis: Long,
    val wrongSameDayDelayMillis: Long,
    val unknownSameDayDelayMillis: Long
)
