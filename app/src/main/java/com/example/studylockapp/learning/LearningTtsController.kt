package com.example.studylockapp.learning

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS (TextToSpeech) の管理を担当するクラス。
 * SE 側と音量ポリシーを統一し、メディアストリーム（USAGE_MEDIA）で再生します。
 */
class LearningTtsController(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 音声属性をメディア用に設定（SE とポリシーを統一）
            // 話者音声なので CONTENT_TYPE_SPEECH を使用
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
     * 指定したテキストを再生。準備ができていない場合はバッファに保存。
     */
    fun speak(text: String) {
        if (isReady) {
            // USAGE_MEDIA 属性で再生される
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "StudyLockTts")
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
