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
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        
        // 7時台または17時台以外はスキップ（WorkManagerのゆらぎを考慮）
        if (hour != 7 && hour != 17) return Result.success()

        val db = AppDatabase.getInstance(applicationContext)
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 既に目標達成している場合は通知しない
        val startedCount = db.wordMasteryDao().countStartedNewWordsToday(startOfDay)
        if (startedCount >= settings.dailyNewWordTarget) {
            Log.d(TAG, "Goal already met today. Skipping notification.")
            return Result.success()
        }

        val character = StudyCharacter.fromId(settings.selectedCharacterId)
        val streak = settings.dailyGoalStreak
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        val lastStudyStr = settings.lastStudyDate

        val notificationContext = if (hour == 7) {
            // 朝のメッセージ選択
            when {
                streak > 0 -> NotificationContext.MORNING_STREAK
                lastStudyStr == null -> NotificationContext.MORNING_NORMAL
                else -> {
                    val lastDate = sdf.parse(lastStudyStr)
                    val diffDays = (startOfDay - (lastDate?.time ?: 0)) / (24 * 60 * 60 * 1000)
                    if (diffDays <= 1) NotificationContext.MORNING_MISSED_1
                    else NotificationContext.MORNING_MISSED_2
                }
            }
        } else {
            // 夕方のメッセージ
            NotificationContext.EVENING_PENDING
        }

        val message = CharacterLines.getLine(character, notificationContext, streak)
        val title = "${character.displayName}からのメッセージ"

        NotificationHelper.showNotification(applicationContext, title, message)
        
        return Result.success()
    }
}
