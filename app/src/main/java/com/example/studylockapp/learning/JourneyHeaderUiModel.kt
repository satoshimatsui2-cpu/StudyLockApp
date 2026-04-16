package com.example.studylockapp.learning

import com.example.studylockapp.GradeLabelFormatter

/**
 * 上部ヘッダー（Journey部品）の表示用モデル
 */
data class JourneyHeaderUiModel(
    val basicCountText: String,
    val longTermCountText: String,
    val sessionPointsText: String,
    val goalText: String,
    val currentLevel: Int,
    val currentWordId: Int // アニメーション制御用のキーとして使用
)

object JourneyHeaderMapper {
    fun map(state: LearningUiState): JourneyHeaderUiModel {
        return JourneyHeaderUiModel(
            basicCountText = state.basicMasterCount.toString(),
            longTermCountText = state.longTermMasterCount.toString(),
            sessionPointsText = "${state.sessionPoints}PT",
            goalText = "目標: ${GradeLabelFormatter.format(state.targetLevel)}",
            currentLevel = state.currentLevel,
            currentWordId = state.quiz?.word?.no ?: -1
        )
    }
}
