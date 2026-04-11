package com.example.studylockapp.learning

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS (TextToSpeech) の管理を担当するクラス。
 * 初期化待ちの間のリクエストをバッファリングし、準備ができ次第再生します。
 */
class LearningTtsController(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("LearningTtsController", "Language not supported")
            } else {
                isReady = true
                // 初期化待ちの間にリクエストがあったテキストを再生
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
