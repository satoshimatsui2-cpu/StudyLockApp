package com.example.studylockapp

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.service.FriendNotificationManager
import com.example.studylockapp.worker.DailyReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit

class StudyLockApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AdAudioManager.apply(this)

        setupDailyReminder()
        FriendNotificationManager.startListening(this)

        // 期限切れの一時解放を掃除（epoch seconds）
        appScope.launch {
            val nowSec = Instant.now().epochSecond
            AppDatabase.getInstance(this@StudyLockApp)
                .appUnlockDao()
                .clearExpired(nowSec)
        }
    }

    private fun setupDailyReminder() {
        // 既存の周期実行を解除
        WorkManager.getInstance(this).cancelUniqueWork("DailyReminder")
        
        // 朝と夕方の通知を個別に予約
        scheduleNextReminder(7)
        scheduleNextReminder(17)
    }

    private fun scheduleNextReminder(hour: Int) {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        
        // 既に今日の指定時刻を過ぎている場合は明日に設定
        if (target.before(now)) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val delay = target.timeInMillis - now.timeInMillis
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("reminder_$hour")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "Reminder_$hour",
            ExistingWorkPolicy.KEEP, // すでに予約されている場合は維持
            request
        )
    }
}
