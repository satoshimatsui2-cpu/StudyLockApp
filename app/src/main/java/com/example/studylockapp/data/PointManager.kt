package com.example.studylockapp.data

import android.content.Context
import androidx.core.content.edit

class PointManager(context: Context) {
    private val prefs = context.getSharedPreferences("points", Context.MODE_PRIVATE)

    fun getTotal(): Int = prefs.getInt("total_points", 0)

    fun add(delta: Int) {
        if (delta == 0) return
        prefs.edit { putInt("total_points", getTotal() + delta) }
    }
}
