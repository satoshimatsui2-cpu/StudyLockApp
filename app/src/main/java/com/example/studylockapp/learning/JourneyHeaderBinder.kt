package com.example.studylockapp.learning

import android.view.View
import com.example.studylockapp.databinding.LayoutJourneyHeaderBinding

/**
 * 上部ヘッダー（Journey部品）の描画を担当するクラス
 */
object JourneyHeaderBinder {
    fun bind(binding: LayoutJourneyHeaderBinding, model: JourneyHeaderUiModel) {
        // 1. 左側のメタチップ列 (15sp化はレイアウト側で定義)
        binding.textCountBasic.text = model.basicCountText
        binding.textCountLongterm.text = model.longTermCountText
        
        // 2. PT表示 (15sp / SESSION: なし)
        binding.textSessionPointsSummary.text = model.sessionPointsText
        
        // 3. 5ノード進捗レール (LVチップ 13sp化はカスタムView内部で定義)
        binding.masteryProgressRail.setProgress(model.currentLevel, animate = true)
        
        // 4. 補助情報
        binding.textTargetLevel.text = model.goalText
    }
}
