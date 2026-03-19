package com.example.studylockapp.learning

import android.content.Context
import com.example.studylockapp.LearningModes
import com.example.studylockapp.data.*
import com.example.studylockapp.nowEpochSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * 学習の回答結果をDBに登録し、ポイントを計算するUseCase
 */
class AnswerRegistrationUseCase(private val context: Context) {

    suspend fun execute(
        word: WordEntity,
        currentMode: String,
        isCorrect: Boolean,
        fromDontKnow: Boolean = false
    ): Pair<Int, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        
        val db = AppDatabase.getInstance(context)
        val settings = AppSettings(context)
        val progressDao = db.wordProgressDao()
        val pointManager = PointManager(context)
        val nowSec = nowEpochSec()

        val current = progressDao.getProgress(word.no, currentMode)
        val currentLevel = current?.level ?: 0

        val (newLevel, nextDueAtSec) = calculateNextDue(
            settings = settings,
            currentMode = currentMode,
            isCorrect = isCorrect,
            currentLevel = currentLevel,
            nowSec = nowSec,
            fromDontKnow = fromDontKnow
        )

        val basePoint = settings.getBasePoint(currentMode)
        var points = ProgressCalculator.calcPoint(isCorrect, currentLevel, basePoint)

        val userGradeStr = settings.currentLearningGrade
        val userGrade = gradeToRank(userGradeStr)
        val wordGrade = gradeToRank(word.grade)

        // 自分より易しい問題なら減額
        if (userGrade > 0 && wordGrade > 0) {
            val gradeDiff = userGrade - wordGrade
            points = when {
                gradeDiff == 1 -> points * settings.pointReductionOneGradeDown / 100
                gradeDiff >= 2 -> points * settings.pointReductionTwoGradesDown / 100
                else -> points
            }
        }

        val penaltyModes = setOf(
            LearningModes.TEST_FILL_BLANK,
            LearningModes.TEST_SORT,
            LearningModes.TEST_LISTEN_Q1,
            LearningModes.TEST_LISTEN_Q2
        )

        if (currentMode in penaltyModes && !isCorrect) {
            var potentialPoints = ProgressCalculator.calcPoint(true, currentLevel, basePoint)
            if (userGrade > 0 && wordGrade > 0) {
                val gradeDiff = userGrade - wordGrade
                potentialPoints = when {
                    gradeDiff == 1 -> potentialPoints * settings.pointReductionOneGradeDown / 100
                    gradeDiff >= 2 -> potentialPoints * settings.pointReductionTwoGradesDown / 100
                    else -> potentialPoints
                }
            }
            val penalty = (potentialPoints * 0.25).toInt()
            if (penalty > 0) {
                pointManager.add(-penalty)
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                        delta = -penalty
                    )
                )
            }
            points = -penalty
        } else {
            pointManager.add(points)
            if (points > 0) {
                db.pointHistoryDao().insert(
                    PointHistoryEntity(
                        mode = currentMode,
                        dateEpochDay = LocalDate.now(settings.getAppZoneId()).toEpochDay(),
                        delta = points
                    )
                )
            }
        }

        // 進捗更新
        progressDao.upsert(
            WordProgressEntity(
                wordId = word.no,
                mode = currentMode,
                level = newLevel,
                nextDueAtSec = nextDueAtSec,
                lastAnsweredAt = System.currentTimeMillis(),
                studyCount = (current?.studyCount ?: 0) + 1
            )
        )

        // 学習ログ挿入
        db.studyLogDao().insert(
            WordStudyLogEntity(
                wordId = word.no,
                mode = currentMode,
                learnedAt = System.currentTimeMillis()
            )
        )

        points to (currentLevel to newLevel)
    }

    private fun calculateNextDue(
        settings: AppSettings,
        currentMode: String,
        isCorrect: Boolean,
        currentLevel: Int,
        nowSec: Long,
        fromDontKnow: Boolean
    ): Pair<Int, Long> {
        val newLevel = if (isCorrect) currentLevel + 1 else maxOf(0, currentLevel - 2)
        val zone = settings.getAppZoneId()
        val isTestMode = currentMode in setOf(
            LearningModes.TEST_FILL_BLANK,
            LearningModes.TEST_SORT,
            LearningModes.TEST_LISTEN_Q1,
            LearningModes.TEST_LISTEN_Q2
        )
        val nextDaySec = Instant.ofEpochSecond(nowSec).atZone(zone)
            .toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond()

        if (!isCorrect) {
            return if (isTestMode) {
                newLevel to nextDaySec
            } else {
                val retrySec = if (fromDontKnow) settings.dontKnowRetrySec else settings.wrongRetrySec
                newLevel to (nowSec + retrySec)
            }
        }
        if (newLevel == 1) {
            return if (isTestMode) newLevel to nextDaySec else newLevel to (nowSec + settings.level1RetrySec)
        }
        val days = when (newLevel) {
            2 -> 1; 3 -> 3; 4 -> 7; 5 -> 14; 6 -> 30; 7 -> 60; else -> 90
        }
        val dueDate = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().plusDays(days.toLong())
        return newLevel to dueDate.atStartOfDay(zone).toEpochSecond()
    }

    /**
     * 後で GradeUtils に移動予定
     */
    private fun gradeToRank(g: String?): Int {
        if (g == null) return 0
        val key = g.replace("英検", "").replace("級", "").trim()
        return when (key) {
            "5" -> 1; "4" -> 2; "3" -> 3; "2.5" -> 4; "2" -> 5; "1.5" -> 6; "1" -> 7; else -> 0
        }
    }
}
