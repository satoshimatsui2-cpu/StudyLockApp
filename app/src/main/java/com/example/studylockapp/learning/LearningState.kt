package com.example.studylockapp.learning

/**
 * UIの表示状態を定義
 */
sealed class QuestionUiState {
    object Loading : QuestionUiState()
    object Empty : QuestionUiState()
    
    // 会話モード用
    data class Conversation(
        val script: String,
        val question: String,
        val choices: List<String>,
        val correctIndex: Int 
    ) : QuestionUiState()

    // 従来の選択問題用
    data class MultipleChoice(
        val questionTitle: String,
        val questionBody: String,
        val choices: List<String>,
        val correctIndex: Int
    ) : QuestionUiState()
}

/**
 * 判定結果データ
 */
data class AnswerResult(
    val isCorrect: Boolean,
    val feedback: String,
    val earnedPoints: Int
)

/**
 * 各モードのロジック共通インターフェース
 */
interface LearningModeStrategy {
    val modeKey: String // この行が重要です！
    suspend fun createQuestion(): QuestionUiState
    suspend fun judgeAnswer(userAnswer: Any): AnswerResult
}