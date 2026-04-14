package com.example.studylockapp.learning

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.studylockapp.R
import com.example.studylockapp.data.SilentMode

/**
 * モード切替ピルの表示用モデル
 */
data class ModePillUiModel(
    val titleRes: Int,
    val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val backgroundColorRes: Int,
    @ColorRes val accentColorRes: Int
)

object ModePillMapper {
    fun map(silentMode: SilentMode): ModePillUiModel {
        return when (silentMode) {
            SilentMode.OFF -> ModePillUiModel(
                titleRes = R.string.mode_balance_title,
                subtitleRes = R.string.mode_balance_subtitle,
                iconRes = R.drawable.ic_hearing_24,
                backgroundColorRes = R.color.navy_primary,
                accentColorRes = R.color.mustard_accent
            )
            SilentMode.ON -> ModePillUiModel(
                titleRes = R.string.mode_silent_title,
                subtitleRes = R.string.mode_silent_subtitle,
                iconRes = R.drawable.outline_volume_off_24,
                backgroundColorRes = R.color.text_sub,
                accentColorRes = R.color.navy_soft
            )
        }
    }
}
