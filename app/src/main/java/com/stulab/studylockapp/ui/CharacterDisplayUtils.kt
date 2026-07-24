package com.stulab.studylockapp.ui

import android.content.Context
import com.stulab.studylockapp.R

/**
 * キャラクターの画像リソースを管理するユーティリティ。
 */
object CharacterDisplayUtils {

    fun getJoyDrawable(context: Context, charId: String): Int {
        return getEmotionDrawable(context, charId, "joy")
    }

    fun getPleasureDrawable(context: Context, charId: String): Int {
        return getEmotionDrawable(context, charId, "pleasure")
    }

    fun getPanicDrawable(context: Context, charId: String): Int {
        return getEmotionDrawable(context, charId, "panic")
    }

    /**
     * 指定された感情の画像を取得する。
     * 1. mini_${charId}_${emotion} を探す
     * 2. なければ char_${charId} を探す
     * 3. なければ mini_george_${emotion} を返す
     */
    private fun getEmotionDrawable(context: Context, charId: String, emotion: String): Int {
        val packageName = context.packageName
        val resources = context.resources

        // 1. mini_name_emotion (例: mini_shion_joy)
        var resId = resources.getIdentifier("mini_${charId}_$emotion", "drawable", packageName)

        // 2. 基本の立ち絵 (例: char_ren)
        if (resId == 0) {
            resId = resources.getIdentifier("char_$charId", "drawable", packageName)
        }

        // 3. 最終フォールバック (ジョージのミニ画像)
        if (resId == 0) {
            resId = resources.getIdentifier("mini_george_$emotion", "drawable", packageName)
        }

        // 万が一すべて見つからない場合
        return if (resId != 0) resId else R.drawable.mini_george_joy
    }
}
