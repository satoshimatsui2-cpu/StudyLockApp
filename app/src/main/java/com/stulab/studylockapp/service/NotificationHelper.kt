package com.stulab.studylockapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stulab.studylockapp.MainActivity
import com.stulab.studylockapp.R

/**
 * 通知の作成と表示を補助するクラス
 */
object NotificationHelper {
    private const val CHANNEL_ID = "daily_reminders"
    private const val CHANNEL_NAME = "学習リマインダー"
    private const val CHANNEL_DESC = "毎日の学習目標を達成するためのリマインダー通知です。"

    fun showNotification(context: Context, title: String, message: String, largeIconResId: Int = 0, notificationId: Int = 1001) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_school_24)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // キャラの画像がある場合は大きなアイコンとして表示
        if (largeIconResId != 0) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, largeIconResId)
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                // 画像読み込み失敗時は無視
            }
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // 通知権限がない場合
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
