package com.stulab.studylockapp.data

import android.content.Context
import androidx.core.content.edit
import com.stulab.studylockapp.learning.QuizMode
import java.time.ZoneId
import kotlin.math.roundToInt

enum class SilentMode {
    OFF, // 通常学習 (AutoPlay: ON, Listening: ON, SE: ON)
    ON   // 音声なし学習 (AutoPlay: OFF, Listening: OFF, SE: OFF)
}

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        // --- Timing Settings ---
        private const val KEY_ANSWER_INTERVAL_MS = "answer_interval_ms"
        private const val KEY_WRONG_RETRY_SEC = "wrong_retry_sec"
        private const val KEY_LEVEL1_RETRY_SEC = "level1_retry_sec"
        private const val KEY_DONT_KNOW_RETRY_SEC = "dont_know_retry_sec"

        // --- Other Settings ---
        private const val KEY_SE_CORRECT_VOLUME = "se_correct_volume"
        private const val KEY_SE_WRONG_VOLUME = "se_wrong_volume"
        private const val KEY_TTS_VOLUME = "tts_volume"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"

        private const val KEY_AD_VOLUME = "ad_volume"
        private const val KEY_AD_MUTED = "ad_muted"

        private const val KEY_LAST_SELECTED_GRADE = "last_selected_grade"

        // --- App Lock ---
        private const val KEY_APP_LOCK_ENABLED = "appLockEnabled"
        private const val KEY_UNLOCK_COST_POINTS_10MIN = "unlockCostPoints10Min"
        private const val KEY_UNLOCK_MIN_PER_10PT = "unlock_min_per_10pt"
        private const val KEY_MIGRATED_APP_LOCK_V2 = "migrated_app_lock_v2"

        // --- Administrative ---
        private const val KEY_UNINSTALL_LOCK = "key_uninstall_lock"
        private const val KEY_HAS_SHOWN_ACCESSIBILITY_INTRO = "hasShownAccessibilityIntro"
        private const val KEY_ACCESSIBILITY_ENABLED_NOTIFIED = "accessibility_enabled_notified"
        private const val KEY_LAST_ACCESSIBILITY_ENABLED = "last_accessibility_enabled"

        private const val KEY_BASE_POINT_PREFIX = "base_point_v2_"
        private const val KEY_CURRENT_LEARNING_GRADE = "current_learning_grade"
        private const val KEY_TARGET_LEARNING_GRADE = "target_learning_grade"
        private const val KEY_POINT_REDUCTION_ONE_GRADE_DOWN = "point_reduction_one_grade_down"
        private const val KEY_POINT_REDUCTION_TWO_GRADES_DOWN = "point_reduction_two_grades_down"

        private const val KEY_PARENT_UID = "parent_uid"
        private const val KEY_LEARNING_MODE = "learning_mode"
        private const val KEY_INCLUDE_OTHER_GRADES = "learning_include_other_grades"
        private const val KEY_HIDE_CHOICES = "learning_hide_choices"
        
        private const val KEY_SILENT_MODE = "learning_silent_mode"
        private const val KEY_HAS_SHOWN_SILENT_EXPLANATION = "has_shown_silent_explanation"

        private const val KEY_LAST_GRADE_FILTER = "learning_last_grade_filter"
        private const val KEY_HAS_RESET_MASTERY_FOR_FIX = "has_reset_mastery_for_fix"
        
        private const val KEY_LAST_ACTIVE_UPDATE_MILLIS = "last_active_update_millis"

        private const val KEY_WORD_DATA_VERSION = "word_data_version"
        private const val KEY_DAILY_NEW_WORD_TARGET = "daily_new_word_target"

        // --- Notification & Character ---
        private const val KEY_TOTAL_GOALS_MET_COUNT = "total_goals_met_count"
        private const val KEY_DAILY_GOAL_STREAK = "daily_goal_streak"
        private const val KEY_LAST_GOAL_MET_DATE = "last_goal_met_date"
        private const val KEY_LAST_STUDY_DATE = "last_study_date"
        private const val KEY_SELECTED_CHARACTER_ID = "selected_character_id"
        private const val KEY_UNLOCKED_CHARACTERS = "unlocked_characters"
        private const val KEY_USER_NAME = "user_name"

        private const val KEY_PERSONAL_GRADE_TARGET_PREFIX = "personal_grade_target_"

        fun getPrefs(context: Context) =
            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    // --- Timing Implementation ---

    var answerIntervalMs: Long
        get() = 0L
        set(value) { /* No-op */ }

    var wrongRetrySec: Long
        get() = prefs.getLong(KEY_WRONG_RETRY_SEC, 300L)
        set(v) = prefs.edit { putLong(KEY_WRONG_RETRY_SEC, v) }

    var level1RetrySec: Long
        get() = prefs.getLong(KEY_LEVEL1_RETRY_SEC, 300L)
        set(v) = prefs.edit { putLong(KEY_LEVEL1_RETRY_SEC, v) }

    var dontKnowRetrySec: Long
        get() = prefs.getLong(KEY_DONT_KNOW_RETRY_SEC, 30L)
        set(value) = prefs.edit { putLong(KEY_DONT_KNOW_RETRY_SEC, value) }

    // --- Grade Settings ---

    var currentLearningGrade: String
        get() = prefs.getString(KEY_CURRENT_LEARNING_GRADE, "3") ?: "3"
        set(value) = prefs.edit { putString(KEY_CURRENT_LEARNING_GRADE, value) }

    val safeLearningGrade: String
        get() {
            val value = currentLearningGrade
            val rank = value.toIntOrNull()
            return if (rank != null && rank in 1..7) value else "3"
        }

    var targetLearningGrade: String
        get() = prefs.getString(KEY_TARGET_LEARNING_GRADE, "0") ?: "0"
        set(value) = prefs.edit { putString(KEY_TARGET_LEARNING_GRADE, value) }

    val isTargetLearningGradeSet: Boolean
        get() = targetLearningGrade.toIntOrNull() in 1..7

    val safeTargetLearningGrade: String
        get() {
            val rank = targetLearningGrade.toIntOrNull()
            return if (rank != null && rank in 1..7) rank.toString() else "3"
        }

    // --- Point Settings ---

    private fun basePointKey(mode: QuizMode): String = KEY_BASE_POINT_PREFIX + mode.name

    fun getBasePoint(mode: QuizMode): Int {
        return prefs.getInt(basePointKey(mode), 8)
    }

    fun setBasePoint(mode: QuizMode, point: Int) {
        prefs.edit { putInt(basePointKey(mode), point) }
    }

    // --- Remaining Settings ---

    var silentMode: SilentMode
        get() = if (prefs.getBoolean(KEY_SILENT_MODE, false)) SilentMode.ON else SilentMode.OFF
        set(value) = prefs.edit { putBoolean(KEY_SILENT_MODE, value == SilentMode.ON) }

    var hasShownSilentExplanation: Boolean
        get() = prefs.getBoolean(KEY_HAS_SHOWN_SILENT_EXPLANATION, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_SHOWN_SILENT_EXPLANATION, value) }

    var hasResetMasteryForFix: Boolean
        get() = prefs.getBoolean(KEY_HAS_RESET_MASTERY_FOR_FIX, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_RESET_MASTERY_FOR_FIX, value) }

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
        get() = readVolumePercent(KEY_AD_VOLUME, 20) / 100f
        set(value) = writeVolumePercent(KEY_AD_VOLUME, (value * 100f).roundToInt())

    var adMuted: Boolean
        get() = prefs.getBoolean(KEY_AD_MUTED, false)
        set(value) = prefs.edit { putBoolean(KEY_AD_MUTED, value) }

    fun getTtsSpeed(): Float = prefs.getFloat(KEY_TTS_SPEED, 1.0f).coerceIn(0.5f, 1.5f)
    fun setTtsSpeed(value: Float) {
        prefs.edit { putFloat(KEY_TTS_SPEED, value.coerceIn(0.5f, 1.5f)) }
    }

    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1.0f).coerceIn(0.5f, 1.5f)
    fun setTtsPitch(value: Float) {
        prefs.edit { putFloat(KEY_TTS_PITCH, value.coerceIn(0.5f, 1.5f)) }
    }

    var lastSelectedGrade: String?
        get() = prefs.getString(KEY_LAST_SELECTED_GRADE, null)
        set(value) = prefs.edit { putString(KEY_LAST_SELECTED_GRADE, value) }

    var includeLowerGradeInReview: Boolean
        get() = prefs.getBoolean("include_lower_grade_in_review", false)
        set(value) = prefs.edit().putBoolean("include_lower_grade_in_review", value).apply()

    fun getAppZoneId(): ZoneId {
        return ZoneId.systemDefault()
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    fun setAppLockEnabled(enabled: Boolean) { prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) } }

    fun isMigratedAppLockV2(): Boolean = prefs.getBoolean(KEY_MIGRATED_APP_LOCK_V2, false)
    fun setMigratedAppLockV2(migrated: Boolean) { prefs.edit { putBoolean(KEY_MIGRATED_APP_LOCK_V2, migrated) } }

    fun isUninstallLockEnabled(): Boolean = prefs.getBoolean(KEY_UNINSTALL_LOCK, false)
    fun setUninstallLockEnabled(enabled: Boolean) { prefs.edit { putBoolean(KEY_UNINSTALL_LOCK, enabled) } }

    fun getUnlockCostPoints10Min(): Int = prefs.getInt(KEY_UNLOCK_COST_POINTS_10MIN, 20).coerceAtLeast(0)

    fun getUnlockMinutesPer10Pt(): Int = prefs.getInt(KEY_UNLOCK_MIN_PER_10PT, 2).coerceIn(1, 10)
    fun setUnlockMinutesPer10Pt(value: Int) { prefs.edit { putInt(KEY_UNLOCK_MIN_PER_10PT, value.coerceIn(1, 10)) } }

    fun hasShownAccessibilityIntro(): Boolean = prefs.getBoolean(KEY_HAS_SHOWN_ACCESSIBILITY_INTRO, false)
    fun setHasShownAccessibilityIntro(shown: Boolean) { prefs.edit { putBoolean(KEY_HAS_SHOWN_ACCESSIBILITY_INTRO, shown) } }

    fun isAccessibilityEnabledNotified(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED_NOTIFIED, false)
    fun setAccessibilityEnabledNotified(notified: Boolean) { prefs.edit { putBoolean(KEY_ACCESSIBILITY_ENABLED_NOTIFIED, notified) } }

    fun hasAccessibilityStateRecorded(): Boolean = prefs.contains(KEY_LAST_ACCESSIBILITY_ENABLED)
    fun isLastAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_LAST_ACCESSIBILITY_ENABLED, false)
    fun setLastAccessibilityEnabled(enabled: Boolean) { prefs.edit { putBoolean(KEY_LAST_ACCESSIBILITY_ENABLED, enabled) } }

    var pointReductionOneGradeDown: Int
        get() = prefs.getInt(KEY_POINT_REDUCTION_ONE_GRADE_DOWN, 50)
        set(value) = prefs.edit { putInt(KEY_POINT_REDUCTION_ONE_GRADE_DOWN, value.coerceIn(0, 100)) }

    var pointReductionTwoGradesDown: Int
        get() = prefs.getInt(KEY_POINT_REDUCTION_TWO_GRADES_DOWN, 25)
        set(value) = prefs.edit { putInt(KEY_POINT_REDUCTION_TWO_GRADES_DOWN, value.coerceIn(0, 100)) }

    fun setParentUid(uid: String?) {
        if (uid == null) prefs.edit { remove(KEY_PARENT_UID) }
        else prefs.edit { putString(KEY_PARENT_UID, uid) }
    }

    fun hasParent(): Boolean = prefs.contains(KEY_PARENT_UID)
    fun getParentUid(): String? = prefs.getString(KEY_PARENT_UID, null)

    var learningMode: String
        get() = prefs.getString(KEY_LEARNING_MODE, "meaning") ?: "meaning"
        set(value) { prefs.edit().putString(KEY_LEARNING_MODE, value).apply() }

    /**
     * 他級復習のON/OFF設定
     */
    var includeOtherGrades: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_OTHER_GRADES, false)
        set(value) { prefs.edit().putBoolean(KEY_INCLUDE_OTHER_GRADES, value).apply() }

    /**
     * 選択肢表示のON/OFF設定 (内部的には KEY_HIDE_CHOICES を反転させて管理)
     */
    var choiceModeEnabled: Boolean
        get() = !prefs.getBoolean(KEY_HIDE_CHOICES, false)
        set(value) { prefs.edit().putBoolean(KEY_HIDE_CHOICES, !value).apply() }

    var lastGradeFilter: String
        get() = prefs.getString(KEY_LAST_GRADE_FILTER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_GRADE_FILTER, value).apply() }

    var isAccessibilityLockEnabled: Boolean
        get() = prefs.getBoolean("accessibility_lock", false)
        set(value) = prefs.edit { putBoolean("accessibility_lock", value) }

    var isTetheringLockEnabled: Boolean
        get() = false
        set(value) { /* No-op, always false for Google Play compatibility */ }
        
    var lastActiveUpdateMillis: Long
        get() = prefs.getLong(KEY_LAST_ACTIVE_UPDATE_MILLIS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_ACTIVE_UPDATE_MILLIS, value) }

    var wordDataVersion: Int
        get() = prefs.getInt(KEY_WORD_DATA_VERSION, 0)
        set(value) { prefs.edit { putInt(KEY_WORD_DATA_VERSION, value) } }

    var dailyNewWordTarget: Int
        get() = prefs.getInt(KEY_DAILY_NEW_WORD_TARGET, 5)
        set(value) { prefs.edit { putInt(KEY_DAILY_NEW_WORD_TARGET, value) } }

    // --- Goal Tracking ---
    var totalGoalsMetCount: Int
        get() = prefs.getInt(KEY_TOTAL_GOALS_MET_COUNT, 0)
        set(v) = prefs.edit { putInt(KEY_TOTAL_GOALS_MET_COUNT, v) }

    var dailyGoalStreak: Int
        get() = prefs.getInt(KEY_DAILY_GOAL_STREAK, 0)
        set(v) = prefs.edit { putInt(KEY_DAILY_GOAL_STREAK, v) }

    var lastGoalMetDate: String?
        get() = prefs.getString(KEY_LAST_GOAL_MET_DATE, null)
        set(v) = prefs.edit { putString(KEY_LAST_GOAL_MET_DATE, v) }

    var lastStudyDate: String?
        get() = prefs.getString(KEY_LAST_STUDY_DATE, null)
        set(v) = prefs.edit { putString(KEY_LAST_STUDY_DATE, v) }

    var selectedCharacterId: String
        get() = prefs.getString(KEY_SELECTED_CHARACTER_ID, "george") ?: "george"
        set(v) = prefs.edit { putString(KEY_SELECTED_CHARACTER_ID, v) }

    /**
     * 解放済みキャラIDのセットを取得・保存
     */
    var unlockedCharacterIds: Set<String>
        get() = prefs.getStringSet(KEY_UNLOCKED_CHARACTERS, setOf("george")) ?: setOf("george")
        set(v) = prefs.edit { putStringSet(KEY_UNLOCKED_CHARACTERS, v) }

    fun unlockCharacter(id: String) {
        val current = unlockedCharacterIds.toMutableSet()
        current.add(id)
        unlockedCharacterIds = current
    }

    fun isCharacterUnlocked(id: String): Boolean {
        return unlockedCharacterIds.contains(id)
    }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(v) = prefs.edit { putString(KEY_USER_NAME, v) }

    /**
     * マイ単語帳(90-99)が達成された際に出題する「実践テスト」の級(1-7)を取得する
     */
    fun getPersonalGradeTarget(personalGrade: Int): Int {
        return prefs.getInt(KEY_PERSONAL_GRADE_TARGET_PREFIX + personalGrade, 3)
    }

    fun setPersonalGradeTarget(personalGrade: Int, targetGrade: Int) {
        prefs.edit { putInt(KEY_PERSONAL_GRADE_TARGET_PREFIX + personalGrade, targetGrade) }
    }
}
