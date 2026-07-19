package com.stulab.studylockapp.learning

import androidx.annotation.StringRes

/**
 * Activityが提供するUI操作インターフェース
 * Rendererはこのインターフェース経由でのみUIを操作します。
 */
interface QuizUiProvider {
    fun showBasicQuiz(title: String, body: String, choices: List<String>)
    fun playAudio(text: String)
    fun getString(@StringRes resId: Int): String
    
    /**
     * 問題本文の文字サイズ倍率を設定します。
     * FILL_BLANKなどで情報量が多い場合に縮小するために使用します。
     */
    fun setQuestionBodyTextScale(scale: Float)
}
