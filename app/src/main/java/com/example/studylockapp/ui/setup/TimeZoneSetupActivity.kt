package com.example.studylockapp.ui.setup

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.service.AppLockAccessibilityService
import com.example.studylockapp.ui.settings.TimeZoneOptions
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class TimeZoneSetupActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var spinner: MaterialAutoCompleteTextView
    private lateinit var buttonOk: Button

    private companion object {
        private const val DEFAULT_ZONE_ID = "Asia/Tokyo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_zone_setup)

        // バックジェスチャーを無効化（初回強制用）
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // do nothing
                }
            }
        )

        settings = AppSettings(this)
        spinner = findViewById(R.id.spinner_time_zone)
        buttonOk = findViewById(R.id.button_time_zone_ok)

        // 候補は TimeZoneOptions に一本化（選択表示をカスタム）
        val adapter = ArrayAdapter(
            this,
            R.layout.item_time_zone_spinner, // 選択表示・ドロップダウンともに共通
            TimeZoneOptions.displayList
        ).apply {
            setDropDownViewResource(R.layout.item_time_zone_spinner) // 既存レイアウトを流用
        }
        spinner.setAdapter(adapter)

        val storedId = settings.appTimeZoneId
        val initialIndex = if (storedId.isNullOrBlank()) {
            // 未設定なら Tokyo を初期選択
            TimeZoneOptions.indexOfOrZero(DEFAULT_ZONE_ID)
        } else {
            TimeZoneOptions.indexOfOrZero(storedId)
        }
        // 選択状態を文字で反映（ドロップダウンを開かないので第二引数 false）
        spinner.setText(TimeZoneOptions.displayList[initialIndex], false)

        buttonOk.setOnClickListener {
            val sel = spinner.text?.toString().orEmpty()
            settings.setTimeZone(TimeZoneOptions.toZoneIdOrNull(sel)) // nullでも選択済みになる想定
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        showAccessibilityIntroIfNeeded()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val target = ComponentName(this, AppLockAccessibilityService::class.java)
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { info ->
            val si = info.resolveInfo?.serviceInfo
            si?.packageName == target.packageName && si.name == target.className
        }
    }

    private fun showAccessibilityIntroIfNeeded() {
        if (settings.hasShownAccessibilityIntro()) return

        if (isAccessibilityServiceEnabled()) {
            // 既に有効ならフラグを立てて二度と出さない
            settings.setHasShownAccessibilityIntro(true)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_lock_accessibility_title))
            .setMessage(getString(R.string.app_lock_accessibility_intro)) // 引数なしで表示
            .setCancelable(false)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                settings.setHasShownAccessibilityIntro(true)
            }
            .show()
    }
}