package com.example.studylockapp.learning

class SortRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        ui.showBasicQuiz(
            title = "並び替え（準備中）",
            body = quiz.question,
            choices = quiz.choices
        )
    }
}
