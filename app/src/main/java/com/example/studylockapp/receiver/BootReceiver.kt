package com.example.studylockapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.studylockapp.StudyLockApp

/**
 * 端末の起動完了を検知して、通知スケジュールを復元するレシーバー
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? StudyLockApp
            app?.triggerDailyReminderSetup()
        }
    }
}
