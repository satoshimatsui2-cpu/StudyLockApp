package com.example.studylockapp.ui.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.db.LockedAppEntity
import com.example.studylockapp.service.AppLockAccessibilityService
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppLockSettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppLockListAdapter
    private lateinit var settings: AppSettings
    private var accessibilityDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)

        settings = AppSettings(this)

        // 必須ONなら「ロック設定を全て解除する」テキストを非表示（ID不明のためテキスト一致で探索）
        hideDisableAllIfRequired()

        val switchEnable = findViewById<MaterialSwitch>(R.id.switch_enable_lock)
        switchEnable.isChecked = settings.isAppLockEnabled()
        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            settings.setAppLockEnabled(isChecked)
            // ON にしたら即誘導（OFF→ONでもダイアログ出す）
            maybeShowAccessibilityDialog()
        }

        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_apps)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppLockListAdapter(emptyList()) { item, checked ->
            onToggleLock(item, checked)
        }
        recycler.adapter = adapter

        lifecycleScope.launch {
            loadApps()
        }
    }

    override fun onResume() {
        super.onResume()
        // アクセシビリティOFF & ロック有効/対象あり なら強制誘導
        maybeShowAccessibilityDialog()
        hideDisableAllIfRequired()
    }

    private fun hideDisableAllIfRequired() {
        val isRequired = AdminAuthManager.isAppLockRequired(this)
        if (!isRequired) return
        val root = findViewById<ViewGroup>(android.R.id.content) ?: return
        val targetText = getString(R.string.app_lock_accessibility_disable_all)
        val target = findViewWithText(root, targetText)
        target?.visibility = View.GONE
    }

    // Viewツリーを走査してテキスト一致するViewを返す
    private fun findViewWithText(view: View, text: String): View? {
        if (view is TextView && view.text == text) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val found = findViewWithText(child, text)
                if (found != null) return found
            }
        }
        return null
    }

    private suspend fun loadApps() {
        val pm = packageManager
        val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
        val lockedDao = db.lockedAppDao()

        val display = withContext(Dispatchers.Default) {
            // 1) ランチャーに出るアプリ
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0).mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null // 自アプリ除外
                val label = info.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }

            // 2) インストール済み（非システム）アプリも拾う
            val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL).mapNotNull { ai ->
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                val pkg = ai.packageName
                if (pkg == packageName) return@mapNotNull null // 自アプリ除外
                val label = ai.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }

            // 3) 重複を除外してソート
            val merged = (launcherApps + installedApps)
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase(Locale.getDefault()) }

            val lockedMap = lockedDao.getAll().associateBy { it.packageName }

            merged.map { (pkg, label) ->
                val locked = lockedMap[pkg]
                AppLockDisplayItem(
                    packageName = pkg,
                    label = label,
                    isLocked = locked?.isLocked ?: false
                )
            }
        }

        withContext(Dispatchers.Main) {
            adapter.submitList(display)
        }
    }

    private fun onToggleLock(item: AppLockDisplayItem, checked: Boolean) {
        // DBを書き換えたあと、画面のリストを再読込してUIも反映させる
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
            val dao = db.lockedAppDao()
            val entity = LockedAppEntity(
                packageName = item.packageName,
                label = item.label,
                isLocked = checked
            )
            dao.upsert(entity)
            loadApps()
            // ロック対象が増えた場合も誘導
            maybeShowAccessibilityDialog()
        }
    }

    // --- アクセシビリティ誘導 ---
    private fun maybeShowAccessibilityDialog() {
        // すでに表示中なら再表示しない
        if (accessibilityDialog?.isShowing == true) return

        val svcEnabled = isAppLockServiceEnabled()
        val isRequired = AdminAuthManager.isAppLockRequired(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            val shouldForce = settings.isAppLockEnabled() || lockedCount > 0
            if (!svcEnabled && shouldForce) {
                withContext(Dispatchers.Main) {
                    val msg = getString(R.string.app_lock_accessibility_message)
                    val builder = AlertDialog.Builder(this@AppLockSettingsActivity)
                        .setTitle(R.string.app_lock_accessibility_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.app_lock_accessibility_go_settings) { _, _ ->
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    // 必須ONなら全解除ボタンを出さない
                    if (!isRequired) {
                        builder.setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                settings.setAppLockEnabled(false)
                                db.lockedAppDao().disableAllLocks()
                            }
                        }
                    }
                    accessibilityDialog = builder.show()
                }
            }
        }
    }

    private fun isAppLockServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(this, AppLockAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo?.serviceInfo?.packageName == packageName &&
                        it.resolveInfo?.serviceInfo?.name == expected.className
            }
    }
}