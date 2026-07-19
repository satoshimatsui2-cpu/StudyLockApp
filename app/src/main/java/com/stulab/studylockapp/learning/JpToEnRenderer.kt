package com.stulab.studylockapp.learning

class JpToEnRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        ui.showBasicQuiz("意味を選んでください", quiz.question, quiz.choices)
    }
}
