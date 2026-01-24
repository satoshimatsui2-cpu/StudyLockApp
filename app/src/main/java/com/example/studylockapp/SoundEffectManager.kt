package com.example.studylockapp

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * 正解・不正解の効果音（SE）を管理するクラス
 */
class SoundEffectManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0

    init {
        // SoundPoolの初期化
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        // 音声ファイルの読み込み
        seCorrectId = loadSeIfExists("se_correct")
        seWrongId = loadSeIfExists("se_wrong")
    }

    /**
     * 正解音を再生
     */
    fun playCorrect(volume: Float) {
        if (seCorrectId != 0) {
            soundPool?.play(seCorrectId, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * 不正解音を再生
     */
    fun playWrong(volume: Float) {
        if (seWrongId != 0) {
            soundPool?.play(seWrongId, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * リソースの解放（画面を閉じるときに呼ぶ）
     */
    fun release() {
        soundPool?.release()
        soundPool = null
    }

    private fun loadSeIfExists(rawName: String): Int {
        val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
        return if (resId != 0) soundPool?.load(context, resId, 1) ?: 0 else 0
    }
}