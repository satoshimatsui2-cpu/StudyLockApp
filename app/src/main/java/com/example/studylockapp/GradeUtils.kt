package com.example.studylockapp

/**
 * 級（Grade）に関する文字列操作とランク変換を統一するユーティリティ
 */
object GradeUtils {

    /**
     * 表記を正規化（数値文字列のみにする）
     * 例: "5級" -> "5", "準2級" -> "2.5", "5" -> "5"
     */
    fun normalize(grade: String?): String {
        if (grade.isNullOrBlank() || grade == "未設定") return ""

        return grade
            .replace("英検", "")
            .replace("準1級", "1.5")
            .replace("準2級", "2.5")
            .replace("準1", "1.5")
            .replace("準2", "2.5")
            .replace("級", "")
            .trim()
    }

    /**
     * 級を比較用のランク（数値）に変換
     * 5級(1) < 4級(2) < 3級(3) < 準2級(4) < 2級(5) < 準1級(6) < 1級(7)
     */
    fun toRank(grade: String?): Int {
        val key = normalize(grade)
        return when (key) {
            "5" -> 1
            "4" -> 2
            "3" -> 3
            "2.5" -> 4
            "2" -> 5
            "1.5" -> 6
            "1" -> 7
            else -> 0
        }
    }

    /**
     * 内部値（"5", "2.5"）を表示用（"5級", "準2級"）に変換
     */
    fun toDisplay(grade: String?): String {
        val key = normalize(grade)
        return when (key) {
            "2.5" -> "準2級"
            "1.5" -> "準1級"
            "" -> "未設定"
            else -> "${key}級"
        }
    }
}
