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
    private val lastNotifiedTimestamps = mutableMapOf<String, Long>()

    private fun observeFriendStatus(context: Context, friendUid: String) {
        if (friendListeners.containsKey(friendUid)) return

        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("users").document(friendUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val broadcastAt = snapshot.getTimestamp("goalMetBroadcastAt") ?: return@addSnapshotListener
                val broadcastMillis = broadcastAt.toDate().time
                
                // 重複通知を防止（同じタイムスタンプなら通知済み）
                if (lastNotifiedTimestamps[friendUid] == broadcastMillis) return@addSnapshotListener
                
                // 5分以内のブロードキャストのみ通知する
                val diffMillis = System.currentTimeMillis() - broadcastMillis
                if (diffMillis > -60000 && diffMillis < 5 * 60 * 1000) {
                    val lastGoalStreak = snapshot.getLong("lastGoalStreak")?.toInt() ?: 0
                    val friendName = friendNicknames[friendUid] ?: snapshot.getString("displayName") ?: "友達"

                    val settings = com.example.studylockapp.data.AppSettings(context)
                    val character = StudyCharacter.fromId(settings.selectedCharacterId)
                    
                    // タイトルを完全に空にすると表示されない端末があるため、キャラ名を入れる
                    val title = character.displayName
                    val message = CharacterLines.getLine(
                        character = character, 
                        context = NotificationContext.FRIEND_GOAL_MET, 
                        streak = lastGoalStreak, 
                        name = settings.userName ?: "きみ",
                        friendName = friendName
                    )

                    // 選択中のキャラのmini_panic画像を探す
                    var imageResId = context.resources.getIdentifier(
                        "mini_${character.id}_panic", "drawable", context.packageName
                    )
                    // 見つからない場合は通常のキャラ画像
                    if (imageResId == 0) {
                        imageResId = context.resources.getIdentifier(
                            "char_${character.id}", "drawable", context.packageName
                        )
                    }

                    lastNotifiedTimestamps[friendUid] = broadcastMillis
                    NotificationHelper.showNotification(context, title, message, imageResId)
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
