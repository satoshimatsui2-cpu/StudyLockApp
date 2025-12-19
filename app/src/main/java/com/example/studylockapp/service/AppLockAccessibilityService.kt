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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class AppLockAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var settings: AppSettings
    private lateinit var db: AppDatabase

    // 連続起動を少し抑制（同一pkgでイベント連打される端末対策）
    private var lastBlockPkg: String? = null
    private var lastBlockAtMs: Long = 0L
    private val blockCooldownMs: Long = 800L

    // 解放期限のポーリング監視
    private val expiryHandler = Handler(Looper.getMainLooper())
    private var expiryRunnable: Runnable? = null

    // 直近で前面にあったパッケージ（rootInActiveWindow が null の端末対策）
    private var lastForegroundPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = AppSettings(this)
        db = AppDatabase.getInstance(this)
        Log.d("AppLockSvc", "Service connected")
        startExpiryWatcher()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // 先にフォアグラウンド記録（自アプリの場合でも上書きする）
        lastForegroundPkg = pkg

        // settings 未初期化 or マスターOFFなら何もしない
        if (!::settings.isInitialized || !settings.isAppLockEnabled()) return

        // 自アプリはスキップ
        if (pkg == packageName) return

        Log.d("AppLockSvc", "Event pkg=$pkg")

        serviceScope.launch(Dispatchers.IO) {
            val locked = db.lockedAppDao().get(pkg)
            if (locked?.isLocked != true) {
                Log.d("AppLockSvc", "pkg=$pkg not locked or null")
                return@launch
            }

            val nowSec = Instant.now().epochSecond

            // 一時解放チェック
            val unlock = db.appUnlockDao().get(pkg)
            if (unlock != null) {
                if (unlock.unlockedUntilSec > nowSec) {
                    // まだ解放中
                    Log.d("AppLockSvc", "pkg=$pkg unlocked until ${unlock.unlockedUntilSec}")
                    return@launch
                } else {
                    // 期限切れ → DBから削除して、以降は確実にロック扱いへ戻す
                    Log.d("AppLockSvc", "pkg=$pkg unlock expired at ${unlock.unlockedUntilSec}, clearing")
                    db.appUnlockDao().clear(pkg)
                }
            }

            val label = locked.label.ifBlank { pkg }
            Log.d("AppLockSvc", "Blocking pkg=$pkg label=$label")

            showBlockScreen(pkg, label)
        }
    }

    private fun showBlockScreen(pkg: String, label: String) {
        val intent = Intent(
            applicationContext,
            com.example.studylockapp.ui.applock.AppLockBlockActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("lockedPackage", pkg)
            putExtra("lockedLabel", label)
        }

        // Activity 起動はメインスレッドで確実に
        Handler(Looper.getMainLooper()).post {
            // 連続起動抑制
            val nowMs = System.currentTimeMillis()
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) {
                Log.d("AppLockSvc", "Skip block (cooldown) pkg=$pkg")
                return@post
            }
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs

            startActivity(intent)
        }
    }

    /**
     * 2秒ごとに解放期限切れを掃除し、前面アプリがロック対象なら即ブロックに戻す
     * rootInActiveWindow が null の端末では lastForegroundPkg をフォールバックに利用
     */
    private fun startExpiryWatcher() {
        val runnable = object : Runnable {
            override fun run() {
                // settings 未初期化 or マスターOFFなら何もしない
                if (!::settings.isInitialized || !settings.isAppLockEnabled()) {
                    expiryHandler.postDelayed(this, 2000L)
                    return
                }

                // 現在前面にあるパッケージ名（null なら lastForegroundPkg を使用）
                val topPkg = rootInActiveWindow?.packageName?.toString() ?: lastForegroundPkg

                serviceScope.launch(Dispatchers.IO) {
                    val nowSec = Instant.now().epochSecond

                    // 期限切れの解放を全削除
                    db.appUnlockDao().clearExpired(nowSec)

                    // 前面パッケージがロック対象で、解放なし/期限切れなら即ブロック
                    if (topPkg != null && topPkg != packageName) {
                        val locked = db.lockedAppDao().get(topPkg)
                        if (locked?.isLocked == true) {
                            val unlock = db.appUnlockDao().get(topPkg)
                            if (unlock == null || unlock.unlockedUntilSec <= nowSec) {
                                val label = locked.label.ifBlank { topPkg }
                                showBlockScreen(topPkg, label)
                            }
                        }
                    }
                }

                expiryHandler.postDelayed(this, 2000L)
            }
        }
        expiryRunnable = runnable
        expiryHandler.postDelayed(runnable, 2000L)
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        super.onDestroy()
        expiryRunnable?.let { expiryHandler.removeCallbacks(it) }
        serviceJob.cancel()
    }
}