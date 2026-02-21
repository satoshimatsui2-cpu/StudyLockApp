package com.example.studylockapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object StudyHistoryRepository {

    private fun todayTokyoStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return sdf.format(Date())
    }

    fun save(grade: String, mode: String, isCorrect: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // ★Tokyo固定（Cloud Functionsとズレない）
        val todayStr = todayTokyoStr()

        val record: Map<String, Any> = hashMapOf(
            "type" to "study",
            "grade" to grade,
            "mode" to mode,
            "isCorrect" to isCorrect,
            "timestamp" to Date()
        )

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        val pointsToAdd = if (isCorrect) 10L else 0L
        val correctToAdd = if (isCorrect) 1L else 0L

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)

            if (!snapshot.exists()) {
                val newData: Map<String, Any> = hashMapOf(
                    "points" to pointsToAdd,
                    // ★互換維持: usedPoints（旧）
                    "usedPoints" to 0L,
                    // ★親向け: pointsUsed（Cloud Functionsが読む）
                    "pointsUsed" to 0L,

                    "studyCount" to 1L,
                    "correctCount" to correctToAdd,
                    "gradesStudied" to listOf(grade),
                    "modesStudied" to listOf(mode),
                    "studyRecords" to listOf(record),
                    "updatedAt" to Date()
                )
                transaction.set(docRef, newData)
            } else {
                val updates: Map<String, Any> = hashMapOf(
                    "points" to FieldValue.increment(pointsToAdd),
                    "studyCount" to FieldValue.increment(1L),
                    "correctCount" to FieldValue.increment(correctToAdd),
                    "gradesStudied" to FieldValue.arrayUnion(grade),
                    "modesStudied" to FieldValue.arrayUnion(mode),
                    "studyRecords" to FieldValue.arrayUnion(record),
                    "updatedAt" to Date()
                )
                transaction.update(docRef, updates)
            }
        }.addOnFailureListener { e ->
            Log.e("StudyLog", "記録保存失敗($todayStr)", e)
        }
    }

    // ★追加：アンロックで「使ったポイント」を日次集計に加算
    fun addUsedPoints(usedPoints: Int, packageName: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // ★Tokyo固定（Cloud Functionsとズレない）
        val todayStr = todayTokyoStr()

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        val record: Map<String, Any> = hashMapOf(
            "type" to "unlock",
            "packageName" to packageName,
            "usedPoints" to usedPoints.toLong(),
            "timestamp" to Date()
        )

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)

            if (!snapshot.exists()) {
                val newData: Map<String, Any> = hashMapOf(
                    "points" to 0L,

                    // ★互換維持: usedPoints（旧）
                    "usedPoints" to usedPoints.toLong(),
                    // ★親向け: pointsUsed（Cloud Functionsが読む）
                    "pointsUsed" to usedPoints.toLong(),

                    "studyCount" to 0L,
                    "correctCount" to 0L,
                    "gradesStudied" to emptyList<String>(),
                    "modesStudied" to emptyList<String>(),
                    "studyRecords" to listOf(record),
                    "updatedAt" to Date()
                )
                transaction.set(docRef, newData)
            } else {
                val updates: Map<String, Any> = hashMapOf(
                    // ★互換維持: usedPoints（旧）
                    "usedPoints" to FieldValue.increment(usedPoints.toLong()),
                    // ★親向け: pointsUsed（Cloud Functionsが読む）
                    "pointsUsed" to FieldValue.increment(usedPoints.toLong()),

                    "studyRecords" to FieldValue.arrayUnion(record),
                    "updatedAt" to Date()
                )
                transaction.update(docRef, updates)
            }
        }.addOnFailureListener { e ->
            Log.e("StudyLog", "使用ポイント保存失敗($todayStr)", e)
        }
    }
}