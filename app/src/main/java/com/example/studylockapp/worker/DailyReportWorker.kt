package com.example.studylockapp.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.studylockapp.MainActivity
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.UnlockHistoryEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
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

        // ★日付は端末のZoneで「前日」を取得する (チャッピー提案の完成形)
        val zoneId = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zoneId).minusDays(1)
        val yesterdayStr = yesterday.toString() // "yyyy-MM-dd"
        val yesterdayEpochDay = yesterday.toEpochDay()

        // 昨日の開始時刻と終了時刻 (Epoch秒) - 解放履歴取得用
        val startSec = yesterday.atStartOfDay(zoneId).toEpochSecond()
        val endSec = yesterday.plusDays(1).atStartOfDay(zoneId).toEpochSecond() - 1

        // --- 1. Firestoreから学習履歴を取得 ---
        val firestoreTask = async {
            try {
                db.collection("users").document(user.uid)
                    .collection("dailyStats").document(yesterdayStr)
                    .get().await()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // --- 2. Room(端末内DB)から使用ポイントと解放履歴を取得 ---
        val roomTask = async(Dispatchers.IO) {
            try {
                val appDb = AppDatabase.getInstance(context)
                // unlock は delta がマイナスで入るので、反転して正の使用ポイントにする
                val sumDelta = appDb.pointHistoryDao().sumDeltaByModeAndDay("unlock", yesterdayEpochDay)
                val histories = appDb.unlockHistoryDao().getUnlockHistoryBetween(startSec, endSec)
                sumDelta to histories
            } catch (e: Exception) {
                e.printStackTrace()
                0 to emptyList<UnlockHistoryEntity>()
            }
        }

        val dailyDoc = firestoreTask.await()
        val (sumDelta, unlockHistories) = roomTask.await()
        val usedPoints = (-sumDelta).coerceAtLeast(0)

        // --- 3. 学習統計の集計 ---
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

        // --- 4. 解放アプリ実績の集計 (実績時間の計算) ---
        val labelCache = mutableMapOf<String, String>()
        val groupedUnlock = unlockHistories.groupBy { it.packageName }
            .mapValues { (_, list) ->
                list.sumOf { history ->
                    val actualSec = if (history.cancelled && history.cancelledAt != null) {
                        (history.cancelledAt!! - history.unlockedAt).coerceAtLeast(0)
                    } else {
                        history.unlockDurationSec
                    }
                    actualSec
                }
            }

        // --- 5. 通知メッセージの作成 ---
        val sb = StringBuilder()
        sb.append("昨日の獲得ポイント：$earnedPoints、使用ポイント：$usedPoints")

        if (groupedUnlock.isNotEmpty()) {
            sb.append("\n【アプリ解放実績】")
            groupedUnlock.entries
                .sortedByDescending { it.value }
                .forEach { (pkg, sec) ->
                    val label = labelCache.getOrPut(pkg) { getAppLabel(context, pkg) }
                    val minutes = sec / 60
                    val remainSec = sec % 60
                    if (minutes > 0) {
                        sb.append("\n・$label: ${minutes}分")
                    } else {
                        sb.append("\n・$label: ${remainSec}秒")
                    }
                }
        }

        if (statsMap.isEmpty()) {
            sb.append("\n(昨日の学習データはありません)")
        } else {
            sb.append("\n【学習詳細】")
            for ((grade, modes) in statsMap) {
                sb.append("\n■$grade：")
                for ((mode, stat) in modes) {
                    sb.append("\n　〇$mode：回答数：${stat.answerCount}、正解数：${stat.correctCount}")
                }
            }
        }

        // 通知タイトルも前日の日付に合わせる
        showNotification("${yesterday.monthValue}月${yesterday.dayOfMonth}日の学習レポート", sb.toString())
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        } catch (e: Exception) {
            packageName
        }
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
            .setContentText(message.substringBefore("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }

    companion object {
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
