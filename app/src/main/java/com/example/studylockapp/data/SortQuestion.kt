package com.example.studylockapp.data

data class SortQuestion(
    val id: String,
    val grade: String,
    val unit: String,
    val japaneseText: String,
    val englishSentence: String
) {
    /**
     * 英語の文をスペースで区切って単語のリストに変換します。
     * 句読点は末尾の単語に含まれます。
     */
    val words: List<String> by lazy {
        englishSentence.split(" ")
    }
}