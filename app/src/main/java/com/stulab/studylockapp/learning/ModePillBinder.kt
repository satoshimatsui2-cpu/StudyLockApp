package com.stulab.studylockapp.learning

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.stulab.studylockapp.databinding.LayoutModePillBinding
import com.stulab.studylockapp.R

/**
 * モード切替ピルの表示（バインド）を担当するクラス
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
        
        // Chevronの色もアクセントに合わせる
        binding.imageModeChevron.imageTintList = ColorStateList.valueOf(accentColor)
    }
}
