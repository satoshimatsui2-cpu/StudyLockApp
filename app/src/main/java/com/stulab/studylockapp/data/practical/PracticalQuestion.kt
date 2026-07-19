package com.stulab.studylockapp.data.practical

/**
 * 実践テストの問題ドメインモデル。
 */
data class PracticalQuestion(
    val no: String,
    val grade: Int,
    val unit: String,
    val question: String, // 既存互換（穴埋め・並べ替え用）
    val ttsScript: String? = null, // リスニング用（読み上げ本文）
    val questionText: String? = null, // リスニング用（設問文：履歴・復習・表示用）
    val choices: List<String>,
    val correctOptionIndex: Int, // 1-based (TSV準拠)
    val explanation: String,
    val type: PracticalQuizMode
)
