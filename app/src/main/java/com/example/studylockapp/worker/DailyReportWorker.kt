package com.example.studylockapp.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.studylockapp.MainActivity
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.data.UnlockHistoryEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class DailyReportWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "DailyReportWorker"

    data class ModeStats(
        var answerCount: Int = 0,
        var correctCount: Int = 0
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started execution")
        return try {
            sendDailyReport()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }

    private suspend fun sendDailyReport() = coroutineScope {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.w(TAG, "No user logged in, skipping report")
            return@coroutineScope
        }
        val db = FirebaseFirestore.getInstance()
        val context = applicationContext

        // 1. 日付の計算 (端末Zoneベース)
        val zoneId = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zoneId).minusDays(1)
        val yesterdayStr = yesterday.toString()
        val yesterdayEpochDay = yesterday.toEpochDay()

        // 昨日の開始/終了時刻 (Epoch秒)
        val startSec = yesterday.atStartOfDay(zoneId).toEpochSecond()
        val endSec = yesterday.plusDays(1).atStartOfDay(zoneId).toEpochSecond() - 1

        Log.d(TAG, "Target Date: $yesterdayStr")

        // 2. データ取得 (Firestore & Room)
        val firestoreTask = async {
            try {
                db.collection("users").document(user.uid)
                    .collection("dailyStats").document(yesterdayStr)
                    .get().await()
            } catch (e: Exception) {
                Log.e(TAG, "Firestore fetch error", e)
                null
            }
        }

        val roomTask = async(Dispatchers.IO) {
            try {
                val appDb = AppDatabase.getInstance(context)
                val sumDelta = appDb.pointHistoryDao().sumDeltaByModeAndDay("unlock", yesterdayEpochDay)
                val histories = appDb.unlockHistoryDao().getUnlockHistoryBetween(startSec, endSec)
                sumDelta to histories
            } catch (e: Exception) {
                Log.e(TAG, "Room fetch error", e)
                0 to emptyList<UnlockHistoryEntity>()
            }
        }

        val dailyDoc = firestoreTask.await()
        val (sumDelta, unlockHistories) = roomTask.await()
        val usedPoints = (-sumDelta).coerceAtLeast(0)

        // 3. 学習統計の集計
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

        // 4. アプリ解放実績の集計
        val labelCache = mutableMapOf<String, String>()
        val groupedUnlock = unlockHistories.groupBy { it.packageName }
            .mapValues { (_, list) ->
                list.sumOf { history ->
                    // 実績時間の計算 (cancelledAt を安全に参照)
                    val cAt = history.cancelledAt
                    if (history.cancelled && cAt != null) {
                        (cAt - history.unlockedAt).coerceAtLeast(0)
                    } else {
                        history.unlockDurationSec
                    }
                }
            }

        // Firestoreへサマリーをアップロード (親へのレポート用)
        val uploadMap = groupedUnlock.mapKeys { (pkg, _) -> 
            labelCache.getOrPut(pkg) { getAppLabel(context, pkg) } 
        }
        try {
            db.collection("users").document(user.uid)
                .collection("dailyStats").document(yesterdayStr)
                .set(mapOf("unlockSummary" to uploadMap), SetOptions.merge())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload summary", e)
        }

        // 5. 通知メッセージの作成
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
                    val modeDisplayName = getModeDisplayName(context, mode)
                    sb.append("\n　〇$modeDisplayName：回答数：${stat.answerCount}、正解数：${stat.correctCount}")
                }
            }
        }

        showNotification("${yesterday.monthValue}月${yesterday.dayOfMonth}日の学習レポート", sb.toString())
    }

    private fun getModeDisplayName(context: Context, mode: String): String {
        return when (mode) {
            "meaning" -> context.getString(R.string.mode_meaning)
            "listening" -> context.getString(R.string.mode_listening)
            "listening_jp" -> context.getString(R.string.mode_listening_jp)
            "japanese_to_english" -> context.getString(R.string.mode_japanese_to_english)
            "english_english_1" -> context.getString(R.string.mode_english_english_1)
            "english_english_2" -> context.getString(R.string.mode_english_english_2)
            "test_fill_blank" -> context.getString(R.string.mode_test_fill_blank)
            "test_sort" -> context.getString(R.string.mode_test_sort)
            "test_listen_q1" -> context.getString(R.string.mode_test_listen_q1)
            "test_listen_q2" -> context.getString(R.string.mode_test_listen_q2)
            else -> mode
        }
    }

    private fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, "REPORT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // 1. 定期実行の設定 (24時間ごと)
            val periodicRequest = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork("DailyReportWork", ExistingPeriodicWorkPolicy.KEEP, periodicRequest)

            // 2. ★一時的なデバッグ実行
            if (true) {
                val debugRequest = OneTimeWorkRequestBuilder<DailyReportWorker>().build()
                workManager.enqueue(debugRequest)
            }
        }
    }
}
