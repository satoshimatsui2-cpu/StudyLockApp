package com.example.studylockapp.learning

import com.example.studylockapp.data.WordEntity
import com.example.studylockapp.data.db.WordDao
import kotlin.math.min

/**
 * 各モードに最適な選択肢（distractor）を生成するクラス。
 * phonetic（発音記号）をブースターとして使い、音の似た誤答を優先します。
 */
class ChoiceGenerator(private val wordDao: WordDao) {

    suspend fun generateChoices(word: WordEntity, mode: QuizMode): List<String> {
        return when (mode) {
            QuizMode.EN_TO_JP -> generateJapaneseChoices(word)
            QuizMode.LISTEN_EN -> generateListeningChoices(word)
            else -> generateEnglishChoices(word)
        }
    }

    private suspend fun generateJapaneseChoices(word: WordEntity): List<String> {
        val candidates = mutableSetOf<String>()
        
        // 1. confusion の日本語訳を優先
        candidates.addAll(wordDao.getJapaneseByWords(word.confusion.filter { it.isNotBlank() }))
        
        // 2. 足りない分を補充（同品詞優先）
        if (candidates.size < 10) {
            val pos = word.pos?.takeIf { it.isNotBlank() }
            val extras = if (pos != null) {
                wordDao.getRandomJapaneseDistractorsByPos(word.japanese, word.grade, pos, 10)
            } else {
                wordDao.getRandomJapaneseDistractors(word.japanese, word.grade, 10)
            }
            candidates.addAll(extras)
        }
        
        // 3. 正解と似すぎている日本語をフィルタリング（完全一致と、トリム後の一致を除去）
        val filtered = candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != word.japanese.trim() }
            .distinct()

        return QuizLogic.createChoices(word.japanese, filtered)
    }

    private suspend fun generateListeningChoices(word: WordEntity): List<String> {
        val finalDistractors = mutableSetOf<String>()
        val pos = word.pos?.takeIf { it.isNotBlank() }
        
        // 1. confusion は無条件で優先（人間が間違えやすいと判定済みのため）
        finalDistractors.addAll(word.confusion.filter { it.isNotBlank() })

        // 2. phonetic ブースター: 同級・同品詞のプールから音が似ているものを探す
        if (finalDistractors.size < 5 && word.phonetic != null && word.type != "phrase" && word.type != "phrasal_verb") {
            val poolSpells = if (pos != null) {
                wordDao.getRandomDistractorsByPos(word.word, word.grade, pos, 40)
            } else {
                wordDao.getRandomDistractors(word.word, word.grade, 40)
            }
            
            val poolEntities = wordDao.getWordsBySpellings(poolSpells)
            val basePhonetic = normalizePhonetic(word.phonetic)

            // 単語の長さに応じて許容距離を変える相対ルール
            val maxDistance = when {
                basePhonetic.length <= 4 -> 1
                basePhonetic.length <= 7 -> 2
                else -> 3
            }

            val scoredCandidates = poolEntities
                .mapNotNull { entity ->
                    val targetPhonetic = entity.phonetic ?: return@mapNotNull null
                    val distance = calculateLevenshteinDistance(basePhonetic, normalizePhonetic(targetPhonetic))
                    entity.word to distance
                }
                .filter { it.second <= maxDistance }
                .sortedBy { it.second }
                .map { it.first }

            finalDistractors.addAll(scoredCandidates)
        }

        // 3. 最終補充（同品詞を優先維持）
        if (finalDistractors.size < 10) {
            val extras = if (pos != null) {
                wordDao.getRandomDistractorsByPos(word.word, word.grade, pos, 10)
            } else {
                wordDao.getRandomDistractors(word.word, word.grade, 10)
            }
            finalDistractors.addAll(extras)
        }

        return QuizLogic.createChoices(word.word, finalDistractors.toList())
    }

    private suspend fun generateEnglishChoices(word: WordEntity): List<String> {
        val distractors = mutableSetOf<String>()
        distractors.addAll(word.confusion.filter { it.isNotBlank() })
        
        val pos = word.pos?.takeIf { it.isNotBlank() }
        val extras = if (pos != null) {
            wordDao.getRandomDistractorsByPos(word.word, word.grade, pos, 10)
        } else {
            wordDao.getRandomDistractors(word.word, word.grade, 10)
        }
        distractors.addAll(extras)
        
        return QuizLogic.createChoices(word.word, distractors.toList())
    }

    private fun normalizePhonetic(p: String): String = p.replace(Regex("[/ˈˌː]"), "")

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
