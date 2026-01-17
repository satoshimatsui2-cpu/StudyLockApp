package com.example.studylockapp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.studylockapp.PrefsManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.applock.AppLockBlockActivity
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

    // 連続起動抑制
    private var lastBlockPkg: String? = null
    private var lastBlockAtMs: Long = 0L
    private val blockCooldownMs: Long = 800L

    // 設定画面スキャンの負荷軽減用
    private var lastScanTimeMs: Long = 0L
    private val scanIntervalMs: Long = 500L

    private val expiryHandler = Handler(Looper.getMainLooper())
    private var expiryRunnable: Runnable? = null

    private var lastForegroundPkg: String? = null
    private var lastNonImeForegroundPkg: String? = null

    private val APP_LOCK_DEBUG = true
    private val APP_LOCK_TAG = "AppLockDebug"

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
        val eventType = event?.eventType ?: return
        val pkgName = event.packageName?.toString() ?: return

        // ---------------------------------------------------------
        // 1. Settings Specific Logic
        // ---------------------------------------------------------
        if (pkgName == "com.android.settings") {
            val isWindowStateChanged = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            val isContentChanged = (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

            if (isWindowStateChanged || isContentChanged) {
                val now = System.currentTimeMillis()
                // コンテンツ変化は頻繁なので間引く
                if (isContentChanged && (now - lastScanTimeMs < scanIntervalMs)) {
                    return
                }
                lastScanTimeMs = now

                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        // ★デバッグ用: 念のため現在の画面タイトル候補をログに出す
                        // Log.d("AppLockDebug", "Scanning settings screen...")

                        // Accessibility Lock
                        if (PrefsManager.isAccessibilityLockEnabled(this)) {
                            val keywords = listOf("Accessibility", "ユーザー補助")
                            if (keywords.any { isScreenTitle(rootNode, it) }) {
                                showBlockScreen(pkgName, "ユーザー補助設定")
                                return
                            }
                        }

                        // Tethering Lock
                        if (PrefsManager.isTetheringLockEnabled(this)) {
                            val keywords = listOf(
                                "Tethering",
                                "テザリング",
                                "Hotspot",
                                "アクセスポイント",
                                "アクセス ポイント",      // スペースあり
                                "アクセス ポイントとテザリング" // 完全一致
                            )
                            if (keywords.any { isScreenTitle(rootNode, it) }) {
                                Log.d("AppLockSvc", "Blocking Tethering: Found target title in $pkgName")
                                showBlockScreen(pkgName, "テザリング設定")
                                return
                            }
                        }
                    } finally {
                        rootNode.recycle()
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 2. Regular App Lock Logic
        // ---------------------------------------------------------
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        lastForegroundPkg = pkgName
        if (pkgName !in IGNORE_FOREGROUND_PKGS && pkgName != packageName) {
            lastNonImeForegroundPkg = pkgName
        }

        if (!::settings.isInitialized || !settings.isAppLockEnabled()) return
        if (pkgName == packageName) return

        serviceScope.launch(Dispatchers.IO) {
            val locked = db.lockedAppDao().get(pkgName)
            if (locked?.isLocked != true) return@launch

            val nowSec = Instant.now().epochSecond
            db.appUnlockDao().clearExpired(nowSec)

            val unlockEntry = db.appUnlockDao().get(pkgName)
            val unlockUntil = unlockEntry?.unlockedUntilSec ?: 0L

            if (unlockUntil > nowSec) return@launch

            val label = locked.label.ifBlank { pkgName }
            showBlockScreen(pkgName, label)
        }
    }

    /**
     * 【修正版】画面タイトル判定
     * クリック判定(isClickable)を廃止し、「隣にSummary(説明文)があるかどうか」で
     * メニュー項目と画面タイトルを区別します。
     */
    private fun isScreenTitle(rootNode: AccessibilityNodeInfo, keyword: String): Boolean {
        val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
        if (nodes.isNullOrEmpty()) return false

        var isTitle = false
        try {
            for (node in nodes) {
                if (node == null) continue

                // 1. Heading属性があれば問答無用でタイトル(API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading) {
                    isTitle = true
                    break
                }

                // 2. 文字自体が summary ID を持っていたら除外
                if (node.viewIdResourceName?.contains("summary", ignoreCase = true) == true) {
                    continue
                }

                // 3. 【新ロジック】兄弟要素（同じ行にある要素）に "summary" があるかチェック
                // メニュー項目の場合: [Title] [Summary(OFF)] という構成になっていることが多い
                // 画面タイトルの場合: [Title] だけ、あるいは [Title] [SearchButton] などで Summary はない
                if (hasSiblingSummary(node)) {
                    // 隣に説明文がある -> これはメニュー項目だ -> 無視
                    Log.d("AppLockSvc", "Ignored '$keyword' because it has a summary sibling (Menu Item).")
                    continue
                }

                // 4. ここまで来たらタイトルとみなす（クリック判定は削除）
                // Summaryを持たない単独のテキストなので、タイトルの可能性が高い
                Log.d("AppLockSvc", "Found '$keyword' with NO summary. Locking.")
                isTitle = true
                break
            }
        } finally {
            nodes.forEach { it?.recycle() }
        }
        return isTitle
    }

    /**
     * ノードの親を調べて、兄弟（同じ親を持つ他のビュー）の中に
     * "android:id/summary" を持つものがいるかチェックする
     */
    private fun hasSiblingSummary(node: AccessibilityNodeInfo): Boolean {
        val parent = node.parent ?: return false
        try {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChild(i)
                if (child != null) {
                    try {
                        // 自分自身はチェックしない
                        if (child != node) {
                            val resId = child.viewIdResourceName
                            if (resId != null && resId.contains("android:id/summary", ignoreCase = true)) {
                                return true // Summaryが見つかった
                            }
                        }
                    } finally {
                        child.recycle()
                    }
                }
            }
        } finally {
            parent.recycle()
        }
        return false
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

        Handler(Looper.getMainLooper()).post {
            val nowMs = System.currentTimeMillis()
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) {
                return@post
            }
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs
            startActivity(intent)
        }
    }

    private fun startExpiryWatcher() {
        val runnable = object : Runnable {
            override fun run() {
                if (!::settings.isInitialized || !settings.isAppLockEnabled()) {
                    expiryHandler.postDelayed(this, 2000L)
                    return
                }
                val rootPkg = rootInActiveWindow?.packageName?.toString()
                val candidate = when {
                    rootPkg != null && rootPkg !in IGNORE_FOREGROUND_PKGS -> rootPkg
                    lastForegroundPkg != null && lastForegroundPkg !in IGNORE_FOREGROUND_PKGS -> lastForegroundPkg
                    else -> lastNonImeForegroundPkg
                }
                val topPkg = candidate

                serviceScope.launch(Dispatchers.IO) {
                    val nowSec = Instant.now().epochSecond
                    db.appUnlockDao().clearExpired(nowSec)
                    if (topPkg != null && topPkg != packageName) {
                        val locked = db.lockedAppDao().get(topPkg)
                        val unlock = db.appUnlockDao().get(topPkg)
                        if (locked?.isLocked == true) {
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

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        expiryRunnable?.let { expiryHandler.removeCallbacks(it) }
        serviceJob.cancel()
    }
}