package com.example.studylockapp

import com.example.studylockapp.data.WordEntity

/**
 * 学習モードの定数定義
 */
object LearningModes {
    const val MEANING = "meaning"
    const val LISTENING = "listening"
    const val LISTENING_JP = "listening_jp"
    const val JA_TO_EN = "japanese_to_english"
    const val EN_EN_1 = "english_english_1"
    const val EN_EN_2 = "english_english_2"
    const val TEST_FILL_BLANK = "test_fill_blank"
    const val TEST_SORT = "test_sort"
    const val TEST_LISTEN_Q1 = "test_listen_q1"
    const val TEST_LISTEN_Q2 = "test_listen_q2" // Conversation Listening
}

/**
 * 従来の単語学習用のコンテキストデータ
 */
data class LegacyQuestionContext(
    val word: WordEntity,
    val title: String,
    val body: String,
    val options: List<String>,
    val correctIndex: Int,
    val shouldAutoPlay: Boolean,
    val audioText: String
)

/**
 * モードごとの集計データ
 */
data class ModeStats(
    val review: Int,
    val newCount: Int,
    val total: Int,
    val bronze: Int,
    val silver: Int,
    val gold: Int,
    val crystal: Int,
    val purple: Int
)
