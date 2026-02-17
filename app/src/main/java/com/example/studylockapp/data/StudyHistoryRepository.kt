package com.example.studylockapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 学習履歴をFirebase(Firestore)に保存するための共通リポジトリ
 * どのActivityからでも StudyHistoryRepository.save(...) で呼び出せます。
 */
object StudyHistoryRepository {

    fun save(grade: String, mode: String, isCorrect: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // 今日の日付 (例: "2026-01-21")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 保存するデータ（詳細履歴）
        val record: Map<String, Any> = hashMapOf(
            "grade" to grade,
            "mode" to mode,
            "isCorrect" to isCorrect,
            "timestamp" to Date()
        )

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        // ★ポイント設定（必要に応じて引数で渡せるように改造も可能）
        // 現状は 正解=10pt, 不正解=0pt で集計
        val pointsToAdd = if (isCorrect) 10L else 0L
        val correctToAdd = if (isCorrect) 1L else 0L

        // トランザクションで安全に書き込み
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)

            if (!snapshot.exists()) {
                // 新規作成（その日最初の学習）
                val newData: Map<String, Any> = hashMapOf(
                    "points" to pointsToAdd,
                    "studyCount" to 1L,
                    "correctCount" to correctToAdd,
                    "gradesStudied" to listOf(grade),
                    "modesStudied" to listOf(mode),
                    "studyRecords" to listOf(record),
                    "updatedAt" to Date()
                )
                transaction.set(docRef, newData)
            } else {
                // 既存更新（加算/配列追加）
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
        }.addOnSuccessListener {
            Log.d("StudyLog", "記録保存成功($todayStr): $grade / $mode / 正解=$isCorrect (+$pointsToAdd pt)")
        }.addOnFailureListener { e ->
            Log.e("StudyLog", "記録保存失敗($todayStr)", e)
        }
    }
}