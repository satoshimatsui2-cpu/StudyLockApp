package com.example.studylockapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppDatabase
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

        // 期限切れの一時解放を掃除（epoch seconds）
        appScope.launch {
            val nowSec = Instant.now().epochSecond
            AppDatabase.getInstance(this@StudyLockApp)
                .appUnlockDao()
                .clearExpired(nowSec)
        }
    }

    private fun setupDailyReminder() {
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
