package com.example.studylockapp.learning

import com.example.studylockapp.ListeningQuestion
import com.example.studylockapp.data.db.WordProgressDao

class ConversationStrategy(
    private val questions: List<ListeningQuestion>,
    private val progressDao: WordProgressDao,
    override val modeKey: String = "test_listen_q2"
) : LearningModeStrategy {

    private var currentQuestion: ListeningQuestion? = null

    override suspend fun createQuestion(): QuestionUiState {
        if (questions.isEmpty()) return QuestionUiState.Empty

        val nowSec = System.currentTimeMillis() / 1000L
        val allQMap = questions.associateBy { it.id }
        
        // 復習優先ロジック
        val dueIds = progressDao.getDueWordIdsOrdered(modeKey, nowSec)
        val dueQuestions = dueIds.mapNotNull { allQMap[it] }
        val progressedIds = progressDao.getProgressIds(modeKey).toSet()
        val newQuestions = questions.filter { it.id !in progressedIds }

        val nextQ = when {
            dueQuestions.isNotEmpty() -> dueQuestions.first()
            newQuestions.isNotEmpty() -> newQuestions.random()
            else -> questions.random()
        }
        
        currentQuestion = nextQ

        return QuestionUiState.Conversation(
            script = nextQ.script,
            question = nextQ.question,
            choices = nextQ.options,
            correctIndex = nextQ.correctIndex
        )
    }

    override suspend fun judgeAnswer(userAnswer: Any): AnswerResult {
        val selectedIndex = userAnswer as? Int ?: -1
        val q = currentQuestion
        
        if (q == null) {
            return AnswerResult(false, "Error", 0)
        }

        val isCorrect = (selectedIndex == q.correctIndex)
        
        val title = if (isCorrect) "正解！" else "残念..."
        val explanation = q.explanation
        val feedback = title + "\n" + explanation

        val points = if (isCorrect) 10 else 0 

        return AnswerResult(isCorrect, feedback, points)
    }
}