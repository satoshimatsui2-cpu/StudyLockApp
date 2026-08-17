package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity

object SpellingEligibilityChecker {
    fun isEligible(word: WordEntity): Boolean {
        // - wordIdが存在する
        // - 英語綴りが空でない
        // - 日本語意味が空でない
        if (word.no <= 0 || word.word.isBlank() || word.japanese.isBlank()) {
            return false
        }

        // - 英字を中心とした単語
        // - 一般的なアポストロフィまたはハイフンを含む単語
        // - 除外するもの：~を含む、スラッシュを含む、記号だけのデータ
        val spelling = word.word.trim()
        if (spelling.contains("~") || spelling.contains("/")) {
            return false
        }

        // 基本的に英数字、空白、ハイフン、アポストロフィ、ピリオド(Mr.など)を許可
        val allowedChars = spelling.all { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '\'' || it == '.' }
        if (!allowedChars) {
            return false
        }

        // 少なくとも1文字は英字を含むべき
        if (spelling.none { it.isLetter() }) {
            return false
        }

        // 長すぎるフレーズは除外（例: 5語以上）
        if (spelling.split(Regex("\\s+")).size >= 5) {
            return false
        }

        return true
    }
}
