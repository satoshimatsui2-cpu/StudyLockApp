package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity

/**
 * TSV内の choices_* 列を使用して選択肢を生成するクラス。
 */
class ChoiceGenerator {
    private val TAG = "ChoiceGenerator"

    fun generateChoices(word: WordEntity, mode: QuizMode): List<String> {
        val (correct, candidates) = when (mode) {
            QuizMode.EN_TO_JP -> word.japanese to word.choicesEnJa
            QuizMode.JP_TO_EN -> word.word to word.choicesJaEn
            QuizMode.LISTEN_EN -> word.word to word.choicesListening
            QuizMode.FILL_BLANK -> word.word to word.choicesJaEn // 穴埋めも単語想起なので ja_en を使用
            else -> return emptyList()
        }

        val result = mutableSetOf<String>()
        result.add(correct)

        val filteredCandidates = candidates.filter { it != correct && it.isNotBlank() }.shuffled()
        filteredCandidates.forEach {
            if (result.size < 4) {
                result.add(it)
            }
        }

        if (result.size < 4) {
            Log.w(TAG, "Insufficient choices for word '${word.word}' in mode $mode.")
        }

        return result.toList().shuffled()
    }
}
