package com.example.studylockapp.learning

enum class QuizMode {
    JP_TO_EN,      // 日本語 -> 英語(4択)
    EN_TO_JP,      // 英語 -> 日本語(4択)
    LISTEN_EN,     // 音声 -> 英語(4択)
    FILL_BLANK,    // 穴埋め
    SORT;          // 並べ替え

    /**
     * 音声の重要度
     */
    enum class AudioImportance {
        NONE,      // 音声不要
        OPTIONAL,  // 推奨（自動再生設定に従う）
        REQUIRED   // 必須（リスニングなど）
    }

    fun getAudioImportance(): AudioImportance = when (this) {
        LISTEN_EN -> AudioImportance.REQUIRED
        EN_TO_JP -> AudioImportance.OPTIONAL
        else -> AudioImportance.NONE
    }
}
