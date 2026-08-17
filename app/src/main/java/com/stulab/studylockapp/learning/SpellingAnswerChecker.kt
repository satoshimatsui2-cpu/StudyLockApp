package com.stulab.studylockapp.learning

import java.text.Normalizer
import java.util.*

object SpellingAnswerChecker {
    fun check(userInput: String, correctAnswer: String): Boolean {
        val normalizedInput = normalize(userInput)
        val normalizedCorrect = normalize(correctAnswer)
        return normalizedInput == normalizedCorrect
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ") // 連続空白の統一
            .replace('’', '\'') // ’を'へ統一
    }
}
