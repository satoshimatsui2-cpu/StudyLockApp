package com.stulab.studylockapp.learning

import com.stulab.studylockapp.data.WordEntity
import java.util.UUID

/**
 * クイズの出題データ。
 */
data class QuizData(
    val id: String = UUID.randomUUID().toString(),
    val mode: QuizMode,
    val word: WordEntity, // 常に元の単語情報を保持
    val question: String,
    val choices: List<String>,
    val answer: String,
    
    // 拡張情報
    val promptRelatedWord: String? = null,
    val promptRelatedNote: String? = null,
    val relationType: String? = null,
    
    // 並び替えモード用：シャッフルされたトークン
    val sortTokens: List<String>? = null
)
