package com.stulab.studylockapp.learning

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Typeface
import com.stulab.studylockapp.R

/**
 * 4択形式のクイズ（JP_TO_EN, EN_TO_JP, LISTEN_EN, SYNONYM_PICK, ANTONYM_PICK）を描画するRenderer
 */
class ChoiceQuizRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        // 文字サイズをリセット
        ui.setQuestionBodyTextScale(1.0f)

        val title: CharSequence = when (quiz.mode) {
            QuizMode.JP_TO_EN -> ui.getProviderString(R.string.question_title_ja_to_en)
            QuizMode.EN_TO_JP -> ui.getProviderString(R.string.question_title_en_to_jp)
            QuizMode.LISTEN_EN -> ui.getProviderString(R.string.question_title_listening)
            QuizMode.SYNONYM_PICK -> highlightKeyword(ui, "この類義語を選んでください。", "類義語")
            QuizMode.ANTONYM_PICK -> highlightKeyword(ui, "この対義語を選んでください。", "対義語")
            else -> ui.getProviderString(R.string.question_title_meaning)
        }
        
        // LISTEN_EN の場合は専用のプレースホルダー（"タップして再生"など）を表示
        val body = if (quiz.mode == QuizMode.LISTEN_EN) {
            ui.getProviderString(R.string.label_listen_placeholder)
        } else {
            // SYNONYM_PICK / ANTONYM_PICK の場合も含め、本文は quiz.question (word.trim()) を表示
            quiz.question
        }
        
        ui.showBasicQuiz(title, body, quiz.choices)
    }

    private fun highlightKeyword(ui: QuizUiProvider, fullText: String, keyword: String): CharSequence {
        val startIndex = fullText.indexOf(keyword)
        if (startIndex == -1) return fullText
        
        val accentColor = ui.getProviderColor(R.color.mustard_dark)
        
        return SpannableStringBuilder(fullText).apply {
            // 太字
            setSpan(
                StyleSpan(Typeface.BOLD),
                startIndex,
                startIndex + keyword.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // 色 (マスタード)
            setSpan(
                ForegroundColorSpan(accentColor),
                startIndex,
                startIndex + keyword.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // 少しだけ大きく (1.1倍)
            setSpan(
                RelativeSizeSpan(1.1f),
                startIndex,
                startIndex + keyword.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
