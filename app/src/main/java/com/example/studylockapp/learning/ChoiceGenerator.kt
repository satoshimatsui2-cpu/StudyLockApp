package com.example.studylockapp.learning

import android.util.Log
import com.example.studylockapp.data.WordEntity

/**
 * 選択肢を生成するクラス。
 * 新モード SYNONYM_PICK / ANTONYM_PICK に対応し、複数正解を防止します。
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
            QuizMode.LISTEN_FILL_BLANK -> {
                Triple(word.word.trim(), word.choicesJaEn, emptySet<String>())
            }
            QuizMode.LISTEN_EN -> {
                Triple(word.word.trim(), word.choicesListening, emptySet<String>())
            }
            QuizMode.SYNONYM_PICK -> {
                // 正解を1つ選ぶ (最初の候補を採用)
                val s = word.synonyms.firstOrNull()?.word?.trim() ?: ""
                // 他の類義語候補を誤答に混ぜないために除外リストを作成
                val others = word.synonyms.map { it.word.trim().lowercase() }.toSet()
                Triple(s, word.choicesJaEn, others)
            }
            QuizMode.ANTONYM_PICK -> {
                // 正解を1つ選ぶ (最初の候補を採用)
                val a = word.antonyms.firstOrNull()?.word?.trim() ?: ""
                // 他の対義語候補を除外リストに含める
                val others = word.antonyms.map { it.word.trim().lowercase() }.toSet()
                Triple(a, word.choicesJaEn, others)
            }
            else -> return emptyList()
        }

        if (correct.isBlank()) return emptyList()

        val result = mutableSetOf<String>()
        // 1. 正解を必ず含める
        result.add(correct)

        // 2. 対応する候補配列からランダムに選び、重複や正解候補の別案(excluded)を除去しながら追加
        val filteredCandidates = candidates
            .filter { candidate ->
                val c = candidate.trim().lowercase()
                c != correct.lowercase() && !excluded.contains(c) && candidate.isNotBlank()
            }
            .shuffled()
        
        filteredCandidates.forEach {
            if (result.size < 4) {
                result.add(it.trim())
            }
        }

        // 3. 最後にシャッフルして返す
        return result.toList().shuffled()
    }
}
