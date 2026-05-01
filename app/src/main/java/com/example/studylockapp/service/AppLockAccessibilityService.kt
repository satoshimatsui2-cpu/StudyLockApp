package com.example.studylockapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.studylockapp.PrefsManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.StudyHistoryRepository
import com.example.studylockapp.ui.alert.BlockedAlertActivity
import com.example.studylockapp.ui.applock.AppLockBlockActivity
import com.example.studylockapp.ui.restricted.RestrictedAccessActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
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

    private var skipLockUntilMs: Long = 0L

    private var accessibilityDisabledHandled = false

    private val myAppName by lazy {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "StudyLockApp"
        }
    }

    private var lastTouchedIconName: String? = null
    private var lastTouchedTime: Long = 0

    private val settingsHomeKeywords = listOf("Settings", "設定")
    private val appsListKeywords = listOf("Apps", "アプリ", "Applications", "App list", "アプリリスト")
    private val tetheringKeywords = listOf("Tethering", "テザリング", "Hotspot", "アクセスポイント", "アクセス ポイント")
    private val networkMenuKeywords = listOf("Network & internet", "ネットワークとインターネット")
    private val accessibilityKeywords = listOf("Accessibility", "ユーザー補助", "Accessibility settings", "ユーザー補助設定", "Security & privacy", "セキュリティとプライバシー")
    private val launcherMenuKeywords = listOf("Pause app", "アプリを一時停止", "App info", "アプリ情報")
    private val appInfoKeywords = listOf("Uninstall", "アンインストール", "Force stop", "強制停止")

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
        if (!::settings.isInitialized) settings = AppSettings(this)
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

        updateAccessibilityStatus(true)
        accessibilityDisabledHandled = false

        val wasEnabled = if (settings.hasAccessibilityStateRecorded()) settings.isLastAccessibilityEnabled() else null
        if (wasEnabled == false) {
            Log.w("AppLockDebug", "Accessibility state transition: OFF -> ON. Sending alert.")
            sendSecurityAlertToFunctions("accessibility_enabled")
        } else {
            Log.w("AppLockDebug", "Skipping ON alert. recorded_wasEnabled: $wasEnabled")
        }
        settings.setLastAccessibilityEnabled(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val pkgName = event.packageName?.toString() ?: return
        if (pkgName == packageName) return

        if (!::settings.isInitialized) settings = AppSettings(this)
        if (!::db.isInitialized) db = AppDatabase.getInstance(this)

        if (launcherPackages.any { pkgName.contains(it, ignoreCase = true) }) {
            if (settings.isUninstallLockEnabled()) {
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
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            if (recursiveCheckForTile(rootNode, launcherMenuKeywords)) {
                                val isRecent = (System.currentTimeMillis() - lastTouchedTime) < 3000
                                val isTarget = lastTouchedIconName?.contains(myAppName, ignoreCase = true) == true
                                if (isRecent && isTarget) {
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

        if (pkgName == "com.android.settings") {
            if (System.currentTimeMillis() < skipLockUntilMs) return
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val clickedText = event.text?.joinToString("") ?: ""
                if (settings.isTetheringLockEnabled) {
                    if (checkKeywords(clickedText, tetheringKeywords) || checkKeywords(clickedText, networkMenuKeywords)) {
                        backAndCooldown()
                        showRestrictedScreen(pkgName)
                        return
                    }
                }
                if (settings.isUninstallLockEnabled()) {
                    if (checkKeywords(clickedText, appsListKeywords)) {
                        backAndCooldown()
                        showRestrictedScreen(pkgName)
                        return
                    }
                }
            }
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
                        if (settings.isUninstallLockEnabled()) {
                            if (findAndValidateTitle(rootNode, appsListKeywords)) {
                                backAndCooldown()
                                showRestrictedScreen(pkgName)
                                return
                            }
                            if (findAndValidateTitle(rootNode, listOf(myAppName))) {
                                if (recursiveCheckForTile(rootNode, appInfoKeywords)) {
                                    backAndCooldown()
                                    showRestrictedScreen(pkgName)
                                    return
                                }
                            }
                        }
                        if (settings.isAccessibilityLockEnabled) {
                            if (findAndValidateTitle(rootNode, accessibilityKeywords)) {
                                backAndCooldown()
                                showRestrictedScreen(pkgName)
                                return
                            }
                        }
                        if (settings.isTetheringLockEnabled) {
                            if (findAndValidateTitle(rootNode, networkMenuKeywords) || findAndValidateTitle(rootNode, tetheringKeywords)) {
                                backAndCooldown()
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

        if (pkgName == "com.android.systemui") {
            if (System.currentTimeMillis() < skipLockUntilMs) return
            if (settings.isTetheringLockEnabled) {
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

        if (System.currentTimeMillis() < skipLockUntilMs) return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        lastForegroundPkg = pkgName
        if (!settings.isAppLockEnabled()) return
        serviceScope.launch(Dispatchers.IO) {
            val locked = db.lockedAppDao().get(pkgName)
            if (locked?.isLocked != true) return@launch
            val nowSec = Instant.now().epochSecond
            db.appUnlockDao().clearExpired(nowSec)
            val unlockEntry = db.appUnlockDao().get(pkgName)
            val unlockUntil = unlockEntry?.unlockedUntilSec ?: 0L
            if (unlockUntil > nowSec) return@launch
            val label = locked.label.ifBlank { pkgName }
            Handler(Looper.getMainLooper()).post {
                backAndCooldown()
                showBlockScreen(pkgName, label)
            }
        }
    }

    private fun backAndCooldown(ms: Long = 800L) {
        skipLockUntilMs = System.currentTimeMillis() + ms
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

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

    private fun recursiveCheckForTile(node: AccessibilityNodeInfo, keywords: List<String>, depth: Int = 0): Boolean {
        if (depth > 20) return false
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (text != null && keywords.any { text.contains(it, ignoreCase = true) }) return true
        if (desc != null && keywords.any { desc.contains(it, ignoreCase = true) }) return true
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                if (recursiveCheckForTile(child, keywords, depth + 1)) {
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

    private fun showRestrictedScreen(pkg: String) {
        val intent = Intent(applicationContext, RestrictedAccessActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val nowMs = System.currentTimeMillis()
            if (pkg == lastBlockPkg && (nowMs - lastBlockAtMs) < blockCooldownMs) return@postDelayed
            lastBlockPkg = pkg
            lastBlockAtMs = nowMs
            skipLockUntilMs = nowMs + 600L
            performGlobalAction(GLOBAL_ACTION_BACK)
            val options = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0)
            startActivity(intent, options.toBundle())
        }, 80L)
    }

    private fun showBlockScreen(pkg: String, label: String) {
        val intent = Intent(applicationContext, AppLockBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("package_name", pkg)
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
        Handler(Looper.getMainLooper()).postDelayed({ startActivity(intent) }, 400)
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
        Log.w("AppLockDebug", "Accessibility onDestroy called. Cleanup only.")
        expiryRunnable?.let { expiryHandler.removeCallbacks(it) }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!::settings.isInitialized) settings = AppSettings(this)
        Log.w("AppLockDebug", "Accessibility onUnbind called. Handling disabled state.")
        handleAccessibilityDisabled("onUnbind")
        return super.onUnbind(intent)
    }

    private fun handleAccessibilityDisabled(source: String) {
        if (accessibilityDisabledHandled) return
        accessibilityDisabledHandled = true

        updateAccessibilityStatus(false)
        showAccessibilityOffNotification()

        val wasEnabled = if (settings.hasAccessibilityStateRecorded()) settings.isLastAccessibilityEnabled() else null
        if (wasEnabled == true) {
            Log.w("AppLockDebug", "Accessibility state transition: ON -> OFF (source: $source). Sending alert.")
            sendSecurityAlertToFunctions("accessibility_disabled")
        } else {
            Log.w("AppLockDebug", "Skipping OFF alert. source: $source, wasEnabled: $wasEnabled")
        }
        settings.setLastAccessibilityEnabled(false)
    }

    private fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(context.packageName) }
    }

    private fun showAccessibilityOffNotification() {
        val channelId = "SECURITY_ALERTS"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "セキュリティ警告",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "アクセシビリティ設定の変更を通知します"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_lock_24dp)
            .setContentTitle("⚠️ アクセシビリティ設定がOFFになっています")
            .setContentText("アプリを使用出来ないためアクセシビリティをONにして下さい。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())
    }

    private fun sendSecurityAlertToFunctions(alertType: String, callback: ((Boolean) -> Unit)? = null) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            callback?.invoke(false)
            return
        }
        val functions = FirebaseFunctions.getInstance("asia-northeast1")
        val data = hashMapOf(
            "alertType" to alertType
        )

        functions.getHttpsCallable("sendSecurityAlert").call(data)
            .addOnSuccessListener { result ->
                val map = result.getData() as? Map<*, *>
                val success = map?.get("success") as? Boolean ?: false
                val message = map?.get("message") as? String
                if (!success && message != null) {
                    Log.w("AppLockDebug", "★警告送信失敗通知: $message")
                }
                callback?.invoke(success)
            }
            .addOnFailureListener { e ->
                Log.e("AppLockDebug", "★警告送信失敗", e)
                callback?.invoke(false)
            }
    }

    private fun updateAccessibilityStatus(enabled: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "accessibilityEnabled" to enabled,
            "accessibilityUpdatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("AppLockDebug", "Failed to update accessibility status", e)
            }
    }
}
