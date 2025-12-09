package com.example.studylockapp.data

import java.time.LocalDate

object ProgressCalculator {
    private val intervalDays = mapOf(
        0 to 0, 1 to 0,
        2 to 1, 3 to 3, 4 to 7,
        5 to 14, 6 to 30, 7 to 60, 8 to 90
    )

    fun update(isCorrect: Boolean, currentLevel: Int, todayEpochDay: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) (currentLevel + 1).coerceAtMost(8) else (currentLevel - 2).coerceAtLeast(0)
        val nextDue = todayEpochDay + (intervalDays[newLevel] ?: 0)
        return newLevel to nextDue
    }

    fun calcPoint(isCorrect: Boolean, levelBefore: Int): Int {
        if (!isCorrect) return 0
        return if (levelBefore <= 1) 10 else 5
    }

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}