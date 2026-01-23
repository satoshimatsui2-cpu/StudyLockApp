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
        val record = hashMapOf(
            "grade" to grade,
            "mode" to mode,
            "isCorrect" to isCorrect,
            "timestamp" to Date()
        )

        val docRef = db.collection("users").document(user.uid)
            .collection("dailyStats").document(todayStr)

        // トランザクションで安全に書き込み
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)

            // ★ポイント設定（必要に応じて引数で渡せるように改造も可能）
            // 現状は 正解=10pt, 不正解=0pt で集計
            val pointsToAdd = if (isCorrect) 10 else 0

            if (!snapshot.exists()) {
                // 新規作成
                val newData = hashMapOf(
                    "points" to pointsToAdd.toLong(),
                    "studyRecords" to listOf(record)
                )
                transaction.set(docRef, newData)
            } else {
                // 既存更新（合計加算 ＋ 配列に追加）
                val newPoints = (snapshot.getLong("points") ?: 0) + pointsToAdd
                transaction.update(docRef, "points", newPoints)
                transaction.update(docRef, "studyRecords", FieldValue.arrayUnion(record))
            }
        }.addOnSuccessListener {
            Log.d("StudyLog", "記録保存成功: $grade / $mode / 正解=$isCorrect")
        }.addOnFailureListener { e ->
            Log.e("StudyLog", "記録保存失敗", e)
        }
    }
}