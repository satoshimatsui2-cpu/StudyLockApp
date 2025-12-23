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
import kotlinx.coroutines.withContext
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
    // IME などを除外した前面パッケージの記憶
    private var lastNonImeForegroundPkg: String? = null

    // --- デバッグ用 ---
    private val APP_LOCK_DEBUG = true
    private val APP_LOCK_TAG = "AppLockDebug"
    private var lastDebugFrontPkg: String? = null

    // IME（キーボード）など前面判定から除外するパッケージ
    private val IGNORE_FOREGROUND_PKGS = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin"
    )

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
        // IME 以外かつ自アプリ以外なら最後の非 IME として記憶
        if (pkg !in IGNORE_FOREGROUND_PKGS && pkg != packageName) {
            lastNonImeForegroundPkg = pkg
        }

        if (APP_LOCK_DEBUG) {
            Log.d(APP_LOCK_TAG, "event: pkg=$pkg")
        }

        // settings 未初期化 or マスターOFFなら何もしない
        if (!::settings.isInitialized || !settings.isAppLockEnabled()) return

        // 自アプリはスキップ
        if (pkg == packageName) return

        Log.d("AppLockSvc", "Event pkg=$pkg")

        serviceScope.launch(Dispatchers.IO) {
            val locked = db.lockedAppDao().get(pkg)
            val nowSec = Instant.now().epochSecond
            val unlockEntry = db.appUnlockDao().get(pkg)
            val unlockUntil = unlockEntry?.unlockedUntilSec ?: 0L

            if (APP_LOCK_DEBUG) {
                Log.d(
                    APP_LOCK_TAG,
                    "eventCheck: pkg=$pkg locked=${locked?.isLocked} unlockUntil=$unlockUntil now=$nowSec"
                )
            }

            if (locked?.isLocked != true) {
                Log.d("AppLockSvc", "pkg=$pkg not locked or null")
                return@launch
            }

            // 期限切れを即クリア
            db.appUnlockDao().clearExpired(nowSec)

            // 再取得して判定
            val unlockedUntil2 = db.appUnlockDao().get(pkg)?.unlockedUntilSec ?: 0L
            if (unlockedUntil2 > nowSec) {
                Log.d("AppLockSvc", "pkg=$pkg is temporarily unlocked")
                return@launch
            }

            val label = locked.label.ifBlank { pkg }
            Log.d("AppLockSvc", "Blocking pkg=$pkg label=$label")
            showBlockScreen(pkg, label)
        }
    }

    private suspend fun isTemporarilyUnlocked(pkg: String): Boolean {
        val nowSec = Instant.now().epochSecond
        // 期限切れを即クリア
        withContext(Dispatchers.IO) {
            db.appUnlockDao().clearExpired(nowSec)
        }
        // 期限内かどうか確認
        val unlockedUntil = withContext(Dispatchers.IO) {
            db.appUnlockDao().get(pkg)?.unlockedUntilSec ?: 0L
        }
        return unlockedUntil > nowSec
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
     * rootInActiveWindow が null の端末では lastForegroundPkg / lastNonImeForegroundPkg をフォールバックに利用
     */
    private fun startExpiryWatcher() {
        val runnable = object : Runnable {
            override fun run() {
                // settings 未初期化 or マスターOFFなら何もしない
                if (!::settings.isInitialized || !settings.isAppLockEnabled()) {
                    expiryHandler.postDelayed(this, 2000L)
                    return
                }

                // 現在前面にあるパッケージ名を取得（IME なら無視してフォールバック）
                val rootPkg = rootInActiveWindow?.packageName?.toString()
                val candidate = when {
                    rootPkg != null && rootPkg !in IGNORE_FOREGROUND_PKGS -> rootPkg
                    lastForegroundPkg != null && lastForegroundPkg !in IGNORE_FOREGROUND_PKGS -> lastForegroundPkg
                    else -> lastNonImeForegroundPkg
                }
                val topPkg = candidate

                serviceScope.launch(Dispatchers.IO) {
                    val nowSec = Instant.now().epochSecond

                    // 期限切れの解放を全削除
                    db.appUnlockDao().clearExpired(nowSec)

                    // デバッグログ: 取得元やロック件数を可視化
                    if (APP_LOCK_DEBUG) {
                        val debugNowSec = System.currentTimeMillis() / 1000L
                        val countLocked = db.lockedAppDao().countLocked()
                        if (topPkg != lastDebugFrontPkg) {
                            Log.d(
                                APP_LOCK_TAG,
                                "poll: nowSec=$debugNowSec rootPkg=$rootPkg lastFg=$lastForegroundPkg lastNonIme=$lastNonImeForegroundPkg front=$topPkg lockedCount=$countLocked (clearExpired executed)"
                            )
                            lastDebugFrontPkg = topPkg
                        }
                    }

                    // 前面パッケージがロック対象で、解放なし/期限切れなら即ブロック
                    if (topPkg != null && topPkg != packageName) {
                        val locked = db.lockedAppDao().get(topPkg)
                        val unlock = db.appUnlockDao().get(topPkg)
                        if (APP_LOCK_DEBUG) {
                            val unlockUntil = unlock?.unlockedUntilSec ?: 0L
                            Log.d(
                                APP_LOCK_TAG,
                                "pollCheck: pkg=$topPkg locked=${locked?.isLocked} unlockUntil=$unlockUntil now=$nowSec"
                            )
                        }
                        if (locked?.isLocked == true) {
                            if (unlock == null || unlock.unlockedUntilSec <= nowSec) {
                                val label = locked.label.ifBlank { topPkg }
                                if (APP_LOCK_DEBUG) {
                                    Log.d(APP_LOCK_TAG, "pollAction: blocking pkg=$topPkg")
                                }
                                showBlockScreen(topPkg, label)
                            }
                        }
                    } else if (APP_LOCK_DEBUG) {
                        val reason = if (topPkg == null) "null/ignored" else "self"
                        Log.d(APP_LOCK_TAG, "pollSkip: topPkg=$reason")
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