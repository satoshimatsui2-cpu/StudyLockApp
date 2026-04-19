package com.example.studylockapp.learning

/**
 * 穴埋め問題の描画担当。
 */
class FillBlankRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        ui.showBasicQuiz(
            title = "日本語に合う単語を選んでください。",
            body = quiz.question,
            choices = quiz.choices
        )
    }
}
