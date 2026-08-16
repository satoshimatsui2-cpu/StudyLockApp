package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity
import com.stulab.studylockapp.data.db.ChoiceMeaningEntity
import java.text.Normalizer
import java.util.*

/**
 * 学習の回答後レビューにおいて、英語選択肢の日本語意味を解決するクラス
 */
object ReviewMeaningResolver {

    /**
     * 英語表現の正規化（インポート・検索の双方で使用）
     */
    fun normalizeChoiceText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    /**
     * 意味の検索キー生成
     */
    fun generateLookupKey(normalizedText: String, quizMode: String?, sourceWordId: Long?): String {
        val mode = quizMode ?: "COMMON"
        val wordId = sourceWordId ?: 0L
        return "${normalizedText}_${mode}_$wordId"
    }

    /**
     * 複数の候補から、優先順位に従って1つの日本語意味を決定する
     * 
     * 優先順位:
     * 1. 英語＋QuizMode＋sourceWordIdの完全一致
     * 2. 英語＋QuizMode
     * 3. 英語のみの共通データ
     */
    fun resolveFromChoiceMeanings(
        normalizedText: String,
        quizMode: String,
        sourceWordId: Long,
        candidates: List<ChoiceMeaningEntity>
    ): String? {
        val fullMatch = candidates.find { 
            it.quizMode == quizMode && it.sourceWordId == sourceWordId 
        }
        if (fullMatch != null && fullMatch.japanese.isNotBlank()) return fullMatch.japanese

        val modeMatch = candidates.find { 
            it.quizMode == quizMode && (it.sourceWordId == null || it.sourceWordId == 0L)
        }
        if (modeMatch != null && modeMatch.japanese.isNotBlank()) return modeMatch.japanese

        val commonMatch = candidates.find { 
            (it.quizMode == null || it.quizMode.isBlank() || it.quizMode == "COMMON") && 
            (it.sourceWordId == null || it.sourceWordId == 0L)
        }
        return commonMatch?.japanese?.takeIf { it.isNotBlank() }
    }

    /**
     * 最終的な日本語意味を決定する（フォールバック考慮）
     */
    fun resolveFinalMeaning(
        isCorrect: Boolean,
        sourceWordJapanese: String?,
        wordEntityMeaning: String?,
        supplementaryMeaning: String?
    ): String? {
        // 1. 正解項目の場合は、出題元単語が保持している日本語意味
        if (isCorrect && !sourceWordJapanese.isNullOrBlank()) {
            return sourceWordJapanese
        }

        // 2 & 3. 既存の単語DBから取得した意味（wordId特定分または綴り検索分）
        if (!wordEntityMeaning.isNullOrBlank()) {
            return wordEntityMeaning
        }

        // 4. 補完DBの意味
        if (!supplementaryMeaning.isNullOrBlank()) {
            return supplementaryMeaning
        }

        return null
    }
}
