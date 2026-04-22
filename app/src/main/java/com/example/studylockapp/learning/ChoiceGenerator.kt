package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity

/**
 * 選択肢を生成するクラス。
 */
class ChoiceGenerator {
    private val TAG = "ChoiceGenerator"

    fun generateChoices(word: WordEntity, mode: QuizMode): List<String> {
        val (correct, candidates, excluded) = when (mode) {
            QuizMode.EN_TO_JP -> {
                Triple(word.japanese.trim(), word.choicesEnJa, emptySet<String>())
            }
            QuizMode.JP_TO_EN,
            QuizMode.FILL_BLANK,
            QuizMode.LISTEN_FILL_BLANK,
            QuizMode.SYNONYM_PICK,
            QuizMode.ANTONYM_PICK -> {
                // 正解は常に元の英単語
                val w = word.word.trim()
                // 除外リスト: 全ての類義語 + 全ての対義語 (問題文に使用した語や他の候補を誤答に混ぜないため)
                val allRelated = (word.synonyms + word.antonyms)
                    .map { it.word.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                Triple(w, word.choicesJaEn, allRelated)
            }
            QuizMode.LISTEN_EN -> {
                Triple(word.word.trim(), word.choicesListening, emptySet<String>())
            }
            else -> return emptyList()
        }

        if (correct.isBlank()) return emptyList()

        val result = mutableSetOf<String>()
        // 1. 正解を必ず含める
        result.add(correct)

        // 2. 対応する候補配列からランダムに選び、重複や除外対象(excluded)を除去しながら追加
        val filteredCandidates = candidates
            .filter { candidate ->
                val c = candidate.trim().lowercase()
                // 正解と同じ語、除外リスト（類義語/対義語）に含まれる語、および空文字を除外
                c != correct.lowercase() && !excluded.contains(c) && candidate.trim().isNotBlank()
            }
            .shuffled()
        
        filteredCandidates.forEach {
            if (result.size < 4) {
                result.add(it.trim())
            }
        }

        return result.toList().shuffled()
    }
}
