package com.example.studylockapp

import android.app.Application
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class StudyLockApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AdAudioManager.apply(this)

        // 期限切れの一時解放を掃除（epoch seconds）
        appScope.launch {
            val nowSec = Instant.now().epochSecond
            AppDatabase.getInstance(this@StudyLockApp)
                .appUnlockDao()
                .clearExpired(nowSec)
        }
    }
}