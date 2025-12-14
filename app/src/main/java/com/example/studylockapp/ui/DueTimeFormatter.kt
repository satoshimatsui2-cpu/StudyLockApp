import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

object DueTimeFormatter {

    /**
     * 表示ルール:
     * - dueSec <= nowSec: 「今すぐ」
     * - due日付が翌日以降: 「○日後」(時間が2時間でも翌日なら1日後)
     * - 当日中:
     *    - <60秒: ○秒
     *    - <3600秒: ○分（切り捨て）
     *    - >=3600秒: ○時間（切り捨て）
     */
    fun formatRemaining(nowSec: Long, dueSec: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val remaining = dueSec - nowSec
        if (remaining <= 0) return "今すぐ"

        val nowDate = Instant.ofEpochSecond(nowSec).atZone(zoneId).toLocalDate()
        val dueDate = Instant.ofEpochSecond(dueSec).atZone(zoneId).toLocalDate()
        val dayDiff = ChronoUnit.DAYS.between(nowDate, dueDate)

        if (dayDiff >= 1) return "${dayDiff}日後"

        // 当日中の残り時間は秒/分/時間（切り捨て）
        return when {
            remaining < 60 -> "${remaining}秒"
            remaining < 3600 -> "${remaining / 60}分"
            else -> "${remaining / 3600}時間"
        }
    }
}

