package com.example.studylockapp.learning

class RendererFactory {
    companion object {
        fun getRenderer(mode: QuizMode): QuizRenderer {
            return when (mode) {
                QuizMode.JP_TO_EN,
                QuizMode.EN_TO_JP,
                QuizMode.LISTEN_EN,
                QuizMode.SYNONYM_PICK,
                QuizMode.ANTONYM_PICK -> ChoiceQuizRenderer()

                QuizMode.FILL_BLANK -> FillBlankRenderer()
                QuizMode.LISTEN_FILL_BLANK -> ListenFillBlankRenderer()
                QuizMode.SORT -> SortRenderer()
            }
        }
    }
}
