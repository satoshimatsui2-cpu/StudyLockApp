package com.example.studylockapp.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * フレンドの目標達成をリアルタイムで監視し、通知を出すクラス
 */
object FriendNotificationManager {
    private const val TAG = "FriendNotifMgr"
    private var listenerRegistration: ListenerRegistration? = null

    /**
     * 監視を開始する
     */
    fun startListening(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (listenerRegistration != null) return

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        // 1. まず自分のフレンドリストを取得し、そのUIDリストの変化を監視する
        // (簡略化のため、アプリ起動時に一括でリスナーを貼る設計にします)
        db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Friend list listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val friendUid = dc.document.id
                        observeFriendStatus(context, friendUid)
                    }
                }
            }
    }

    private val friendListeners = mutableMapOf<String, ListenerRegistration>()

    private fun observeFriendStatus(context: Context, friendUid: String) {
        if (friendListeners.containsKey(friendUid)) return

        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("users").document(friendUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val lastGoalMetDate = snapshot.getString("lastGoalMetDate")
                val lastGoalStats = snapshot.getString("lastGoalStats") ?: ""
                val broadcastAt = snapshot.getTimestamp("goalMetBroadcastAt") ?: return@addSnapshotListener
                val friendName = snapshot.getString("displayName") ?: "友達"

                // 5分以内のブロードキャストのみ通知する（重複や古い通知を防ぐ）
                val diffMillis = System.currentTimeMillis() - broadcastAt.toDate().time
                if (diffMillis < 5 * 60 * 1000) {
                    val title = "フレンドの目標達成！"
                    val message = "${friendName}さんが本日の目標（${lastGoalStats}）を達成しました！"
                    NotificationHelper.showNotification(context, title, message)
                }
            }
        
        friendListeners[friendUid] = listener
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        friendListeners.values.forEach { it.remove() }
        friendListeners.clear()
    }
}
