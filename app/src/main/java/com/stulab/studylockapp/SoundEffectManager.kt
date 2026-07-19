package com.stulab.studylockapp

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.stulab.studylockapp.data.AppSettings

/**
 * 正解・不正解の効果音（SE）を管理するクラス。
 * AppSettings から設定された音量を読み取って再生します。
 */
class SoundEffectManager(private val context: Context) {

    private val appSettings = AppSettings(context)
    private var soundPool: SoundPool? = null
    private var seCorrectId: Int = 0
    private var seWrongId: Int = 0

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        seCorrectId = loadSeIfExists("se_correct")
        seWrongId = loadSeIfExists("se_wrong")
    }

    /**
     * 正解音を再生（AppSettings の音量を反映）
     */
    fun playCorrect() {
        val volume = appSettings.seCorrectVolume
        if (seCorrectId != 0) {
            soundPool?.play(seCorrectId, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * 不正解音を再生（AppSettings の音量を反映）
     */
    fun playWrong() {
        val volume = appSettings.seWrongVolume
        if (seWrongId != 0) {
            soundPool?.play(seWrongId, volume, volume, 1, 0, 1f)
        }
    }

    /**
     * リソースの解放
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
