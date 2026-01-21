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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import java.util.Date

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

    // クールダウン用
    private var skipLockUntilMs: Long = 0L

    // 自分のアプリ名
    private val myAppName by lazy {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "StudyLockApp"
        }
    }

    // ランチャー判定用
    private var lastTouchedIconName: String? = null
    private var lastTouchedTime: Long = 0

    // 設定TOP画面
    private val settingsHomeKeywords = listOf("Settings", "設定")

    // ★追加: 設定＞アプリ一覧画面をブロックするためのキーワード
    private val appsListKeywords = listOf(
        "Apps", "アプリ", "Applications", "App list", "アプリリスト"
    )

    // ロック対象キーワード（テザリング等）
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

    // ランチャーメニュー
    private val launcherMenuKeywords = listOf(
        "Pause app", "アプリを一時停止",
        "App info", "アプリ情報"
    )

    // 詳細画面（アンインストール等）
    private val appInfoKeywords = listOf(
        "Uninstall", "アンインストール",
        "Force stop", "強制停止"
    )

    // Launcher Packages
    private val launcherPackages = listOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.teslacoilsw.launcher"
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
        startExpiryWatcher()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val pkgName = event.packageName?.toString() ?: return

        if (pkgName == packageName) return

        // ---------------------------------------------------------
        // 1. Launcher Logic (ホーム画面)
        // ---------------------------------------------------------
        if (launcherPackages.any { pkgName.contains(it, ignoreCase = true) }) {

            // ★チェック: 「アプリ削除ロック」がONの時だけランチャー監視を行う
            if (settings.isUninstallLockEnabled()) {

                // タッチ記録
                if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {

                    val text = event.text?.joinToString("") ?: event.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        lastTouchedIconName = text
                        lastTouchedTime = System.currentTimeMillis()
                    }
                }

                // メニュー出現検知
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            if (recursiveCheckForTile(rootNode, launcherMenuKeywords)) {
                                val isRecent = (System.currentTimeMillis() - lastTouchedTime) < 3000
                                val isTarget = lastTouchedIconName?.contains(myAppName, ignoreCase = true) == true

                                if (isRecent && isTarget) {
                                    Log.d("AppLockSvc", "Launcher: Blocked menu for $lastTouchedIconName")
                                    skipLockUntilMs = System.currentTimeMillis() + 1500L
                                    performGlobalAction(GLOBAL_ACTION_BACK)
                                    return
                                }
                            }
                        } finally {
                            rootNode.recycle()
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 2. Settings App Logic (設定アプリ)
        // ---------------------------------------------------------
        if (pkgName == "com.android.settings") {

            if (System.currentTimeMillis() < skipLockUntilMs) return

            // A. Click Detection
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val clickedText = event.text?.joinToString("") ?: ""

                // テザリング等の既存ロック
                if (PrefsManager.isTetheringLockEnabled(this)) {
                    if (checkKeywords(clickedText, tetheringKeywords) ||
                        checkKeywords(clickedText, networkMenuKeywords)) {
                        showRestrictedScreen(pkgName)
                        return
                    }
                }

                // ★追加: 「アプリ削除ロック」がONなら、設定一覧の「アプリ」クリックもブロック
                if (settings.isUninstallLockEnabled()) {
                    if (checkKeywords(clickedText, appsListKeywords)) {
                        showRestrictedScreen(pkgName)
                        return
                    }
                }
            }

            // B. Window & Content Scanning
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

                        // ★チェック: 「アプリ削除ロック」がONの場合の処理
                        if (settings.isUninstallLockEnabled()) {

                            // 1. 設定＞アプリ一覧画面自体のブロック
                            if (findAndValidateTitle(rootNode, appsListKeywords)) {
                                showRestrictedScreen(pkgName)
                                return
                            }

                            // 2. 詳細画面でのアンインストールボタン検知
                            if (findAndValidateTitle(rootNode, listOf(myAppName))) {
                                if (recursiveCheckForTile(rootNode, appInfoKeywords)) {
                                    Log.d("AppLockSvc", "Protected Self Settings detected. Locking.")
                                    showRestrictedScreen(pkgName)
                                    return
                                }
                            }
                        }

                        // ユーザー補助
                        if (PrefsManager.isAccessibilityLockEnabled(this)) {
                            if (findAndValidateTitle(rootNode, accessibilityKeywords)) {
                                showRestrictedScreen(pkgName)
                                return
                            }
                        }

                        // テザリング
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
        // 3. SystemUI Logic (通知パネル)
        // ---------------------------------------------------------
        if (pkgName == "com.android.systemui") {
            if (System.currentTimeMillis() < skipLockUntilMs) return

            if (PrefsManager.isTetheringLockEnabled(this)) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        if (rootNode.packageName?.toString() != "com.android.systemui") return
                        if (recursiveCheckForTile(rootNode, tetheringKeywords)) {
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
        // 4. Regular App Lock Logic (ここは通常通り)
        // ---------------------------------------------------------
        if (System.currentTimeMillis() < skipLockUntilMs) return
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
    // (変更なし、そのまま記述してください)

    private fun isSettingsTopScreen(rootNode: AccessibilityNodeInfo): Boolean {
        val windows = this.windows
        for (window in windows) {
            if (window.isActive) {
                val title = window.title?.toString() ?: ""
                if (settingsHomeKeywords.any { title.equals(it, ignoreCase = true) }) return true
            }
        }
        for (keyword in settingsHomeKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                try {
                    for (node in nodes) {
                        if (node == null) continue
                        if (Build.VERSION.SDK_INT >= 28 && node.isHeading) return true
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
                            if (resId != null && resId.contains("summary", ignoreCase = true)) return true
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

    private fun recursiveCheckForTile(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (text != null && keywords.any { text.contains(it, ignoreCase = true) }) return true
        if (desc != null && keywords.any { desc.contains(it, ignoreCase = true) }) return true
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

    private fun checkKeywords(text: String, keywords: List<String>): Boolean {
        if (text.isBlank()) return false
        val hasTarget = keywords.any { text.contains(it, ignoreCase = true) }
        if (!hasTarget) return false
        val exclude = listOf("Wi-Fi", "Bluetooth", "SIM")
        if (exclude.any { text.contains(it, ignoreCase = true) }) return false
        return true
    }

// AppLockAccessibilityService.kt 内

    private fun showRestrictedScreen(pkg: String) {
        // クールダウン中なら何もしない
        if (System.currentTimeMillis() < skipLockUntilMs) return

        // ★修正: やはり警告画面を出す（無言バックはやめる）
        val intent = Intent(applicationContext, RestrictedAccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Handler(Looper.getMainLooper()).post {
            val nowMs = System.currentTimeMillis()
            // 連続起動防止
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) return@post
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs

            // 警告画面を起動
            startActivity(intent)
        }
    }

    // 学習アプリ用のロック画面（こちらは表示する）
    private fun showBlockScreen(pkg: String, label: String) {
        if (System.currentTimeMillis() < skipLockUntilMs) return

        val intent = Intent(applicationContext, AppLockBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            // ★重要修正: これがないとアイコンが出ず、解除もできません
            putExtra("package_name", pkg) // キー名は AppLockBlockActivity 側の受け取りキーと合わせてください
            putExtra("app_label", label)
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
                if (System.currentTimeMillis() < skipLockUntilMs) {
                    expiryHandler.postDelayed(this, 1000L)
                    return
                }
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
    // ▼▼▼ デバッグログ付きに変更 ▼▼▼
    override fun onUnbind(intent: Intent?): Boolean {
        android.util.Log.d("AppLockDebug", "★onUnbind 呼ばれました！(OFF操作を検知)")
        sendSecurityAlertToFunctions("accessibility_disabled")
        return super.onUnbind(intent)
    }

    private fun sendSecurityAlertToFunctions(alertType: String) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // 原因1: ユーザーがいない？
        if (user == null) {
            android.util.Log.e("AppLockDebug", "★エラー: User is null。ログイン情報が取れませんでした。")
            return
        }

        android.util.Log.d("AppLockDebug", "★送信開始: UID=${user.uid} へ警告を送ります...")

        val functions = FirebaseFunctions.getInstance("asia-northeast1")
        val data = hashMapOf(
            "alertType" to alertType,
            "uid" to user.uid,
            "timestamp" to java.util.Date().toString()
        )

        functions.getHttpsCallable("sendSecurityAlert").call(data)
            .addOnSuccessListener {
                android.util.Log.d("AppLockDebug", "★送信成功！親に通知が届いたはずです")
            }
            .addOnFailureListener { e ->
                // 原因2: 送信エラー？
                android.util.Log.e("AppLockDebug", "★送信失敗...", e)
            }
    }
}