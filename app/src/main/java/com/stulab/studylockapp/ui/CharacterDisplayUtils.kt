package com.stulab.studylockapp.ui

import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.notification.StudyCharacter

/**
 * キャラクターの画像リソースを管理するユーティリティ。
 */
object CharacterDisplayUtils {
    
    fun getJoyDrawable(charId: String): Int {
        return when (charId) {
            "george" -> R.drawable.mini_george_joy
            "shion" -> R.drawable.mini_shion_joy
            // 他のキャラも mini_..._joy があれば追加
            else -> {
                // 汎用的に取得を試みる
                0 // 後続の処理でフォールバック
            }
        }.let { if (it == 0) R.drawable.mini_george_joy else it }
    }

    fun getPleasureDrawable(charId: String): Int {
        return when (charId) {
            "george" -> R.drawable.mini_george_pleasure
            "shion" -> R.drawable.mini_shion_pleasure
            else -> R.drawable.mini_george_pleasure
        }
    }

    fun getPanicDrawable(charId: String): Int {
        return when (charId) {
            "george" -> R.drawable.mini_george_panic
            "shion" -> R.drawable.mini_shion_panic
            // 他のキャラは必要に応じて mini_..._panic を追加
            else -> R.drawable.mini_george_panic
        }
    }
}
