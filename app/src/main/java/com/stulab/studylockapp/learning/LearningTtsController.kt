package com.stulab.studylockapp.learning

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.stulab.studylockapp.data.AppSettings
import java.util.Locale

/**
 * TTS (TextToSpeech) の管理を担当するクラス。
 * AppSettings から速度・ピッチ・音量を取得して再生に反映します。
 */
class LearningTtsController(context: Context) : TextToSpeech.OnInitListener {

    private val appSettings = AppSettings(context)
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady = true
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
            }
        } else {
            Log.e("LearningTtsController", "Initialization failed")
        }
    }

    /**
     * 指定したテキストを再生。AppSettings の設定値を反映します。
     */
    fun speak(text: String) {
        if (isReady) {
            val speed = appSettings.getTtsSpeed()
            val pitch = appSettings.getTtsPitch()
            val volume = appSettings.ttsVolume

            tts?.apply {
                setSpeechRate(speed)
                setPitch(pitch)
                
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                
                speak(text, TextToSpeech.QUEUE_FLUSH, params, "StudyLockTts_${System.currentTimeMillis()}")
            }
        } else {
            pendingText = text
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
