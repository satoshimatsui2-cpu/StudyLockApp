package com.example.studylockapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.example.studylockapp.ui.alert.BlockedAlertActivity
import com.example.studylockapp.ui.applock.AppLockBlockActivity
import com.example.studylockapp.ui.restricted.RestrictedAccessActivity
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

    private var lastBlockPkg: String? = null
    private var lastBlockAtMs: Long = 0L
    private val blockCooldownMs: Long = 800L

    private var lastScanTimeMs: Long = 0L
    private val scanIntervalMs: Long = 300L

    private val expiryHandler = Handler(Looper.getMainLooper())
    private var expiryRunnable: Runnable? = null

    private var lastForegroundPkg: String? = null
    private val IGNORE_FOREGROUND_PKGS = setOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin"
    )

    private val settingsHomeKeywords = listOf("Settings", "設定")

    private val tetheringKeywords = listOf(
        "Tethering", "テザリング", "Hotspot",
        "アクセスポイント", "アクセス ポイント"
    )
    private val networkMenuKeywords = listOf(
        "Network & internet", "ネットワークとインターネット"
    )
    private val accessibilityKeywords = listOf(
        "Accessibility", "ユーザー補助"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = AppSettings(this)
        db = AppDatabase.getInstance(this)

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        this.serviceInfo = info

        Log.d("AppLockSvc", "Service connected: Deep Scan Mode")
        startExpiryWatcher()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val pkgName = event.packageName?.toString() ?: return

        if (pkgName == packageName) return

        // ---------------------------------------------------------
        // 1. Settings App Logic (設定アプリ)
        // ---------------------------------------------------------
        if (pkgName == "com.android.settings") {

            // A. Click Detection
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val clickedText = event.text?.joinToString("") ?: ""

                if (checkKeywords(clickedText, tetheringKeywords) ||
                    checkKeywords(clickedText, networkMenuKeywords)) {
                    showRestrictedScreen(pkgName)
                    return
                }
            }

            // B. Window & Content
            val isWindowStateChanged = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            val isContentChanged = (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

            if (isWindowStateChanged || isContentChanged) {
                val now = System.currentTimeMillis()
                if (isContentChanged && (now - lastScanTimeMs < scanIntervalMs)) return
                lastScanTimeMs = now

                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        if (isSettingsTopScreen(rootNode)) return

                        if (PrefsManager.isAccessibilityLockEnabled(this)) {
                            if (findAndValidateTitle(rootNode, accessibilityKeywords)) {
                                showRestrictedScreen(pkgName)
                                return
                            }
                        }

                        if (PrefsManager.isTetheringLockEnabled(this)) {
                            if (findAndValidateTitle(rootNode, networkMenuKeywords) ||
                                findAndValidateTitle(rootNode, tetheringKeywords)) {
                                showRestrictedScreen(pkgName)
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
        // 2. SystemUI Logic (通知パネル・クイック設定) - ★強化版
        // ---------------------------------------------------------
        if (pkgName == "com.android.systemui") {
            if (PrefsManager.isTetheringLockEnabled(this)) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        // 設定画面での誤爆を防ぐチェック
                        if (rootNode.packageName?.toString() != "com.android.systemui") {
                            return
                        }

                        // ★修正: 再帰的スキャンでOFF状態のタイルも逃さない
                        if (recursiveCheckForTile(rootNode, tetheringKeywords)) {
                            Log.d("AppLockSvc", "SystemUI: Tethering Tile Detected (Deep Scan)")

                            if (Build.VERSION.SDK_INT >= 31) {
                                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                            } else {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            }
                            showAlertActivity()
                            return
                        }
                    } finally {
                        rootNode.recycle()
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 3. Regular App Lock Logic
        // ---------------------------------------------------------
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        lastForegroundPkg = pkgName

        if (!::settings.isInitialized || !settings.isAppLockEnabled()) return

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

    // --- Helper Methods ---

    /**
     * ★新メソッド: 木構造を再帰的に巡回し、テキストまたは説明文にキーワードが含まれるか執拗にチェックする
     * これにより、OFF状態のタイルなど、標準検索で引っかかりにくい要素も検知します。
     */
    private fun recursiveCheckForTile(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        // 1. 自分自身のチェック
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (text != null && keywords.any { text.contains(it, ignoreCase = true) }) return true
        if (desc != null && keywords.any { desc.contains(it, ignoreCase = true) }) return true

        // 2. 子要素の再帰チェック
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                if (recursiveCheckForTile(child, keywords)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    // --- 以下、既存メソッド ---

    private fun isSettingsTopScreen(rootNode: AccessibilityNodeInfo): Boolean {
        val windows = this.windows
        for (window in windows) {
            if (window.isActive) {
                val title = window.title?.toString() ?: ""
                if (settingsHomeKeywords.any { title.equals(it, ignoreCase = true) }) {
                    return true
                }
            }
        }
        for (keyword in settingsHomeKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                try {
                    for (node in nodes) {
                        if (node == null) continue
                        if (Build.VERSION.SDK_INT >= 28 && node.isHeading) {
                            return true
                        }
                    }
                } finally {
                    nodes.forEach { it?.recycle() }
                }
            }
        }
        return false
    }

    private fun findAndValidateTitle(rootNode: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                try {
                    for (node in nodes) {
                        if (node == null) continue
                        if (Build.VERSION.SDK_INT >= 28 && node.isHeading) return true
                        if (hasSiblingSummary(node)) continue
                        if (node.isClickable || isAnyParentClickable(node)) continue
                        return true
                    }
                } finally {
                    nodes.forEach { it?.recycle() }
                }
            }
        }
        return false
    }

    private fun hasSiblingSummary(node: AccessibilityNodeInfo): Boolean {
        val parent = node.parent ?: return false
        try {
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChild(i)
                if (child != null) {
                    try {
                        if (child != node) {
                            val resId = child.viewIdResourceName
                            if (resId != null && resId.contains("summary", ignoreCase = true)) {
                                return true
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

    private fun isAnyParentClickable(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 10) {
            if (current.isClickable) {
                current.recycle()
                return true
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        return false
    }

    private fun checkKeywords(text: String, keywords: List<String>): Boolean {
        if (text.isBlank()) return false
        val hasTarget = keywords.any { text.contains(it, ignoreCase = true) }
        if (!hasTarget) return false
        val exclude = listOf("Wi-Fi", "Bluetooth", "SIM")
        if (exclude.any { text.contains(it, ignoreCase = true) }) return false
        return true
    }

    private fun checkWindowTitleForTarget(keywords: List<String>): Boolean {
        val windows = this.windows
        for (window in windows) {
            if (window.isActive) {
                val title = window.title?.toString() ?: ""
                if (keywords.any { title.equals(it, ignoreCase = true) }) {
                    return true
                }
            }
        }
        return false
    }

    private fun showRestrictedScreen(pkg: String) {
        val intent = Intent(applicationContext, RestrictedAccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        Handler(Looper.getMainLooper()).post {
            val nowMs = System.currentTimeMillis()
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) return@post
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs
            startActivity(intent)
        }
    }

    private fun showBlockScreen(pkg: String, label: String) {
        val intent = Intent(applicationContext, AppLockBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("lockedPackage", pkg)
            putExtra("lockedLabel", label)
        }
        Handler(Looper.getMainLooper()).post {
            val nowMs = System.currentTimeMillis()
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) return@post
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs
            startActivity(intent)
        }
    }

    private fun showAlertActivity() {
        val intent = Intent(this, BlockedAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(intent)
        }, 400)
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
                    else -> null
                }

                if (candidate != null && candidate != packageName) {
                    serviceScope.launch(Dispatchers.IO) {
                        val nowSec = Instant.now().epochSecond
                        db.appUnlockDao().clearExpired(nowSec)
                        val locked = db.lockedAppDao().get(candidate)
                        if (locked?.isLocked == true) {
                            val unlock = db.appUnlockDao().get(candidate)
                            if (unlock == null || unlock.unlockedUntilSec <= nowSec) {
                                val label = locked.label.ifBlank { candidate }
                                showBlockScreen(candidate, label)
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