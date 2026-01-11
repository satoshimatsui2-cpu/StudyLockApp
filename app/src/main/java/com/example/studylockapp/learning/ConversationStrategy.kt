package com.example.studylockapp.learning

import com.example.studylockapp.ListeningQuestion // dataパッケージから削除
import com.example.studylockapp.data.db.WordProgressDao // dataパッケージから削除
import com.example.studylockapp.data.WordProgressEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

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
            else -> {
                // 新規問題も復習期限問題もない場合、学習済みの問題から一番学習日が古いものを出す
                val allProgress = progressDao.getAllProgressForMode(modeKey)
                val oldestReviewId = allProgress.minByOrNull { it.lastAnsweredAt }?.wordId
                if (oldestReviewId != null) {
                    allQMap[oldestReviewId]
                } else {
                    questions.randomOrNull() // 学習進捗がない場合はランダム
                }
            }
        }

        if (nextQ == null) {
            return QuestionUiState.Empty // Should not happen if questions is not empty
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

        // --- DB更新処理 ---
        val nowSec = System.currentTimeMillis() / 1000L

        // 既存のメソッド名 getProgress を使用
        val currentProgress = progressDao.getProgress(q.id, modeKey)

        // 次のレベルと期日を計算
        val currentLevel = currentProgress?.level ?: 0
        val studyCount = (currentProgress?.studyCount ?: 0) + 1

        val (newLevel, nextDueAtSec) = calcNextDueAtSec(isCorrect, currentLevel, nowSec)

        val newEntity = WordProgressEntity(
            wordId = q.id,
            mode = modeKey,
            level = newLevel,
            nextDueAtSec = nextDueAtSec,
            lastAnsweredAt = System.currentTimeMillis(),
            studyCount = studyCount
        )

        progressDao.upsert(newEntity)
        // --- ここまで ---

        val title = if (isCorrect) "正解！" else "残念..."
        val explanation = q.explanation
        // 文字列テンプレートを使用して安全に改行を埋め込む
        val feedback = "$title\n$explanation"

        val points = if (isCorrect) 10 else 0

        return AnswerResult(isCorrect, feedback, points)
    }

    private fun calcNextDueAtSec(isCorrect: Boolean, currentLevel: Int, nowSec: Long): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else max(0, currentLevel - 2)
        val zone = ZoneId.systemDefault()

        if (!isCorrect) return newLevel to (nowSec + 60)

        if (newLevel == 1) return newLevel to (nowSec + 86400)

        val days = when (newLevel) {
            2 -> 1
            3 -> 3
            4 -> 7
            5 -> 14
            6 -> 30
            7 -> 60
            else -> 90
        }

        val baseDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
        val dueDate = baseDate.plusDays(days.toLong())
        val dueAtSec = dueDate.atStartOfDay(zone).toEpochSecond()

        return newLevel to dueAtSec
    }
}