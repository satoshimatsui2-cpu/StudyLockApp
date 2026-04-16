package com.example.studylockapp.learning

import androidx.compose.ui.platform.ComposeView
import com.example.studylockapp.databinding.LayoutJourneyHeaderBinding
import com.example.studylockapp.ui.components.MasteryProgressGauge

/**
 * 上部ヘッダー（Journey部品）の描画を担当するクラス
 */
object JourneyHeaderBinder {
    fun bind(binding: LayoutJourneyHeaderBinding, model: JourneyHeaderUiModel) {
        // 1. 左側のメタチップ列
        binding.textCountBasic.text = model.basicCountText
        binding.textCountLongterm.text = model.longTermCountText
        
        // 2. PT表示
        binding.textSessionPointsSummary.text = model.sessionPointsText
        
        // 3. 5ノード進捗レール (Compose版究極挙動)
        binding.composeMasteryGauge.setContent {
            MasteryProgressGauge(
                absoluteLevel = model.currentLevel,
                itemKey = model.currentWordId // 単語IDを渡して切替を検知
            )
        }
        
        // 4. 補助情報
        binding.textTargetLevel.text = model.goalText
    }
}
