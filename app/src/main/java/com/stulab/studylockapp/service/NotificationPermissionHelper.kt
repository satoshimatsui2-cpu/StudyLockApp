package com.stulab.studylockapp.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stulab.studylockapp.ui.alert.AppDialogHelper

/**
 * 通知権限の確認と誘導を行うヘルパー
 */
class NotificationPermissionHelper(private val activity: AppCompatActivity) {

    private var requestPermissionLauncher: ActivityResultLauncher<String>? = null

    init {
        // Activityの初期化時にランチャーを登録する必要がある
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                // 拒否された場合、必要ならさらに設定画面への誘導を検討するが、
                // 今回の仕様では「Runtime Permissionリクエスト」を優先する。
            }
        }
    }

    /**
     * 通知が有効か確認し、無効なら適切な誘導を行う
     * @return 誘導を開始した場合はtrue
     */
    fun checkAndPromptNotification(): Boolean {
        val context = activity
        val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        if (areEnabled) return false

        // API 33以上かつ権限が未許可の場合
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                return true
            }
        }

        // それ以外（権限はあるがオフ、または旧OSでオフ）の場合は設定画面へ誘導
        showSettingsGuidanceDialog()
        return true
    }

    private fun showSettingsGuidanceDialog() {
        AppDialogHelper.showConfirm(
            context = activity,
            title = "通知をオンにしてください",
            message = "学習リマインダーやロック解除に関するお知らせを受け取るため、通知をオンにしてください。",
            positiveText = "設定を開く",
            negativeText = "あとで",
            onPositive = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
                activity.startActivity(intent)
            }
        )
    }
}
