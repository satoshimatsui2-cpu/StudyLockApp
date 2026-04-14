package com.example.studylockapp.learning

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.studylockapp.databinding.LayoutModePillBinding

/**
 * モード切替ピルの表示（バインド）を担当するクラス
 * 入力範囲を LayoutModePillBinding に限定
 */
object ModePillBinder {
    fun bind(binding: LayoutModePillBinding, model: ModePillUiModel) {
        val context = binding.root.context
        val accentColor = ContextCompat.getColor(context, model.accentColorRes)
        val bgColor = ContextCompat.getColor(context, model.backgroundColorRes)
        
        binding.rootModePill.setCardBackgroundColor(bgColor)
        binding.imageModeIcon.setImageResource(model.iconRes)
        binding.imageModeIcon.imageTintList = ColorStateList.valueOf(accentColor)
        
        binding.textModeTitle.text = context.getString(model.titleRes)
        binding.textModeSubtitle.text = context.getString(model.subtitleRes)
        binding.textModeSubtitle.setTextColor(accentColor)
    }
}
