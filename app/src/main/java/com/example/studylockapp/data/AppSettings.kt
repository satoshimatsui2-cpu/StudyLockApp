package com.example.studylockapp.data

import android.content.Context

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANSWER_INTERVAL_MS = "answer_interval_ms" // Long

        private const val KEY_SE_CORRECT_VOLUME = "se_correct_volume"
        private const val KEY_SE_WRONG_VOLUME = "se_wrong_volume"
        private const val KEY_TTS_VOLUME = "tts_volume"

        // ★追加：広告音量（AdMob等を入れた時に使う）
        private const val KEY_AD_VOLUME = "ad_volume"     // Float 0..1
        private const val KEY_AD_MUTED = "ad_muted"       // Boolean
    }

    var answerIntervalMs: Long
        get() = prefs.getLong(KEY_ANSWER_INTERVAL_MS, 1000L)
        set(value) = prefs.edit().putLong(KEY_ANSWER_INTERVAL_MS, value).apply()

    var seCorrectVolume: Float
        get() = prefs.getFloat(KEY_SE_CORRECT_VOLUME, 0.9f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_SE_CORRECT_VOLUME, value.coerceIn(0f, 1f)).apply()

    var seWrongVolume: Float
        get() = prefs.getFloat(KEY_SE_WRONG_VOLUME, 0.9f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_SE_WRONG_VOLUME, value.coerceIn(0f, 1f)).apply()

    var ttsVolume: Float
        get() = prefs.getFloat(KEY_TTS_VOLUME, 1.0f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_TTS_VOLUME, value.coerceIn(0f, 1f)).apply()

    // ★追加：広告音量
    var adVolume: Float
        get() = prefs.getFloat(KEY_AD_VOLUME, 0.2f).coerceIn(0f, 1f) // デフォルト小さめ
        set(value) = prefs.edit().putFloat(KEY_AD_VOLUME, value.coerceIn(0f, 1f)).apply()

    var adMuted: Boolean
        get() = prefs.getBoolean(KEY_AD_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_AD_MUTED, value).apply()
}