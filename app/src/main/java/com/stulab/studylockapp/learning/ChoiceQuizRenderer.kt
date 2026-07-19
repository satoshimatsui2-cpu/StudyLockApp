package com.stulab.studylockapp.learning

import com.stulab.studylockapp.R

/**
 * 4択形式のクイズ（JP_TO_EN, EN_TO_JP, LISTEN_EN, SYNONYM_PICK, ANTONYM_PICK）を描画するRenderer
 */
class ChoiceQuizRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        // 文字サイズをリセット
        ui.setQuestionBodyTextScale(1.0f)

        val title = when (quiz.mode) {
            QuizMode.JP_TO_EN -> ui.getString(R.string.question_title_ja_to_en)
            QuizMode.EN_TO_JP -> ui.getString(R.string.question_title_en_to_jp)
            QuizMode.LISTEN_EN -> ui.getString(R.string.question_title_listening)
            QuizMode.SYNONYM_PICK -> "この類義語を選んでください。"
            QuizMode.ANTONYM_PICK -> "この対義語を選んでください。"
            else -> ui.getString(R.string.question_title_meaning)
        }
        
        // LISTEN_EN の場合は専用のプレースホルダー（"タップして再生"など）を表示
        val body = if (quiz.mode == QuizMode.LISTEN_EN) {
            ui.getString(R.string.label_listen_placeholder)
        } else {
            // SYNONYM_PICK / ANTONYM_PICK の場合も含め、本文は quiz.question (word.trim()) を表示
            quiz.question
        }
        
        ui.showBasicQuiz(title, body, quiz.choices)
    }
}
