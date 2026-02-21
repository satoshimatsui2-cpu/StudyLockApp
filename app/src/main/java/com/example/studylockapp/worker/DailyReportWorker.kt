package com.example.studylockapp.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.studylockapp.MainActivity
import com.example.studylockapp.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DailyReportWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    // 集計用のデータクラス
    data class ModeStats(
        var answerCount: Int = 0,
        var correctCount: Int = 0
    )

    override suspend fun doWork(): Result {
        return try {
            sendDailyReport()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun sendDailyReport() = coroutineScope {
        val user = FirebaseAuth.getInstance().currentUser ?: return@coroutineScope
        val db = FirebaseFirestore.getInstance()
        val context = applicationContext

        // ★日付は端末のZoneで揃える（Firestoreキーもこれで一致させる）
        val zoneId = ZoneId.systemDefault()
        val todayStr = LocalDate.now(zoneId).toString() // "yyyy-MM-dd"

        // --- 1. Firestoreから学習履歴を取得 ---
        val firestoreTask = async {
            try {
                db.collection("users").document(user.uid)
                    .collection("dailyStats").document(todayStr)
                    .get().await()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // --- 2. Room(端末内DB)から使用ポイントを取得（point_history: mode="unlock"） ---
        val roomTask = async(Dispatchers.IO) {
            try {
                val appDb = AppDatabase.getInstance(context)

                // dateEpochDay は LocalDate.toEpochDay() で入ってる前提
                val todayEpochDay = LocalDate.now(zoneId).toEpochDay()

                // unlock は delta がマイナスで入るので、反転して正の使用ポイントにする
                val sumDelta = appDb.pointHistoryDao().sumDeltaByModeAndDay("unlock", todayEpochDay)

                (-sumDelta).coerceAtLeast(0)
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }

        val dailyDoc = firestoreTask.await()
        val usedPoints = roomTask.await()

        // --- 3. 集計処理 ---
        var earnedPoints = 0
        val statsMap = mutableMapOf<String, MutableMap<String, ModeStats>>()

        if (dailyDoc != null && dailyDoc.exists()) {
            earnedPoints = dailyDoc.getLong("points")?.toInt() ?: 0

            @Suppress("UNCHECKED_CAST")
            val records = dailyDoc.get("studyRecords") as? List<Map<String, Any>> ?: emptyList()

            for (record in records) {
                val grade = record["grade"] as? String ?: "不明"
                val mode = record["mode"] as? String ?: "通常"
                val isCorrect = record["isCorrect"] as? Boolean ?: false

                val gradeMap = statsMap.getOrPut(grade) { mutableMapOf() }
                val modeStats = gradeMap.getOrPut(mode) { ModeStats() }

                modeStats.answerCount += 1
                if (isCorrect) modeStats.correctCount += 1
            }
        }

        // --- 4. 通知メッセージの作成 ---
        val sb = StringBuilder()
        sb.append("・獲得ポイント：$earnedPoints、使用ポイント：$usedPoints")

        if (statsMap.isEmpty()) {
            sb.append("(本日の学習データはありません)")
        } else {
            for ((grade, modes) in statsMap) {
                sb.append("　■$grade：")
                for ((mode, stat) in modes) {
                    sb.append("　　〇$mode：")
                    sb.append("　　　・回答数：${stat.answerCount}、正解数：${stat.correctCount}")
                }
            }
        }

        showNotification("本日の学習詳細レポート", sb.toString())
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "REPORT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText("獲得: ${message.substringBefore("、")}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }

    companion object {
        // ★ここを省略なしの完全なコードにしました★
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyReportWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyReportWork",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}