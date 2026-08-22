package com.stulab.studylockapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

class ChallengePointManager(context: Context) {
    private val prefs = context.getSharedPreferences("points", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CP = "challenge_points"
    }

    fun getCP(): Int = prefs.getInt(KEY_CP, 0)

    fun addCP(delta: Int) {
        if (delta <= 0) return
        prefs.edit { putInt(KEY_CP, getCP() + delta) }
    }

    fun consumeCP(amount: Int): Boolean {
        val current = getCP()
        if (current < amount) return false
        prefs.edit { putInt(KEY_CP, current - amount) }
        return true
    }

    fun getCPFlow(): Flow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == KEY_CP) {
                trySend(p.getInt(KEY_CP, 0))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(getCP()) }
}
