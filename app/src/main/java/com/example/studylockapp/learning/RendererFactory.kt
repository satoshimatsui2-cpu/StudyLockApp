package com.example.studylockapp.learning

class RendererFactory {
    companion object {
        fun getRenderer(mode: QuizMode): QuizRenderer {
            return when (mode) {
                QuizMode.JP_TO_EN,
                QuizMode.EN_TO_JP,
                QuizMode.LISTEN_EN -> ChoiceQuizRenderer() // 4択系は統合
                QuizMode.FILL_BLANK -> FillBlankRenderer()
                QuizMode.SORT -> SortRenderer()
            }
        }
    }
}
