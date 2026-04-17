package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity

/**
 * TSV内の choices_* 列を使用して選択肢を生成するクラス。
 */
class ChoiceGenerator {
    private val TAG = "ChoiceGenerator"

    /**
     * 各モードに合わせて選択肢を生成します。
     * 最終的な選択肢数はUI仕様に合わせ、正解1つ + 誤答3つの計4つで固定します。
     */
    fun generateChoices(word: WordEntity, mode: QuizMode): List<String> {
        val (correct, candidates) = when (mode) {
            QuizMode.EN_TO_JP -> word.japanese to word.choicesEnJa
            QuizMode.JP_TO_EN -> word.word to word.choicesJaEn
            QuizMode.LISTEN_EN -> word.word to word.choicesListening
            else -> return emptyList()
        }

        val result = mutableSetOf<String>()
        // 1. 正解を必ず含める
        result.add(correct)

        // 2. 対応する候補配列からランダムに選び、重複を除去しながら追加
        val filteredCandidates = candidates.filter { it != correct && it.isNotBlank() }.shuffled()
        
        filteredCandidates.forEach {
            if (result.size < 4) {
                result.add(it)
            }
        }

        // 3. 候補が不足している場合の警告ログ
        if (result.size < 4) {
            Log.w(TAG, "Insufficient choices for word '${word.word}' in mode $mode. Found only ${result.size} choices.")
        }

        // 4. 最後にシャッフルして返す
        return result.toList().shuffled()
    }
}
