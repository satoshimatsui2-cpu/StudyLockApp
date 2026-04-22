package com.example.studylockapp.learning

enum class QuizMode {
    JP_TO_EN,          // 日本語 -> 英語(4択)
    EN_TO_JP,          // 英語 -> 日本語(4択)
    LISTEN_EN,         // 音声単語 -> 英語(4択)
    FILL_BLANK,        // 文脈穴埋め(4択)
    LISTEN_FILL_BLANK, // 文脈リスニング穴埋め(4択)
    SYNONYM_PICK,      // 類義語選び(4択)
    ANTONYM_PICK,      // 対義語選び(4択)
    SENTENCE_SORT,     // 英文並び替え (新規)
    SORT;              // 汎用並べ替え(既存)

    /**
     * 音声の重要度
     */
    enum class AudioImportance {
        NONE,      // 音声不要
        OPTIONAL,  // 推奨（自動再生設定に従う）
        REQUIRED   // 必須（リスニングなど）
    }

    fun getAudioImportance(): AudioImportance = when (this) {
        LISTEN_EN, LISTEN_FILL_BLANK -> AudioImportance.REQUIRED
        EN_TO_JP -> AudioImportance.OPTIONAL
        SENTENCE_SORT -> AudioImportance.NONE // 出題時は読まない
        else -> AudioImportance.NONE
    }
}
