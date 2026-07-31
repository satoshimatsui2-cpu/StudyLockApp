package com.stulab.studylockapp.worker

import android.content.Context
import androidx.work.*
import com.stulab.studylockapp.data.AppDatabase
import com.stulab.studylockapp.data.AppSettings
import com.stulab.studylockapp.data.notification.CharacterLines
import com.stulab.studylockapp.data.notification.NotificationContext
import com.stulab.studylockapp.data.notification.StudyCharacter
import com.stulab.studylockapp.learning.LearningEmptyState
import com.stulab.studylockapp.learning.QuizManager
import com.stulab.studylockapp.service.NotificationHelper
import com.stulab.studylockapp.ui.CharacterDisplayUtils
import java.util.concurrent.TimeUnit

class ReviewReadyWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        val quizManager = QuizManager(
            db.wordDao(),
            db.wordMasteryDao(),
            db.studyLogDao(),
            settings
        )

        // 1. 本当に復習できる問題があるか確認
        if (!quizManager.checkAvailabilityNow()) {
            // まだ学習できない場合
            val reason = quizManager.getEmptyStateReason()
            if (reason is LearningEmptyState.NoReviewAvailable) {
                // 次の時刻を取得して再予約
                val nextTime = quizManager.getNextReviewTime()
                if (nextTime != null) {
                    schedule(applicationContext, nextTime)
                }
            }
            return Result.success()
        }

        // 2. 目標達成済みなら通知しない
        val reason = quizManager.getEmptyStateReason()
        if (reason is LearningEmptyState.DailyGoalMet) {
            return Result.success()
        }

        // 3. 復習可能なら通知を出す
        val charId = settings.selectedCharacterId
        val character = StudyCharacter.fromId(charId)
        val userName = settings.userName ?: "きみ"
        
        val lineResult = CharacterLines.getLineWithEmotion(
            character,
            NotificationContext.REVIEW_READY,
            name = userName
        )

        val imageResId = CharacterDisplayUtils.getNotificationIconDrawable(
            applicationContext, charId, lineResult.emotion.id
        )

        NotificationHelper.showNotification(
            context = applicationContext,
            title = character.displayName,
            message = lineResult.text,
            largeIconResId = imageResId,
            notificationId = NOTIFICATION_ID
        )

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ReviewReadyNotification"
        private const val NOTIFICATION_ID = 1002

        fun schedule(context: Context, nextReviewTimeMillis: Long) {
            val now = System.currentTimeMillis()
            val delay = (nextReviewTimeMillis - now).coerceAtLeast(0)

            val request = OneTimeWorkRequestBuilder<ReviewReadyWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
