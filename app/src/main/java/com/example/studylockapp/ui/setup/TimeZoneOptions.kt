package com.example.studylockapp.ui.settings

object TimeZoneOptions {

    const val LABEL_SYSTEM_DEFAULT = "端末の設定（systemDefault）"

    /**
     * Spinner表示用の候補（少数・主要都市のみ）
     * - 先頭: 端末の設定（null扱い）
     * - 以降: IANA ZoneId（ZoneId.of(...) で使える）
     */
    val displayList: List<String> = listOf(
        LABEL_SYSTEM_DEFAULT,

        // Asia
        "Asia/Tokyo",
        "Asia/Seoul",
        "Asia/Shanghai",
        "Asia/Singapore",
        "Asia/Bangkok",
        "Asia/Jakarta",
        "Asia/Kolkata",
        "Asia/Dubai",

        // Europe
        "Europe/London",
        "Europe/Paris",
        "Europe/Berlin",
        "Europe/Moscow",

        // North America
        "America/New_York",
        "America/Chicago",
        "America/Denver",
        "America/Los_Angeles",
        "America/Phoenix",
        "America/Toronto",
        "America/Vancouver",

        // Oceania
        "Australia/Sydney",
        "Australia/Perth",
        "Pacific/Auckland",

        // South America (代表)
        "America/Sao_Paulo"
    )

    fun toZoneIdOrNull(selected: String): String? {
        return if (selected == LABEL_SYSTEM_DEFAULT) null else selected
    }

    fun indexOfOrZero(timeZoneId: String?): Int {
        if (timeZoneId.isNullOrBlank()) return 0
        val idx = displayList.indexOf(timeZoneId)
        return if (idx >= 0) idx else 0
    }
}

