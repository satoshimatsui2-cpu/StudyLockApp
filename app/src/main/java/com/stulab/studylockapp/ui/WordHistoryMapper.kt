package com.stulab.studylockapp.ui

import android.content.Context
import com.stulab.studylockapp.GradeUtils
import com.stulab.studylockapp.R
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.WordHistoryItem
import com.stulab.studylockapp.data.WordHistoryQueryResult
import com.stulab.studylockapp.learning.QuizMode
import java.text.SimpleDateFormat
import java.util.*

/**
 * DBから取得した生データを表示用のデータクラスに変換するマッパー
 */
class WordHistoryMapper(private val context: Context) {

    private val settings = AppSettings(context)
    private val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    fun map(result: WordHistoryQueryResult): WordHistoryItem {
        val now = System.currentTimeMillis()

        // 級ラベル
        val gradeLabel = GradeUtils.toDisplay(result.grade.toString(), settings)

        // 習得度ティア
        val tierLabel = when {
            result.isLongTermMastered -> "長期マスター"
            result.isBasicMastered -> "基礎マスター"
            else -> "学習中"
        }

        // 復習ステータス
        val isReviewWaiting = result.nextReviewTime > 0 && result.nextReviewTime <= now
        val reviewStatusLabel = when {
            result.nextReviewTime <= 0 -> "学習済み"
            isReviewWaiting -> "復習待ち"
            else -> "次回: ${dateTimeFormat.format(Date(result.nextReviewTime))}"
        }

        // 最終学習日時
        val lastSeenLabel = if (result.lastSeen > 0) {
            "最終学習: ${dateTimeFormat.format(Date(result.lastSeen))}"
        } else {
            "最終学習: -"
        }

        // 成績
        val scoreLabel = "○ ${result.successCount}  × ${result.failureCount}"

        // 次回モードのラベル
        val modeLabel = try {
            val mode = QuizMode.valueOf(result.scheduledMode)
            when (mode) {
                QuizMode.EN_TO_JP -> "英日"
                QuizMode.JP_TO_EN -> "日英"
                QuizMode.LISTEN_EN -> "リスニング"
                QuizMode.FILL_BLANK -> "穴埋め"
                QuizMode.LISTEN_FILL_BLANK -> "リスニング穴埋め"
                QuizMode.SYNONYM_PICK -> "類義語"
                QuizMode.ANTONYM_PICK -> "対義語"
                QuizMode.SENTENCE_SORT -> "並び替え"
                else -> result.scheduledMode
            }
        } catch (e: Exception) {
            result.scheduledMode
        }

        return WordHistoryItem(
            id = result.no.toLong(),
            word = result.word,
            japanese = result.japanese,
            description = result.description,
            sentence = result.sentence,
            japaneseSentence = result.japaneseSentence,
            gradeLabel = gradeLabel,
            levelLabel = "LV ${result.level}",
            tierLabel = tierLabel,
            reviewStatusLabel = reviewStatusLabel,
            lastSeenLabel = lastSeenLabel,
            scoreLabel = scoreLabel,
            scheduledModeLabel = modeLabel,
            hasPendingListenReview = result.pendingListenReview,
            grade = result.grade,
            successCount = result.successCount,
            failureCount = result.failureCount,
            isNew = result.challengeCount <= 0,
            isReviewWaiting = isReviewWaiting
        )
    }
}
