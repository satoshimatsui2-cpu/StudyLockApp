package com.example.studylockapp.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.notification.CharacterLines
import com.example.studylockapp.data.notification.NotificationContext
import com.example.studylockapp.data.notification.StudyCharacter
import com.example.studylockapp.service.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class DailyReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyReminderWorker"
    }

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        
        val isMorning = tags.contains("reminder_6_30")
        val isEvening = tags.contains("reminder_17_0")

        // 予約タグがない場合は何もしない
        if (!isMorning && !isEvening) return Result.success()

        val db = AppDatabase.getInstance(applicationContext)
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 既に目標達成している場合は通知しない (新規と復習の両方が0の場合)
        val startedCount = db.wordMasteryDao().countStartedNewWordsToday(startOfDay)
        val target = settings.dailyNewWordTarget

        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        
        val currentGrade = settings.safeLearningGrade.toIntOrNull() ?: 3
        val includeOthers = if (settings.includeOtherGrades) 1 else 0
        val isSilent = if (settings.silentMode == com.example.studylockapp.data.SilentMode.ON) 1 else 0
        
        val remainingReviewsToday = db.wordMasteryDao().countRemainingReviewsAvailable(
            now = endOfDay,
            currentGrade = currentGrade,
            includeOtherGrades = includeOthers,
            isSilentMode = isSilent
        )

        val newRemaining = (target - startedCount).coerceAtLeast(0)
        
        // 朝の通知は未達でも達成済みでも送る。
        // 夕方の通知は未達の場合、または達成済みお祝いとして送る。
        // つまり、このガード条件（何もしない）は削除または緩和する。
        // if (newRemaining == 0 && remainingReviewsToday == 0) { ... } 
        
        val isGoalMet = (newRemaining == 0 && remainingReviewsToday == 0)

        val character = StudyCharacter.fromId(settings.selectedCharacterId)
        val streak = settings.dailyGoalStreak
        val userName = settings.userName ?: "友達"
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        val lastStudyStr = settings.lastStudyDate

        val notificationContext = if (isMorning) {
            // 朝のメッセージ選択
            when {
                lastStudyStr == null -> NotificationContext.MORNING_NORMAL
                else -> {
                    val lastDate = sdf.parse(lastStudyStr)
                    val diffDays = (startOfDay - (lastDate?.time ?: 0)) / (24 * 60 * 60 * 1000L)
                    
                    // 前日または今日既に勉強している場合は通常メッセージ（継続日数は言及しない）
                    if (diffDays <= 1) NotificationContext.MORNING_NORMAL
                    // 1日以上空いている場合はサボり指摘
                    else if (diffDays <= 2) NotificationContext.MORNING_MISSED_1
                    else NotificationContext.MORNING_MISSED_2
                }
            }
        } else {
            // 夕方のメッセージ
            if (isGoalMet) NotificationContext.GOAL_COMPLETED
            else NotificationContext.EVENING_PENDING
        }

        val message = CharacterLines.getLine(character, notificationContext, streak, name = userName)
        val title = ""

        // 感情に連動したミニ画像IDを取得
        val emotion = CharacterLines.getEmotionForContext(character, notificationContext)
        var imageResId = applicationContext.resources.getIdentifier(
            "mini_${character.id}_${emotion.id}", "drawable", applicationContext.packageName
        )
        
        // ミニ画像がない場合は、通常のキャラ画像で代用
        if (imageResId == 0) {
            imageResId = applicationContext.resources.getIdentifier(
                "char_${character.id}", "drawable", applicationContext.packageName
            )
        }

        try {
            NotificationHelper.showNotification(applicationContext, title, message, imageResId)
        } catch (e: Exception) {
            // 通知表示自体のエラーは無視して次回の予約へ進む
        }
        
        // 次回の実行を予約
        if (isMorning) scheduleNext(6, 30)
        if (isEvening) scheduleNext(17, 0)

        return Result.success()
    }

    private fun scheduleNext(hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val delay = target.timeInMillis - now.timeInMillis
        val request = androidx.work.OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("reminder_${hour}_${minute}")
            .build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "Reminder_${hour}_${minute}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
