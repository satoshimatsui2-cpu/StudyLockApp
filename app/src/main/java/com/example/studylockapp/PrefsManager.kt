package com.example.studylockapp

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "AdminSettings"
    private const val KEY_IS_ACCESSIBILITY_LOCK_ENABLED = "is_accessibility_lock_enabled"
    private const val KEY_IS_TETHERING_LOCK_ENABLED = "is_tethering_lock_enabled"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAccessibilityLockEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_ACCESSIBILITY_LOCK_ENABLED, false)
    }

    fun setAccessibilityLockEnabled(context: Context, isEnabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_ACCESSIBILITY_LOCK_ENABLED, isEnabled).apply()
    }

    fun isTetheringLockEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_TETHERING_LOCK_ENABLED, false)
    }

    fun setTetheringLockEnabled(context: Context, isEnabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_TETHERING_LOCK_ENABLED, isEnabled).apply()
    }
}
