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
     * 通知用のアイコン画像を取得する (icon_キャラクター名_感情)
     */
    fun getNotificationIconDrawable(context: Context, charId: String, emotionId: String): Int {
        val packageName = context.packageName
        val resources = context.resources

        // 1. icon_name_emotion (例: icon_shion_joy)
        var resId = resources.getIdentifier("icon_${charId}_$emotionId", "drawable", packageName)

        // 2. なければ mini_... (フォールバック)
        if (resId == 0) {
            resId = resources.getIdentifier("mini_${charId}_$emotionId", "drawable", packageName)
        }

        // 3. なければ 基本立ち絵
        if (resId == 0) {
            resId = resources.getIdentifier("char_$charId", "drawable", packageName)
        }

        // 4. 最終フォールバック
        if (resId == 0) {
            resId = resources.getIdentifier("icon_george_$emotionId", "drawable", packageName)
        }

        return if (resId != 0) resId else R.drawable.mini_george_joy
    }

    /**
     * アプリ内画面用のミニ画像を取得する (mini_キャラクター名_感情)
     */
    fun getMiniIconDrawable(context: Context, charId: String, emotionId: String): Int {
        return getEmotionDrawable(context, charId, emotionId)
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
