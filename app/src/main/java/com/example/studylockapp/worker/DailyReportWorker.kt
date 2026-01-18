package com.example.studylockapp.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyReportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "User not logged in, cannot send report.")
            return Result.failure()
        }

        return try {
            // 1. 学習データの集計 (アプリの実装に合わせてDAOなどから取得してください)
            val reportData = getLearningStats()

            // 2. Cloud Functions の呼び出し
            callSendDailyReport(reportData)

            Log.d(TAG, "Daily report sent successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending daily report", e)
            if (shouldRetry(e)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun callSendDailyReport(data: Map<String, Any>) {
        // Cloud Functions の関数名 'sendDailyReport' を指定
        functions
            .getHttpsCallable("sendDailyReport")
            .call(data)
            .await()
    }

    // 集計ロジックのスタブ（実際の実装に置き換えてください）
    private fun getLearningStats(): Map<String, Any> {
        // 例: Roomデータベースから当日の学習記録を取得する
        return mapOf(
            "mode" to "English Vocabulary", // 例: 学習モード
            "answerCount" to 50,            // 例: 回答数
            "correctCount" to 45,           // 例: 正解数
            "pointsChange" to 100           // 例: ポイント増減
        )
    }

    private fun shouldRetry(e: Exception): Boolean {
        // 一時的なネットワークエラーなどの場合はリトライする
        return e !is FirebaseFunctionsException || 
               e.code == FirebaseFunctionsException.Code.UNAVAILABLE
    }

    companion object {
        private const val TAG = "DailyReportWorker"
        private const val WORK_NAME = "DailyReportWork"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // 現在時刻とターゲット時刻（朝7時）の差分を計算
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            // ターゲット時刻を設定 (デフォルト朝7時)
            dueDate.set(Calendar.HOUR_OF_DAY, 7)
            dueDate.set(Calendar.MINUTE, 0)
            dueDate.set(Calendar.SECOND, 0)

            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            // 制約の設定: ネットワーク接続時のみ実行
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 定期実行リクエストの作成 (24時間間隔)
            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyReportWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            // スケジュール登録 (既存のものは置き換え)
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
            
            Log.d(TAG, "Daily report scheduled. Initial delay: ${timeDiff / 1000 / 60} minutes")
        }
    }
}