package com.example.studylockapp.data

import android.content.Context
import androidx.core.content.edit
import java.time.ZoneId
import kotlin.math.roundToInt

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANSWER_INTERVAL_MS = "answer_interval_ms" // Long
        private const val DEFAULT_ZONE_ID = "Asia/Tokyo"

        // 音量・TTS
        private const val KEY_SE_CORRECT_VOLUME = "se_correct_volume" // 0..100 または 0f..1f が混在し得る
        private const val KEY_SE_WRONG_VOLUME = "se_wrong_volume"
        private const val KEY_TTS_VOLUME = "tts_volume"
        private const val KEY_TTS_SPEED = "tts_speed"   // Float 0.5..1.5
        private const val KEY_TTS_PITCH = "tts_pitch"   // Float 0.5..1.5

        // 広告音量
        private const val KEY_AD_VOLUME = "ad_volume"
        private const val KEY_AD_MUTED = "ad_muted"

        private const val KEY_WRONG_RETRY_SEC = "wrong_retry_sec"
        private const val KEY_LEVEL1_RETRY_SEC = "level1_retry_sec"

        // タイムゾーン（例: "Asia/Tokyo"）。null/空なら端末の systemDefault を使う
        private const val KEY_APP_TIME_ZONE_ID = "app_time_zone_id"
        // 「初回にタイムゾーン選択を済ませたか」
        private const val KEY_TIME_ZONE_CHOSEN = "time_zone_chosen" // Boolean

        private const val KEY_LAST_SELECTED_GRADE = "last_selected_grade"

        // --- App Lock ---
        private const val KEY_APP_LOCK_ENABLED = "appLockEnabled"
        private const val KEY_UNLOCK_COST_POINTS_10MIN = "unlockCostPoints10Min"
        private const val KEY_UNLOCK_MIN_PER_10PT = "unlock_min_per_10pt" // 10pt あたりの分数（1〜10）

        // ★追加: アンインストール防止機能用キー
        private const val KEY_UNINSTALL_LOCK = "key_uninstall_lock"

        // アクセシビリティ誘導を表示済みかどうか
        private const val KEY_HAS_SHOWN_ACCESSIBILITY_INTRO = "hasShownAccessibilityIntro"
        private const val KEY_ENABLE_ADMIN_LONG_PRESS = "enable_admin_long_press"

        // ベースポイント設定
        private const val KEY_BASE_POINT_PREFIX = "base_point_"

        // 現在の学習グレードとポイント減少率
        private const val KEY_CURRENT_LEARNING_GRADE = "current_learning_grade"
        private const val KEY_POINT_REDUCTION_ONE_GRADE_DOWN = "point_reduction_one_grade_down"
        private const val KEY_POINT_REDUCTION_TWO_GRADES_DOWN = "point_reduction_two_grades_down"

        // ▼▼▼ 追加: 親連携用ID ▼▼▼
        private const val KEY_PARENT_UID = "parent_uid"
        private const val KEY_LEARNING_MODE = "learning_mode"
        private const val KEY_INCLUDE_OTHER_GRADES = "learning_include_other_grades"
        private const val KEY_HIDE_CHOICES = "learning_hide_choices"
        private const val KEY_AUTO_PLAY = "learning_auto_play"
        private const val KEY_LAST_GRADE_FILTER = "learning_last_grade_filter"
        // 追加: 外部から SharedPreferences を取得するためのヘルパー
        fun getPrefs(context: Context) =
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)


    }

    fun isEnableAdminLongPress(): Boolean = prefs.getBoolean(KEY_ENABLE_ADMIN_LONG_PRESS, true)
    fun setEnableAdminLongPress(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_ADMIN_LONG_PRESS, enabled) }
    }

    // --- 共通ヘルパ: Float/Int 混在への対応 ---
    private fun readVolumePercent(key: String, defaultPercent: Int): Int {
        val v = prefs.all[key]
        return when (v) {
            is Float -> (v * 100f).roundToInt().coerceIn(0, 100)
            is Double -> (v * 100.0).roundToInt().coerceIn(0, 100)
            is Int -> v.coerceIn(0, 100)
            else -> defaultPercent
        }
    }

    private fun writeVolumePercent(key: String, percent: Int) {
        prefs.edit { putInt(key, percent.coerceIn(0, 100)) }
    }

    // 0.0〜1.0 で扱うプロパティ（内部保存は 0..100 の Int に統一）
    var seCorrectVolume: Float
        get() = readVolumePercent(KEY_SE_CORRECT_VOLUME, 20) / 100f
        set(value) = writeVolumePercent(KEY_SE_CORRECT_VOLUME, (value * 100f).roundToInt())

    var seWrongVolume: Float
        get() = readVolumePercent(KEY_SE_WRONG_VOLUME, 20) / 100f
        set(value) = writeVolumePercent(KEY_SE_WRONG_VOLUME, (value * 100f).roundToInt())

    var ttsVolume: Float
        get() = readVolumePercent(KEY_TTS_VOLUME, 100) / 100f
        set(value) = writeVolumePercent(KEY_TTS_VOLUME, (value * 100f).roundToInt())

    var adVolume: Float
        get() = readVolumePercent(KEY_AD_VOLUME, 20) / 100f // デフォルト小さめ
        set(value) = writeVolumePercent(KEY_AD_VOLUME, (value * 100f).roundToInt())

    var adMuted: Boolean
        get() = prefs.getBoolean(KEY_AD_MUTED, false)
        set(value) = prefs.edit { putBoolean(KEY_AD_MUTED, value) }

    // TTS スピード／ピッチ（0.5〜1.5）
    fun getTtsSpeed(): Float = prefs.getFloat(KEY_TTS_SPEED, 1.0f).coerceIn(0.5f, 1.5f)
    fun setTtsSpeed(value: Float) {
        prefs.edit { putFloat(KEY_TTS_SPEED, value.coerceIn(0.5f, 1.5f)) }
    }

    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1.0f).coerceIn(0.5f, 1.5f)
    fun setTtsPitch(value: Float) {
        prefs.edit { putFloat(KEY_TTS_PITCH, value.coerceIn(0.5f, 1.5f)) }
    }

    var answerIntervalMs: Long
        get() = prefs.getLong(KEY_ANSWER_INTERVAL_MS, 1000L)
        set(value) = prefs.edit { putLong(KEY_ANSWER_INTERVAL_MS, value) }

    var wrongRetrySec: Long
        get() = prefs.getLong(KEY_WRONG_RETRY_SEC, 120L)
        set(v) = prefs.edit { putLong(KEY_WRONG_RETRY_SEC, v) }

    var level1RetrySec: Long
        get() = prefs.getLong(KEY_LEVEL1_RETRY_SEC, 120L)
        set(v) = prefs.edit { putLong(KEY_LEVEL1_RETRY_SEC, v) }

    var lastSelectedGrade: String?
        get() = prefs.getString(KEY_LAST_SELECTED_GRADE, null)
        set(value) = prefs.edit { putString(KEY_LAST_SELECTED_GRADE, value) }

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

    // ★追加: アンインストール防止機能が有効かどうか
    fun isUninstallLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_UNINSTALL_LOCK, false)
    }

    // ★追加: アンインストール防止機能の設定を変更
    fun setUninstallLockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_UNINSTALL_LOCK, enabled) }
    }

    fun getUnlockCostPoints10Min(): Int =
        prefs.getInt(KEY_UNLOCK_COST_POINTS_10MIN, 20).coerceAtLeast(0)

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

    // --- Base Points ---
    fun getBasePoint(mode: String): Int {
        val defaultPoint = when (mode) {
            "test_sort","test_listen_q2" -> 12
            "test_listen_q1","test_fill_blank" -> 8
            "english_english_1", "english_english_2" -> 8
            "listening_jp" -> 4
            "meaning", "japanese_to_english", "listening" -> 4
            else -> 4 // fallback for any other modes
        }
        return prefs.getInt(KEY_BASE_POINT_PREFIX + mode, defaultPoint).coerceIn(4, 32)
    }

    fun setBasePoint(mode: String, value: Int) {
        prefs.edit { putInt(KEY_BASE_POINT_PREFIX + mode, value.coerceIn(4, 32)) }
    }

    // --- Grade-based Point Reduction ---
    var currentLearningGrade: String
        get() = prefs.getString(KEY_CURRENT_LEARNING_GRADE, "All") ?: "All"
        set(value) = prefs.edit { putString(KEY_CURRENT_LEARNING_GRADE, value) }

    var pointReductionOneGradeDown: Int
        get() = prefs.getInt(KEY_POINT_REDUCTION_ONE_GRADE_DOWN, 50)
        set(value) = prefs.edit { putInt(KEY_POINT_REDUCTION_ONE_GRADE_DOWN, value.coerceIn(0, 100)) }

    var pointReductionTwoGradesDown: Int
        get() = prefs.getInt(KEY_POINT_REDUCTION_TWO_GRADES_DOWN, 25)
        set(value) = prefs.edit { putInt(KEY_POINT_REDUCTION_TWO_GRADES_DOWN, value.coerceIn(0, 100)) }

    // ▼▼▼ 追加: 親IDの読み書き（通知チェック用） ▼▼▼

    // 親IDを保存する
    fun setParentUid(uid: String?) {
        if (uid == null) {
            prefs.edit { remove(KEY_PARENT_UID) }
        } else {
            prefs.edit { putString(KEY_PARENT_UID, uid) }
        }
    }

    // 親IDを持っているか判定 (これが true なら監視対象)
    fun hasParent(): Boolean {
        return prefs.contains(KEY_PARENT_UID)
    }

    // 必要ならIDを取り出す
    fun getParentUid(): String? {
        return prefs.getString(KEY_PARENT_UID, null)
    }
    var learningMode: String
        get() = prefs.getString(KEY_LEARNING_MODE, "meaning") ?: "meaning"
        set(value) { prefs.edit().putString(KEY_LEARNING_MODE, value).apply() }

    var learningIncludeOtherGrades: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_OTHER_GRADES, false)
        set(value) { prefs.edit().putBoolean(KEY_INCLUDE_OTHER_GRADES, value).apply() }

    var learningHideChoices: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CHOICES, false)
        set(value) { prefs.edit().putBoolean(KEY_HIDE_CHOICES, value).apply() }

    var learningAutoPlay: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_PLAY, value).apply() }

    var lastGradeFilter: String
        get() = prefs.getString(KEY_LAST_GRADE_FILTER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_GRADE_FILTER, value).apply() }
}