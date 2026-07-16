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
    suspend fun updateLastActiveStatus(customName: String? = null, onSuccess: (() -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. 引数の名前 2. Firebase Authの表示名 3. Fallback
        val myName = customName ?: user.displayName ?: "User-${user.uid.takeLast(4)}"

        val updates = hashMapOf<String, Any>(
            "displayName" to myName,
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
     * @param currentTotalPoints 保存時点でのユーザーの総保有ポイント（スナップショット）
     */
    suspend fun save(
        grade: String,
        mode: String,
        isCorrect: Boolean,
        points: Int = 0,
        word: String? = null,
        currentTotalPoints: Int? = null
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

            val updates: MutableMap<String, Any> = hashMapOf(
                "points" to FieldValue.increment(points.toLong()),
                "studyCount" to FieldValue.increment(1L),
                "correctCount" to FieldValue.increment(if (isCorrect) 1L else 0L),
                "gradesStudied" to FieldValue.arrayUnion(grade),
                "modesStudied" to FieldValue.arrayUnion(mode),
                "studyRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // 総保有ポイントのスナップショットを保存
            if (currentTotalPoints != null) {
                updates["lastKnownPoints"] = currentTotalPoints.toLong()
            }

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
     * @param currentTotalPoints ポイント加算後の総保有ポイント
     */
    suspend fun addVoiceBonusPoints(
        grade: String,
        word: String,
        points: Int,
        checkType: String? = null,
        currentTotalPoints: Int? = null
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

            val updates: MutableMap<String, Any> = hashMapOf(
                "points" to FieldValue.increment(points.toLong()),
                "studyRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (currentTotalPoints != null) {
                updates["lastKnownPoints"] = currentTotalPoints.toLong()
            }

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("StudyLog", "addVoiceBonusPoints success")
        } catch (e: Exception) {
            Log.e("StudyLog", "addVoiceBonusPoints failed ($todayStr)", e)
        }
    }

    /**
     * アプリ解放に使用したポイントを保存する。
     * @param currentTotalPoints ポイント使用後の総保有ポイント
     */
    suspend fun addUsedPoints(
        usedPoints: Int,
        packageName: String,
        appLabel: String,
        unlockedMinutes: Int,
        currentTotalPoints: Int? = null
    ) {
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

            val updates: MutableMap<String, Any> = hashMapOf(
                "usedPoints" to FieldValue.increment(usedPoints.toLong()),
                "unlockRecords" to FieldValue.arrayUnion(record),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (currentTotalPoints != null) {
                updates["lastKnownPoints"] = currentTotalPoints.toLong()
            }

            docRef.set(updates, SetOptions.merge()).await()
            updateLastActiveStatus()
            Log.d("DailyStats", "save unlock success")
        } catch (e: Exception) {
            Log.e("DailyStats", "save unlock failed", e)
        }
    }

    /**
     * 総保有ポイントのスナップショットのみを更新する（ポイント返却時などに使用）。
     */
    suspend fun updatePointSnapshot(currentTotalPoints: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        try {
            val docRef = db.collection("users").document(user.uid)
                .collection("dailyStats").document(todayStr)

            val updates = mapOf(
                "lastKnownPoints" to currentTotalPoints.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(updates, SetOptions.merge()).await()
            Log.d("DailyStats", "updatePointSnapshot success: $currentTotalPoints")
        } catch (e: Exception) {
            Log.e("DailyStats", "updatePointSnapshot failed", e)
        }
    }

    /**
     * マスター数を保存する。
     */
    suspend fun updateMasteryCounts(
        lv1: Int,
        lv2: Int,
        lv3: Int,
        shortMasterCount: Int,
        longMasterCount: Int
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val updates = hashMapOf(
            "lv1Count" to lv1.toLong(),
            "lv2Count" to lv2.toLong(),
            "lv3Count" to lv3.toLong(),
            "shortMasterCount" to shortMasterCount.toLong(),
            "longMasterCount" to longMasterCount.toLong(),
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

    // --- Friend Connection Features ---

    /**
     * フレンドを追加する (相互)
     */
    suspend fun addFriend(friendUid: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.e("FriendConnection", "addFriend: No current user")
            return false
        }
        val myUid = user.uid
        if (myUid == friendUid) return false

        val db = FirebaseFirestore.getInstance()
        val myName = user.displayName ?: "ユーザー"

        try {
            // 1. 相手の存在確認
            val friendDoc = db.collection("users").document(friendUid).get().await()
            if (!friendDoc.exists()) {
                Log.w("FriendConnection", "addFriend: Friend UID $friendUid not found in users collection")
                return false
            }
            val friendName = friendDoc.getString("displayName") ?: "友達"

            // 2. 自分のフレンドリストに追加
            db.collection("users").document(myUid).collection("friends").document(friendUid)
                .set(mapOf("displayName" to friendName, "addedAt" to FieldValue.serverTimestamp()))
                .await()

            // 3. 相手のフレンドリストに自分を追加
            // ※Firestoreのセキュリティルールで許可されている必要があります
            db.collection("users").document(friendUid).collection("friends").document(myUid)
                .set(mapOf("displayName" to myName, "addedAt" to FieldValue.serverTimestamp()))
                .await()

            return true
        } catch (e: Exception) {
            Log.e("FriendConnection", "フレンド追加失敗: ${e.message}", e)
            return false
        }
    }

    /**
     * 目標達成をフレンドに通知するためにステータスを更新する
     */
    suspend fun broadcastGoalMet(newWords: Int, reviews: Int, streak: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val todayStr = todayTokyoStr()

        val updates = hashMapOf(
            "lastGoalMetDate" to todayStr,
            "lastGoalStats" to "新規${newWords}問、復習${reviews}問",
            "lastGoalStreak" to streak.toLong(),
            "goalMetBroadcastAt" to FieldValue.serverTimestamp()
        )

        try {
            db.collection("users").document(user.uid)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FriendConnection", "目標達成のブロードキャスト失敗", e)
        }
    }

    /**
     * フレンド一覧を取得する
     */
    suspend fun getFriends(): List<Pair<String, String>> {
        val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        val db = FirebaseFirestore.getInstance()
        
        return try {
            val snapshot = db.collection("users").document(user.uid).collection("friends").get().await()
            snapshot.documents.map { it.id to (it.getString("displayName") ?: "友達") }
        } catch (e: Exception) {
            Log.e("FriendConnection", "フレンド取得失敗", e)
            emptyList()
        }
    }

    /**
     * フレンドの表示名を変更する (ローカル表示用)
     */
    suspend fun updateFriendDisplayName(friendUid: String, newName: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val db = FirebaseFirestore.getInstance()

        try {
            db.collection("users").document(user.uid)
                .collection("friends").document(friendUid)
                .update("displayName", newName)
                .await()
            return true
        } catch (e: Exception) {
            Log.e("FriendConnection", "フレンド名更新失敗", e)
            return false
        }
    }

    /**
     * フレンドを削除する (相互)
     */
    suspend fun removeFriend(friendUid: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val myUid = user.uid

        val db = FirebaseFirestore.getInstance()

        try {
            // 1. 自分のフレンドリストから削除
            db.collection("users").document(myUid).collection("friends").document(friendUid)
                .delete()
                .await()

            // 2. 相手のフレンドリストから自分を削除
            db.collection("users").document(friendUid).collection("friends").document(myUid)
                .delete()
                .await()

            return true
        } catch (e: Exception) {
            Log.e("FriendConnection", "フレンド削除失敗", e)
            return false
        }
    }
}
