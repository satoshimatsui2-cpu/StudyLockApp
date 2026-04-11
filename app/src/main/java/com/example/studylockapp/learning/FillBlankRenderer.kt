package com.example.studylockapp.learning

class FillBlankRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        ui.showBasicQuiz("（ ）に入るものを選んでください", quiz.question, quiz.choices)
    }
}
