package com.example.studylockapp.ui

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DueTimeFormatter {

    /**
     * 表示ルール:
     * - dueSec <= nowSec: 「今すぐ」
     * - due の日付が翌日以降: 「○日後」（残り時間が数時間でも “翌日” なら1日後）
     * - 当日中:
     *    - <60秒: ○秒後
     *    - <3600秒: ○分後（切り捨て）
     *    - >=3600秒: ○時間後（切り捨て）
     */
    fun formatRemaining(nowSec: Long, dueSec: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val remaining = dueSec - nowSec
        if (remaining <= 0) return "今すぐ"

        val nowDate = Instant.ofEpochSecond(nowSec).atZone(zoneId).toLocalDate()
        val dueDate = Instant.ofEpochSecond(dueSec).atZone(zoneId).toLocalDate()

        val dayDiff = ChronoUnit.DAYS.between(nowDate, dueDate)
        if (dayDiff >= 1) return "${dayDiff}日後"

        // 当日中（切り捨て）
        return when {
            remaining < 60 -> "${remaining}秒後"
            remaining < 3600 -> "${remaining / 60}分後"
            else -> "${remaining / 3600}時間後"
        }
    }
}