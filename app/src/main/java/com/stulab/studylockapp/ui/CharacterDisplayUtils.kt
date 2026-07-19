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
            "ren" -> R.drawable.char_ren
            "niko" -> R.drawable.char_niko
            "tetra" -> R.drawable.char_tetra
            "noa" -> R.drawable.char_noa
            "leo" -> R.drawable.char_leo
            "allen" -> R.drawable.char_allen
            "hina" -> R.drawable.char_hina
            "elena" -> R.drawable.char_elena
            else -> R.drawable.mini_george_joy
        }
    }
}
