package com.stulab.studylockapp.learning

import java.util.concurrent.TimeUnit

/**
 * 簡易的な間隔反復（SRS）計算ロジック
 */
object ReviewScheduler {
    /**
     * 正解回数に応じて次の復習時刻（UNIXタイム）を計算する
     */
    fun calculateNextReview(correctCount: Int): Long {
        val now = System.currentTimeMillis()
        
        // インターバル設定（正解するほど間隔が広がる）
        val intervalMinutes = when (correctCount) {
            1 -> 1L       // 1回正解: 1分後
            2 -> 10L      // 2回正解: 10分後
            3 -> 60L      // 3回正解: 1時間後
            4 -> 1440L    // 4回正解: 1日後
            else -> 10080L // それ以上: 1週間後
        }
        
        return now + TimeUnit.MINUTES.toMillis(intervalMinutes)
    }
}
