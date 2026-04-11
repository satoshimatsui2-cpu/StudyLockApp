package com.example.studylockapp.learning

import com.example.studylockapp.R

/**
 * 4択形式のクイズ（JP_TO_EN, EN_TO_JP, LISTEN_EN）を統合して描画するRenderer
 */
class ChoiceQuizRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        val title = when (quiz.mode) {
            QuizMode.EN_TO_JP -> ui.getString(R.string.question_title_en_to_jp)
            QuizMode.LISTEN_EN -> ui.getString(R.string.question_title_listening)
            else -> ui.getString(R.string.question_title_meaning)
        }
        
        // LISTEN_EN の場合は専用のプレースホルダー（"タップして再生"など）を表示
        val body = if (quiz.mode == QuizMode.LISTEN_EN) {
            ui.getString(R.string.label_listen_placeholder)
        } else {
            quiz.question
        }
        
        ui.showBasicQuiz(title, body, quiz.choices)
    }
}
