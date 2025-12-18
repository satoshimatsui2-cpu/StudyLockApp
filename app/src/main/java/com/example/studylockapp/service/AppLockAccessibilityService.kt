package com.example.studylockapp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

class AppLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var settings: AppSettings

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = AppSettings(this)
        Log.d("AppLockSvc", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // マスターOFFなら何もしない
        if (!::settings.isInitialized || !settings.isAppLockEnabled()) return

        // 自アプリはスキップ
        if (pkg == packageName) return

        Log.d("AppLockSvc", "Event pkg=$pkg")

        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@AppLockAccessibilityService)
            val locked = db.lockedAppDao().get(pkg)
            if (locked?.isLocked != true) {
                Log.d("AppLockSvc", "pkg=$pkg not locked or null")
                return@launch
            }

            // 一時解放チェック（未実装なら常にnull）
            val unlock = db.appUnlockDao().get(pkg)
            val nowSec = Instant.now().epochSecond
            if (unlock != null && unlock.unlockedUntilSec > nowSec) {
                Log.d("AppLockSvc", "pkg=$pkg unlocked until ${unlock.unlockedUntilSec}")
                return@launch
            }

            val label = locked.label.ifBlank { pkg }
            Log.d("AppLockSvc", "Blocking pkg=$pkg label=$label")

            showBlockScreen(pkg, label)
        }
    }

    private fun showBlockScreen(pkg: String, label: String) {
        val intent = Intent(applicationContext, com.example.studylockapp.ui.applock.AppLockBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("lockedPackage", pkg)
            putExtra("lockedLabel", label)
        }
        // Activity 起動はメインスレッドで確実に
        Handler(Looper.getMainLooper()).post {
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
}