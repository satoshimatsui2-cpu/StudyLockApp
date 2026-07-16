package com.example.studylockapp.service

import android.content.Context
import android.util.Log
import com.example.studylockapp.data.notification.CharacterLines
import com.example.studylockapp.data.notification.NotificationContext
import com.example.studylockapp.data.notification.StudyCharacter
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

    // フレンドUIDと、あなたが設定した表示名（ニックネーム）のマップ
    private val friendNicknames = mutableMapOf<String, String>()

    /**
     * 監視を開始する
     */
    fun startListening(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (listenerRegistration != null) return

        val db = FirebaseFirestore.getInstance()
        val myUid = user.uid

        // 1. まず自分のフレンドリスト（ニックネームを含む）の変化を監視する
        listenerRegistration = db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Friend list listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    val friendUid = dc.document.id
                    val nickname = dc.document.getString("displayName") ?: "友達"

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            friendNicknames[friendUid] = nickname
                            observeFriendStatus(context, friendUid)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            // 名前が編集されたらメモリ上の値を更新
                            friendNicknames[friendUid] = nickname
                        }
                        DocumentChange.Type.REMOVED -> {
                            friendNicknames.remove(friendUid)
                            friendListeners[friendUid]?.remove()
                            friendListeners.remove(friendUid)
                        }
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

                val broadcastAt = snapshot.getTimestamp("goalMetBroadcastAt") ?: return@addSnapshotListener
                
                // 5分以内のブロードキャストのみ通知する
                val diffMillis = System.currentTimeMillis() - broadcastAt.toDate().time
                if (diffMillis < 5 * 60 * 1000) {
                    val lastGoalStreak = snapshot.getLong("lastGoalStreak")?.toInt() ?: 0
                    
                    // 【重要】編集した名前があればそれを使う。なければ相手の設定名を使う。
                    val friendName = friendNicknames[friendUid] ?: snapshot.getString("displayName") ?: "友達"

                    val settings = com.example.studylockapp.data.AppSettings(context)
                    val character = StudyCharacter.fromId(settings.selectedCharacterId)
                    
                    val title = "" // タイトルは不要との要望により空文字に
                    val message = CharacterLines.getLine(
                        character, 
                        NotificationContext.FRIEND_GOAL_MET, 
                        streak = lastGoalStreak, 
                        name = friendName
                    )

                    // miniのpanicアイコンを通知に含める
                    NotificationHelper.showNotification(context, title, message, com.example.studylockapp.R.drawable.mini_shion_panic)
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
