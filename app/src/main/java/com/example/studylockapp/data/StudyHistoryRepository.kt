package com.example.studylockapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    fun updateLastActiveStatus(onSuccess: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val updates = hashMapOf<String, Any>(
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "dailyReportPaused" to false,
            "dailyReportPauseReason" to FieldValue.delete(),
            "dailyReportPausedAt" to FieldValue.delete(),
            "inactivityNoticeSentAt" to FieldValue.delete()
        )

        db.collection("users").document(user.uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("StudyLog", "Activity更新失敗", e)
            }
    }

    /**
     * 学習結果を保存する。
     */
    fun save(
        grade: String,
        mode: String,
        isCorrect: Boolean,
        points: Int = 0
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val record: Map<String, Any> = hashMapOf(
            "type" to "study",
            "grade" to grade,
            "mode" to mode,
            "isCorrect" to isCorrect,
            "earnedPoints" to points.toLong(),
            "timestamp" to Date()
        )

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        val updates: Map<String, Any> = hashMapOf(
            "points" to FieldValue.increment(points.toLong()),
            "studyCount" to FieldValue.increment(1L),
            "correctCount" to FieldValue.increment(if (isCorrect) 1L else 0L),
            "gradesStudied" to FieldValue.arrayUnion(grade),
            "modesStudied" to FieldValue.arrayUnion(mode),
            "studyRecords" to FieldValue.arrayUnion(record),
            "updatedAt" to Date()
        )

        docRef.set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updateLastActiveStatus()
            }
            .addOnFailureListener { e ->
                Log.e("StudyLog", "学習記録の保存に失敗しました($todayStr)", e)
            }
    }

    /**
     * 音声チェックによるボーナスポイントを加算する。
     * 学習回数(studyCount)等には影響を与えず、ポイントと個別レコードのみを保存する。
     */
    fun addVoiceBonusPoints(
        grade: String,
        word: String,
        points: Int,
        checkType: String? = null
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val record: MutableMap<String, Any> = hashMapOf(
            "type" to "voice_bonus",
            "grade" to grade,
            "word" to word,
            "earnedPoints" to points.toLong(),
            "timestamp" to Date()
        )
        if (checkType != null) {
            record["checkType"] = checkType
        }

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        val updates: Map<String, Any> = hashMapOf(
            "points" to FieldValue.increment(points.toLong()),
            "studyRecords" to FieldValue.arrayUnion(record),
            "updatedAt" to Date()
        )

        docRef.set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updateLastActiveStatus()
            }
            .addOnFailureListener { e ->
                Log.e("StudyLog", "音声ボーナスの保存に失敗しました($todayStr)", e)
            }
    }

    /**
     * アプリ解放に使用したポイントを保存する。
     */
    fun addUsedPoints(usedPoints: Int, packageName: String, appLabel: String, unlockedMinutes: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val record: Map<String, Any> = hashMapOf(
            "type" to "unlock",
            "packageName" to packageName,
            "appLabel" to appLabel,
            "unlockedMinutes" to unlockedMinutes.toLong(),
            "usedPoints" to usedPoints.toLong(),
            "timestamp" to Date()
        )

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        val updates: Map<String, Any> = hashMapOf(
            "usedPoints" to FieldValue.increment(usedPoints.toLong()),
            "pointsUsed" to FieldValue.increment(usedPoints.toLong()),
            "studyRecords" to FieldValue.arrayUnion(record),
            "updatedAt" to Date()
        )

        docRef.set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updateLastActiveStatus()
            }
            .addOnFailureListener { e ->
                Log.e("StudyLog", "ポイント使用記録の保存に失敗しました($todayStr)", e)
            }
    }

    /**
     * マスター数を保存する。
     */
    fun updateMasteryCounts(shortCount: Int, longCount: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val updates = hashMapOf(
            "shortMasterCount" to shortCount.toLong(),
            "longMasterCount" to longCount.toLong(),
            "updatedAt" to Date()
        )
        db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)
            .set(updates, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("StudyLog", "マスター数の保存に失敗しました", e)
            }
    }
}
