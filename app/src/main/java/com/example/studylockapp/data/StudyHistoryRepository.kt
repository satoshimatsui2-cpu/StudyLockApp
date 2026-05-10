package com.example.studylockapp.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 学習履歴およびポイントの使用状況をFirestoreに保存するリポジトリ。
 */
object StudyHistoryRepository {

    private fun todayTokyoStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return sdf.format(Date())
    }

    /**
     * 最終アクティブ日時を更新し、レポート停止状態を解除する。
     */
    suspend fun updateLastActiveStatus(onSuccess: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val updates = hashMapOf<String, Any>(
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "dailyReportPaused" to false,
            "dailyReportPauseReason" to FieldValue.delete(),
            "dailyReportPausedAt" to FieldValue.delete(),
            "inactivityNoticeSentAt" to FieldValue.delete()
        )

        try {
            db.collection("users").document(user.uid)
                .set(updates, SetOptions.merge())
                .await()
            onSuccess?.invoke()
        } catch (e: Exception) {
            Log.e("StudyLog", "Activity更新失敗", e)
        }
    }

    /**
     * 学習結果を保存する。
     */
    suspend fun save(
        grade: String,
        mode: String,
        isCorrect: Boolean,
        points: Int = 0,
        word: String? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.w("DailyStats", "save study skipped: user is null")
            return
        }
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        Log.d("DailyStats", "save study start uid=$uid word=$word grade=$grade correct=$isCorrect points=$points")

        try {
            val now = Timestamp.now()
            val record: MutableMap<String, Any> = hashMapOf(
                "type" to "study",
                "grade" to grade,
                "mode" to mode,
                "isCorrect" to isCorrect,
                "earnedPoints" to points.toLong(),
                "timestamp" to now
            )
            if (word != null) record["word"] = word

            val docRef = db.collection("users").document(uid)
                .collection("dailyStats").document(todayStr)

            val updates: Map<String, Any> = hashMapOf(
                "points" to FieldValue.increment(points.toLong()),
                "studyCount" to FieldValue.increment(1L),
                "correctCount" to FieldValue.increment(if (isCorrect) 1L else 0L),
                "gradesStudied" to FieldValue.arrayUnion(grade),
                "modesStudied" to FieldValue.arrayUnion(mode),
                "studyRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("DailyStats", "save study success")
        } catch (e: Exception) {
            Log.e("DailyStats", "save study failed", e)
        }
    }

    /**
     * 発音チェックの試行を記録する。
     */
    suspend fun addVoiceCheckRecord(
        grade: String,
        word: String,
        checkType: String,
        success: Boolean
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        try {
            val now = Timestamp.now()
            val record: Map<String, Any> = hashMapOf(
                "type" to "voice_check",
                "checkType" to checkType,
                "word" to word,
                "grade" to grade,
                "success" to success,
                "timestamp" to now
            )

            val docRef = db.collection("users").document(user.uid)
                .collection("dailyStats").document(todayStr)

            val updates: Map<String, Any> = hashMapOf(
                "studyRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("StudyLog", "addVoiceCheckRecord success")
        } catch (e: Exception) {
            Log.e("StudyLog", "addVoiceCheckRecord failed ($todayStr)", e)
        }
    }

    /**
     * 音声チェックによるボーナスポイントを加算する。
     */
    suspend fun addVoiceBonusPoints(
        grade: String,
        word: String,
        points: Int,
        checkType: String? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        try {
            val now = Timestamp.now()
            val record: MutableMap<String, Any> = hashMapOf(
                "type" to "voice_bonus",
                "grade" to grade,
                "word" to word,
                "earnedPoints" to points.toLong(),
                "timestamp" to now
            )
            if (checkType != null) {
                record["checkType"] = checkType
            }

            val docRef = db.collection("users").document(user.uid)
                .collection("dailyStats").document(todayStr)

            val updates: Map<String, Any> = hashMapOf(
                "points" to FieldValue.increment(points.toLong()),
                "studyRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("StudyLog", "addVoiceBonusPoints success")
        } catch (e: Exception) {
            Log.e("StudyLog", "addVoiceBonusPoints failed ($todayStr)", e)
        }
    }

    /**
     * アプリ解放に使用したポイントを保存する。
     */
    suspend fun addUsedPoints(usedPoints: Int, packageName: String, appLabel: String, unlockedMinutes: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.w("DailyStats", "save unlock skipped: user is null")
            return
        }
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        Log.d("DailyStats", "save unlock start uid=$uid app=$appLabel usedPoints=$usedPoints minutes=$unlockedMinutes")

        try {
            val now = Timestamp.now()
            val record: Map<String, Any> = hashMapOf(
                "type" to "unlock",
                "packageName" to packageName,
                "appLabel" to appLabel,
                "unlockedMinutes" to unlockedMinutes.toLong(),
                "usedPoints" to usedPoints.toLong(),
                "timestamp" to now
            )

            val docRef = db.collection("users").document(uid)
                .collection("dailyStats").document(todayStr)

            val updates: Map<String, Any> = hashMapOf(
                "usedPoints" to FieldValue.increment(usedPoints.toLong()),
                "unlockRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("DailyStats", "save unlock success")
        } catch (e: Exception) {
            Log.e("DailyStats", "save unlock failed", e)
        }
    }

    /**
     * マスター数を保存する。
     */
    suspend fun updateMasteryCounts(
        lv1: Int,
        lv2: Int,
        lv3: Int,
        shortCount: Int,
        longCount: Int
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val updates = hashMapOf(
            "lv1Count" to lv1.toLong(),
            "lv2Count" to lv2.toLong(),
            "lv3Count" to lv3.toLong(),
            "shortMasterCount" to shortCount.toLong(),
            "longMasterCount" to longCount.toLong(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("users").document(user.uid)
                .collection("dailyStats").document(todayStr)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("StudyLog", "マスター数の保存に失敗しました", e)
        }
    }
}
