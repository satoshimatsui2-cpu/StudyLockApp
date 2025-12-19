package com.example.studylockapp.ui.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.db.LockedAppEntity
import com.example.studylockapp.service.AppLockAccessibilityService
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockSettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppLockListAdapter
    private lateinit var settings: AppSettings
    private var accessibilityDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)

        settings = AppSettings(this)

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
    }

    private suspend fun loadApps() {
        val pm = packageManager
        val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
        val lockedDao = db.lockedAppDao()

        val resolveInfos = withContext(Dispatchers.Default) {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val installed = resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null // 自アプリ除外
                val label = info.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val lockedMap = lockedDao.getAll().associateBy { it.packageName }

        val display = installed.map { (pkg, label) ->
            val locked = lockedMap[pkg]
            AppLockDisplayItem(
                packageName = pkg,
                label = label,
                isLocked = locked?.isLocked ?: false
            )
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
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            val shouldForce = settings.isAppLockEnabled() || lockedCount > 0
            if (!svcEnabled && shouldForce) {
                withContext(Dispatchers.Main) {
                    val msg = getString(R.string.app_lock_accessibility_message)
                    accessibilityDialog = AlertDialog.Builder(this@AppLockSettingsActivity)
                        .setTitle(R.string.app_lock_accessibility_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.app_lock_accessibility_go_settings) { _, _ ->
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                        .setNegativeButton(R.string.app_lock_accessibility_disable_all) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                settings.setAppLockEnabled(false)
                                db.lockedAppDao().disableAllLocks()
                            }
                        }
                        .show()
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