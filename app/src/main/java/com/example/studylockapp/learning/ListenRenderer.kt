package com.example.studylockapp.learning

class ListenRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        ui.showBasicQuiz("音声を聞いて選んでください", "???", quiz.choices)
    }
}
