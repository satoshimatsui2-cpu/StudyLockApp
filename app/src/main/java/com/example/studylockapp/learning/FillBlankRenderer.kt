package com.example.studylockapp.learning

/**
 * 穴埋め問題の描画担当。
 */
class FillBlankRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        // 情報量が多いため、文字サイズを70%に縮小
        ui.setQuestionBodyTextScale(0.9f)

        ui.showBasicQuiz(
            title = "日本語に合う単語を選んでください。",
            body = quiz.question,
            choices = quiz.choices
        )
    }
}
