package com.example.studylockapp.learning

import androidx.annotation.StringRes

/**
 * Activityが提供するUI操作インターフェース
 * Rendererはこのインターフェース経由でのみUIを操作します。
 */
interface QuizUiProvider {
    fun showBasicQuiz(title: String, body: String, choices: List<String>)
    fun playAudio(text: String)
    fun getString(@StringRes resId: Int): String
}
