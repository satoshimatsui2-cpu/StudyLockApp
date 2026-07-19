package com.stulab.studylockapp.learning

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.SilentMode

/**
 * モード切替ピルの表示用モデル
 */
data class ModePillUiModel(
    val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val backgroundColorRes: Int,
    @ColorRes val accentColorRes: Int
)

object ModePillMapper {
    fun map(silentMode: SilentMode): ModePillUiModel {
        return when (silentMode) {
            SilentMode.OFF -> ModePillUiModel(
                titleRes = R.string.mode_balance_title,
                iconRes = R.drawable.ic_headphones_24,
                backgroundColorRes = R.color.navy_primary,
                accentColorRes = R.color.mustard_accent
            )
            SilentMode.ON -> ModePillUiModel(
                titleRes = R.string.mode_silent_title,
                iconRes = R.drawable.outline_volume_off_24,
                backgroundColorRes = R.color.text_sub,
                accentColorRes = R.color.navy_soft
            )
        }
    }
}
