package com.stulab.studylockapp

/**
 * ランク数値（1〜7）を表示用の級名に変換するフォーマッター
 */
object GradeLabelFormatter {

    /**
     * 数値ランクを級名に変換する
     * 1 -> 5級, 2 -> 4級, 3 -> 3級, 4 -> 準2級, 5 -> 2級, 6 -> 準1級, 7 -> 1級
     */
    fun format(rank: Int): String {
        return when (rank) {
            1 -> "5級"
            2 -> "4級"
            3 -> "3級"
            4 -> "準2級"
            5 -> "2級"
            6 -> "準1級"
            7 -> "1級"
            else -> "未設定"
        }
    }
}
