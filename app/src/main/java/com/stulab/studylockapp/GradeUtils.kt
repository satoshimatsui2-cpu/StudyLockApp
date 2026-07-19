package com.stulab.studylockapp

/**
 * 級（Grade）に関する表示変換を統一するユーティリティ
 */
object GradeUtils {

    /**
     * 内部値（"1"〜"7"）を表示用（"5級"〜"1級"）に変換
     * 1:5級, 2:4級, 3:3級, 4:準2級, 5:2級, 6:準1級, 7:1級
     * マッピング外は "不明" を返し、誤表示を防止する。
     */
    fun toDisplay(grade: String?): String {
        return when (grade?.trim()) {
            "1" -> "5級"
            "2" -> "4級"
            "3" -> "3級"
            "4" -> "準2級"
            "5" -> "2級"
            "6" -> "準1級"
            "7" -> "1級"
            else -> "不明"
        }
    }

    /**
     * 文字列の級ランクを数値に変換（1〜7）
     */
    fun toRank(grade: String?): Int {
        return normalize(grade).toIntOrNull() ?: 3
    }

    /**
     * 内部値を 1〜7 に正規化する。
     * 表示名（"5級"など）が渡された場合も内部値（"1"〜"7"）に変換する。
     */
    fun normalize(grade: String?): String {
        val value = grade?.trim() ?: ""
        
        // すでに内部ランク値(1-7)である場合
        val rank = value.toIntOrNull()
        if (rank != null && rank in 1..7) {
            return rank.toString()
        }

        // 表示名から内部値(1-7)への変換
        return when {
            value.contains("準2") -> "4"
            value.contains("準1") -> "6"
            value.contains("5") -> "1"
            value.contains("4") -> "2"
            value.contains("3") -> "3"
            value.contains("2") -> "5"
            value.contains("1") -> "7"
            else -> "3"
        }
    }
}
