package com.example.studylockapp.data

import android.content.Context
import androidx.core.content.edit
import java.time.ZoneId

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANSWER_INTERVAL_MS = "answer_interval_ms" // Long
        private const val DEFAULT_ZONE_ID = "Asia/Tokyo"

        private const val KEY_SE_CORRECT_VOLUME = "se_correct_volume"
        private const val KEY_SE_WRONG_VOLUME = "se_wrong_volume"
        private const val KEY_TTS_VOLUME = "tts_volume"

        // 広告音量（AdMob等を入れた時に使う）
        private const val KEY_AD_VOLUME = "ad_volume"     // Float 0..1
        private const val KEY_AD_MUTED = "ad_muted"       // Boolean

        private const val KEY_WRONG_RETRY_SEC = "wrong_retry_sec"
        private const val KEY_LEVEL1_RETRY_SEC = "level1_retry_sec"

        // タイムゾーン（例: "Asia/Tokyo"）。null/空なら端末の systemDefault を使う
        private const val KEY_APP_TIME_ZONE_ID = "app_time_zone_id"
        // 「初回にタイムゾーン選択を済ませたか」
        private const val KEY_TIME_ZONE_CHOSEN = "time_zone_chosen" // Boolean

        // --- App Lock ---
        private const val KEY_APP_LOCK_ENABLED = "appLockEnabled"
        private const val KEY_UNLOCK_COST_POINTS_10MIN = "unlockCostPoints10Min"
        private const val KEY_UNLOCK_DURATION_MIN = "unlockDurationMin"
        private const val KEY_UNLOCK_MIN_PER_10PT = "unlock_min_per_10pt" // 10pt あたりの分数（1〜10）

        // アクセシビリティ誘導を表示済みかどうか
        private const val KEY_HAS_SHOWN_ACCESSIBILITY_INTRO = "hasShownAccessibilityIntro"
    }

    var answerIntervalMs: Long
        get() = prefs.getLong(KEY_ANSWER_INTERVAL_MS, 1000L)
        set(value) = prefs.edit { putLong(KEY_ANSWER_INTERVAL_MS, value) }

    var seCorrectVolume: Float
        get() = prefs.getFloat(KEY_SE_CORRECT_VOLUME, 0.9f).coerceIn(0f, 1f)
        set(value) = prefs.edit { putFloat(KEY_SE_CORRECT_VOLUME, value.coerceIn(0f, 1f)) }

    var seWrongVolume: Float
        get() = prefs.getFloat(KEY_SE_WRONG_VOLUME, 0.9f).coerceIn(0f, 1f)
        set(value) = prefs.edit { putFloat(KEY_SE_WRONG_VOLUME, value.coerceIn(0f, 1f)) }

    var ttsVolume: Float
        get() = prefs.getFloat(KEY_TTS_VOLUME, 1.0f).coerceIn(0f, 1f)
        set(value) = prefs.edit { putFloat(KEY_TTS_VOLUME, value.coerceIn(0f, 1f)) }

    // 広告音量
    var adVolume: Float
        get() = prefs.getFloat(KEY_AD_VOLUME, 0.2f).coerceIn(0f, 1f) // デフォルト小さめ
        set(value) = prefs.edit { putFloat(KEY_AD_VOLUME, value.coerceIn(0f, 1f)) }

    var adMuted: Boolean
        get() = prefs.getBoolean(KEY_AD_MUTED, false)
        set(value) = prefs.edit { putBoolean(KEY_AD_MUTED, value) }

    var wrongRetrySec: Long
        get() = prefs.getLong(KEY_WRONG_RETRY_SEC, 60L)
        set(v) = prefs.edit { putLong(KEY_WRONG_RETRY_SEC, v) }

    var level1RetrySec: Long
        get() = prefs.getLong(KEY_LEVEL1_RETRY_SEC, 60L)
        set(v) = prefs.edit { putLong(KEY_LEVEL1_RETRY_SEC, v) }

    /**
     * タイムゾーンID（例: "Asia/Tokyo"）
     * null/空の場合は端末の systemDefault を使う
     */
    var appTimeZoneId: String?
        get() = prefs.getString(KEY_APP_TIME_ZONE_ID, null)
        set(value) = prefs.edit { putString(KEY_APP_TIME_ZONE_ID, value) }

    /**
     * 初回セットアップ用：タイムゾーンを「選択したことがあるか」
     * - 端末デフォルト（appTimeZoneId=null）を選んでも true にできるよう別キーで管理
     */
    var timeZoneChosen: Boolean
        get() = prefs.getBoolean(KEY_TIME_ZONE_CHOSEN, false)
        set(value) = prefs.edit { putBoolean(KEY_TIME_ZONE_CHOSEN, value) }

    fun hasChosenTimeZone(): Boolean = timeZoneChosen

    /**
     * timeZoneId: "Asia/Tokyo" など。端末デフォルトを使うなら null を渡す
     * ※必ず「選択済み」にする
     */
    fun setTimeZone(timeZoneId: String?) {
        appTimeZoneId = timeZoneId
        timeZoneChosen = true
    }

    /**
     * アプリ内で使う ZoneId を統一して取得する
     * 保存値が null/空：Tokyo をデフォルト採用
     * 保存値が不正：Tokyo → それも失敗した場合は systemDefault にフォールバック
     */
    fun getAppZoneId(): ZoneId {
        val stored = appTimeZoneId
        val tokyo = try {
            ZoneId.of(DEFAULT_ZONE_ID)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
        return try {
            if (stored.isNullOrBlank()) tokyo else ZoneId.of(stored)
        } catch (_: Exception) {
            tokyo
        }
    }

    // --- App Lock ---
    fun isAppLockEnabled(): Boolean =
        prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) }
    }

    fun getUnlockCostPoints10Min(): Int =
        prefs.getInt(KEY_UNLOCK_COST_POINTS_10MIN, 20).coerceAtLeast(0)

    fun setUnlockCostPoints10Min(points: Int) {
        prefs.edit { putInt(KEY_UNLOCK_COST_POINTS_10MIN, points.coerceAtLeast(0)) }
    }

    fun getUnlockDurationMin(): Int =
        prefs.getInt(KEY_UNLOCK_DURATION_MIN, 10).coerceIn(1, 240) // 1〜240分にクランプ

    fun setUnlockDurationMin(min: Int) {
        prefs.edit { putInt(KEY_UNLOCK_DURATION_MIN, min.coerceIn(1, 240)) }
    }

    /**
     * 10pt あたりの解放分数（1〜10分）
     */
    fun getUnlockMinutesPer10Pt(): Int =
        prefs.getInt(KEY_UNLOCK_MIN_PER_10PT, 1).coerceIn(1, 10)

    fun setUnlockMinutesPer10Pt(value: Int) {
        prefs.edit { putInt(KEY_UNLOCK_MIN_PER_10PT, value.coerceIn(1, 10)) }
    }

    // --- Accessibility Intro ---
    fun hasShownAccessibilityIntro(): Boolean =
        prefs.getBoolean(KEY_HAS_SHOWN_ACCESSIBILITY_INTRO, false)

    fun setHasShownAccessibilityIntro(shown: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_SHOWN_ACCESSIBILITY_INTRO, shown) }
    }
}