package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.db.WordDao
import kotlin.math.abs
import kotlin.math.min

/**
 * 各モードに最適な選択肢（distractor）を生成するクラス。
 */
class ChoiceGenerator(private val wordDao: WordDao) {

    suspend fun generateChoices(word: WordEntity, mode: QuizMode): List<String> {
        return when (mode) {
            QuizMode.EN_TO_JP -> generateJapaneseChoices(word)
            QuizMode.LISTEN_EN -> generateListeningChoices(word)
            else -> generateEnglishChoices(word)
        }
    }

    /**
     * 英語 -> 日本語選択肢 (EN_TO_JP)
     * 日本語の完全重複を避け、品詞とタイプを揃えます。
     */
    private suspend fun generateJapaneseChoices(word: WordEntity): List<String> {
        val pos = word.pos?.takeIf { it.isNotBlank() }
        val type = word.type
        val candidates = mutableSetOf<String>()

        // 1. confusion 単語から日本語訳を取得
        val confusionWords = word.confusion.filter { it.isNotBlank() }
        val confusionEntities = wordDao.getWordsBySpellings(confusionWords)
        
        // confusion でも type は合わせる。pos があるなら pos も合わせる。
        candidates.addAll(
            confusionEntities.filter { entity ->
                entity.type == type && (pos == null || entity.pos == pos)
            }.map { it.japanese }
        )
        
        // 2. 候補プールを作成（同級・同タイプ・できれば同品詞）
        if (candidates.size < 15) {
            val extras = if (pos != null) {
                wordDao.getRandomJapaneseDistractorsByPosAndType(word.japanese, word.grade, pos, type, 20)
            } else {
                wordDao.getRandomJapaneseDistractorsByType(word.japanese, word.grade, type, 20)
            }
            candidates.addAll(extras)
        }
        
        // 3. 日本語のクレンジング（完全一致除外と重複除去）
        val correctTrimmed = word.japanese.trim()
        val filtered = candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != correctTrimmed }
            .distinct()

        return QuizLogic.createChoices(word.japanese, filtered)
    }

    /**
     * 日本語 -> 英語選択肢 (JP_TO_EN)
     * 綴りよりも、品詞と形式の一致を最優先して消去法を封じます。
     */
    private suspend fun generateEnglishChoices(word: WordEntity): List<String> {
        val targetSpell = word.word
        val pos = word.pos?.takeIf { it.isNotBlank() }
        val type = word.type
        val distractors = mutableSetOf<String>()
        
        // 1. confusion を優先 (type 一致、できれば pos も一致)
        val confusionEntities = wordDao.getWordsBySpellings(word.confusion.filter { it.isNotBlank() })
        distractors.addAll(
            confusionEntities.filter { entity ->
                entity.type == type && (pos == null || entity.pos == pos)
            }.map { it.word }
        )
        
        // 2. 同品詞・同タイプの単語から補充
        if (distractors.size < 15) {
            val extras = if (pos != null) {
                wordDao.getRandomDistractorsByPosAndType(targetSpell, word.grade, pos, type, 20)
            } else {
                wordDao.getRandomDistractorsByType(targetSpell, word.grade, type, 20)
            }
            distractors.addAll(extras)
        }
        
        // 3. 最終補充（タイプを合わせ、文字数が近いものを優先）
        if (distractors.size < 10) {
            val minLen = (targetSpell.length - 3).coerceAtLeast(1)
            val maxLen = targetSpell.length + 3
            distractors.addAll(wordDao.getWordsByLengthRangeAndType(targetSpell, word.grade, type, minLen, maxLen, 10))
        }

        return QuizLogic.createChoices(targetSpell, distractors.toList())
    }

    /**
     * リスニング用選択肢 (LISTEN_EN)
     */
    private suspend fun generateListeningChoices(word: WordEntity): List<String> {
        val targetWord = word.word
        val targetSpell = targetWord.lowercase()
        val grade = word.grade
        val pool = mutableSetOf<String>()

        if (targetWord.length >= 2) {
            val matches = wordDao.getWordsByPrefix(targetWord, grade, targetWord.take(2), 40)
            pool.addAll(matches.filter { isQualifiedListeningCandidate(targetWord, it) })
        }
        if (pool.size < 20) {
            val matches = wordDao.getWordsByPrefix(targetWord, grade, targetWord.take(1), 40)
            pool.addAll(matches.filter { isQualifiedListeningCandidate(targetWord, it) })
        }
        if (pool.size < 30) {
            pool.addAll(
                wordDao.getWordsByLengthRange(targetWord, grade, targetWord.length - 1, targetWord.length + 1, 40)
                    .filter { isQualifiedListeningCandidate(targetWord, it) }
            )
        }

        val poolSpells = pool.filter { it.lowercase() != targetSpell }.toList()
        val poolEntities = wordDao.getWordsBySpellings(poolSpells)
        val basePhonetic = normalizePhonetic(word.phonetic ?: "")

        val scored = poolEntities.map { candidate ->
            candidate.word to calculateListeningScore(word, basePhonetic, candidate)
        }.sortedByDescending { it.second }

        val bestDistractors = scored.take(3).map { it.first }.toMutableList()
        
        if (bestDistractors.size < 3) {
            val extras = wordDao.getRandomDistractors(targetWord, grade, 10)
            bestDistractors.addAll(extras.filter { it.lowercase() != targetSpell })
        }

        return QuizLogic.createChoices(targetWord, bestDistractors.take(3))
    }

    private fun isQualifiedListeningCandidate(target: String, candidate: String): Boolean {
        val t = target.lowercase()
        val c = candidate.lowercase()
        if (t == c) return false
        if (c.contains(" ")) return false
        if (t.length <= 4) {
            if (c.take(1) != t.take(1)) return false
            if (abs(c.length - t.length) > 1) return false
        }
        return true
    }

    private fun calculateListeningScore(target: WordEntity, basePhonetic: String, candidate: WordEntity): Int {
        var score = 0
        val targetSpell = target.word.lowercase()
        val candSpell = candidate.word.lowercase()
        val candPhonetic = normalizePhonetic(candidate.phonetic ?: "")

        if (candSpell.take(2) == targetSpell.take(2)) score += 40
        else if (candSpell.take(1) == targetSpell.take(1)) score += 20
        if (abs(candSpell.length - targetSpell.length) <= 1) score += 10

        if (basePhonetic.isNotEmpty() && candPhonetic.isNotEmpty() && target.type == "word" && candidate.type == "word") {
            val distance = calculateLevenshteinDistance(basePhonetic, candPhonetic)
            if (distance <= (if (basePhonetic.length <= 4) 1 else 2)) score += 30
            if (candPhonetic.take(2) == basePhonetic.take(2)) score += 50
            if (candPhonetic.takeLast(2) == basePhonetic.takeLast(2)) score += 30
            
            val confusablePairs = listOf("lr", "rl", "bv", "vb", "sθ", "θs", "fh", "hf")
            for (pair in confusablePairs) {
                if (basePhonetic.contains(pair[0]) && candPhonetic.contains(pair[1])) {
                    score += 20; break
                }
            }
        }

        val tPos = target.pos?.takeIf { it.isNotBlank() }
        if (tPos != null && tPos == candidate.pos) score += 15
        if (target.confusion.contains(candidate.word)) score += 5

        return score
    }

    private fun normalizePhonetic(p: String): String = p.replace(Regex("[/ˈˌː .,\\-]"), "").lowercase()

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
