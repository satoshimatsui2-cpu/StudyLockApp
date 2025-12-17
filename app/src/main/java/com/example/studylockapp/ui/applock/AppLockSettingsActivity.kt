package com.example.studylockapp.ui.applock

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.data.db.LockedAppEntity
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockSettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppLockListAdapter
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)

        settings = AppSettings(this)

        val switchEnable = findViewById<MaterialSwitch>(R.id.switch_enable_lock)
        switchEnable.isChecked = settings.isAppLockEnabled()
        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            settings.setAppLockEnabled(isChecked)
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

    private suspend fun loadApps() {
        val pm = packageManager
        val db = AppDatabase.getInstance(this@AppLockSettingsActivity)
        val lockedDao = db.lockedAppDao()

        // ランチャーに出るアプリ一覧（自アプリ除外）
        val mainIntent = pm.getLaunchIntentForPackage(packageName) // 自アプリ intent 確認用
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

            // トグル状態をすぐ画面に反映するため再読込
            loadApps()
        }
    }

}
