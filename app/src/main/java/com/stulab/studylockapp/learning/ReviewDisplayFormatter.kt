package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.RelatedWord

import com.stulab.studylockapp.data.WordEntity

/**
 * レビュー画面の表示テキストを整形するユーティリティ
 */
object ReviewDisplayFormatter {
    /**
     * 英語表現に日本語の意味を括弧付きで付加する。
     * 意味が空の場合は英語のみを返す。
     */
    fun formatWithJapaneseMeaning(english: String, japaneseMeaning: String?): String {
        val meaning = japaneseMeaning?.trim()
        return if (meaning.isNullOrEmpty()) {
            english
        } else {
            "$english（$meaning）"
        }
    }

    /**
     * 関連語リストの中から、表示語に一致するものの補足（note）を取得する。
     */
    fun findRelatedNote(displayedWord: String, relatedWords: List<RelatedWord>): String? {
        return relatedWords.firstOrNull {
            it.word.trim().equals(displayedWord.trim(), ignoreCase = true)
        }?.note?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 再生可能な選択肢レビュー用リストを生成する。
     * (JP_TO_EN, LISTEN_EN, LISTEN_FILL_BLANK, FILL_BLANK, SYNONYM_PICK, ANTONYM_PICK 等で使用)
     */
    fun createPlayableReviewChoices(
        choices: List<String>,
        resolvedMeanings: Map<String, String?>,
        correctAnswer: String,
        selectedAnswer: String
    ): List<PlayableReviewChoiceUiModel> {
        return choices.map { choice ->
            val isCorrect = choice.equals(correctAnswer, ignoreCase = true)
            val meaning = resolvedMeanings[choice]

            PlayableReviewChoiceUiModel(
                englishText = choice,
                japaneseMeaning = meaning,
                ttsText = choice,
                isCorrect = isCorrect,
                isSelected = choice.equals(selectedAnswer, ignoreCase = true)
            )
        }
    }

    /**
     * 英語→日本語問題の回答後レビュー用リストを生成する。
     */
    fun createEnToJpReviewChoices(
        choices: List<String>,
        correctAnswer: String,
        selectedAnswer: String,
        englishWord: String
    ): List<EnToJpReviewChoiceUiModel> {
        return choices.map { choice ->
            val isCorrect = choice.equals(correctAnswer, ignoreCase = true)
            EnToJpReviewChoiceUiModel(
                japaneseText = choice,
                englishText = null, // 正解項目にも英単語を付けない
                ttsText = null,
                isCorrect = isCorrect,
                isSelected = choice.equals(selectedAnswer, ignoreCase = true),
                isPlayable = false // 全カード再生不可
            )
        }
    }
}
