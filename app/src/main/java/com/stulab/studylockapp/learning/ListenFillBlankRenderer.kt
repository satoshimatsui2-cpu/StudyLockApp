package com.stulab.studylockapp.learning

/**
 * 文脈つきリスニング穴埋めの描画担当。
 * 音声を聞きながら英文の空欄を埋めます（日本語は非表示）。
 */
class ListenFillBlankRenderer : QuizRenderer {
    override fun render(ui: QuizUiProvider, quiz: QuizData) {
        // FILL_BLANK と同様に文字サイズを 90% に設定
        ui.setQuestionBodyTextScale(0.9f)
        
        ui.showBasicQuiz(
            title = "音声を聞いて空欄に入る語を選んでください。",
            body = quiz.question, // 英文の穴埋め文のみ
            choices = quiz.choices
        )
    }
}
