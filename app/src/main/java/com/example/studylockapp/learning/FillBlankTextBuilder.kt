package com.example.studylockapp.learning

import java.util.regex.Pattern

/**
 * 穴埋め文を生成するヘルパー。
 * 単語の複数形などの表記ゆれも考慮する。
 */
object FillBlankTextBuilder {
    private const val BLANK_MARKER = "＿＿＿"

    /**
     * sentence 内の word (またはその派生形) を穴埋めマーカーに置換する。
     * 見つからない場合は null を返す。
     */
    fun build(sentence: String, word: String): String? {
        val s = sentence.trim()
        val w = word.trim()
        if (s.isBlank() || w.isBlank()) return null

        val variations = mutableListOf<String>()
        variations.add(w)
        variations.add("${w}s")
        variations.add("${w}es")
        
        if (w.endsWith("y", ignoreCase = true) && w.length > 1) {
            variations.add(w.substring(0, w.length - 1) + "ies")
        }

        // 重複を除去し、長いものから順に試行する（例: hands を hand より先に探す）
        val sortedVariations = variations.distinct().sortedByDescending { it.length }

        for (v in sortedVariations) {
            val escapedV = Pattern.quote(v).replace(" ", "\\s+")
            val pattern = Pattern.compile("\\b$escapedV\\b", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(s)
            if (matcher.find()) {
                return matcher.replaceFirst(BLANK_MARKER)
            }
        }

        return null
    }
}
