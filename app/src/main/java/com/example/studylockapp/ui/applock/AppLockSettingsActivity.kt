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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AdminAuthManager
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.db.LockedAppEntity
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.alert.AppDialogHelper
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AppLockSettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppLockListAdapter
    private lateinit var settings: AppSettings
    private var isAccessibilityDialogOpen: Boolean = false
    private var isAuthenticatedLocally: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)

        val mainLayout = findViewById<View>(R.id.main_app_lock_layout)
        val initialTop = mainLayout.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                initialTop + bars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        settings = AppSettings(this)
        
        // AdminSettingsActivity からの認証状態を引き継ぐ
        isAuthenticatedLocally = savedInstanceState?.getBoolean("isAuth")
            ?: intent.getBooleanExtra("isAuthenticated", false)

        // UIから削除（非表示）されたマスタースイッチ
        val switchEnable = findViewById<MaterialSwitch>(R.id.switch_enable_lock)
        switchEnable?.visibility = View.GONE

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isAuth", isAuthenticatedLocally)
    }

    override fun onResume() {
        super.onResume()
        // アクセシビリティOFF & ロック対象あり なら誘導
        maybeShowAccessibilityDialog()
    }

    private suspend fun loadApps() {
        val pm = packageManager
        val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
        val lockedDao = db.lockedAppDao()

        val display = withContext(Dispatchers.Default) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0).mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }

            val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL).mapNotNull { ai ->
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                val pkg = ai.packageName
                if (pkg == packageName) return@mapNotNull null
                val label = ai.loadLabel(pm)?.toString() ?: pkg
                pkg to label
            }

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
        // 管理者ロックが有効で、かつ認証されていない場合は変更不可
        if (AdminAuthManager.isAdminLockEnabled(this) && !isAuthenticatedLocally) {
            Toast.makeText(this, "管理者認証が必要です。", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                loadApps() // 元に戻す
            }
            return
        }

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
            maybeShowAccessibilityDialog()
        }
    }

    private fun maybeShowAccessibilityDialog() {
        if (isAccessibilityDialogOpen) return

        val svcEnabled = isAppLockServiceEnabled()
        if (svcEnabled) return 

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
            val lockedCount = db.lockedAppDao().countLocked()
            val shouldForce = lockedCount > 0
            if (shouldForce) {
                withContext(Dispatchers.Main) {
                    isAccessibilityDialogOpen = true
                    
                    // 管理者ロックが有効なら「全て解除（今は設定しない）」を許容しない
                    val isAdminLocked = AdminAuthManager.isAdminLockEnabled(this@AppLockSettingsActivity)
                    
                    AppDialogHelper.showConfirm(
                        context = this@AppLockSettingsActivity,
                        title = getString(R.string.app_lock_accessibility_title),
                        message = getString(R.string.app_lock_accessibility_message),
                        positiveText = getString(R.string.app_lock_accessibility_go_settings),
                        negativeText = if (isAdminLocked) "" else getString(R.string.app_lock_accessibility_disable_all),
                        onPositive = {
                            isAccessibilityDialogOpen = false
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        onNegative = {
                            isAccessibilityDialogOpen = false
                            if (!isAdminLocked) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    db.lockedAppDao().disableAllLocks()
                                    withContext(Dispatchers.Main) {
                                        loadApps()
                                    }
                                }
                            }
                        }
                    )
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